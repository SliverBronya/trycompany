import request from '@/utils/request'
import { AI_TIMEOUT } from './ai'

// 生成防治建议（未诊断的记录会被后端拒绝，需先执行诊断）
export function generateSuggestion(recordId, useLlm = true) {
  return request({
    url: '/tz/suggestion/generate/' + recordId,
    method: 'post',
    params: { useLlm },
    timeout: AI_TIMEOUT
  })
}

// 查询已生成的防治建议
export function getSuggestionResult(recordId) {
  return request({
    url: '/tz/suggestion/result/' + recordId,
    method: 'get'
  })
}
