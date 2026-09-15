<template>
  <div class="m-page m-chat">
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
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { askQuestion, listQaMessages } from '@/api/tianzhen/qa'
import { Loading } from '@element-plus/icons-vue'

const messages = ref([])
const question = ref('')
const asking = ref(false)
const threadRef = ref()
let sessionId = undefined

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

async function ask() {
  const text = (question.value || '').trim()
  if (!text || asking.value) return

  messages.value.push({ messageId: 'local-' + Date.now(), role: 'user', content: text })
  // 立刻清空输入框：等答案回来才清，会让人以为没发出去而重复发送
  question.value = ''
  asking.value = true
  scrollToBottom()

  try {
    const res = await askQuestion(text, sessionId, true)
    const data = res.data || {}
    sessionId = data.sessionId
    const list = await listQaMessages(sessionId)
    messages.value = (list.data || []).filter(m => m.role === 'user' || m.role === 'assistant')
    scrollToBottom()
  } catch (e) {
    messages.value = messages.value.filter(m => String(m.messageId).indexOf('local-') !== 0)
    question.value = text
    ElMessage.error('提问失败，内容已为您保留')
  } finally {
    asking.value = false
  }
}
</script>

<style scoped>
.m-chat {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.m-chat-body {
  flex: 1;
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
.m-bubble {
  max-width: 82%;
  background: #fff;
  border-radius: 12px;
  padding: 10px 12px;
  box-shadow: 0 1px 4px rgba(15, 110, 86, 0.08);
  font-size: 14px;
  line-height: 1.7;
}
.m-bubble.is-me {
  background: #0f6e56;
  color: #fff;
}
.m-bubble.is-me .m-text {
  color: #fff;
}
/* 底部输入框要给 tabbar 让位 */
.m-chat-input {
  display: flex;
  gap: 8px;
  padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1px solid #ebeef5;
  margin-bottom: 58px;
}
.m-chat-text {
  flex: 1;
  border: 1px solid #dfe5e2;
  border-radius: 18px;
  padding: 9px 14px;
  font-size: 15px;
  resize: none;
  max-height: 120px;
  line-height: 1.5;
}
.m-chat-text:focus {
  outline: none;
  border-color: #1d9e75;
}
.m-chat-send {
  border: none;
  background: #0f6e56;
  color: #fff;
  border-radius: 18px;
  padding: 0 16px;
  font-size: 15px;
  -webkit-tap-highlight-color: transparent;
}
.m-chat-send:disabled {
  background: #b3c2bc;
}
</style>
