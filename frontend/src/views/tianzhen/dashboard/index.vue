<template>
  <div class="app-container">
    <!-- 概览：一张卡里排 8 个指标，而不是 8 张等宽小卡片。
         卡片一多就成了"卡片墙"——每个数字都要单独扫一遍，还得在 8 个圆角框之间跳。
         排成一张带网格线的数据块才像台账，视线可以顺着行列走。 -->
    <el-card shadow="never" class="tz-overview">
      <div class="tz-metric-grid">
        <div v-for="card in statCards" :key="card.key" class="tz-metric">
          <div class="tz-metric-value" :class="{ 'is-alert': card.alert }">{{ card.value }}</div>
          <div class="tz-metric-label">{{ card.label }}</div>
        </div>
      </div>
    </el-card>

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
import { TZ_CHART_COLORS, TZ_RISK_COLORS, chartTextStyle, emptyChartOption } from '@/utils/echartsTheme'

/** ECharts 把文字和线条画在 canvas 上，读不到 CSS 变量，只能按当前模式取固定色值 */
function isDark() {
  return document.documentElement.classList.contains('dark')
}

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
 *
 * 顺序按阅读逻辑重排：总量 → 规模 → 本月进度 → 质量 → 待办 → 风险。
 * 原来八项的颜色是逐项硬编码的（蓝绿灰红黄），既跟全站墨绿体系打架，
 * 也让"颜色"承担了它不该承担的区分职责 —— 不同颜色之间并没有语义差别。
 * 现在只有真正的告警项（高风险、剂量拦截）上色，其余用统一的墨色，
 * 颜色才重新变成信号。
 */
const statCards = computed(() => [
  { key: 'totalRecords', label: '巡田记录总数', value: pick('totalRecords') },
  { key: 'plotCount', label: '地块数量', value: pick('plotCount') },
  { key: 'monthRecords', label: '本月巡田', value: pick('monthRecords') },
  { key: 'diagnosedRecords', label: '已完成诊断', value: pick('diagnosedRecords') },
  { key: 'avgConfidence', label: '平均置信度', value: pick('avgConfidence') },
  { key: 'pendingTaskCount', label: '待复查任务', value: pick('pendingTaskCount') },
  { key: 'highRiskRecords', label: '高风险记录', value: pick('highRiskRecords'), alert: true },
  { key: 'dosageGuardHits', label: '剂量拦截次数', value: pick('dosageGuardHits'), alert: true }
])

function pick(key) {
  const value = stats.value[key]
  return value === null || value === undefined ? 0 : value
}

/** 饼图通用配置：无数据时 ECharts 会画成空白，所以先兜一个占位 */
function pieOption(title, list, colorSet) {
  const data = (list || []).map(item => ({ name: item.name, value: Number(item.value) }))
  if (!data.length) {
    return emptyChartOption('暂无数据')
  }
  const t = chartTextStyle(isDark())
  return {
    color: colorSet || TZ_CHART_COLORS,
    tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 条（{d}%）' },
    legend: {
      bottom: 0,
      type: 'scroll',
      textStyle: { color: t.secondary, fontSize: 12 },
      itemWidth: 9,
      itemHeight: 9,
      itemGap: 14
    },
    series: [
      {
        name: title,
        type: 'pie',
        radius: ['40%', '64%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        /* 扇区之间留一点底色缝隙，比描边干净 */
        itemStyle: { borderColor: 'transparent', borderWidth: 2 },
        label: { formatter: '{b}: {c}', color: t.primary, fontSize: 12 },
        labelLine: { lineStyle: { color: t.axisLine } },
        data
      }
    ]
  }
}

/** 折线图：后端按天聚合，缺的日子直接没有行，这里不补零——补零会画出不存在的低谷 */
function lineOption(list) {
  const rows = list || []
  if (!rows.length) {
    return emptyChartOption('近 30 天暂无巡田记录')
  }
  const t = chartTextStyle(isDark())
  return {
    color: TZ_CHART_COLORS,
    tooltip: { trigger: 'axis' },
    grid: { left: 46, right: 22, top: 26, bottom: 32 },
    xAxis: {
      type: 'category',
      data: rows.map(item => item.date),
      boundaryGap: false,
      axisLine: { lineStyle: { color: t.axisLine } },
      axisTick: { show: false },
      axisLabel: { color: t.secondary, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: t.secondary, fontSize: 11 },
      /* 横向网格线保留、纵向去掉：只有 y 轴需要参照线，画满格子就成坐标纸了 */
      splitLine: { lineStyle: { color: t.splitLine } }
    },
    series: [
      {
        name: '巡田次数',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 2 },
        areaStyle: { color: 'rgba(47, 107, 79, 0.12)' },
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
      [riskRef.value, pieOption('风险等级', charts.riskDistribution, TZ_RISK_COLORS)],
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
.tz-overview {
  margin-bottom: 12px;

  /* 让网格贴到卡片边缘，否则卡片自身的 18px 内边距会在网格外再套一圈白边，
     看起来像"框里套框" */
  :deep(.el-card__body) {
    padding: 0;
  }
}

/* 网格线用 1px 的 gap 露出底色来实现，比给每格加 border 干净：
   不会出现相邻边框叠加导致的粗细不均 */
.tz-metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  background-color: var(--tz-line-soft);
  border-radius: calc(var(--tz-radius) - 1px);
  overflow: hidden;
}

.tz-metric {
  background-color: var(--tz-surface);
  padding: 15px 16px;
}

/* 只有真正的告警项上色 —— 高风险与剂量拦截。
   其余指标一律用墨色，颜色才重新变回信号，而不是逐项点缀的装饰。 */
.tz-metric-value.is-alert {
  color: var(--tz-risk-high);
}

.tz-chart {
  height: 320px;
}

.tz-icon {
  width: 1em;
  height: 1em;
  vertical-align: middle;
  color: var(--tz-primary);
}

@media (max-width: 1100px) {
  .tz-metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .tz-metric-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
