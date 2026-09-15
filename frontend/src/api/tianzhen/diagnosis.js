import request from '@/utils/request'
import { AI_TIMEOUT } from './ai'

/**
 * 对指定巡田记录发起 AI 诊断。
 *
 * useLlm=false 表示强制走预置映射保底，演示时网络不稳可切到这条路。
 */
export function diagnose(recordId, useLlm = true) {
  return request({
    url: '/tz/diagnosis/diagnose',
    method: 'post',
    params: { recordId, useLlm },
    timeout: AI_TIMEOUT
  })
}

// 查询某条巡田记录当前的诊断结论（只读，不重新推理）
export function getDiagnosisResult(recordId) {
  return request({
    url: '/tz/diagnosis/result/' + recordId,
    method: 'get'
  })
}
