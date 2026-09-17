/**
 * 图表配色 —— 与全站「农事台账」的墨绿纸张体系对齐。
 *
 * 为什么需要它：ECharts 出厂色板是 #5470c6 / #91cc75 / #fac858 / #ee6666 这一组
 * 高饱和蓝绿黄红，单独看没问题，但放在米白底 + 墨绿的界面里非常跳，
 * 一眼就能看出"图表是默认的、没设计过"。
 *
 * 用法：
 *   import { TZ_CHART_COLORS, chartTextStyle } from '@/utils/echartsTheme'
 *   series[0].color = TZ_CHART_COLORS
 *   // 或直接给 option 传 color
 */

/** 主色板：墨绿打头，后面是苔绿 / 赭黄 / 土红 / 灰蓝 / 卡其 —— 都是低饱和的"土色系" */
export const TZ_CHART_COLORS = [
  '#2f6b4f', // 墨绿，主
  '#7fa88c', // 浅苔绿
  '#bd8330', // 赭黄
  '#b4453a', // 土红
  '#4a6f8a', // 灰蓝
  '#8c7a5e', // 卡其
  '#5f8f76', // 中绿
  '#a8654f'  // 陶土
]

/** 风险等级专用：低/中/高，语义固定，不要跟主色板混用 */
export const TZ_RISK_COLORS = ['#4e7c5b', '#bd8330', '#b4453a']

/**
 * 图表文字色。ECharts 把文字画在 canvas 上，读不到 CSS 变量，
 * 只能按当前亮暗模式取一组固定值。暗色下用亮灰，否则字会糊在深底上。
 */
export function chartTextStyle(isDark) {
  return {
    primary: isDark ? '#c2c9c5' : '#3d4f47',
    secondary: isDark ? '#8d9792' : '#6f8078',
    axisLine: isDark ? '#33383a' : '#e4e0d5',
    splitLine: isDark ? '#2b2f31' : '#efede5'
  }
}

/** 表格/图表里的空数据占位文字，两处口径保持一致 */
export function emptyChartOption(text) {
  return {
    title: {
      text: text || '暂无数据',
      left: 'center',
      top: 'middle',
      textStyle: { color: '#9aa8a1', fontSize: 13, fontWeight: 'normal' }
    }
  }
}
