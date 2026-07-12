import { request, requestRaw } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'
import type {
  ChatConversationDetailPayload,
  ChatConversationDetailResponse,
  ChatConversationDeletePayload,
  ChatConversationPinPayload,
  ChatConversationQueryPayload,
  ChatConversationRenamePayload,
  ChatEnabledModel,
  ChatQueryPayload,
  ChatSessionItem,
  ChatStreamEvent,
  ChatTransportEvent,
  ChatTransportRequest,
} from '../types'

const CHAT_API_PREFIX = getBackendService(SERVICE_NAMES.CHAT).gatewayPrefix

export function fetchConversationList(payload: ChatConversationQueryPayload = {}) {
  return request<ChatSessionItem[]>(`${CHAT_API_PREFIX}/api/v1/chat/conversation/list`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function renameConversation(payload: ChatConversationRenamePayload) {
  return request<ChatSessionItem>(`${CHAT_API_PREFIX}/api/v1/chat/conversation/rename`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function pinConversation(payload: ChatConversationPinPayload) {
  return request<ChatSessionItem>(`${CHAT_API_PREFIX}/api/v1/chat/conversation/pin`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deleteConversation(payload: ChatConversationDeletePayload) {
  return request<boolean>(`${CHAT_API_PREFIX}/api/v1/chat/conversation/delete`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchConversationDetail(payload: ChatConversationDetailPayload) {
  return request<ChatConversationDetailResponse>(`${CHAT_API_PREFIX}/api/v1/chat/detail`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function fetchEnabledModels() {
  return request<ChatEnabledModel[]>(`${CHAT_API_PREFIX}/api/v1/chat/models/enable`, {
    method: 'GET',
  })
}

function parseSseChunk(chunk: string) {
  const lines = chunk.replaceAll('\r\n', '\n').split('\n')
  let eventId = ''
  let eventName = 'message'
  const dataLines: string[] = []

  lines.forEach((line) => {
    if (line.startsWith('id:')) {
      eventId = line.slice(3).trim()
      return
    }
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
      return
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  })

  const rawData = dataLines.join('\n')
  if (!rawData) {
    return null
  }

  return {
    id: eventId,
    event: eventName,
    data: JSON.parse(rawData) as ChatTransportEvent,
  }
}

export function createChatTransportRequest(payload: ChatQueryPayload, route: string): ChatTransportRequest {
  const timestamp = new Date().toISOString()
  const requestId = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : `chat-${Date.now()}-${Math.random().toString(16).slice(2)}`

  return {
    type: 'chat.user_message',
    requestId,
    sessionCode: payload.sessionCode,
    modelCode: payload.modelCode,
    message: {
      id: `user-${requestId}`,
      role: 'user',
      createdAt: timestamp,
      content: [{ type: 'text', text: payload.message }],
    },
    clientContext: {
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai',
      locale: navigator.language || 'zh-CN',
      route,
      renderCapabilities: ['markdown', 'dashboardCanvas', 'line-chart', 'common-list', 'zg-common-info'],
    },
  }
}

export async function streamChatTransport(
  payload: ChatTransportRequest,
  onEvent: (event: { id: string; event: string; data: ChatTransportEvent }) => void,
) {
  const endpoint = payload.sessionCode
    ? `${CHAT_API_PREFIX}/api/chat/sessions/${encodeURIComponent(payload.sessionCode)}/rounds/stream`
    : `${CHAT_API_PREFIX}/api/chat/rounds/stream`
  const response = await requestRaw(endpoint, {
    method: 'POST',
    body: JSON.stringify(payload),
  })

  if (!response.body) {
    throw new Error('stream response body is empty')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }

    buffer += decoder.decode(value, { stream: true })
    const chunks = buffer.split(/\r?\n\r?\n/)
    buffer = chunks.pop() ?? ''

    chunks.forEach((chunk) => {
      const parsed = parseSseChunk(chunk.trim())
      if (parsed) {
        onEvent(parsed)
      }
    })
  }

  const lastChunk = buffer.trim()
  if (lastChunk) {
    const parsed = parseSseChunk(lastChunk)
    if (parsed) {
      onEvent(parsed)
    }
  }
}

/** @deprecated Use the chat-event.v2 transport functions above. */
export async function streamChatCompletion(
  payload: ChatQueryPayload,
  onEvent: (event: { event: string; data: ChatStreamEvent }) => void,
) {
  return streamChatTransport(createChatTransportRequest(payload, window.location.pathname), (event) => {
    onEvent({ event: event.event, data: event.data as unknown as ChatStreamEvent })
  })
}
