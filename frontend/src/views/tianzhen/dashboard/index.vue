<template>
  <div class="app-container tz-dashboard">
    <section class="tz-command-panel">
      <div class="tz-command-copy">
        <p class="tz-command-kicker">田间工作台</p>
        <h1>让每一次巡田<br />都有可追溯的判断。</h1>
        <p>从照片、症状到复查记录，结论与处置依据始终在同一条证据链里。</p>
        <div class="tz-command-actions">
          <el-button type="primary" size="large" @click="go('/record')"><Camera />新建巡田</el-button>
          <el-button size="large" @click="go('/followup')"><Clock />处理复查</el-button>
        </div>
      </div>
      <div class="tz-command-brief">
        <div class="tz-brief-title"><span class="tz-brief-dot" />今日优先处理</div>
        <div class="tz-brief-row">
          <span>待复查任务</span><strong>{{ pick('pendingTaskCount') }}</strong>
        </div>
        <div class="tz-brief-row is-risk">
          <span>高风险记录</span><strong>{{ pick('highRiskRecords') }}</strong>
        </div>
        <button class="tz-brief-link" @click="go('/followup')">查看复查清单</button>
      </div>
    </section>

    <section class="tz-overview tz-stagger">
      <div class="tz-overview-head">
        <h2>巡田概览</h2>
        <span>数据随当前公司范围更新</span>
      </div>
      <div class="tz-metric-grid">
        <div v-for="card in statCards" :key="card.key" class="tz-metric">
          <div class="tz-metric-value" :class="{ 'is-alert': card.alert }">{{ card.value }}</div>
          <div class="tz-metric-label">{{ card.label }}</div>
        </div>
      </div>
    </section>

    <el-alert
      v-if="!aiConfig.visionAvailable"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="图片诊断当前使用本地兜底链路"
    >
      <template #default>
        未命中预置样张时，照片诊断将走知识库检索；文本问答与报告仍可使用已配置的 DeepSeek。
        需要图片理解时，再单独配置兼容图片输入的视觉模型 Key。
      </template>
    </el-alert>

    <el-row :gutter="16" v-loading="loading" class="tz-chart-grid tz-stagger">
      <el-col :xs="24" :md="12" class="card-box">
        <el-card shadow="never">
          <template #header>
            <PieChart class="tz-icon" /> <span>诊断结果分布</span>
          </template>
          <div ref="diagnosisRef" class="tz-chart" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12" class="card-box">
        <el-card shadow="never">
          <template #header>
            <Warning class="tz-icon" /> <span>风险等级分布</span>
          </template>
          <div ref="riskRef" class="tz-chart" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12" class="card-box">
        <el-card shadow="never">
          <template #header>
            <Histogram class="tz-icon" /> <span>近 30 天巡田趋势</span>
          </template>
          <div ref="trendRef" class="tz-chart" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12" class="card-box">
        <el-card shadow="never">
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
import { Camera, Clock } from '@element-plus/icons-vue'

const router = useRouter()

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

function go(path) {
  router.push(path)
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
.tz-dashboard { max-width: 1500px; margin: 0 auto; }
.tz-command-panel {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(260px, .75fr);
  gap: 28px;
  padding: 34px 36px;
  margin-bottom: 18px;
  background: #234837;
  border-radius: 18px;
  color: #f4f7f3;
  overflow: hidden;
  position: relative;
}
.tz-command-panel::after { content: ''; position: absolute; width: 360px; height: 360px; border: 1px solid rgba(226, 239, 229, .13); border-radius: 50%; right: -130px; top: -190px; pointer-events: none; }
.tz-command-copy { position: relative; z-index: 1; }
.tz-command-kicker { margin: 0 0 12px; color: rgba(235, 244, 237, .66); font-size: 13px; letter-spacing: .08em; }
.tz-command-copy h1 { margin: 0; color: #fff; font-size: clamp(28px, 3vw, 42px); line-height: 1.2; letter-spacing: -.04em; font-weight: 600; }
.tz-command-copy > p:not(.tz-command-kicker) { max-width: 40em; margin: 16px 0 24px; color: rgba(235, 244, 237, .73); line-height: 1.8; font-size: 14px; }
.tz-command-actions { display: flex; gap: 10px; }
.tz-command-actions :deep(.el-button) { min-height: 42px; border-radius: 9px; }
.tz-command-actions :deep(.el-button--primary) { background: #eef5ef; color: #244735; border-color: #eef5ef; box-shadow: none; }
.tz-command-actions :deep(.el-button:not(.el-button--primary)) { background: transparent; color: #f4f7f3; border-color: rgba(238, 245, 239, .32); }
.tz-command-brief { position: relative; z-index: 1; align-self: center; padding: 20px; border: 1px solid rgba(238,245,239,.14); background: rgba(255,255,255,.065); border-radius: 12px; backdrop-filter: blur(8px); }
.tz-brief-title { display: flex; align-items: center; gap: 8px; color: rgba(244,247,243,.72); font-size: 13px; margin-bottom: 12px; }
.tz-brief-dot { width: 7px; height: 7px; border-radius: 50%; background: #9dc5aa; box-shadow: 0 0 0 4px rgba(157,197,170,.12); }
.tz-brief-row { display: flex; justify-content: space-between; align-items: baseline; padding: 10px 0; border-top: 1px solid rgba(238,245,239,.13); color: rgba(244,247,243,.77); font-size: 13px; }
.tz-brief-row strong { font-family: var(--tz-font-num); color: #fff; font-size: 26px; font-weight: 600; }
.tz-brief-row.is-risk strong { color: #f0bdad; }
.tz-brief-link { padding: 3px 0 0; background: none; border: 0; color: #bed6c3; font: inherit; font-size: 13px; cursor: pointer; }
.tz-overview { margin-bottom: 18px; padding: 4px 0 0; background: var(--tz-surface); border: 1px solid var(--tz-line); border-radius: var(--tz-radius-lg); box-shadow: var(--tz-shadow-1); overflow: hidden; }
.tz-overview-head { display: flex; align-items: baseline; justify-content: space-between; padding: 17px 20px 13px; }
.tz-overview-head h2 { margin: 0; font-size: 15px; font-weight: 600; color: var(--tz-ink); }
.tz-overview-head span { font-size: 12px; color: var(--tz-ink-3); }

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
  height: 300px;
}
.tz-chart-grid :deep(.el-card) { height: 100%; }
.tz-chart-grid :deep(.el-card__header) { padding: 15px 18px; }
.tz-stagger { animation: tz-rise .55s ease both; }
.tz-chart-grid.tz-stagger { animation-delay: .1s; }
@keyframes tz-rise { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
@media (prefers-reduced-motion: reduce) { .tz-stagger { animation: none; } }

.tz-icon {
  width: 1em;
  height: 1em;
  vertical-align: middle;
  color: var(--tz-primary);
}

@media (max-width: 1100px) {
  .tz-command-panel { grid-template-columns: 1fr; }
  .tz-metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .tz-command-panel { padding: 26px 20px; border-radius: 14px; }
  .tz-command-copy h1 { font-size: 28px; }
  .tz-command-actions { flex-wrap: wrap; }
  .tz-overview-head { align-items: flex-start; gap: 6px; flex-direction: column; }
  .tz-metric-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
