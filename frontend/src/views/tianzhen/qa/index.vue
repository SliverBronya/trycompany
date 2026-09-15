<template>
  <div class="app-container">
    <div class="tz-page">
      <el-card shadow="never" class="tz-head">
        <div class="tz-head-row">
          <el-select
            v-model="sessionId"
            placeholder="新会话（未选择时提问会自动建一个）"
            clearable
            filterable
            style="flex: 1; min-width: 200px"
            @change="handleSessionChange"
          >
            <el-option v-for="item in sessions" :key="item.sessionId" :label="item.sessionTitle" :value="item.sessionId">
              <span>{{ item.sessionTitle }}</span>
              <span class="tz-option-meta">{{ item.msgCount || 0 }} 条 · {{ parseTime(item.createTime, '{m}-{d} {h}:{i}') }}</span>
            </el-option>
          </el-select>
          <el-button icon="Plus" @click="startNewSession">新会话</el-button>
          <el-button icon="Clock" @click="historyOpen = true">历史</el-button>
        </div>

        <el-alert
          v-if="aiConfig.mode === 'preset'"
          type="warning"
          :closable="false"
          show-icon
          class="tz-tip"
          title="当前未配置大模型 Key"
        >
          <template #default>
            只能在知识库检索到明确条目时作答；检索不到会直接告诉您「本次不作答」，而不会给一个听起来合理的猜测。
          </template>
        </el-alert>
      </el-card>

      <el-card shadow="never" class="tz-thread-card">
        <div ref="threadRef" v-loading="loading" class="tz-thread">
          <el-empty v-if="!messages.length && !loading" description="问点什么吧，例如：叶片上出现近圆形病斑、周围有黄色晕圈是怎么回事？" />

          <div v-for="msg in messages" :key="msg.messageId" :class="['tz-msg', msg.role === 'user' ? 'tz-msg-user' : 'tz-msg-ai']">
            <div class="tz-bubble">
              <pre class="tz-pre">{{ msg.content }}</pre>
              <div v-if="msg.role === 'assistant'" class="tz-meta">
                <el-tag v-if="msg.answerStatus === '1'" type="warning" size="small" effect="plain">知识库无匹配</el-tag>
                <el-tag v-else-if="msg.answerStatus === '2'" type="info" size="small" effect="plain">降级作答</el-tag>
                <el-tag v-else type="success" size="small" effect="plain">已作答</el-tag>
                <span v-if="msg.sources" class="tz-sources">依据：{{ msg.sources }}</span>
              </div>
            </div>
          </div>

          <div v-if="asking" class="tz-msg tz-msg-ai">
            <div class="tz-bubble tz-bubble-wait">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>正在检索知识库并作答…</span>
            </div>
          </div>
        </div>

        <div class="tz-input">
          <el-input
            v-model="question"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="500"
            show-word-limit
            placeholder="描述越具体越容易匹配到知识库条目：作物与品种、部位、颜色与形态、出现时间、近期天气与用药"
            @keydown.enter.exact.prevent="handleAsk"
          />
          <el-button type="primary" icon="Promotion" :loading="asking" @click="handleAsk">发送</el-button>
        </div>
      </el-card>
    </div>

    <!-- 历史会话 -->
    <el-drawer v-model="historyOpen" title="历史会话" size="420px">
      <el-table :data="sessions" v-loading="sessionLoading" @row-click="handlePickSession">
        <el-table-column label="会话" prop="sessionTitle" :show-overflow-tooltip="true" />
        <el-table-column label="条数" prop="msgCount" width="70" align="center" />
        <el-table-column label="操作" width="120" align="center">
          <template #default="scope">
            <el-button
              link
              type="danger"
              icon="Delete"
              @click.stop="handleDeleteSession(scope.row)"
              v-hasPermi="['tz:qa:remove']"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup name="TzQa">
import { askQuestion, listQaMessages, listQaSession, delQaSession } from '@/api/tianzhen/qa'
import { getAiConfig } from '@/api/tianzhen/ai'

const { proxy } = getCurrentInstance()

