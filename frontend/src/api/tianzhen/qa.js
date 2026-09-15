import request from '@/utils/request'
import { AI_TIMEOUT } from './ai'

/**
 * 提问并取得回答。
 *
 * sessionId 传空表示开一个新会话，返回结果里会带上新建的 sessionId，
 * 之后追问沿用同一个即可。
 */
export function askQuestion(question, sessionId, useLlm = true) {
  return request({
    url: '/tz/qa/ask',
    method: 'post',
    params: { question, sessionId, useLlm },
    timeout: AI_TIMEOUT
  })
}

// 读取会话的完整问答记录（只读，不重新作答）
export function listQaMessages(sessionId) {
  return request({
    url: '/tz/qa/messages/' + sessionId,
    method: 'get'
  })
}

// 查询会话列表
export function listQaSession(query) {
  return request({
    url: '/tz/qa/session/list',
    method: 'get',
    params: query
  })
}

// 查询会话详细
export function getQaSession(sessionId) {
  return request({
    url: '/tz/qa/session/' + sessionId,
    method: 'get'
  })
}

// 关闭会话（保留记录，只是不再接受追问）
export function closeQaSession(sessionId) {
  return request({
    url: '/tz/qa/session/close/' + sessionId,
    method: 'put'
  })
}

// 删除会话（连同其下全部消息）
export function delQaSession(sessionId) {
  return request({
    url: '/tz/qa/session/' + sessionId,
    method: 'delete'
  })
}
