<template>
  <div class="m-page">
    <header class="m-hero">
      <div class="m-hero-top">
        <div>
          <div class="m-hero-title">田诊助手</div>
          <div class="m-hero-sub">{{ modeText }}</div>
        </div>
        <el-icon :size="20" @click="mineVisible = true"><User /></el-icon>
      </div>
      <div class="m-hero-actions">
        <div class="m-hero-btn" @click="go('/m/record/new')">
          <el-icon :size="24"><Camera /></el-icon>
          <span>拍照巡田</span>
        </div>
        <div class="m-hero-btn" @click="go('/m/plots')">
          <el-icon :size="24"><MapLocation /></el-icon>
          <span>地块</span>
        </div>
      </div>
    </header>

    <div class="m-section">
      <div class="m-section-title">概览</div>
      <div class="m-grid-3">
        <div class="m-stat" v-for="card in cards" :key="card.key">
          <div class="m-stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="m-stat-label">{{ card.label }}</div>
        </div>
      </div>
    </div>

    <div class="m-section">
      <div class="m-section-title">最近巡田</div>
      <div v-if="loading" class="m-tip">加载中…</div>
      <div v-else-if="!records.length" class="m-tip">还没有巡田记录，点上方「拍照巡田」开始</div>
      <div
        v-for="r in records"
        :key="r.recordId"
        class="m-card m-card-link"
        @click="go('/m/record/' + r.recordId)"
      >
        <div class="m-row">
          <span class="m-strong">{{ r.diagnosisName || '待诊断' }}</span>
          <span class="m-tag" :class="riskClass(r.riskLevel)">{{ riskText(r.riskLevel) }}</span>
        </div>
        <div class="m-muted m-ellipsis">{{ r.plotName || '—' }} · {{ r.scoutTime || '' }}</div>
      </div>
    </div>

    <!-- 我的 -->
    <el-drawer v-model="mineVisible" direction="rtl" size="72%">
      <template #header><b>我的</b></template>
      <div class="m-drawer-item" @click="go('/m/plots')">地块管理</div>
      <div class="m-drawer-item" @click="go('/m/server')">
        服务器设置
        <span class="m-tag" :class="isCustom ? 'is-mid' : ''" style="float:right">
          {{ isCustom ? '自定义' : '默认' }}
        </span>
      </div>
      <div class="m-drawer-item" @click="switchDesktop">切换到电脑版</div>
      <div class="m-drawer-item m-danger" @click="logout">退出登录</div>
    </el-drawer>
  </div>
</template>

<script setup name="MHome">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { getDashboardStats } from '@/api/tianzhen/dashboard'
import { listRecord } from '@/api/tianzhen/record'
import { getAiConfig } from '@/api/tianzhen/ai'
import { User, Camera, MapLocation } from '@element-plus/icons-vue'
import { isCustomServer } from '@/utils/tzServer'

const router = useRouter()
const stats = ref({})
const records = ref([])
const loading = ref(false)
const mineVisible = ref(false)
const isCustom = ref(false)
const modeText = ref('')

const cards = ref([
  { key: 'totalRecords', label: '巡田总数', value: 0, color: '#0f6e56' },
  { key: 'monthRecords', label: '本月巡田', value: 0, color: '#1d9e75' },
  { key: 'highRiskRecords', label: '高风险', value: 0, color: '#d9534f' },
  { key: 'pendingTaskCount', label: '待复查', value: 0, color: '#e6a23c' },
  { key: 'plotCount', label: '地块数', value: 0, color: '#0f6e56' },
  { key: 'avgConfidence', label: '平均置信', value: 0, color: '#1d9e75' }
])

function pick(key) {
  const v = stats.value[key]
  return v === null || v === undefined ? 0 : v
}

function riskText(level) {
  return { '3': '高', '2': '中', '1': '低' }[String(level)] || '—'
}
function riskClass(level) {
  return { '3': 'is-high', '2': 'is-mid', '1': 'is-low' }[String(level)] || ''
}

function go(path) {
  mineVisible.value = false
  router.push(path)
}

function switchDesktop() {
  localStorage.setItem('tz-prefer', 'desktop')
  window.location.href = '/index'
}

function logout() {
  ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' }).then(() => {
    localStorage.removeItem('tz-prefer')
    router.push('/logout')
  }).catch(() => {})
}

// 打开抽屉时现读一次：用户可能刚在「服务器设置」里改过地址
watch(mineVisible, v => { if (v) isCustom.value = isCustomServer() })

onMounted(async () => {
  loading.value = true
  try {
    const [s, r, c] = await Promise.all([
      getDashboardStats(),
      listRecord({ pageNum: 1, pageSize: 5 }),
      getAiConfig()
    ])
    stats.value = s.data || {}
    records.value = (r.rows || []).slice(0, 5)
    modeText.value = (c.data || {}).modeText || ''
  } finally {
    loading.value = false
  }
  cards.value.forEach(c => { c.value = pick(c.key) })
})
</script>
