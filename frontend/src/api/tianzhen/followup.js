import request from '@/utils/request'

// 查询复查任务列表
export function listFollowup(query) {
  return request({
    url: '/tz/followup/list',
    method: 'get',
    params: query
  })
}

// 查询复查任务详细
export function getFollowup(taskId) {
  return request({
    url: '/tz/followup/' + taskId,
    method: 'get'
  })
}

// 新增复查任务（一般由巡田报告自动生成，此处为手工补录）
export function addFollowup(data) {
  return request({
    url: '/tz/followup',
    method: 'post',
    data: data
  })
}

// 修改复查任务
export function updateFollowup(data) {
  return request({
    url: '/tz/followup',
    method: 'put',
    data: data
  })
}

/**
 * 完成复查 / 改期。
 *
 * 传 status='1' 完成时后端会回写巡田记录为「已完成」，闭环由后端保证，
 * 前端不要自己再去改记录状态，否则两处逻辑迟早对不上。
 */
export function changeFollowupStatus(data) {
  return request({
    url: '/tz/followup/status',
    method: 'put',
    data: data
  })
}

// 删除复查任务
export function delFollowup(taskId) {
  return request({
    url: '/tz/followup/' + taskId,
    method: 'delete'
  })
}
