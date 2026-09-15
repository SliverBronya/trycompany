import request from '@/utils/request'
import { AI_TIMEOUT } from './ai'

/**
 * 根据刚上传的照片自动生成「症状描述」。
 *
 * 按 imageUrl 而不是 recordId 取图：这个动作发生在巡田记录保存之前，
 * 此刻还没有主键；后端读完图即用，不落业务数据。
 *
 * 用 AI_TIMEOUT 而不是全局 10 秒：未命中预置样张时要交给多模态模型读图，
 * 十秒经常不够，用户会看到「请求超时」而模型其实还在正常生成。
 */
export function describeRecordImage(imageUrl, plantPart) {
  return request({
    url: '/tz/record/describe-image',
    method: 'post',
    params: { imageUrl, plantPart },
    timeout: AI_TIMEOUT
  })
}

// 查询巡田记录列表
export function listRecord(query) {
  return request({
    url: '/tz/record/list',
    method: 'get',
    params: query
  })
}

// 查询巡田记录详细
export function getRecord(recordId) {
  return request({
    url: '/tz/record/' + recordId,
    method: 'get'
  })
}

// 新增巡田记录（返回 data.recordId，供后续诊断直接串起来）
export function addRecord(data) {
  return request({
    url: '/tz/record',
    method: 'post',
    data: data
  })
}

// 修改巡田记录
export function updateRecord(data) {
  return request({
    url: '/tz/record',
    method: 'put',
    data: data
  })
}

// 删除巡田记录（级联删除其复查任务）
export function delRecord(recordId) {
  return request({
    url: '/tz/record/' + recordId,
    method: 'delete'
  })
}
