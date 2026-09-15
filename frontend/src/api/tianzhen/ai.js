import request from '@/utils/request'

/**
 * AI 类接口的专用超时时间（毫秒）。
 *
 * request.js 全局超时是 10 秒，对 CRUD 足够，但诊断/建议/报告这三个接口
 * 背后可能是「调多模态大模型 → 拿回文本 → RAG 检索 → 剂量校验」一整套，
 * 十秒经常不够，用户会看到「系统接口请求超时」，而其实模型还在正常生成。
 * 这里按请求粒度放宽，不抬高全局超时——否则任何一个后端卡住的普通接口
 * 都要用户干等两分钟。
 */
export const AI_TIMEOUT = 120000

/**
 * 查询后端 AI 配置状态。
 *
 * 用来在界面上说清楚「这次结论是预置保底还是真大模型给的」，
 * 避免没配 key 时把查表结果讲成 AI 推理结果。
 */
export function getAiConfig() {
  return request({
    url: '/tz/diagnosis/config',
    method: 'get'
  })
}
