export interface ChatGroupItem {
  groupCode: string
  userId?: number
  groupName: string
  createTime?: string | null
  updateTime?: string | null
}

export interface ChatSessionItem {
  sessionCode: string
  userId?: number
  groupCode?: string | null
  businessType?: number
  sessionName: string
  pinned?: boolean
  updateTime?: string | null
}

export interface ChatConversationQueryPayload {
  sessionCode?: string
  groupCode?: string | null
  businessType?: number
}

export interface ChatGroupCreatePayload {
  groupName: string
}

export interface ChatGroupRenamePayload {
  groupCode: string
  groupName: string
}

export interface ChatGroupDeletePayload {
  groupCode: string
}

export interface ChatGroupAssignPayload {
  sessionCode: string
  groupCode?: string | null
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

export type ChatMemoryScope = 'SESSION' | 'LONG_TERM' | string

export type ChatMemoryType = 'RAW' | 'SEMANTIC' | 'EPISODIC' | 'PROCEDURAL' | string

export type ChatMemoryItemStatus = 'ACTIVE' | 'DISABLED' | 'PROCESSING' | 'FAILED' | 'FORGOTTEN' | string

export interface ChatMemoryItem {
  memoryRef: string
  scope?: ChatMemoryScope | null
  memoryType?: ChatMemoryType | null
  status?: ChatMemoryItemStatus | null
  content?: string | null
  sourceSessionCode?: string | null
  sourceRoundCode?: string | null
  createdAt?: string | null
  excludedFromSession?: boolean
}

export interface ChatMemoryCounts {
  sessionMemories: number
  longTermMemories: number
  processing: number
  disabled: number
}

export interface ChatMemoryContextResponse {
  sessionCode: string
  generatedAt?: string | null
  providerStatus?: string | null
  memoryLag?: boolean
  counts?: Partial<ChatMemoryCounts> | null
  sessionMemories?: ChatMemoryItem[] | null
  longTermMemories?: ChatMemoryItem[] | null
  processingMemories?: ChatMemoryItem[] | null
  disabledMemories?: ChatMemoryItem[] | null
}

export interface ChatMemoryListResponse {
  generatedAt?: string | null
  providerStatus?: string | null
  memoryLag?: boolean
  items?: ChatMemoryItem[] | null
  processingItems?: ChatMemoryItem[] | null
}

export interface ChatMemoryOperationResponse {
  memoryRef?: string | null
  status?: ChatMemoryItemStatus | null
  accepted?: boolean
}

export interface ChatMemoryConfirmPayload {
  confirmed: boolean
}

export interface ChatMemoryCorrectionPayload extends ChatMemoryConfirmPayload {
  content: string
}

export interface ChatMemorySessionPolicyPayload {
  sessionCode: string
}

export interface ConversationHistoryPageResponse {
  sessionCode: string
  rounds: ChatConversationRound[]
  nextCursor?: string | null
  hasMore?: boolean
}

export interface ConversationHistoryWindowResponse {
  sessionCode: string
  aroundRoundCode?: string | null
  rounds: ChatConversationRound[]
  beforeCursor?: string | null
  afterCursor?: string | null
  hasEarlier?: boolean
  hasLater?: boolean
}

export interface ChatConversationRound {
  round?: ChatRoundInfo | null
  messages: ChatMessageItem[]
  artifacts?: ChatArtifact[]
  activities?: Array<ChatRunActivity | Record<string, unknown>>
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
  groupCode?: string | null
  target?: ChatAgentTarget
  /** User-selected enabled model. Agent routing remains server-owned. */
  modelId?: number
  /** Development-only privileged model override. */
  modelOverrideId?: number
  message: string
  attachments?: unknown[]
  tools?: unknown[]
  ext?: Record<string, unknown>
}

export interface ChatEnabledModel {
  id?: number
  modelCode?: string
  modelName?: string
  apiModel?: string
  clientType?: number
}

export interface ChatAgentTarget {
  type: 'AGENT'
  agentCode: string
  agentVersion?: number
}

export interface ChatAvailableAgent {
  code: string
  name: string
  description?: string
  version?: number
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

export type ChatRunActivityKind =
  | 'agent'
  | 'handoff'
  | 'tool'
  | 'skill'
  | 'artifact'
  | 'check'
  | 'repair'
  | 'execution'
  | 'thinking'

export interface ChatRunActivity {
  id: string
  kind: ChatRunActivityKind
  title: string
  detail?: string
  inputSummary?: string
  outputSummary?: string
  callReason?: string
  status?: 'pending' | 'running' | 'success' | 'failed' | 'cancelled' | string
  agentCode?: string
  agentVersion?: number
  timestamp?: string
  startedAt?: string
  finishedAt?: string
  durationMs?: number
  confidence?: number
  confidenceThreshold?: number
  confidenceBasis?: string[]
  executionReadiness?: number
  toolKey?: string
  toolName?: string
  skillKey?: string
  skillName?: string
  analysis?: Record<string, unknown>
  metadata?: Record<string, unknown>
}

export interface ChatArtifact {
  artifactCode?: string
  codeRef?: string
  artifactType?: string
  title?: string
  content?: unknown
  contentFormat?: string
  status?: string
  stage?: string
  seqNo?: number
  visibleFlag?: boolean | null
  visible?: boolean | null
  extJson?: string | null
}

export interface ChatTransportEvent {
  eventId?: string
  eventType: string
  schemaVersion?: 'chat-event.v2' | string
  runId?: string
  requestId?: string
  sessionCode?: string
  sessionName?: string
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
  groupCode?: string
  roundCode?: string
  target?: ChatAgentTarget
  modelId?: number
  modelOverrideId?: number
  message: ChatTransportMessage
  clientContext: {
    timezone: string
    locale: string
    route: string
    renderCapabilities: string[]
  }
}

export interface ChatTransportReconnectRequest {
  runId: string
  lastEventId?: string
  sessionCode?: string
  roundCode?: string
}

export interface ChatTransportStreamResult {
  terminalEventReceived: boolean
  terminalEventName?: ChatTransportTerminalEventName
  lastEventId?: string
  runId?: string
  sessionCode?: string
  roundCode?: string
}

export type ChatTransportTerminalEventName =
  | 'round.completed'
  | 'round.failed'
  | 'round.cancelled'
  | 'assistant.input_required'

export interface ChatTransportStreamOptions {
  signal?: AbortSignal
  /** Reuse the same set across reconnect attempts to ignore replayed events. */
  seenEventIds?: Set<string>
  /** Defaults to true. EOF without a protocol terminal event is treated as an interruption. */
  requireTerminalEvent?: boolean
  /** Maximum interval without receiving any SSE bytes (including heartbeat comments). Defaults to 30s. */
  inactivityTimeoutMs?: number
}

export interface ChatUiError {
  code?: string
  userMessage: string
  detail?: string
  retryable?: boolean
  traceId?: string
}

export interface ChatRunStatus {
  runId?: string
  requestId?: string
  sessionCode?: string
  roundCode?: string
  status?: 'accepted' | 'running' | 'waiting_input' | 'cancelling' | 'cancelled' | 'completed' | 'failed' | string
  active?: boolean
  createdAt?: string
  startedAt?: string
  finishedAt?: string
  error?: string
  errorInfo?: Partial<ChatUiError>
}

export interface ChatRoundThinkingDetail {
  schemaVersion?: string
  sessionCode?: string
  roundCode?: string
  status?: string
  summary?: string
  agents?: Array<Record<string, unknown>>
  /** @deprecated Migration-only compatibility with round-thinking.v1. */
  nodes?: Array<Record<string, unknown>>
  activities?: Array<Record<string, unknown>>
  ext?: Record<string, unknown>
}

export interface ChatUiMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  roundCode?: string
  status?: string
  error?: ChatUiError
  actorName?: string
  activities?: ChatRunActivity[]
  artifacts?: ChatArtifact[]
}
