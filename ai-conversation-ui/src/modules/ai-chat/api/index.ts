import { request, requestRaw } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'
import type {
  ChatConversationDetailPayload,
  ChatConversationDetailResponse,
  ChatConversationQueryPayload,
  ChatEnabledModel,
  ChatQueryPayload,
  ChatSessionItem,
  ChatStreamEvent,
} from '../types'

const CHAT_API_PREFIX = getBackendService(SERVICE_NAMES.CHAT).gatewayPrefix

export function fetchConversationList(payload: ChatConversationQueryPayload = {}) {
  return request<ChatSessionItem[]>(`${CHAT_API_PREFIX}/api/v1/chat/conversation/list`, {
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
  const lines = chunk.split('\n')
  let eventName = 'message'
  const dataLines: string[] = []

  lines.forEach((line) => {
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
    event: eventName,
    data: JSON.parse(rawData) as ChatStreamEvent,
  }
}

export async function streamChatCompletion(
  payload: ChatQueryPayload,
  onEvent: (event: { event: string; data: ChatStreamEvent }) => void,
) {
  const response = await requestRaw(`${CHAT_API_PREFIX}/api/v1/chat/completions/stream`, {
    method: 'POST',
    body: JSON.stringify({
      attachments: [],
      tools: [],
      ext: {},
      ...payload,
    }),
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
    const chunks = buffer.split('\n\n')
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
