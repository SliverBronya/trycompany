<template>
  <div class="m-page m-chat">
    <!--
      顶部栏：标题 + 新对话 + 历史入口。
      原来这里什么都没有，会话只存在一个内存变量里，退出页面就找不回来 ——
      用户看不到自己问过什么，而"上次那个问题是怎么答的"恰恰是最需要回头翻的。
    -->
    <div class="m-chat-head">
      <span class="m-chat-title">农技问答</span>
      <div class="m-chat-actions">
        <el-icon :size="18" @click="newSession"><Plus /></el-icon>
        <el-icon :size="18" @click="openHistory"><Clock /></el-icon>
      </div>
    </div>

    <div class="m-chat-body" ref="threadRef">
      <div v-if="!messages.length && !asking" class="m-empty">
        有什么拿不准的，直接问<br />比如：叶片上有黄色晕圈是怎么回事？
      </div>

      <div
        v-for="m in messages"
        :key="m.messageId"
        class="m-bubble-row"
        :class="m.role === 'user' ? 'is-me' : ''"
      >
        <div class="m-bubble" :class="m.role === 'user' ? 'is-me' : ''">
          <div class="m-text" v-html="safe(m.content)"></div>
        </div>
      </div>

      <div v-if="asking" class="m-bubble-row">
        <div class="m-bubble">
          <el-icon class="is-loading"><Loading /></el-icon> 正在思考…
        </div>
      </div>
    </div>

    <div class="m-chat-input">
      <textarea
        v-model="question"
        class="m-chat-text"
        rows="1"
        placeholder="输入你的问题"
        @keydown.enter.exact.prevent="ask"
      />
      <button class="m-chat-send" :disabled="asking" @click="ask">发送</button>
    </div>

    <!-- 历史会话：从底部升起，点一条即可回看当时的问答 -->
    <el-drawer v-model="historyOpen" title="历史问答" direction="btt" size="68%">
      <div v-if="!sessions.length" class="m-empty" style="padding:36px 0">
        还没有历史问答记录
      </div>
      <div
        v-for="s in sessions"
        :key="s.sessionId"
        class="m-hist-item"
        :class="{ 'is-current': s.sessionId === sessionId }"
        @click="pickSession(s)"
      >
        <div class="m-hist-title">{{ s.sessionTitle || '未命名会话' }}</div>
        <div class="m-hist-meta">
          {{ s.msgCount || 0 }} 条消息<span v-if="s.createTime"> · {{ s.createTime }}</span>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { askQuestion, listQaMessages, listQaSession } from '@/api/tianzhen/qa'
import { Loading, Plus, Clock } from '@element-plus/icons-vue'

/** 当前会话号记在本地：退出页面再回来还能接着上次那份对话 */
const SESSION_KEY = 'tz-qa-session'

const messages = ref([])
const question = ref('')
const asking = ref(false)
const threadRef = ref()
const sessionId = ref(localStorage.getItem(SESSION_KEY) || undefined)

const sessions = ref([])
const historyOpen = ref(false)

// 回答是分段文本（【回答】【具体建议】…），前端只做换行与转义，
// 绝不用 v-html 渲染未经处理的内容
function safe(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br/>')
}

