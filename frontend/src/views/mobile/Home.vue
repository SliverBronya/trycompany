<template>
  <div class="m-page">
    <header class="m-hero">
      <div class="m-hero-top">
        <div>
          <div class="m-brand-line"><TzBrandMark compact /><div class="m-hero-title">田诊助手</div></div>
          <div class="m-hero-sub">{{ modeText }}</div>
        </div>
        <button class="m-hero-profile" type="button" aria-label="打开个人菜单" @click="mineVisible = true">
          <el-icon :size="20"><User /></el-icon>
        </button>
      </div>
      <button class="m-capture-action" type="button" @click="go('/m/record/new')">
        <span class="m-capture-icon"><el-icon :size="22"><Camera /></el-icon></span>
        <span><b>开始巡田</b><small>拍照、记录症状并生成处置依据</small></span>
        <span class="m-capture-arrow">→</span>
      </button>
      <div class="m-hero-actions" aria-label="快捷入口">
        <button class="m-hero-btn" type="button" @click="go('/m/plots')">
          <el-icon :size="18"><MapLocation /></el-icon><span>查看地块</span>
        </button>
        <button class="m-hero-btn" type="button" @click="go('/m/tasks')">
          <span>待办复查 {{ pick('pendingTaskCount') }}</span>
        </button>
      </div>
    </header>

    <div class="m-section">
      <div class="m-section-heading"><div><span>田间动态</span><strong>今天值得先处理的事</strong></div><button type="button" @click="go('/m/tasks')">查看复查</button></div>
      <button class="m-priority-card" type="button" @click="go('/m/tasks')">
        <span class="m-priority-mark" :class="pick('highRiskRecords') ? 'is-high' : ''"></span>
        <span><b>{{ pick('highRiskRecords') ? '存在高风险记录，建议优先复查' : '当前没有高风险巡田记录' }}</b><small>待办复查 {{ pick('pendingTaskCount') }} 项 · 点击进入处理清单</small></span>
        <span>›</span>
      </button>
    </div>

    <div class="m-section">
      <div class="m-section-heading"><div><span>工作概览</span><strong>本周期的田间数据</strong></div></div>
      <div class="m-grid-3 m-stat-grid">
        <div class="m-stat" v-for="card in cards" :key="card.key">
          <div class="m-stat-value" :class="card.tone ? 'is-' + card.tone : ''">{{ card.value }}</div>
          <div class="m-stat-label">{{ card.label }}</div>
        </div>
      </div>
    </div>

    <div class="m-section">
      <div class="m-section-heading"><div><span>最近记录</span><strong>继续查看上一次巡田</strong></div><button type="button" @click="go('/m/records')">全部记录</button></div>
      <div v-if="loading" class="m-tip">加载中…</div>
      <div v-else-if="!records.length" class="m-tip">还没有巡田记录，点上方「拍照巡田」开始</div>
      <div
        v-for="r in records"
        :key="r.recordId"
        class="m-rec-item"
        @click="go('/m/record/' + r.recordId)"
      >
        <TzImage v-if="r.imageUrl" :url="r.imageUrl" class-name="m-rec-thumb" />
        <div v-else class="m-rec-thumb m-rec-thumb--empty">
          <el-icon :size="18"><Picture /></el-icon>
        </div>
        <div class="m-rec-body">
          <div class="m-row">
            <span class="m-strong">{{ r.diagnosisName || '待诊断' }}</span>
            <span class="m-tag" :class="riskClass(r.riskLevel)">{{ riskText(r.riskLevel) }}</span>
          </div>
          <div class="m-muted m-ellipsis">{{ r.plotName || '—' }} · {{ r.scoutTime || '' }}</div>
        </div>
      </div>
    </div>

    <!-- 我的 -->
    <el-drawer v-model="mineVisible" direction="rtl" size="72%">
      <template #header><b>我的</b></template>
      <div class="m-drawer-item" @click="go('/m/plots')">地块管理</div>
      <div class="m-drawer-item" @click="go('/m/knowledge')">植保知识库</div>
      <div class="m-drawer-item" @click="go('/m/company')">
        我的公司
        <span v-if="!hasCompany" class="m-tag is-mid" style="float:right">未加入</span>
      </div>
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
import { User, Camera, MapLocation, Picture } from '@element-plus/icons-vue'
import { isCustomServer } from '@/utils/tzServer'
import request from '@/utils/request'
import TzImage from '@/components/TzImage/index.vue'
import TzBrandMark from '@/components/TzBrandMark/index.vue'

const router = useRouter()
const stats = ref({})
const records = ref([])
const loading = ref(false)
const mineVisible = ref(false)
const isCustom = ref(false)
const hasCompany = ref(true)
const modeText = ref('')

/**
 * 概览指标。
 *
 * 原来每项都硬编码一个颜色（墨绿/浅绿/红/黄交替出现），但那几种颜色之间
 * 并没有语义差别 —— 纯粹是装饰。颜色一多，真正需要行动的两项（高风险、待复查）
 * 反而淹在中间。现在只有这两项上色，其余统一墨色，颜色重新变回信号。
 */
const cards = ref([
  { key: 'totalRecords', label: '巡田总数', value: 0 },
  { key: 'monthRecords', label: '本月巡田', value: 0 },
  { key: 'highRiskRecords', label: '高风险', value: 0, tone: 'high' },
  { key: 'pendingTaskCount', label: '待复查', value: 0, tone: 'mid' },
  { key: 'plotCount', label: '地块数', value: 0 },
  { key: 'avgConfidence', label: '平均置信', value: 0 }
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
watch(mineVisible, async v => {
  if (!v) return
  isCustom.value = isCustomServer()
  try {
    // 没公司的用户必须先走引导页，否则他能进来却什么都做不了，还以为是系统坏了
    const res = await request({ url: '/tz/company/mine' })
    hasCompany.value = !!(res.data && res.data.companyId)
  } catch (e) {
    hasCompany.value = false
  }
})

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

<style scoped>
/* 只有需要行动的两项上色：高风险（土红）与待复查（赭黄）。
   六个数字全上色 = 都不上色，视线反而抓不住该先看哪个。 */
.m-stat-value.is-high {
  color: var(--tz-risk-high);
}
.m-stat-value.is-mid {
  color: var(--tz-risk-mid);
}
</style>
