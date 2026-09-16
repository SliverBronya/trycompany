<template>
  <div class="m-page">
    <div class="m-navbar">
      <el-icon :size="20" @click="back"><ArrowLeft /></el-icon>
      <span>服务器设置</span>
      <span />
    </div>

    <div class="m-section">
      <div class="m-block">
        <div class="m-block-title">当前生效的地址</div>
        <div class="m-text" style="word-break:break-all">{{ current }}</div>
        <div class="m-muted" style="margin-top:6px">
          {{ isCustom ? '自定义地址' : '默认地址（打包时写入）' }}
        </div>
      </div>

      <div class="m-field">
        <div class="m-field-label">改成你的后端地址</div>
        <input
          v-model="draft"
          class="m-input"
          placeholder="https://你的域名/dev-api  或  http://192.168.1.5:18080"
          autocapitalize="off"
          autocorrect="off"
          spellcheck="false"
        />
        <div class="m-muted" style="margin-top:6px;line-height:1.7">
          · 公网演示：<b>https://你的ngrok地址/dev-api</b><br />
          · 同一 WiFi 下：<b>http://电脑的局域网IP:18080</b>（注意不用加 /dev-api，
          直连后端时地址就是它本身）
        </div>
      </div>

      <button class="m-btn is-plain" :disabled="testing" @click="test">
        {{ testing ? '测试中…' : '测试连接' }}
      </button>
      <div v-if="testResult" class="m-block" style="margin-top:12px">
        <div class="m-block-title">{{ testOk ? '连接正常' : '连不上' }}</div>
        <div class="m-text">{{ testResult }}</div>
      </div>

      <div class="m-btn-row">
        <button class="m-btn" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存并生效' }}
        </button>
      </div>
      <div class="m-btn-row">
        <button class="m-btn is-plain" @click="reset">恢复默认地址</button>
      </div>

      <div class="m-muted" style="margin:16px 2px;line-height:1.8">
        保存后立即生效，不用重启 APP —— 每次请求都会重新读这个地址。
        改完如果还是连不上，先点「测试连接」确认地址本身能通。
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getServerBase, setServerBase, isCustomServer, DEFAULT_SERVER, tunnelHeaders } from '@/utils/tzServer'

const router = useRouter()
const draft = ref('')
const current = ref('')
const isCustom = ref(false)
const testing = ref(false)
const saving = ref(false)
const testResult = ref('')
const testOk = ref(false)

function back() { router.back() }

function refresh() {
  current.value = getServerBase()
  isCustom.value = isCustomServer()
  draft.value = isCustom.value ? current.value : ''
}

/** 把地址拼成「一个可访问的探测点」。直连后端时 base 本身就是根，公网时带 /dev-api */
function probeUrl(base) {
  return base.replace(/\/+$/, '') + '/captchaImage'
}

async function test() {
  const url = (draft.value || '').trim()
  if (!url) {
    ElMessage.warning('先填一个地址')
    return
  }
  testing.value = true
  testResult.value = ''
  const t0 = Date.now()
  try {
    // 用 fetch 而不是项目里的 axios：这里要测的是**候选地址**，
    // 而 axios 实例带的是当前生效地址，用它测不出新地址通不通
    const ctl = new AbortController()
    const timer = setTimeout(() => ctl.abort(), 12000)
    const res = await fetch(probeUrl(url), { signal: ctl.signal, headers: tunnelHeaders() })
    clearTimeout(timer)
    const body = await res.text()
    const ms = Date.now() - t0
    if (res.ok && /"code"\s*:\s*200/.test(body)) {
      testOk.value = true
      testResult.value = '用时 ' + ms + ' ms，后端有正常响应。可以保存了。'
    } else {
      testOk.value = false
      testResult.value = 'HTTP ' + res.status + '，返回内容不是预期的接口响应。地址可能是对的但路径不对（比如少了或多了 /dev-api）。'
    }
  } catch (e) {
    testOk.value = false
    testResult.value = e.name === 'AbortError'
      ? '12 秒超时。常见原因：电脑或后端没开、地址写错、手机和电脑不在同一网络。'
      : ('请求失败：' + (e.message || e))
  } finally {
    testing.value = false
  }
}

function save() {
  const url = (draft.value || '').trim()
  if (!url) {
    ElMessage.warning('先填一个地址')
    return
  }
  if (!/^https?:\/\//i.test(url)) {
    ElMessage.warning('地址要以 http:// 或 https:// 开头')
    return
  }
  saving.value = true
  setServerBase(url)
  refresh()
  saving.value = false
  ElMessage.success('已保存，立即生效')
}

function reset() {
  setServerBase('')
  refresh()
  ElMessage.success('已恢复默认地址：' + DEFAULT_SERVER)
}

onMounted(refresh)
</script>
