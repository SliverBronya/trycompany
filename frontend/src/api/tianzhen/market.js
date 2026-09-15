import request from '@/utils/request'

// 查询行情列表
export function listMarket(query) {
  return request({
    url: '/tz/market/list',
    method: 'get',
    params: query
  })
}

// 查询行情详细
export function getMarket(priceId) {
  return request({
    url: '/tz/market/' + priceId,
    method: 'get'
  })
}

// 新增行情（source 为必填：行情必须能说清数据从哪来）
export function addMarket(data) {
  return request({
    url: '/tz/market',
    method: 'post',
    data: data
  })
}

// 修改行情
export function updateMarket(data) {
  return request({
    url: '/tz/market',
    method: 'put',
    data: data
  })
}

// 删除行情
export function delMarket(priceId) {
  return request({
    url: '/tz/market/' + priceId,
    method: 'delete'
  })
}
