export interface ChatSessionItem {
  sessionCode: string
  userId?: number
  businessType?: number
  sessionName: string
  pinned?: boolean
  updateTime?: string | null
}

export interface ChatConversationQueryPayload {
  sessionCode?: string
  businessType?: number
}

export interface ChatConversationRenamePayload {
  sessionCode: string
  sessionName: string
}

export interface ChatConversationPinPayload {
  sessionCode: string
  pinned: boolean
}

export interface ChatConversationDeletePayload {
  sessionCode: string
}

export interface ChatConversationDetailPayload {
  sessionCode: string
}

export interface ChatConversationDetailResponse {
  session?: ChatSessionItem | null
  rounds: ChatConversationRound[]
}

export interface ChatConversationRound {
  round?: ChatRoundInfo | null
  messages: ChatMessageItem[]
  artifacts?: unknown[]
  renderType?: string | null
}

export interface ChatRoundInfo {
  roundCode: string
  roundType?: string
  parentRoundCode?: string | null
  sessionCode?: string
  userId?: number
  modelCode?: string | null
  actualModel?: string | null
  status?: string | null
}

export interface ChatMessageItem {
  id?: number
  messageCode?: string
  roundCode?: string
  sessionCode?: string
  role: string
  actorType?: string | null
  messageType?: string | null
  displayLevel?: string | null
  contentFormat?: string | null
  parentMessageCode?: string | null
  sourceMessageCode?: string | null
  status?: string | null
  content: string
  sortNo?: number | null
  extJson?: string | null
  createdAt?: string | null
}

export interface ChatQueryPayload {
  sessionCode?: string
  modelCode?: string
  message: string
  attachments?: unknown[]
  tools?: unknown[]
  ext?: Record<string, unknown>
}

export interface ChatEnabledModel {
  modelCode?: string
  modelName?: string
  apiModel?: string
  clientType?: number
}

export interface ChatStreamEvent {
  eventType?: string
  requestId?: string
  sessionCode?: string
  sessionName?: string
  roundCode?: string
  answer?: string
  status?: string
  message?: string
}

export type ChatTransportContentBlock = {
  type: 'text' | 'markdown' | 'render' | string
  text?: string
  markdown?: string
}

export interface ChatTransportEvent {
  eventId?: string
  eventType: string
  schemaVersion?: 'chat-event.v2' | string
  runId?: string
  requestId?: string
  sessionCode?: string
  roundCode?: string
  timestamp?: string
  payload: Record<string, any>
}

export interface ChatTransportMessage {
  id: string
  role: 'user'
  createdAt: string
  content: ChatTransportContentBlock[]
}

export interface ChatTransportRequest {
  type: 'chat.user_message'
  requestId: string
  sessionCode?: string
  roundCode?: string
  modelCode?: string
  message: ChatTransportMessage
  clientContext: {
    timezone: string
    locale: string
    route: string
    renderCapabilities: string[]
  }
}

export interface ChatUiMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  roundCode?: string
  status?: string
}
