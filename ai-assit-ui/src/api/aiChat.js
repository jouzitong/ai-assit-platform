import { buildUrl, request } from '../utils/request'
import { getToken } from '../utils/session'

const AI_CHAT_API_PREFIX = '/aiChat/api/v1/ai/chat'

function buildAuthorizedHeaders(extraHeaders = {}) {
  const token = getToken()
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...extraHeaders
  }
}

export function queryAiChat(payload) {
  return request(`${AI_CHAT_API_PREFIX}/query`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export async function queryAiChatStream(payload) {
  const response = await fetch(buildUrl(`${AI_CHAT_API_PREFIX}/query/stream`), {
    method: 'POST',
    headers: buildAuthorizedHeaders({
      Accept: 'text/event-stream;charset=UTF-8'
    }),
    body: JSON.stringify(payload)
  })

  if (!response.ok) {
    const errorText = await response.text().catch(() => '')
    throw new Error(errorText || `Request failed with status ${response.status}`)
  }

  return response
}

export function listAiChatConversations(payload) {
  return request(`${AI_CHAT_API_PREFIX}/conversation/list`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function detailAiChatConversation(payload) {
  return request(`${AI_CHAT_API_PREFIX}/conversation/detail`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function createAiChatConversation(payload) {
  return request(`${AI_CHAT_API_PREFIX}/conversation/create`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function renameAiChatConversation(payload) {
  return request(`${AI_CHAT_API_PREFIX}/conversation/rename`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function pinAiChatConversation(payload) {
  return request(`${AI_CHAT_API_PREFIX}/conversation/pin`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function deleteAiChatConversation(payload) {
  return request(`${AI_CHAT_API_PREFIX}/conversation/delete`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function listAiChatModels(payload) {
  return request(`${AI_CHAT_API_PREFIX}/meta/internal/model/_search`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function listAiChatProviders(payload) {
  return request(`${AI_CHAT_API_PREFIX}/meta/provider/list`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}
