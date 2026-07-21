import { request, requestRaw } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'
import type {
  ChatConversationDetailPayload,
  ChatConversationDetailResponse,
  ChatConversationDeletePayload,
  ChatConversationPinPayload,
  ChatConversationQueryPayload,
  ChatConversationRenamePayload,
  ChatAvailableAgent,
  ChatEnabledModel,
  ChatQueryPayload,
  ChatRoundThinkingDetail,
  ChatRunStatus,
  ChatSessionItem,
  ChatStreamEvent,
  ChatTransportEvent,
  ChatTransportReconnectRequest,
  ChatTransportRequest,
  ChatTransportStreamOptions,
  ChatTransportStreamResult,
  ChatTransportTerminalEventName,
} from '../types'

const CHAT_API_PREFIX = getBackendService(SERVICE_NAMES.CHAT).gatewayPrefix
export const PAGE_ASSISTANT_BUSINESS_TYPE = 3

export function fetchConversationList(payload: ChatConversationQueryPayload = {}) {
  return request<ChatSessionItem[]>(`${CHAT_API_PREFIX}/api/v1/chat/conversation/list`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }).then((sessions) => {
    const items = Array.isArray(sessions) ? sessions : []
    return payload.businessType === PAGE_ASSISTANT_BUSINESS_TYPE
      ? items
      : items.filter(session => session.businessType !== PAGE_ASSISTANT_BUSINESS_TYPE)
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

export function fetchAvailableHomeAgents() {
  return request<ChatAvailableAgent[]>(`${CHAT_API_PREFIX}/api/v1/ai/agent-entries/HOME_CHAT/available-agents`, {
    method: 'GET',
  })
}

function parseSseChunk(chunk: string) {
  const lines = chunk.replace(/\r\n/g, '\n').split('\n')
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

export type ChatTransportStreamEvent = { id: string; event: string; data: ChatTransportEvent }

export function resolveChatTransportEventType(event: ChatTransportStreamEvent) {
  return event.data.eventType?.trim() || event.event?.trim() || 'message'
}

const CHAT_TRANSPORT_TERMINAL_EVENTS = new Set<ChatTransportTerminalEventName>([
  'round.completed',
  'round.failed',
  'round.cancelled',
  'assistant.input_required',
])

export class ChatStreamInterruptedError extends Error {
  readonly result: ChatTransportStreamResult

  constructor(result: ChatTransportStreamResult) {
    super('聊天连接已中断，尚未收到任务终态')
    this.name = 'ChatStreamInterruptedError'
    this.result = result
  }
}

function resolveEventId(event: ChatTransportStreamEvent) {
  return event.data.eventId || event.id || ''
}

export async function consumeChatTransportStream(
  response: Response,
  onEvent: (event: ChatTransportStreamEvent) => void,
  options: ChatTransportStreamOptions = {},
) {
  if (!response.body) {
    throw new Error('聊天连接未返回可读取的数据流')
  }

  const result: ChatTransportStreamResult = { terminalEventReceived: false }
  const seenEventIds = options.seenEventIds ?? new Set<string>()
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  const inactivityTimeoutMs = options.inactivityTimeoutMs ?? 30_000
  let buffer = ''
  let inactivityTimer: number | undefined

  const armInactivityTimer = () => {
    if (inactivityTimer !== undefined) {
      window.clearTimeout(inactivityTimer)
    }
    if (inactivityTimeoutMs <= 0) {
      return
    }
    inactivityTimer = window.setTimeout(() => {
      void reader.cancel('chat stream inactivity timeout')
    }, inactivityTimeoutMs)
  }

  const acceptEvent = (event: ChatTransportStreamEvent | null) => {
    if (!event) {
      return
    }

    const eventType = resolveChatTransportEventType(event)
    const normalizedEvent = event.event === eventType ? event : { ...event, event: eventType }
    const eventId = resolveEventId(normalizedEvent)
    if (eventId && seenEventIds.has(eventId)) {
      return
    }
    if (eventId) {
      seenEventIds.add(eventId)
      result.lastEventId = eventId
    }

    result.runId = normalizedEvent.data.runId || result.runId
    result.sessionCode = normalizedEvent.data.sessionCode || result.sessionCode
    result.roundCode = normalizedEvent.data.roundCode || result.roundCode
    if (CHAT_TRANSPORT_TERMINAL_EVENTS.has(eventType as ChatTransportTerminalEventName)) {
      result.terminalEventReceived = true
      result.terminalEventName = eventType as ChatTransportTerminalEventName
    }
    onEvent(normalizedEvent)
  }

  try {
    armInactivityTimer()
    while (true) {
      const { value, done } = await reader.read()
      if (done) {
        break
      }

      armInactivityTimer()
      buffer += decoder.decode(value, { stream: true })
      const chunks = buffer.split(/\r?\n\r?\n/)
      buffer = chunks.pop() ?? ''
      chunks.forEach((chunk) => acceptEvent(parseSseChunk(chunk.trim())))
    }

    buffer += decoder.decode()
    const lastChunk = buffer.trim()
    if (lastChunk) {
      acceptEvent(parseSseChunk(lastChunk))
    }
  } finally {
    if (inactivityTimer !== undefined) {
      window.clearTimeout(inactivityTimer)
    }
    reader.releaseLock()
  }

  if (options.requireTerminalEvent !== false && !result.terminalEventReceived) {
    throw new ChatStreamInterruptedError(result)
  }
  return result
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
    target: payload.target,
    modelId: payload.modelId,
    modelOverrideId: payload.modelOverrideId,
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
  onEvent: (event: ChatTransportStreamEvent) => void,
  options: ChatTransportStreamOptions = {},
): Promise<ChatTransportStreamResult> {
  const endpoint = payload.sessionCode
    ? `${CHAT_API_PREFIX}/api/chat/sessions/${encodeURIComponent(payload.sessionCode)}/rounds/stream`
    : `${CHAT_API_PREFIX}/api/chat/rounds/stream`
  const response = await requestRaw(endpoint, {
    method: 'POST',
    headers: { Accept: 'text/event-stream' },
    body: JSON.stringify(payload),
    signal: options.signal,
  })

  return consumeChatTransportStream(response, onEvent, options)
}

export async function reconnectChatTransport(
  payload: ChatTransportReconnectRequest,
  onEvent: (event: ChatTransportStreamEvent) => void,
  options: ChatTransportStreamOptions = {},
): Promise<ChatTransportStreamResult> {
  const response = await requestRaw(`${CHAT_API_PREFIX}/api/chat/stream/reconnect`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      ...(payload.lastEventId ? { 'Last-Event-ID': payload.lastEventId } : {}),
    },
    body: JSON.stringify(payload),
    signal: options.signal,
  })
  return consumeChatTransportStream(response, onEvent, options)
}

export function fetchChatRunStatus(runId: string) {
  return request<ChatRunStatus>(`${CHAT_API_PREFIX}/api/chat/runs/${encodeURIComponent(runId)}`, {
    method: 'GET',
  })
}

export function stopChatRun(runId: string) {
  return request<boolean>(`${CHAT_API_PREFIX}/api/chat/runs/${encodeURIComponent(runId)}/stop`, {
    method: 'POST',
  })
}

export function fetchRoundThinking(sessionCode: string, roundCode: string) {
  return request<ChatRoundThinkingDetail>(
    `${CHAT_API_PREFIX}/api/chat/sessions/${encodeURIComponent(sessionCode)}/rounds/${encodeURIComponent(roundCode)}/thinking`,
    { method: 'GET' },
  )
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