const sessions = ref([])
const sessionId = ref(undefined)
const messages = ref([])
const question = ref('')
const loading = ref(false)
const asking = ref(false)
const sessionLoading = ref(false)
const historyOpen = ref(false)
const aiConfig = ref({})
const threadRef = ref(null)

/** 会话列表 */
function getSessions() {
  sessionLoading.value = true
  listQaSession({ pageNum: 1, pageSize: 50 }).then(response => {
    sessions.value = response.rows || []
    sessionLoading.value = false
  }).catch(() => {
    sessionLoading.value = false
  })
}

/** 读取会话消息 */
function getMessages() {
  if (!sessionId.value) {
    messages.value = []
    return
  }
  loading.value = true
  listQaMessages(sessionId.value).then(response => {
    messages.value = response.data || []
    loading.value = false
    scrollToBottom()
  }).catch(() => {
    loading.value = false
  })
}

function handleSessionChange() {
  getMessages()
}

function handlePickSession(row) {
  sessionId.value = row.sessionId
  historyOpen.value = false
  getMessages()
}

function startNewSession() {
  sessionId.value = undefined
  messages.value = []
  question.value = ''
}

/**
 * 提问。
 *
 * 不做「发送失败就把问题清空」这种事：农户打一段描述不容易，
 * 网络抖一下就得重打一遍，是让人直接放弃的那种体验。
 */
function handleAsk() {
  const text = (question.value || '').trim()
  if (!text) {
    proxy.$modal.msgWarning('请先输入您想问的问题')
    return
  }
  if (asking.value) {
    return
  }

  // 先把提问摆进气泡里，让人看到自己说了什么，而不是干等
  messages.value.push({ messageId: 'local-' + Date.now(), role: 'user', content: text })
  // 输入框**立刻**清空。原来这行写在 .then 里，要等 3~8 秒答案回来才清，
  // 期间输入框里还留着自己刚发的那句话 —— 看着像没发出去，容易让人再点一次发送。
  question.value = ''
  asking.value = true
  scrollToBottom()

  askQuestion(text, sessionId.value, true).then(response => {
    const data = response.data || {}
    sessionId.value = data.sessionId
    getMessages()
    getSessions()
    asking.value = false
  }).catch(() => {
    asking.value = false
    // 失败时把提问从气泡里撤掉，并**把原文放回输入框**——
    // 输入框已经清过了，不还原的话「内容已为您保留」这句提示就是空的承诺
    messages.value = messages.value.filter(item => String(item.messageId).indexOf('local-') !== 0)
    question.value = text
    proxy.$modal.msgError('提问失败，内容已为您保留，可稍后重试')
  })
}

function handleDeleteSession(row) {
  proxy.$modal.confirm('删除会话「' + row.sessionTitle + '」？该会话下的全部问答记录会一并删除。').then(function () {
    return delQaSession(row.sessionId)
  }).then(() => {
    if (sessionId.value === row.sessionId) {
      startNewSession()
    }
    getSessions()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

function scrollToBottom() {
  nextTick(() => {
    const el = threadRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

getSessions()
getAiConfig().then(response => {
  aiConfig.value = response.data || {}
}).catch(() => {})
</script>

<style scoped>
.tz-page {
  max-width: 720px;
  margin: 0 auto;
}
.tz-head {
  margin-bottom: 12px;
}
.tz-head-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.tz-tip {
  margin-top: 10px;
}
.tz-option-meta {
  float: right;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.tz-thread-card :deep(.el-card__body) {
  padding: 0;
}
.tz-thread {
  height: 52vh;
  min-height: 280px;
  overflow-y: auto;
  padding: 12px;
}
.tz-msg {
  display: flex;
  margin-bottom: 12px;
}
.tz-msg-user {
  justify-content: flex-end;
}
.tz-msg-ai {
  justify-content: flex-start;
}
.tz-bubble {
  max-width: 86%;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
  line-height: 1.7;
}
.tz-msg-user .tz-bubble {
  background: var(--el-color-primary-light-9);
}
.tz-bubble-wait {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
}
.tz-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
}
.tz-meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.tz-sources {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.tz-input {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  padding: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.tz-input :deep(.el-textarea) {
  flex: 1;
}
</style>
