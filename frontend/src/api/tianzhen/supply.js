import request from '@/utils/request'

// 查询供求信息列表
export function listSupply(query) {
  return request({
    url: '/tz/supply/list',
    method: 'get',
    params: query
  })
}

// 查询供求信息详细
export function getSupply(infoId) {
  return request({
    url: '/tz/supply/' + infoId,
    method: 'get'
  })
}

// 发布供求信息
export function addSupply(data) {
  return request({
    url: '/tz/supply',
    method: 'post',
    data: data
  })
}

// 修改供求信息
export function updateSupply(data) {
  return request({
    url: '/tz/supply',
    method: 'put',
    data: data
  })
}

// 变更发布状态（发布 / 下架 / 成交）
//
// 单独一个接口，不走 updateSupply：改状态只需要 infoId + status，而 updateSupply 会做
// 「品种、联系人、电话不能为空」的完整校验，把它挡回来。后端只更新动态拼进 SQL 的字段，
// 少传的内容不会被清空。
export function changeSupplyStatus(data) {
  return request({
    url: '/tz/supply/status',
    method: 'put',
    data: data
  })
}

// 删除供求信息
export function delSupply(infoId) {
  return request({
    url: '/tz/supply/' + infoId,
    method: 'delete'
  })
}
