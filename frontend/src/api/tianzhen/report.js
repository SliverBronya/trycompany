import request from '@/utils/request'
import { AI_TIMEOUT } from './ai'

// 生成巡田报告（同时会按风险等级自动排一条复查任务）
export function generateReport(recordId, useLlm = true) {
  return request({
    url: '/tz/report/generate/' + recordId,
    method: 'post',
    params: { useLlm },
    timeout: AI_TIMEOUT
  })
}

// 查询已生成的巡田报告
export function getReportResult(recordId) {
  return request({
    url: '/tz/report/result/' + recordId,
    method: 'get'
  })
}
