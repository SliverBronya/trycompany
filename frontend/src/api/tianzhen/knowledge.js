import request from '@/utils/request'

// 查询知识库列表
export function listKnowledge(query) {
  return request({
    url: '/tz/knowledge/list',
    method: 'get',
    params: query
  })
}

// 查询知识库详细
export function getKnowledge(knowledgeId) {
  return request({
    url: '/tz/knowledge/' + knowledgeId,
    method: 'get'
  })
}

// 新增知识库条目
export function addKnowledge(data) {
  return request({
    url: '/tz/knowledge',
    method: 'post',
    data: data
  })
}

// 修改知识库条目
export function updateKnowledge(data) {
  return request({
    url: '/tz/knowledge',
    method: 'put',
    data: data
  })
}

// 删除知识库条目
export function delKnowledge(knowledgeId) {
  return request({
    url: '/tz/knowledge/' + knowledgeId,
    method: 'delete'
  })
}