function scrollToBottom() {
  nextTick(() => {
    const el = threadRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/** 载入某个会话的消息；不传则用当前会话号 */
async function loadMessages(id) {
  const target = id || sessionId.value
  if (!target) return
  try {
    const res = await listQaMessages(target)
    messages.value = (res.data || []).filter(m => m.role === 'user' || m.role === 'assistant')
    scrollToBottom()
  } catch (e) {
    /* 读不出来就保持现状，不打断用户 */
  }
}

/** 打开历史。每次都重新拉：会话列表会因为刚提的问而变化，缓存了看到的就是旧的 */
async function openHistory() {
  historyOpen.value = true
  try {
    const res = await listQaSession({ pageNum: 1, pageSize: 50 })
    sessions.value = res.rows || []
  } catch (e) {
    sessions.value = []
  }
}

/** 点一条历史会话，回看当时问过什么、答了什么 */
async function pickSession(s) {
  sessionId.value = s.sessionId
  localStorage.setItem(SESSION_KEY, s.sessionId)
  historyOpen.value = false
  await loadMessages(s.sessionId)
}

/** 开一段新对话 */
function newSession() {
  sessionId.value = undefined
  localStorage.removeItem(SESSION_KEY)
  messages.value = []
}

async function ask() {
  const text = (question.value || '').trim()
  if (!text || asking.value) return

  messages.value.push({ messageId: 'local-' + Date.now(), role: 'user', content: text })
  // 立刻清空输入框：等答案回来才清，会让人以为没发出去而重复发送
  question.value = ''
  asking.value = true
  scrollToBottom()

  try {
    const res = await askQuestion(text, sessionId.value, true)
    const data = res.data || {}
    sessionId.value = data.sessionId
    localStorage.setItem(SESSION_KEY, data.sessionId)
    await loadMessages(data.sessionId)
  } catch (e) {
    messages.value = messages.value.filter(m => String(m.messageId).indexOf('local-') !== 0)
    question.value = text
    ElMessage.error('提问失败，内容已为您保留')
  } finally {
    asking.value = false
  }
}

// 回到这个页面时把上次那份对话接上，而不是每次从空白开始
onMounted(() => {
  if (sessionId.value) loadMessages()
})
</script>

<style scoped>
.m-chat {
  display: flex;
  flex-direction: column;
  /*
    关键修复：用 dvh 而不是 vh。

    vh 指的是「大视口高度」，移动端弹出键盘时它**不会变**。于是容器仍按
    整屏高度布局，而可视区已经被键盘压掉一半，输入栏就被顶到键盘下面、
    跟着页面上滑 —— 这正是「上划时输入栏一起跑」的根因。
    dvh 会随键盘与地址栏伸缩，输入栏因此始终贴在可视区底部。

    前一行 vh 是给不支持 dvh 的老浏览器兜底（会覆盖成上面那行）。
    减 58px 是给底部 tabbar 让位，这样就不必再给输入栏加 margin。
  */
  height: calc(100vh - 58px);
  height: calc(100dvh - 58px);
  background: var(--tz-paper);
}

.m-chat-head {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 46px;
  padding: 0 14px;
  background: var(--tz-surface);
  border-bottom: 1px solid var(--tz-line);
}

.m-chat-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--tz-ink);
}

.m-chat-actions {
  display: flex;
  align-items: center;
  gap: 18px;
  color: var(--tz-ink-3);
}

.m-chat-body {
  flex: 1;
  /* flex 子项默认 min-height:auto，不设 0 的话内容一多就会把容器撑高，
     内部的 overflow-y 也就永远不触发 —— 滚动会变成整页滚动 */
  min-height: 0;
  overflow-y: auto;
  padding: 12px 12px 8px;
  -webkit-overflow-scrolling: touch;
}

.m-bubble-row {
  display: flex;
  margin-bottom: 10px;
}

.m-bubble-row.is-me {
  justify-content: flex-end;
}

/* 气泡跟桌面端对齐：对方 = 白底细边，自己 = 主色实底。
   原来用的是一层绿味阴影来"浮起"，在浅底上显得脏，也跟桌面端不是一套。 */
.m-bubble {
  max-width: 82%;
  background: var(--tz-surface);
  border: 1px solid var(--tz-line);
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--tz-ink);
}

.m-bubble.is-me {
  background: var(--tz-primary);
  border-color: var(--tz-primary);
  color: #fff;
}

.m-bubble.is-me .m-text {
  color: #fff;
}

.m-chat-input {
  flex: 0 0 auto;
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  background: var(--tz-surface);
  border-top: 1px solid var(--tz-line);
}

.m-chat-text {
  flex: 1;
  border: 1px solid var(--tz-line);
  border-radius: 18px;
  padding: 9px 14px;
  font-size: 15px;
  line-height: 1.5;
  resize: none;
  max-height: 120px;
  background: var(--tz-surface);
  color: var(--tz-ink);
}

.m-chat-text:focus {
  outline: none;
  border-color: var(--tz-primary);
}

.m-chat-send {
  border: none;
  background: var(--tz-primary);
  color: #fff;
  border-radius: 18px;
  padding: 0 16px;
  font-size: 15px;
  -webkit-tap-highlight-color: transparent;
}

.m-chat-send:disabled {
  background: var(--tz-ink-4);
}

.m-hist-item {
  padding: 12px 4px;
  border-bottom: 1px solid var(--tz-line-soft);
}

.m-hist-item.is-current .m-hist-title {
  color: var(--tz-primary);
}

.m-hist-title {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.5;
  color: var(--tz-ink);
}

.m-hist-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--tz-ink-3);
}
</style>
