import request from '@/utils/request'

// 查询预置样张映射列表
export function listPreset(query) {
  return request({
    url: '/tz/preset/list',
    method: 'get',
    params: query
  })
}

// 查询预置样张映射详细
export function getPreset(presetId) {
  return request({
    url: '/tz/preset/' + presetId,
    method: 'get'
  })
}

// 新增预置样张映射
export function addPreset(data) {
  return request({
    url: '/tz/preset',
    method: 'post',
    data: data
  })
}

// 修改预置样张映射
export function updatePreset(data) {
  return request({
    url: '/tz/preset',
    method: 'put',
    data: data
  })
}

// 删除预置样张映射
export function delPreset(presetId) {
  return request({
    url: '/tz/preset/' + presetId,
    method: 'delete'
  })
}
