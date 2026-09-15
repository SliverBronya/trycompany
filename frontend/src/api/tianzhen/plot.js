import request from '@/utils/request'

// 查询地块列表
export function listPlot(query) {
  return request({
    url: '/tz/plot/list',
    method: 'get',
    params: query
  })
}

// 查询地块详细
export function getPlot(plotId) {
  return request({
    url: '/tz/plot/' + plotId,
    method: 'get'
  })
}

// 新增地块
export function addPlot(data) {
  return request({
    url: '/tz/plot',
    method: 'post',
    data: data
  })
}

// 修改地块
export function updatePlot(data) {
  return request({
    url: '/tz/plot',
    method: 'put',
    data: data
  })
}

// 删除地块（该地块下还有巡田记录时后端会拒绝）
export function delPlot(plotId) {
  return request({
    url: '/tz/plot/' + plotId,
    method: 'delete'
  })
}

// 地块下拉选项
export function plotOptionSelect() {
  return request({
    url: '/tz/plot/optionselect',
    method: 'get'
  })
}
