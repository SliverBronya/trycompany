import request from '@/utils/request'

// 看板顶部指标卡：记录总数 / 本月巡田 / 高风险数 / 已诊断数 / 剂量拦截次数 / 平均置信度 / 地块数 / 待复查数
export function getDashboardStats() {
  return request({
    url: '/tz/dashboard/stats',
    method: 'get'
  })
}

// 看板图表数据：诊断分布 / 风险分布 / 来源占比 / 巡田趋势
export function getDashboardCharts() {
  return request({
    url: '/tz/dashboard/charts',
    method: 'get'
  })
}
