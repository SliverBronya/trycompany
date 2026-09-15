<template>
  <div class="app-container">
    <el-row :gutter="12">
      <el-col v-for="card in statCards" :key="card.key" :xs="12" :sm="8" :md="6" :lg="3" class="card-box">
        <el-card shadow="hover" class="tz-stat">
          <div class="tz-stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          <div class="tz-stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert
      v-if="aiConfig.mode === 'preset'"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="当前未配置大模型 API Key"
    >
      <template #default>
        诊断走的是预置样张映射 + 知识库检索，功能完整可演示，但「AI 初诊」这一环并未真正调用大模型。
        配好 Key 后无需改代码即可自动切换。
      </template>
    </el-alert>

    <el-row :gutter="12" v-loading="loading">
      <el-col :xs="24" :md="12" class="card-box">
        <el-card shadow="hover">
          <template #header>
            <PieChart class="tz-icon" /> <span>诊断结果分布</span>
          </template>
          <div ref="diagnosisRef" class="tz-chart" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12" class="card-box">
        <el-card shadow="hover">
          <template #header>
            <Warning class="tz-icon" /> <span>风险等级分布</span>
          </template>
          <div ref="riskRef" class="tz-chart" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12" class="card-box">
        <el-card shadow="hover">
          <template #header>
            <Histogram class="tz-icon" /> <span>近 30 天巡田趋势</span>
          </template>
          <div ref="trendRef" class="tz-chart" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12" class="card-box">
        <el-card shadow="hover">
          <template #header>
            <Connection class="tz-icon" /> <span>结论来源占比</span>
          </template>
          <div ref="sourceRef" class="tz-chart" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="TzDashboard">
import * as echarts from 'echarts'
import { getDashboardStats, getDashboardCharts } from '@/api/tianzhen/dashboard'
import { getAiConfig } from '@/api/tianzhen/ai'

const loading = ref(true)
const stats = ref({})
const aiConfig = ref({})

const diagnosisRef = ref(null)
const riskRef = ref(null)
const trendRef = ref(null)
const sourceRef = ref(null)

// 保存实例用于销毁。ECharts 实例挂在 DOM 上，组件卸载时不 dispose 会连带
// 监听器和 canvas 一起留着，来回切菜单几次就能明显感到卡。
const instances = []

/**
 * 指标卡。
 *
 * 「剂量拦截次数」故意放在首页而不是藏进日志：这是本系统合规约束的可见证据，
 * 演示时指着这个数字比口述「我们做了剂量校验」有说服力。
 */
const statCards = computed(() => [
  { key: 'totalRecords', label: '巡田记录总数', value: pick('totalRecords'), color: '#409eff' },
  { key: 'monthRecords', label: '本月巡田', value: pick('monthRecords'), color: '#67c23a' },
  { key: 'diagnosedRecords', label: '已完成诊断', value: pick('diagnosedRecords'), color: '#909399' },
  { key: 'highRiskRecords', label: '高风险记录', value: pick('highRiskRecords'), color: '#f56c6c' },
  { key: 'pendingTaskCount', label: '待复查任务', value: pick('pendingTaskCount'), color: '#e6a23c' },
  { key: 'plotCount', label: '地块数量', value: pick('plotCount'), color: '#409eff' },
  { key: 'avgConfidence', label: '平均置信度', value: pick('avgConfidence'), color: '#67c23a' },
  { key: 'dosageGuardHits', label: '剂量拦截次数', value: pick('dosageGuardHits'), color: '#f56c6c' }
])

function pick(key) {
  const value = stats.value[key]
  return value === null || value === undefined ? 0 : value
}

/** 饼图通用配置：无数据时 ECharts 会画成空白，所以先兜一个占位 */
function pieOption(title, list) {
  const data = (list || []).map(item => ({ name: item.name, value: Number(item.value) }))
  if (!data.length) {
    return {
      title: { text: '暂无数据', left: 'center', top: 'middle', textStyle: { color: '#909399', fontSize: 14, fontWeight: 'normal' } }
    }
  }
  return {
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 条（{d}%）' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        name: title,
        type: 'pie',
        radius: ['38%', '62%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        label: { formatter: '{b}: {c}' },
        data
      }
    ]
  }
}

/** 折线图：后端按天聚合，缺的日子直接没有行，这里不补零——补零会画出不存在的低谷 */
function lineOption(list) {
  const rows = list || []
  if (!rows.length) {
    return {
      title: { text: '近 30 天暂无巡田记录', left: 'center', top: 'middle', textStyle: { color: '#909399', fontSize: 14, fontWeight: 'normal' } }
    }
  }
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: rows.map(item => item.date), boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '巡田次数',
        type: 'line',
        smooth: true,
        areaStyle: {},
        data: rows.map(item => Number(item.count))
      }
    ]
  }
}

function render() {
  getDashboardCharts().then(response => {
    const charts = response.data || {}
    const specs = [
      [diagnosisRef.value, pieOption('诊断结果', charts.diagnosisDistribution)],
      [riskRef.value, pieOption('风险等级', charts.riskDistribution)],
      [trendRef.value, lineOption(charts.scoutTrend)],
      [sourceRef.value, pieOption('结论来源', charts.sourceDistribution)]
    ]
    specs.forEach(([el, option]) => {
      if (!el) return
      const instance = echarts.init(el)
      instance.setOption(option)
      instances.push(instance)
    })
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

function handleResize() {
  instances.forEach(instance => instance.resize())
}

onMounted(() => {
  getDashboardStats().then(response => {
    stats.value = response.data || {}
  }).catch(() => {})
  getAiConfig().then(response => {
    aiConfig.value = response.data || {}
  }).catch(() => {})
  render()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  instances.forEach(instance => instance.dispose())
  instances.length = 0
})
</script>

<style scoped>
.tz-stat {
  text-align: center;
}
.tz-stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.4;
}
.tz-stat-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.tz-chart {
  height: 320px;
}
.tz-icon {
  width: 1em;
  height: 1em;
  vertical-align: middle;
}
</style>
