import { buildUrl, request, resolveBusinessMessage, unwrapBusinessPayload } from '../utils/request'
import { getToken } from '../utils/session'

const AI_CHAT_API_PREFIX = '/aiChat/api/v1/ai/chat'
const AI_META_API_PREFIX = '/aiEngine/api/v1/ai/meta'
const AI_ENGINE_API_PREFIX = '/aiEngine/api/v1/ai'

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
    const errorPayload = await tryReadErrorPayload(response)
    throw new Error(resolveBusinessMessage(errorPayload, `Request failed with status ${response.status}`))
  }

  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    unwrapBusinessPayload(await response.json(), '请求 ai-chat 失败')
    throw new Error('流式接口未返回事件流')
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
  return request(`${AI_META_API_PREFIX}/model/list`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function listEnabledAiChatModels() {
  return request(`${AI_ENGINE_API_PREFIX}/models/enable`, {
    method: 'GET'
  })
}

export function listAiChatProviders(payload) {
  return request(`${AI_META_API_PREFIX}/provider/list`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function searchAiChatProviderConfigs(payload) {
  return request(`${AI_META_API_PREFIX}/internal/provider/_search`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function createAiChatProviderConfig(payload) {
  return request(`${AI_META_API_PREFIX}/internal/provider`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function updateAiChatProviderConfig(id, payload) {
  return request(`${AI_META_API_PREFIX}/internal/provider/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload ?? {})
  })
}

export function editAiChatProviderConfig(id, payload) {
  return request(`${AI_META_API_PREFIX}/internal/provider/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(payload ?? {})
  })
}

export function deleteAiChatProviderConfig(id) {
  return request(`${AI_META_API_PREFIX}/internal/provider/${id}`, {
    method: 'DELETE'
  })
}

export function searchAiChatModelManages(payload) {
  return request(`${AI_META_API_PREFIX}/internal/model-manage/_search`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function getAiChatModelManage(id) {
  return request(`${AI_META_API_PREFIX}/internal/model-manage/${id}`, {
    method: 'GET'
  })
}

export function createAiChatModelManage(payload) {
  return request(`${AI_META_API_PREFIX}/internal/model-manage`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function updateAiChatModelManage(id, payload) {
  return request(`${AI_META_API_PREFIX}/internal/model-manage/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload ?? {})
  })
}

export function editAiChatModelManage(id, payload) {
  return request(`${AI_META_API_PREFIX}/internal/model-manage/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(payload ?? {})
  })
}

export function deleteAiChatModelManage(id) {
  return request(`${AI_META_API_PREFIX}/internal/model-manage/${id}`, {
    method: 'DELETE'
  })
}

async function tryReadErrorPayload(response) {
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    return response.json()
  }
  return response.text().catch(() => '')
}
