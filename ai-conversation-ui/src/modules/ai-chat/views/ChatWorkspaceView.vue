<script setup lang="ts">
import {
  ChatDotRound,
  Check,
  CloseBold,
  Clock,
  Crop,
  Document,
  EditPen,
  FolderOpened,
  Microphone,
  MoreFilled,
  Operation,
  Plus,
  Promotion,
  ArrowDown,
  ArrowLeftBold,
  ArrowRight,
  ArrowRightBold,
  Search,
  Setting,
  StarFilled,
  SwitchButton,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import brandLogoDark from '../../../assets/icons/brand-logo-dark.svg'
import brandLogo from '../../../assets/icons/brand-logo.svg'
import brandMark from '../../../assets/icons/brand-mark.svg'
import { applyTheme, getSavedTheme } from '../../../stores/theme'
import { formatRelativeTime } from '../../../utils/date'
import { getDeveloperModeEnabled, setDeveloperModeEnabled } from '../../../utils/developerMode'
import { clearSession, getStoredUser } from '../../../utils/session'
import { AppDialog, useAppConfirm } from '../../../components'
import ChatMessageErrorCard from '../components/ChatMessageErrorCard.vue'
import ChatArtifactList from '../components/ChatArtifactList.vue'
import ConversationContextDrawer from '../components/ConversationContextDrawer.vue'
import ConversationContextSummaryBar from '../components/ConversationContextSummaryBar.vue'
import RunActivityTimeline from '../components/RunActivityTimeline.vue'
import {
  activityFromTransportEvent,
  artifactsFromTransportEvent,
  normalizeHistoricalActivities,
  normalizeHistoricalArtifacts,
  upsertArtifact,
  upsertRunActivity,
} from '../composables/useChatRun'
import { useConversationContext } from '../composables/useConversationContext'
import { useConversationHistory } from '../composables/useConversationHistory'
import { renderMarkdown } from '../utils/markdown'
import {
  fetchConversationGroups,
  fetchConversationList,
  fetchEnabledModels,
  createChatTransportRequest,
  createConversationGroup,
  deleteConversationGroup,
  deleteConversation,
  assignConversationGroup,
  pinConversation,
  renameConversationGroup,
  renameConversation,
  ChatStreamInterruptedError,
  fetchChatRunStatus,
  reconnectChatTransport,
  stopChatRun,
  streamChatTransport,
} from '../api'
import type {
  ChatArtifact,
  ChatConversationRound,
  ChatEnabledModel,
  ChatGroupItem,
  ChatMemoryItem,
  ChatRunActivity,
  ChatSessionItem,
  ChatTransportEvent,
  ChatTransportStreamResult,
  ChatUiError,
  ChatUiMessage,
} from '../types'

const route = useRoute()
const router = useRouter()
const ASSISTANT_DISPLAY_NAME = '智能任务助手'
const appConfirm = useAppConfirm()
const {
  context: conversationContext,
  longTermList: conversationLongTermList,
  counts: conversationContextCounts,
  isLoading: isLoadingConversationContext,
  isLoadingLongTerm: isLoadingLongTermMemories,
  error: conversationContextError,
  isOpen: contextDrawerVisible,
  actionLoading: conversationContextActionLoading,
  clear: clearConversationContext,
  load: loadConversationContext,
  loadLongTerm: loadLongTermMemories,
  refresh: refreshConversationContext,
  open: openConversationContextState,
  disable: disableMemory,
  restore: restoreMemory,
  correct: correctMemory,
  promote: promoteMemory,
  excludeFromSession: excludeMemoryFromSession,
  forget: forgetMemory,
  clearLongTerm: clearLongTermMemory,
} = useConversationContext()
const {
  hasMore: hasOlderHistory,
  isLoading: isLoadingHistory,
  reset: resetConversationHistory,
  loadInitial: loadInitialConversationHistory,
  loadOlder: loadOlderConversationHistoryPage,
  loadWindow: loadConversationHistoryWindow,
} = useConversationHistory()

type CurrentUserProfile = {
  displayName?: string
  name?: string
  username?: string
  avatarUrl?: string
  profileImageUrl?: string
}

type SidebarConversation = ChatSessionItem & {
  id: string
  title: string
  meta: string
}

type SidebarConversationSection = {
  key: string
  groupCode: string
  label: string
  conversations: SidebarConversation[]
  isPinned?: boolean
  isRecent?: boolean
}

const prompt = ref('')
const selectedModel = ref<number | undefined>()
const modelOptions = ref<ChatEnabledModel[]>([])
const isLoadingModels = ref(false)
const modelLoadError = ref('')
const activeNav = ref('chats')
const sidebarExpanded = ref(true)
const activeUserMenu = ref<'topbar' | 'sidebar' | null>(null)
const activeTheme = ref<'dark' | 'light'>('light')
const developerModeEnabled = ref(false)
const conversationList = ref<ChatSessionItem[]>([])
const groupList = ref<ChatGroupItem[]>([])
const isLoadingGroups = ref(false)
const groupLoadError = ref('')
const chatMessages = ref<ChatUiMessage[]>([])
const currentRoundCode = ref('')
const currentSessionName = ref('')
const pendingSessionCode = ref('')
const isLoadingList = ref(false)
const isLoadingDetail = ref(false)
const isStreaming = ref(false)
const conversationError = ref('')
const currentRunId = ref('')
const lastEventId = ref('')
type ChatInteractionState =
  | 'idle'
  | 'connecting'
  | 'thinking'
  | 'streaming'
  | 'reconnecting'
  | 'stopping'
  | 'waiting_input'
  | 'completed'
  | 'failed'
  | 'cancelled'

const MAX_RECONNECT_ATTEMPTS = 3
const ASSISTANT_PLACEHOLDERS = new Set(['正在连接 AI...', '正在思考...', '正在生成回复...', '正在处理...'])
const interactionState = ref<ChatInteractionState>('idle')
const activeAssistantMessageId = ref('')
const stopRequested = ref(false)
let activeStreamController: AbortController | null = null
let activeSeenEventIds = new Set<string>()
let stopRequestInFlight = false
let detailRequestSequence = 0
const renameDialogVisible = ref(false)
const renameSubmitting = ref(false)
const renamingConversation = ref<ChatSessionItem | null>(null)
const renameSessionName = ref('')
const openConversationMenuCode = ref('')
const openGroupMenuCode = ref('')
const groupDialogVisible = ref(false)
const groupDialogSubmitting = ref(false)
const groupNameInput = ref('')
const editingGroupCode = ref('')
const editingGroupName = ref('')
const groupRenameSubmitting = ref(false)
const groupRenameInput = ref<HTMLInputElement | null>(null)
const moveDialogVisible = ref(false)
const moveDialogSubmitting = ref(false)
const moveTargetGroupCode = ref('')
const movingConversation = ref<ChatSessionItem | null>(null)
const memoryCorrectionDialogVisible = ref(false)
const memoryCorrectionSubmitting = ref(false)
const memoryCorrectionTarget = ref<ChatMemoryItem | null>(null)
const memoryCorrectionContent = ref('')
const pendingMemorySource = ref<{ sessionCode: string; roundCode: string } | null>(null)
const pinnedExpanded = ref(true)
const recentExpanded = ref(true)
const collapsedGroupCodes = ref<Set<string>>(new Set())
const welcomeTextarea = useTemplateRef<HTMLTextAreaElement>('welcomeTextarea')
const conversationTextarea = useTemplateRef<HTMLTextAreaElement>('conversationTextarea')
const conversationScrollContainer = useTemplateRef<HTMLElement>('conversationScrollContainer')

const welcomeCards = [
  '帮我分析本周核心业务波动，并给出三条解释假设',
  '把这个需求整理成执行计划，标出风险和依赖',
  '根据销售数据，生成一份管理层晨报摘要',
  '对接知识库后，我该如何设计问答链路和结果区？',
]

const welcomeSuggestions = [
  { title: '分析业务波动', subtitle: '识别异常并给出解释假设', prompt: welcomeCards[0] },
  { title: '整理执行计划', subtitle: '拆解步骤、风险和依赖', prompt: welcomeCards[1] },
  { title: '生成管理层摘要', subtitle: '提炼重点并形成汇报口径', prompt: welcomeCards[2] },
]

const quickNavItems = [
  { key: 'new-chat', label: '新对话', count: '', icon: EditPen },
  { key: 'search', label: '搜索', count: '', icon: Search },
  { key: 'notes', label: '笔记', count: '', icon: Document },
  { key: 'workspace', label: '工作空间', count: '', icon: Operation },
  { key: 'scheduler', label: '定时任务', count: '', icon: Clock },
]

const userMenuItems = [
  { key: 'system-settings', label: '系统设置', icon: Setting },
  { key: 'explore', label: 'AI 对话探索区', icon: Crop },
]

const isConversationMode = computed(() => typeof route.params.sessionId === 'string' && route.params.sessionId.trim() !== '')
const currentSessionCode = computed(() =>
  typeof route.params.sessionId === 'string' ? route.params.sessionId.trim() : '',
)
const currentGroupId = computed(() =>
  typeof route.params.groupId === 'string' ? route.params.groupId.trim() : '',
)
const currentGroup = computed(() => groupList.value.find(group => group.groupCode === currentGroupId.value))
const currentGroupName = computed(() => currentGroup.value?.groupName || currentGroupId.value)
const activeConversation = computed(() => currentSessionCode.value)
const currentUserProfile = getStoredUser<CurrentUserProfile>() || {}
const currentUserName = computed(() =>
  [currentUserProfile.displayName, currentUserProfile.name, currentUserProfile.username]
    .find((value) => typeof value === 'string' && value.trim())?.trim() || '当前用户',
)
const currentUserAvatarUrl = computed(() =>
  [currentUserProfile.avatarUrl, currentUserProfile.profileImageUrl]
    .find((value) => typeof value === 'string' && value.trim())?.trim() || '',
)
const currentUserAvatarText = computed(() => Array.from(currentUserName.value)[0]?.toLocaleUpperCase() || '?')
const selectedModelOption = computed(() => modelOptions.value.find(item => item.id === selectedModel.value))
const selectedModelLabel = computed(() => {
  const model = selectedModelOption.value
  if (model) {
    return model.modelName || model.modelCode || model.apiModel || '已选择模型'
  }
  return isLoadingModels.value ? '正在加载模型...' : '请选择模型'
})
const composerModelContentWidth = computed(() => {
  const displayUnits = Array.from(selectedModelLabel.value).reduce((width, character) => {
    const codePoint = character.codePointAt(0) || 0
    return width + (codePoint <= 0x7f ? 1 : 2)
  }, 0)
  return `${Math.max(displayUnits, 1)}ch`
})
const welcomeModelDescription = computed(() => {
  if (isLoadingModels.value) {
    return '正在加载系统已启用模型，加载完成后即可描述任务。'
  }
  if (selectedModelOption.value) {
    return `已选择 ${selectedModelLabel.value}，系统会根据需求自动调用合适的智能体、Skill 和工具。`
  }
  return '请先选择一个系统已启用模型，再描述你想完成的任务。'
})
const modelSelectEmptyText = computed(() => modelLoadError.value || '暂无已启用模型')
const modelAvailabilityMessage = computed(() => {
  if (modelLoadError.value) {
    return `模型列表加载失败：${modelLoadError.value}`
  }
  if (!isLoadingModels.value && modelOptions.value.length === 0) {
    return '系统暂无已启用模型，请先在系统设置中启用模型。'
  }
  return ''
})
const isPrimaryActionDisabled = computed(() => {
  if (isStreaming.value) {
    return interactionState.value === 'stopping'
  }
  return !prompt.value.trim() || !selectedModel.value || isLoadingModels.value
})
const sidebarConversationGroups = computed<SidebarConversationSection[]>(() => {
  const conversations: SidebarConversation[] = conversationList.value.map((conversation) => ({
    ...conversation,
    id: conversation.sessionCode,
    title: conversation.sessionName || conversation.sessionCode,
    meta: formatRelativeTime(conversation.updateTime) || conversation.sessionCode.slice(-6),
  }))
  const sortConversations = (items: SidebarConversation[]) => items.sort((left, right) => {
    const pinnedDifference = Number(Boolean(right.pinned)) - Number(Boolean(left.pinned))
    if (pinnedDifference !== 0) {
      return pinnedDifference
    }
    return String(right.updateTime || '').localeCompare(String(left.updateTime || ''))
  })
  const pinnedConversations = sortConversations(conversations.filter(conversation => Boolean(conversation.pinned)))
  const recentConversations = sortConversations(conversations.filter(conversation => !conversation.groupCode && !conversation.pinned))

  return [
    ...(pinnedConversations.length > 0
      ? [{
          key: '__pinned__',
          groupCode: '',
          label: '置顶',
          conversations: pinnedConversations,
          isPinned: true,
        }]
      : []),
    ...groupList.value.map(group => ({
      key: group.groupCode,
      groupCode: group.groupCode,
      label: group.groupName,
      conversations: sortConversations(conversations.filter(
        conversation => conversation.groupCode === group.groupCode && !conversation.pinned,
      )),
    })),
    {
      key: '__recent__',
      groupCode: '',
      label: '最近',
      conversations: recentConversations,
      isRecent: true,
    },
  ]
})

function resizeTextarea(element: HTMLTextAreaElement | null) {
  if (!element) {
    return
  }

  const computedStyle = window.getComputedStyle(element)
  const lineHeight = Number.parseFloat(computedStyle.lineHeight) || 24
  const maxHeight = lineHeight * 8
  element.style.height = '0px'
  element.style.height = `${Math.min(element.scrollHeight, maxHeight)}px`
  element.style.overflowY = element.scrollHeight > maxHeight ? 'auto' : 'hidden'
}

async function syncTextareaHeights() {
  await nextTick()
  resizeTextarea(welcomeTextarea.value)
  resizeTextarea(conversationTextarea.value)
}

function applySuggestion(text: string) {
  prompt.value = text
  void syncTextareaHeights()
}

function normalizeRole(role?: string) {
  if (!role) {
    return 'assistant' as const
  }
  return role.toLowerCase() === 'user' ? 'user' : 'assistant'
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined
}

function textValue(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function sanitizeErrorDetail(value: unknown) {
  const detail = textValue(value)
  if (!detail) {
    return undefined
  }
  const sanitized = detail
    .replace(/\bbearer\s+[a-z0-9._~+/=-]+/gi, 'Bearer [已隐藏]')
    .replace(/\bsk-[a-z0-9_-]+\b/gi, '[已隐藏]')
    .replace(
      /(["']?(?:authorization|api[ _-]?key|secret|password|access[_-]?token|refresh[_-]?token|token)["']?\s*[:=]\s*["']?)([^"'\s,;}]+)/gi,
      '$1[已隐藏]',
    )
  return sanitized.length > 500 ? `${sanitized.slice(0, 499)}…` : sanitized
}

function parseExtJson(value?: string | null) {
  if (!value?.trim()) {
    return undefined
  }
  try {
    return asRecord(JSON.parse(value))
  } catch {
    return undefined
  }
}

function projectLegacyReviewerAnswer(content: string, ext?: Record<string, unknown>) {
  // Before HOME_CHAT entry eligibility was enforced, a specialist could be bound as the root Agent.
  // Keep the stored audit payload intact, but recover its explicit user-facing answer for old sessions only.
  if (textValue(ext?.agentCode)?.toLowerCase() !== 'result-reviewer') {
    return content
  }
  const trimmed = content.trim()
  const fenced = trimmed.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i)
  try {
    const envelope = asRecord(JSON.parse(fenced?.[1] || trimmed))
    const report = asRecord(envelope?.['检查报告']) || asRecord(envelope?.report)
    return textValue(report?.['回答']) || textValue(report?.answer) || content
  }
  catch {
    return content
  }
}

function isMessageVisibleInTimeline(message: ChatConversationRound['messages'][number]) {
  return textValue(message.displayLevel)?.toUpperCase() !== 'HIDDEN'
}

function isAssistantRoundEnded(message: ChatUiMessage) {
  if (message.role !== 'assistant') return false
  return ['COMPLETED', 'SUCCESS', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'CANCELED']
    .includes(message.status?.trim().toUpperCase() || '')
}

function showPlannedMessageAction(action: '点赞' | '反馈') {
  ElMessage.info(`${action}功能待规划`)
}

function normalizeStructuredError(
  value: unknown,
  fallbackDetail?: string,
  fallbackCode = 'CHAT_RUN_FAILED',
): ChatUiError {
  const record = asRecord(value)
  const nested = asRecord(record?.error)
  const source = nested || record
  return {
    code: textValue(source?.code) || fallbackCode,
    userMessage: textValue(source?.userMessage) || 'AI 处理失败，请稍后重试。',
    detail: sanitizeErrorDetail(source?.detail)
      || sanitizeErrorDetail(source?.reason)
      || sanitizeErrorDetail(source?.errorMessage)
      || sanitizeErrorDetail(fallbackDetail),
    retryable: typeof source?.retryable === 'boolean' ? source.retryable : true,
    traceId: textValue(source?.traceId),
  }
}

function historicalRoundError(round: ChatConversationRound): ChatUiError {
  const failedAssistant = [...(round.messages || [])].reverse().find((message) =>
    isMessageVisibleInTimeline(message)
    && normalizeRole(message.role) === 'assistant'
    && message.status?.toUpperCase() === 'FAILED',
  )
  const ext = parseExtJson(failedAssistant?.extJson)
  const messageDetail = failedAssistant?.messageType?.toUpperCase().includes('ERROR')
    ? failedAssistant.content
    : undefined
  return normalizeStructuredError(
    ext,
    textValue(ext?.reason) || textValue(ext?.message) || messageDetail,
  )
}

function flattenRoundsToMessages(rounds: ChatConversationRound[]) {
  return rounds.flatMap((round) => {
    const visibleHistoryMessages = (round.messages || []).filter(isMessageVisibleInTimeline)
    const messages: ChatUiMessage[] = visibleHistoryMessages
      .filter((message) => typeof message.content === 'string' && message.content.trim())
      .map((message) => {
        const role = normalizeRole(message.role)
        const ext = parseExtJson(message.extJson)
        return {
          id: message.messageCode || `${round.round?.roundCode || 'round'}-${message.sortNo || 0}`,
          role,
          content: role === 'assistant' ? projectLegacyReviewerAnswer(message.content, ext) : message.content,
          roundCode: message.roundCode,
          status: message.status || round.round?.status || undefined,
          // Specialist identities belong to the execution timeline; HOME_CHAT owns the visible reply.
          actorName: role === 'assistant' ? ASSISTANT_DISPLAY_NAME : undefined,
        }
      })
    const artifacts = normalizeHistoricalArtifacts(round.artifacts)
    const activities = normalizeHistoricalActivities(round.activities)
    let runOwner = [...messages].reverse().find(message => message.role === 'assistant')
    if (!runOwner && (artifacts.length || activities.length)) {
      runOwner = {
        id: `${round.round?.roundCode || 'round'}-assistant-run`,
        role: 'assistant',
        content: '',
        roundCode: round.round?.roundCode,
        status: round.round?.status || undefined,
      }
      messages.push(runOwner)
    }
    if (runOwner) {
      if (artifacts.length) runOwner.artifacts = artifacts
      if (activities.length) runOwner.activities = activities
    }
    const failed = round.round?.status?.toUpperCase() === 'FAILED'
      || visibleHistoryMessages.some((message) => message.status?.toUpperCase() === 'FAILED')
    if (!failed) {
      return messages
    }

    const error = historicalRoundError(round)
    const assistant = [...messages].reverse().find((message) => message.role === 'assistant')
    if (assistant) {
      assistant.status = 'FAILED'
      assistant.error = error
      return messages
    }

    messages.push({
      id: `${round.round?.roundCode || 'round'}-assistant-error`,
      role: 'assistant',
      content: '',
      roundCode: round.round?.roundCode,
      status: 'FAILED',
      error,
    })
    return messages
  })
}

function mergeActivities(current: ChatRunActivity[] = [], persisted: ChatRunActivity[] = []) {
  return persisted.reduce((items, activity) => upsertRunActivity(items, activity), current)
}

function mergeArtifacts(current: ChatArtifact[] = [], persisted: ChatArtifact[] = []) {
  return persisted.reduce((items, artifact) => upsertArtifact(items, artifact), current)
}

function mergeConversationMessages(current: ChatUiMessage[], persisted: ChatUiMessage[]) {
  const matchedCurrentIndexes = new Set<number>()
  const merged = persisted.map((persistedMessage) => {
    let currentIndex = current.findIndex((message, index) => (
      !matchedCurrentIndexes.has(index) && message.id === persistedMessage.id
    ))
    if (currentIndex < 0 && persistedMessage.roundCode) {
      currentIndex = current.findIndex((message, index) => (
        !matchedCurrentIndexes.has(index)
        && message.roundCode === persistedMessage.roundCode
        && message.role === persistedMessage.role
      ))
    }
    if (currentIndex < 0) {
      currentIndex = current.findIndex((message, index) => (
        !matchedCurrentIndexes.has(index)
        && message.role === persistedMessage.role
        && message.content === persistedMessage.content
      ))
    }
    if (currentIndex < 0) {
      return persistedMessage
    }

    matchedCurrentIndexes.add(currentIndex)
    const currentMessage = current[currentIndex]
    return {
      ...currentMessage,
      ...persistedMessage,
      activities: mergeActivities(currentMessage?.activities, persistedMessage.activities),
      artifacts: mergeArtifacts(currentMessage?.artifacts, persistedMessage.artifacts),
    }
  })
  return [
    ...merged,
    ...current.filter((_, index) => !matchedCurrentIndexes.has(index)),
  ]
}

function upsertAssistantMessage(messageId: string, payload: Partial<ChatUiMessage>) {
  const target = chatMessages.value.find((item) => item.id === messageId)
  if (!target) {
    return
  }

  if (payload.content !== undefined) {
    target.content = payload.content
  }
  if (payload.roundCode !== undefined) {
    target.roundCode = payload.roundCode
  }
  if (payload.status !== undefined) {
    target.status = payload.status
  }
  if (payload.actorName !== undefined) {
    target.actorName = payload.actorName
  }
  if (payload.activities !== undefined) {
    target.activities = payload.activities
  }
  if (payload.artifacts !== undefined) {
    target.artifacts = payload.artifacts
  }
  if (Object.prototype.hasOwnProperty.call(payload, 'error')) {
    target.error = payload.error
  }
}

function findAssistantMessage(messageId: string) {
  return chatMessages.value.find((item) => item.id === messageId)
}

function preserveAssistantContent(messageId: string, fallback: string) {
  const content = findAssistantMessage(messageId)?.content || ''
  return !content || ASSISTANT_PLACEHOLDERS.has(content) ? fallback : content
}

function syncStreamResult(result?: ChatTransportStreamResult) {
  if (!result) {
    return
  }
  currentRunId.value = result.runId || currentRunId.value
  lastEventId.value = result.lastEventId || lastEventId.value
  pendingSessionCode.value = result.sessionCode || pendingSessionCode.value
  currentRoundCode.value = result.roundCode || currentRoundCode.value
}

function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}

function waitForReconnect(delay: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException('Aborted', 'AbortError'))
      return
    }
    const timer = window.setTimeout(() => {
      signal.removeEventListener('abort', handleAbort)
      resolve()
    }, delay)
    const handleAbort = () => {
      window.clearTimeout(timer)
      reject(new DOMException('Aborted', 'AbortError'))
    }
    signal.addEventListener('abort', handleAbort, { once: true })
  })
}

function normalizeStreamError(error: unknown): ChatUiError {
  if (error instanceof ChatStreamInterruptedError || error instanceof TypeError) {
    return {
      code: 'CHAT_STREAM_INTERRUPTED',
      userMessage: '聊天连接未能恢复，请检查网络后重试。已接收的回复内容会保留。',
      detail: error instanceof Error ? sanitizeErrorDetail(error.message) : undefined,
      retryable: true,
    }
  }
  return {
    code: 'CHAT_REQUEST_FAILED',
    userMessage: '本轮处理失败，请稍后重试。',
    detail: error instanceof Error ? sanitizeErrorDetail(error.message) : undefined,
    retryable: true,
  }
}

function encodeRouteSegment(value: string) {
  return encodeURIComponent(value)
}

function groupRoute(groupCode: string) {
  return `/g/${encodeRouteSegment(groupCode)}`
}

function conversationRoute(conversation: Pick<ChatSessionItem, 'sessionCode' | 'groupCode'>) {
  const sessionCode = encodeRouteSegment(conversation.sessionCode)
  return conversation.groupCode
    ? `${groupRoute(conversation.groupCode)}/c/${sessionCode}`
    : `/c/${sessionCode}`
}

async function loadConversationGroups() {
  isLoadingGroups.value = true
  groupLoadError.value = ''
  try {
    groupList.value = await fetchConversationGroups()
  } catch (error) {
    groupList.value = []
    groupLoadError.value = error instanceof Error ? error.message : '分组列表加载失败'
  } finally {
    isLoadingGroups.value = false
  }
}

async function loadConversationList() {
  isLoadingList.value = true
  try {
    const sessions = await fetchConversationList({})
    conversationList.value = Array.isArray(sessions) ? sessions : []
  } finally {
    isLoadingList.value = false
  }
}

function applyConversationUpdate(updated: ChatSessionItem) {
  conversationList.value = conversationList.value
    .map((conversation) => conversation.sessionCode === updated.sessionCode ? updated : conversation)
    .sort((left, right) => {
      const pinnedDifference = Number(Boolean(right.pinned)) - Number(Boolean(left.pinned))
      if (pinnedDifference !== 0) {
        return pinnedDifference
      }
      return String(right.updateTime || '').localeCompare(String(left.updateTime || ''))
    })

  if (currentSessionCode.value === updated.sessionCode) {
    currentSessionName.value = updated.sessionName || currentSessionName.value
  }
}

function openCreateGroup() {
  closeConversationMenu()
  closeGroupMenu()
  groupNameInput.value = ''
  groupDialogVisible.value = true
}

function openRenameGroup(group: ChatGroupItem) {
  closeConversationMenu()
  closeGroupMenu()
  editingGroupCode.value = group.groupCode
  editingGroupName.value = group.groupName || ''
  groupRenameSubmitting.value = false
  void nextTick(() => groupRenameInput.value?.focus())
}

function resetGroupDialog() {
  groupNameInput.value = ''
  groupDialogSubmitting.value = false
}

function setGroupRenameInput(element: Element | null) {
  groupRenameInput.value = element instanceof HTMLInputElement ? element : null
}

function cancelGroupRename() {
  editingGroupCode.value = ''
  editingGroupName.value = ''
  groupRenameSubmitting.value = false
  groupRenameInput.value = null
}

async function submitGroupRename(group: ChatGroupItem) {
  if (groupRenameSubmitting.value) {
    return
  }
  const groupName = editingGroupName.value.trim()
  if (!groupName) {
    ElMessage.warning('请输入分组名称')
    void nextTick(() => groupRenameInput.value?.focus())
    return
  }
  if (groupName.length > 128) {
    ElMessage.warning('分组名称不能超过 128 个字符')
    void nextTick(() => groupRenameInput.value?.focus())
    return
  }

  groupRenameSubmitting.value = true
  try {
    const updated = await renameConversationGroup({
      groupCode: group.groupCode,
      groupName,
    })
    groupList.value = groupList.value.map(item => item.groupCode === updated.groupCode ? updated : item)
    cancelGroupRename()
    ElMessage.success('分组已重命名')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存分组失败')
  } finally {
    groupRenameSubmitting.value = false
  }
}

async function submitGroupDialog() {
  if (groupDialogSubmitting.value) {
    return
  }
  const groupName = groupNameInput.value.trim()
  if (!groupName) {
    ElMessage.warning('请输入分组名称')
    return
  }
  if (groupName.length > 128) {
    ElMessage.warning('分组名称不能超过 128 个字符')
    return
  }

  groupDialogSubmitting.value = true
  try {
    const created = await createConversationGroup({ groupName })
    groupList.value = [...groupList.value, created]
    groupDialogVisible.value = false
    ElMessage.success('分组已创建')
    await router.push(groupRoute(created.groupCode))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存分组失败')
  } finally {
    groupDialogSubmitting.value = false
  }
}

async function removeGroup(group: ChatGroupItem) {
  try {
    const confirmed = await appConfirm(
      `确定删除分组“${group.groupName}”吗？其中的会话会保留并移到未分组。`,
      {
        title: '删除分组',
        danger: true,
        confirmButtonText: '删除分组',
        cancelButtonText: '取消',
      },
    )
    if (!confirmed) {
      return
    }
    await deleteConversationGroup({ groupCode: group.groupCode })
    groupList.value = groupList.value.filter(item => item.groupCode !== group.groupCode)
    conversationList.value = conversationList.value.map(conversation => (
      conversation.groupCode === group.groupCode
        ? { ...conversation, groupCode: null }
        : conversation
    ))
    closeGroupMenu()
    ElMessage.success('分组已删除，会话已移到未分组')

    if (currentGroupId.value === group.groupCode) {
      const currentConversation = conversationList.value.find(item => item.sessionCode === currentSessionCode.value)
      await router.replace(currentConversation ? conversationRoute(currentConversation) : '/')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除分组失败')
  }
}

function toggleGroupMenu(groupCode: string) {
  openConversationMenuCode.value = ''
  openGroupMenuCode.value = openGroupMenuCode.value === groupCode ? '' : groupCode
}

function closeGroupMenu() {
  openGroupMenuCode.value = ''
}

function toggleRecent() {
  recentExpanded.value = !recentExpanded.value
}

function togglePinned() {
  pinnedExpanded.value = !pinnedExpanded.value
}

function isGroupExpanded(groupCode: string) {
  return !collapsedGroupCodes.value.has(groupCode)
}

function groupConversationListId(groupCode: string) {
  return `chat-home-group-${encodeURIComponent(groupCode)}`
}

function isSidebarSectionExpanded(section: SidebarConversationSection) {
  if (section.isRecent) {
    return recentExpanded.value
  }
  if (section.isPinned) {
    return pinnedExpanded.value
  }
  return isGroupExpanded(section.groupCode)
}

function toggleGroup(groupCode: string) {
  const nextCollapsedGroupCodes = new Set(collapsedGroupCodes.value)
  const isCurrentGroup = currentGroupId.value === groupCode

  if (isCurrentGroup) {
    if (nextCollapsedGroupCodes.has(groupCode)) {
      nextCollapsedGroupCodes.delete(groupCode)
    } else {
      nextCollapsedGroupCodes.add(groupCode)
    }
  } else {
    nextCollapsedGroupCodes.delete(groupCode)
  }

  collapsedGroupCodes.value = nextCollapsedGroupCodes
  closeConversationMenu()
  closeGroupMenu()

  if (!isCurrentGroup) {
    void router.push(groupRoute(groupCode))
  }
}

function openMoveConversation(conversation: ChatSessionItem) {
  closeConversationMenu()
  closeGroupMenu()
  movingConversation.value = conversation
  moveTargetGroupCode.value = conversation.groupCode || ''
  moveDialogVisible.value = true
}

function resetMoveConversation() {
  movingConversation.value = null
  moveTargetGroupCode.value = ''
  moveDialogSubmitting.value = false
}

async function submitMoveConversation() {
  if (moveDialogSubmitting.value) {
    return
  }
  const conversation = movingConversation.value
  if (!conversation) {
    return
  }

  moveDialogSubmitting.value = true
  try {
    const updated = await assignConversationGroup({
      sessionCode: conversation.sessionCode,
      groupCode: moveTargetGroupCode.value || null,
    })
    applyConversationUpdate(updated)
    moveDialogVisible.value = false
    ElMessage.success(updated.groupCode ? '会话已移动到分组' : '会话已移到未分组')
    if (currentSessionCode.value === updated.sessionCode) {
      await router.replace(conversationRoute(updated))
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '移动会话失败')
  } finally {
    moveDialogSubmitting.value = false
  }
}

function openRenameConversation(conversation: ChatSessionItem) {
  renamingConversation.value = conversation
  renameSessionName.value = conversation.sessionName || ''
  renameDialogVisible.value = true
}

function resetRenameConversation() {
  renamingConversation.value = null
  renameSessionName.value = ''
}

async function submitRenameConversation() {
  if (renameSubmitting.value) {
    return
  }
  const conversation = renamingConversation.value
  const sessionName = renameSessionName.value.trim()
  if (!conversation) {
    return
  }
  if (!sessionName) {
    ElMessage.warning('请输入会话名称')
    return
  }

  renameSubmitting.value = true
  try {
    const updated = await renameConversation({
      sessionCode: conversation.sessionCode,
      sessionName,
    })
    applyConversationUpdate(updated)
    renameDialogVisible.value = false
    ElMessage.success('会话已重命名')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重命名会话失败')
  } finally {
    renameSubmitting.value = false
  }
}

async function toggleConversationPin(conversation: ChatSessionItem) {
  try {
    const updated = await pinConversation({
      sessionCode: conversation.sessionCode,
      pinned: !conversation.pinned,
    })
    applyConversationUpdate(updated)
    ElMessage.success(updated.pinned ? '会话已置顶' : '已取消置顶')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更新会话置顶状态失败')
  }
}

async function removeConversation(conversation: ChatSessionItem) {
  try {
    const confirmed = await appConfirm(
      `确定删除会话“${conversation.sessionName || conversation.sessionCode}”吗？此操作不可恢复。`,
      {
        title: '删除会话',
        danger: true,
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )
    if (!confirmed) {
      return
    }
    await deleteConversation({ sessionCode: conversation.sessionCode })
    conversationList.value = conversationList.value.filter((item) => item.sessionCode !== conversation.sessionCode)
    ElMessage.success('会话已删除')
    if (currentSessionCode.value === conversation.sessionCode) {
      await router.replace('/')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除会话失败')
  }
}

function handleConversationCommand(command: string, conversation: ChatSessionItem) {
  closeConversationMenu()
  if (command === 'rename') {
    openRenameConversation(conversation)
    return
  }
  if (command === 'pin') {
    void toggleConversationPin(conversation)
    return
  }
  if (command === 'move') {
    openMoveConversation(conversation)
    return
  }
  if (command === 'delete') {
    void removeConversation(conversation)
  }
}

function toggleConversationMenu(sessionCode: string) {
  openConversationMenuCode.value = openConversationMenuCode.value === sessionCode ? '' : sessionCode
}

function closeConversationMenu() {
  openConversationMenuCode.value = ''
}

async function loadEnabledModelList() {
  isLoadingModels.value = true
  modelLoadError.value = ''
  try {
    const models = await fetchEnabledModels()
    modelOptions.value = (Array.isArray(models) ? models : [])
      .filter((model) => typeof model.id === 'number' && Number.isSafeInteger(model.id) && model.id > 0)
    const selectedStillAvailable = modelOptions.value.some((model) => model.id === selectedModel.value)
    if (!selectedStillAvailable) {
      selectedModel.value = modelOptions.value[0]?.id
    }
  }
  catch (error) {
    modelOptions.value = []
    selectedModel.value = undefined
    modelLoadError.value = error instanceof Error ? error.message : '模型列表加载失败'
  }
  finally {
    isLoadingModels.value = false
  }
}

function handleModelDropdownVisible(visible: boolean) {
  if (visible && !modelOptions.value.length && !isLoadingModels.value) {
    void loadEnabledModelList()
  }
}

async function loadConversationDetail(sessionCode: string, preserveMessagesOnError = false) {
  const normalizedSessionCode = sessionCode.trim()
  const sequence = ++detailRequestSequence
  if (!normalizedSessionCode) {
    chatMessages.value = []
    currentRoundCode.value = ''
    currentSessionName.value = ''
    resetConversationHistory()
    isLoadingDetail.value = false
    return
  }

  isLoadingDetail.value = true
  conversationError.value = ''
  try {
    const page = await loadInitialConversationHistory(normalizedSessionCode, 20)
    if (!page || sequence !== detailRequestSequence
      || currentSessionCode.value !== normalizedSessionCode) {
      return
    }
    const conversation = conversationList.value.find(item => item.sessionCode === normalizedSessionCode)
    currentSessionName.value = conversation?.sessionName || ''
    const persistedMessages = flattenRoundsToMessages(page.rounds || [])
    chatMessages.value = preserveMessagesOnError
      ? mergeConversationMessages(chatMessages.value, persistedMessages)
      : persistedMessages
    const lastRound = [...(page.rounds || [])].reverse().find((item) => item.round?.roundCode)
    currentRoundCode.value = lastRound?.round?.roundCode || ''
  } catch (error) {
    if (sequence !== detailRequestSequence || currentSessionCode.value !== normalizedSessionCode) {
      return
    }
    if (!preserveMessagesOnError) {
      chatMessages.value = []
      currentRoundCode.value = ''
      currentSessionName.value = ''
    }
    conversationError.value = error instanceof Error ? error.message : '会话详情加载失败'
  } finally {
    if (sequence === detailRequestSequence) {
      isLoadingDetail.value = false
    }
  }
}

async function loadOlderConversationHistory() {
  if (isLoadingHistory.value || !hasOlderHistory.value) {
    return
  }
  const targetSessionCode = currentSessionCode.value
  if (!targetSessionCode) {
    return
  }
  const container = conversationScrollContainer.value
  const previousScrollHeight = container?.scrollHeight || 0
  const previousScrollTop = container?.scrollTop || 0
  try {
    const response = await loadOlderConversationHistoryPage(targetSessionCode)
    if (!response || currentSessionCode.value !== targetSessionCode) {
      return
    }
    const olderMessages = flattenRoundsToMessages(response.rounds || [])
    if (olderMessages.length) {
      // The history endpoint is chronological. Seed the merge with the older page so
      // the in-flight local SSE message remains at the end of the timeline.
      chatMessages.value = mergeConversationMessages(
        [...olderMessages, ...chatMessages.value],
        olderMessages,
      )
    }
    await nextTick()
    if (container) {
      container.scrollTop = container.scrollHeight - previousScrollHeight + previousScrollTop
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '更早的会话记录加载失败')
  }
}

async function openConversationHistoryWindow(sessionCode: string, roundCode: string) {
  const targetSessionCode = sessionCode.trim()
  const targetRoundCode = roundCode.trim()
  if (!targetSessionCode || !targetRoundCode) {
    return
  }
  try {
    const response = await loadConversationHistoryWindow(targetSessionCode, targetRoundCode)
    if (!response || currentSessionCode.value !== targetSessionCode) {
      return
    }
    const windowMessages = flattenRoundsToMessages(response.rounds || [])
    if (windowMessages.length) {
      chatMessages.value = mergeConversationMessages(chatMessages.value, windowMessages)
    }
    await nextTick()
    const target = Array.from(
      conversationScrollContainer.value?.querySelectorAll<HTMLElement>('[data-round-code]') || [],
    ).find(element => element.dataset.roundCode === targetRoundCode)
    target?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '来源记录加载失败')
  }
}

function openConversationContext() {
  if (!currentSessionCode.value) {
    return
  }
  void openConversationContextState(currentSessionCode.value).catch(() => undefined)
  void loadLongTermMemories().catch(() => undefined)
}

function refreshConversationContextView() {
  void refreshConversationContext().catch((error) => {
    ElMessage.error(error instanceof Error ? error.message : '上下文刷新失败')
  })
  void loadLongTermMemories().catch((error) => {
    ElMessage.error(error instanceof Error ? error.message : '长期记忆刷新失败')
  })
}

async function handleMemorySource(item: ChatMemoryItem) {
  const sourceSessionCode = item.sourceSessionCode?.trim()
  const sourceRoundCode = item.sourceRoundCode?.trim()
  if (!sourceSessionCode || !sourceRoundCode) {
    ElMessage.info('这条记忆没有可定位的会话来源。')
    return
  }
  if (sourceSessionCode === currentSessionCode.value) {
    await openConversationHistoryWindow(sourceSessionCode, sourceRoundCode)
    return
  }
  const sourceConversation = conversationList.value.find(
    conversation => conversation.sessionCode === sourceSessionCode,
  )
  if (!sourceConversation) {
    ElMessage.warning('来源会话不在当前会话列表中，暂时无法跳转。')
    return
  }
  pendingMemorySource.value = { sessionCode: sourceSessionCode, roundCode: sourceRoundCode }
  await router.push(conversationRoute(sourceConversation))
}

async function runMemoryAction(
  action: () => Promise<unknown>,
  successMessage: string,
  fallbackMessage: string,
) {
  try {
    await action()
    ElMessage.success(successMessage)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : fallbackMessage)
  }
}

function handleDisableMemory(item: ChatMemoryItem) {
  void runMemoryAction(
    () => disableMemory(item.memoryRef),
    '记忆已停用',
    '停用记忆失败',
  )
}

function handleRestoreMemory(item: ChatMemoryItem) {
  void runMemoryAction(
    () => restoreMemory(item.memoryRef),
    '记忆已恢复',
    '恢复记忆失败',
  )
}

function openMemoryCorrection(item: ChatMemoryItem) {
  memoryCorrectionTarget.value = item
  memoryCorrectionContent.value = item.content || ''
  memoryCorrectionSubmitting.value = false
  memoryCorrectionDialogVisible.value = true
}

function resetMemoryCorrection() {
  memoryCorrectionTarget.value = null
  memoryCorrectionContent.value = ''
  memoryCorrectionSubmitting.value = false
}

async function submitMemoryCorrection() {
  if (memoryCorrectionSubmitting.value) {
    return
  }
  const target = memoryCorrectionTarget.value
  const content = memoryCorrectionContent.value.trim()
  if (!target) {
    return
  }
  if (!content) {
    ElMessage.warning('请输入修正后的记忆内容')
    return
  }
  memoryCorrectionSubmitting.value = true
  try {
    await correctMemory(target.memoryRef, content)
    memoryCorrectionDialogVisible.value = false
    ElMessage.success('已提交记忆修正，正在重新整理')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '修正记忆失败')
  } finally {
    memoryCorrectionSubmitting.value = false
  }
}

async function handlePromoteMemory(item: ChatMemoryItem) {
  const confirmed = await appConfirm(
    '确认将这条会话记忆保存为长期记忆吗？保存后可能在其他会话中被召回。',
    {
      title: '保存为长期记忆',
      confirmButtonText: '确认保存',
      cancelButtonText: '取消',
    },
  )
  if (!confirmed) {
    return
  }
  await runMemoryAction(
    () => promoteMemory(item.memoryRef),
    '已提交长期记忆保存',
    '保存长期记忆失败',
  )
}

async function handleExcludeMemory(item: ChatMemoryItem) {
  const confirmed = await appConfirm(
    '确认让本次会话不再使用这条长期记忆吗？不会影响其他会话。',
    {
      title: '当前会话排除',
      confirmButtonText: '确认排除',
      cancelButtonText: '取消',
    },
  )
  if (!confirmed) {
    return
  }
  await runMemoryAction(
    () => excludeMemoryFromSession(item.memoryRef, currentSessionCode.value),
    '已从当前会话排除',
    '排除长期记忆失败',
  )
}

async function handleForgetMemory(item: ChatMemoryItem) {
  const confirmed = await appConfirm(
    '永久删除后将无法恢复这条记忆。会话历史和消息不会被删除。',
    {
      title: '永久删除记忆',
      danger: true,
      confirmButtonText: '永久删除',
      cancelButtonText: '取消',
    },
  )
  if (!confirmed) {
    return
  }
  await runMemoryAction(
    () => forgetMemory(item.memoryRef),
    '记忆已删除',
    '删除记忆失败',
  )
}

async function handleClearLongTermMemory() {
  const confirmed = await appConfirm(
    '确认清空全部长期记忆吗？会话、消息、产物和活动记录会保留。',
    {
      title: '清空长期记忆',
      danger: true,
      confirmButtonText: '清空长期记忆',
      cancelButtonText: '取消',
    },
  )
  if (!confirmed) {
    return
  }
  await runMemoryAction(
    () => clearLongTermMemory(),
    '长期记忆清空任务已提交',
    '清空长期记忆失败',
  )
}

function handleStreamEvent(
  event: { id: string; event: string; data: ChatTransportEvent },
  assistantMessageId: string,
) {
  const { data } = event
  const eventName = data.eventType?.trim() || event.event
  const payload = data.payload || {}
  lastEventId.value = data.eventId || event.id || lastEventId.value
  if (data.runId) {
    currentRunId.value = data.runId
  }

  if (data.sessionCode && !currentSessionCode.value) {
    pendingSessionCode.value = data.sessionCode
    void router.replace(currentGroupId.value
      ? `${groupRoute(currentGroupId.value)}/c/${encodeRouteSegment(data.sessionCode)}`
      : `/c/${encodeRouteSegment(data.sessionCode)}`)
  }
  if (data.sessionCode) {
    pendingSessionCode.value = data.sessionCode
  }
  if (data.sessionName) {
    currentSessionName.value = data.sessionName
  }
  if (data.roundCode) {
    currentRoundCode.value = data.roundCode
  }
  if (eventName === 'session.initialized') {
    const conversation = payload.conversation as { title?: string } | undefined
    currentSessionName.value = conversation?.title || currentSessionName.value
  }

  const assistantMessage = findAssistantMessage(assistantMessageId)
  const activity = activityFromTransportEvent(
    eventName,
    payload,
    data.eventId || event.id,
    data.timestamp,
  )
  if (activity) {
    upsertAssistantMessage(assistantMessageId, {
      activities: upsertRunActivity(assistantMessage?.activities || [], activity),
    })
  }
  const eventArtifacts = artifactsFromTransportEvent(eventName, payload)
  if (eventArtifacts.length) {
    const artifacts = eventArtifacts.reduce(
      (items, artifact) => upsertArtifact(items, artifact),
      assistantMessage?.artifacts || [],
    )
    upsertAssistantMessage(assistantMessageId, { artifacts })
  }

  if (eventName === 'run.accepted') {
    interactionState.value = stopRequested.value ? 'stopping' : 'connecting'
    if (stopRequested.value && currentRunId.value) {
      void requestStopCurrentRun()
    }
    return
  }

  if (eventName === 'session.initialized' || eventName === 'round.initialized') {
    conversationError.value = ''
    interactionState.value = stopRequested.value ? 'stopping' : 'thinking'
    return
  }

  if (eventName === 'run.started' || eventName === 'assistant.started' || eventName === 'thinking.started') {
    conversationError.value = ''
    interactionState.value = stopRequested.value ? 'stopping' : 'thinking'
    upsertAssistantMessage(assistantMessageId, {
      content: preserveAssistantContent(assistantMessageId, '正在思考...'),
      status: 'RUNNING',
    })
    return
  }

  if (eventName === 'thinking.updated') {
    conversationError.value = ''
    interactionState.value = stopRequested.value ? 'stopping' : 'thinking'
    return
  }

  if (eventName === 'thinking.completed') {
    return
  }

  if (eventName === 'assistant.message.delta') {
    const message = payload.message as { content?: Array<{ text?: string; markdown?: string }>; append?: boolean } | undefined
    const content = (message?.content || []).map((item) => item.markdown || item.text || '').join('\n')
    if (content) {
      conversationError.value = ''
      interactionState.value = stopRequested.value ? 'stopping' : 'streaming'
      const existingContent = findAssistantMessage(assistantMessageId)?.content || ''
      const previous = ASSISTANT_PLACEHOLDERS.has(existingContent) ? '' : existingContent
      upsertAssistantMessage(assistantMessageId, {
        content: message?.append ? `${previous}${content}` : content,
        roundCode: data.roundCode,
        status: 'RUNNING',
      })
    }
    return
  }

  if (eventName === 'round.completed') {
    const assistant = (payload.round as { assistant?: { messages?: Array<{ content?: Array<{ text?: string; markdown?: string }> }> } } | undefined)?.assistant
    const content = (assistant?.messages || [])
      .flatMap((message) => message.content || [])
      .map((item) => item.markdown || item.text || '')
      .join('\n')
    upsertAssistantMessage(assistantMessageId, {
      content: content || preserveAssistantContent(assistantMessageId, '回复已完成，正在同步内容...'),
      roundCode: data.roundCode,
      status: 'COMPLETED',
      error: undefined,
    })
    interactionState.value = 'completed'
    stopRequested.value = false
    conversationError.value = ''
    return
  }

  if (eventName === 'assistant.input_required') {
    const message = (payload.input as { message?: string } | undefined)?.message
    upsertAssistantMessage(assistantMessageId, {
      content: preserveAssistantContent(assistantMessageId, message || '请补充更多信息后继续。'),
      roundCode: data.roundCode,
      status: 'WAITING_INPUT',
    })
    interactionState.value = 'waiting_input'
    stopRequested.value = false
    conversationError.value = ''
    void nextTick(() => conversationTextarea.value?.focus())
    return
  }

  if (eventName === 'round.failed') {
    const error = normalizeStructuredError(
      payload.error,
      (payload.round as { message?: string } | undefined)?.message,
    )
    upsertAssistantMessage(assistantMessageId, {
      content: preserveAssistantContent(assistantMessageId, ''),
      roundCode: data.roundCode,
      status: 'FAILED',
      error,
    })
    interactionState.value = 'failed'
    stopRequested.value = false
    return
  }

  if (eventName === 'round.cancelled') {
    upsertAssistantMessage(assistantMessageId, {
      content: preserveAssistantContent(assistantMessageId, '对话已取消。'),
      roundCode: data.roundCode,
      status: 'CANCELLED',
      error: undefined,
    })
    interactionState.value = 'cancelled'
    stopRequested.value = false
  }
}

function applyTerminalRunStatus(
  status: string | undefined,
  assistantMessageId: string,
  errorInfo?: unknown,
  errorDetail?: string,
) {
  const normalizedStatus = status?.toLowerCase()
  if (normalizedStatus === 'completed') {
    interactionState.value = 'completed'
    stopRequested.value = false
    upsertAssistantMessage(assistantMessageId, {
      content: preserveAssistantContent(assistantMessageId, '回复已完成，正在同步内容...'),
      status: 'COMPLETED',
      error: undefined,
    })
    return true
  }
  if (normalizedStatus === 'failed') {
    interactionState.value = 'failed'
    stopRequested.value = false
    upsertAssistantMessage(assistantMessageId, {
      content: preserveAssistantContent(assistantMessageId, ''),
      status: 'FAILED',
      error: normalizeStructuredError(errorInfo, errorDetail),
    })
    return true
  }
  if (normalizedStatus === 'cancelled') {
    interactionState.value = 'cancelled'
    stopRequested.value = false
    conversationError.value = ''
    upsertAssistantMessage(assistantMessageId, {
      content: preserveAssistantContent(assistantMessageId, '对话已取消。'),
      status: 'CANCELLED',
    })
    return true
  }
  if (normalizedStatus === 'waiting_input') {
    interactionState.value = 'waiting_input'
    stopRequested.value = false
    upsertAssistantMessage(assistantMessageId, { status: 'WAITING_INPUT' })
    return true
  }
  if (normalizedStatus === 'cancelling') {
    interactionState.value = 'stopping'
  }
  return false
}

async function streamWithRecovery(
  requestPayload: ReturnType<typeof createChatTransportRequest>,
  assistantMessageId: string,
  controller: AbortController,
) {
  const handleEvent = (event: { id: string; event: string; data: ChatTransportEvent }) =>
    handleStreamEvent(event, assistantMessageId)

  try {
    const result = await streamChatTransport(requestPayload, handleEvent, {
      signal: controller.signal,
      seenEventIds: activeSeenEventIds,
    })
    syncStreamResult(result)
    return result
  } catch (initialError) {
    if (isAbortError(initialError)) {
      throw initialError
    }
    if (initialError instanceof ChatStreamInterruptedError) {
      syncStreamResult(initialError.result)
    }

    let lastError: unknown = initialError
    for (let attempt = 1; attempt <= MAX_RECONNECT_ATTEMPTS; attempt += 1) {
      if (!currentRunId.value) {
        break
      }

      interactionState.value = 'reconnecting'
      conversationError.value = ''
      await waitForReconnect(attempt * 500, controller.signal)

      try {
        const run = await fetchChatRunStatus(currentRunId.value)
        currentRunId.value = run.runId || currentRunId.value
        pendingSessionCode.value = run.sessionCode || pendingSessionCode.value
        currentRoundCode.value = run.roundCode || currentRoundCode.value
        if (applyTerminalRunStatus(run.status, assistantMessageId, run.errorInfo, run.error)) {
          return {
            terminalEventReceived: true,
            terminalEventName: run.status === 'cancelled'
              ? 'round.cancelled'
              : run.status === 'failed'
                ? 'round.failed'
                : run.status === 'waiting_input'
                  ? 'assistant.input_required'
                  : 'round.completed',
            lastEventId: lastEventId.value,
            runId: currentRunId.value,
            sessionCode: pendingSessionCode.value,
            roundCode: currentRoundCode.value,
          } as ChatTransportStreamResult
        }
      } catch (statusError) {
        if (isAbortError(statusError)) {
          throw statusError
        }
        lastError = statusError
      }

      try {
        const result = await reconnectChatTransport({
          runId: currentRunId.value,
          lastEventId: lastEventId.value || undefined,
          sessionCode: pendingSessionCode.value || currentSessionCode.value || undefined,
          roundCode: currentRoundCode.value || undefined,
        }, handleEvent, {
          signal: controller.signal,
          seenEventIds: activeSeenEventIds,
        })
        syncStreamResult(result)
        return result
      } catch (reconnectError) {
        if (isAbortError(reconnectError)) {
          throw reconnectError
        }
        if (reconnectError instanceof ChatStreamInterruptedError) {
          syncStreamResult(reconnectError.result)
        }
        lastError = reconnectError
      }
    }
    throw lastError
  }
}

async function submitChatMessage(message: string) {
  if (!ensureModelReady()) {
    return
  }
  const userMessageId = `user-${Date.now()}`
  const assistantMessageId = `assistant-${Date.now()}`
  activeAssistantMessageId.value = assistantMessageId
  currentRunId.value = ''
  lastEventId.value = ''
  activeSeenEventIds = new Set<string>()
  stopRequested.value = false
  const streamController = new AbortController()
  activeStreamController = streamController

  chatMessages.value.push({
    id: userMessageId,
    role: 'user',
    content: message,
  })
  chatMessages.value.push({
    id: assistantMessageId,
    role: 'assistant',
    content: '正在连接 AI...',
    status: 'RUNNING',
    actorName: ASSISTANT_DISPLAY_NAME,
    activities: [],
    artifacts: [],
  })

  conversationError.value = ''
  interactionState.value = 'connecting'
  isStreaming.value = true
  await syncTextareaHeights()

  try {
    const result = await streamWithRecovery(
      createChatTransportRequest({
        sessionCode: currentSessionCode.value || undefined,
        groupCode: currentSessionCode.value ? undefined : currentGroupId.value || null,
        modelId: selectedModel.value,
        message,
      }, route.path),
      assistantMessageId,
      streamController,
    )
    syncStreamResult(result)

    const finalSessionCode = currentSessionCode.value || pendingSessionCode.value
    try {
      await loadConversationList()
    } catch {
      ElMessage.warning('回复已完成，但会话列表刷新失败。')
    }
    if (finalSessionCode && result.terminalEventName === 'round.completed') {
      await loadConversationDetail(finalSessionCode, true)
      await loadConversationContext(finalSessionCode, { preserve: true }).catch(() => undefined)
    }
  } catch (error) {
    if (isAbortError(error) && interactionState.value === 'cancelled') {
      return
    }
    const messageError = normalizeStreamError(error)
    upsertAssistantMessage(assistantMessageId, {
      content: preserveAssistantContent(assistantMessageId, ''),
      status: 'FAILED',
      error: messageError,
    })
    interactionState.value = 'failed'
  } finally {
    if (activeStreamController === streamController) {
      activeStreamController = null
    }
    isStreaming.value = false
    activeAssistantMessageId.value = ''
    stopRequested.value = false
  }
}

function ensureModelReady() {
  if (isLoadingModels.value) {
    ElMessage.warning('模型列表正在加载，请稍后再试。')
    return false
  }
  if (!selectedModel.value) {
    ElMessage.warning(modelLoadError.value || '请先选择一个已启用模型。')
    return false
  }
  return true
}

async function handlePrimaryAction() {
  if (isStreaming.value) {
    await handleStopAction()
    return
  }

  const message = prompt.value.trim()
  if (!message) {
    return
  }
  if (!ensureModelReady()) {
    return
  }
  prompt.value = ''
  await submitChatMessage(message)
}

async function handleStopAction() {
  if (!isStreaming.value || interactionState.value === 'stopping') {
    return
  }

  stopRequested.value = true
  interactionState.value = 'stopping'
  conversationError.value = ''
  if (!currentRunId.value) {
    return
  }

  await requestStopCurrentRun()
}

async function requestStopCurrentRun() {
  if (!currentRunId.value || stopRequestInFlight) {
    return
  }

  stopRequestInFlight = true
  try {
    const accepted = await stopChatRun(currentRunId.value)
    if (!accepted) {
      throw new Error('停止请求未被接受，请稍后重试。')
    }
  } catch (error) {
    stopRequested.value = false
    interactionState.value = 'thinking'
    ElMessage.error(error instanceof Error ? error.message : '停止任务失败，请稍后重试。')
  } finally {
    stopRequestInFlight = false
  }
}

function retrySourceMessage(message: ChatUiMessage) {
  const messageIndex = chatMessages.value.findIndex((item) => item.id === message.id)
  if (messageIndex < 0) {
    return undefined
  }
  for (let index = messageIndex - 1; index >= 0; index -= 1) {
    const candidate = chatMessages.value[index]
    const belongsToRound = !message.roundCode
      || !candidate?.roundCode
      || candidate.roundCode === message.roundCode
    if (candidate?.role === 'user' && candidate.content.trim() && belongsToRound) {
      return candidate.content
    }
  }
  return undefined
}

function canRetryMessage(message: ChatUiMessage) {
  return message.error?.retryable !== false && Boolean(retrySourceMessage(message))
}

function retryMessage(message: ChatUiMessage) {
  if (isStreaming.value || !canRetryMessage(message)) {
    return
  }
  const sourceMessage = retrySourceMessage(message)
  if (sourceMessage) {
    void submitChatMessage(sourceMessage)
  }
}

function handlePromptKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey) {
    return
  }

  event.preventDefault()
  if (isStreaming.value) {
    return
  }
  void handlePrimaryAction()
}

function toggleUserMenu(target: 'topbar' | 'sidebar') {
  activeUserMenu.value = activeUserMenu.value === target ? null : target
}

function selectTheme(theme: 'dark' | 'light') {
  activeTheme.value = applyTheme(theme)
}

function toggleDeveloperMode() {
  developerModeEnabled.value = !developerModeEnabled.value
  setDeveloperModeEnabled(developerModeEnabled.value)
  ElMessage.success(developerModeEnabled.value ? '已进入开发者模式' : '已退出开发者模式')
}

async function handleUserMenuAction(key: string) {
  if (key === 'system-settings') {
    closeUserMenu()
    await router.push('/settings/system')
    return
  }
}

function closeUserMenu() {
  activeUserMenu.value = null
}

async function handleLogout() {
  closeUserMenu()
  clearSession()
  await router.push('/auth/login')
}

function handleDocumentClick(event: MouseEvent) {
  if (!activeUserMenu.value && !openConversationMenuCode.value && !openGroupMenuCode.value) {
    return
  }

  const target = event.target
  if (!(target instanceof Node)) {
    return
  }

  if (target instanceof Element && target.closest('.chat-home-user-menu-anchor')) {
    return
  }

  if (target instanceof Element && target.closest('.chat-home-thread-wrap')) {
    return
  }

  if (target instanceof Element && target.closest('.chat-home-group-wrap')) {
    return
  }

  closeUserMenu()
  closeConversationMenu()
  closeGroupMenu()
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  developerModeEnabled.value = getDeveloperModeEnabled()
  activeTheme.value = getSavedTheme()
  void loadEnabledModelList()
  void loadConversationGroups()
  void loadConversationList()
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  activeStreamController?.abort()
})

watch(prompt, () => {
  void syncTextareaHeights()
})

watch(isConversationMode, () => {
  void syncTextareaHeights()
}, { immediate: true })

async function handleSessionChange(sessionCode: string) {
  const normalizedSessionCode = sessionCode.trim()
  if (!normalizedSessionCode) {
    detailRequestSequence += 1
    chatMessages.value = []
    currentRoundCode.value = ''
    currentSessionName.value = ''
    pendingSessionCode.value = ''
    conversationError.value = ''
    interactionState.value = 'idle'
    resetConversationHistory()
    clearConversationContext()
    isLoadingDetail.value = false
    return
  }

  void loadConversationContext(normalizedSessionCode, { preserve: true }).catch(() => undefined)
  if (isStreaming.value && normalizedSessionCode === pendingSessionCode.value) {
    return
  }
  interactionState.value = 'idle'
  await loadConversationDetail(normalizedSessionCode)

  if (currentSessionCode.value !== normalizedSessionCode) {
    return
  }
  const pendingSource = pendingMemorySource.value
  if (pendingSource?.sessionCode === normalizedSessionCode) {
    pendingMemorySource.value = null
    await openConversationHistoryWindow(normalizedSessionCode, pendingSource.roundCode)
  }
}

watch(currentSessionCode, (sessionCode) => {
  void handleSessionChange(sessionCode)
}, { immediate: true })

watch(route, () => {
  closeUserMenu()
})
</script>

<template>
  <div
    :class="[
      'chat-home-shell',
      {
        'is-sidebar-collapsed': !sidebarExpanded,
      },
    ]"
    :style="{ '--chat-sidebar-width': sidebarExpanded ? '13.125rem' : '3rem' }"
  >
    <aside class="chat-home-sidebar">
      <div class="chat-home-brand">
        <button
          class="chat-home-brand__logo"
          type="button"
          aria-label="返回聊天首页"
          title="返回聊天首页"
          :disabled="isStreaming"
          @click="router.push('/')"
        >
          <img
            class="chat-home-brand__logo-image"
            :src="sidebarExpanded ? (activeTheme === 'dark' ? brandLogoDark : brandLogo) : brandMark"
            :alt="sidebarExpanded ? '智能问数 ZG' : '智能问数'"
          />
        </button>
      </div>

      <nav class="chat-home-nav">
        <button
          v-for="item in quickNavItems"
          :key="item.key"
          :class="['chat-home-nav__item', { 'is-active': activeNav === item.key }]"
          type="button"
          :disabled="isStreaming"
          @click="
            item.key === 'new-chat'
              ? router.push('/')
              : (activeNav = item.key)
          "
        >
          <span class="chat-home-nav__leading">
            <el-icon><component :is="item.icon" /></el-icon>
            <span v-if="sidebarExpanded">{{ item.label }}</span>
          </span>
        </button>
      </nav>

      <div v-if="sidebarExpanded" class="chat-home-sidebar__section chat-home-sidebar__section--conversations">
        <div class="chat-home-sidebar__header chat-home-sidebar__header--actions">
          <button
            class="chat-home-sidebar__header-action"
            type="button"
            aria-label="新建分组"
            title="新建分组"
            :disabled="isStreaming"
            @click="openCreateGroup"
          >
            <el-icon><Plus /></el-icon>
          </button>
        </div>
        <div v-if="groupLoadError" class="chat-home-thread-empty chat-home-thread-empty--error">
          {{ groupLoadError }}
        </div>
        <div v-else-if="isLoadingGroups" class="chat-home-thread-empty">分组加载中...</div>
        <template v-for="group in sidebarConversationGroups" :key="group.key">
          <div :class="['chat-home-group-wrap', { 'chat-home-group-wrap--recent': group.isRecent }]">
            <button
              v-if="group.isPinned"
              class="chat-home-group-label chat-home-group-label--button chat-home-group-label--heading chat-home-group-label--pinned"
              type="button"
              :aria-expanded="pinnedExpanded"
              aria-controls="chat-home-pinned-list"
              :disabled="isStreaming"
              @click="togglePinned"
            >
              <el-icon class="chat-home-group-label__toggle chat-home-group-label__toggle--leading">
                <ArrowDown v-if="pinnedExpanded" />
                <ArrowRight v-else />
              </el-icon>
              <el-icon><StarFilled /></el-icon>
              <span>{{ group.label }}</span>
              <small>{{ group.conversations.length }}</small>
            </button>
            <button
              v-else-if="group.isRecent"
              class="chat-home-group-label chat-home-group-label--button chat-home-group-label--collapsible"
              type="button"
              :aria-expanded="recentExpanded"
              aria-controls="chat-home-recent-list"
              :disabled="isStreaming"
              @click="toggleRecent"
            >
              <el-icon><Clock /></el-icon>
              <span>{{ group.label }}</span>
              <small>{{ group.conversations.length }}</small>
              <el-icon class="chat-home-group-label__toggle">
                <ArrowDown v-if="recentExpanded" />
                <ArrowRight v-else />
              </el-icon>
            </button>
            <div
              v-else-if="editingGroupCode === group.groupCode"
              class="chat-home-group-label chat-home-group-label--heading chat-home-group-label--editing"
            >
              <el-icon class="chat-home-group-label__toggle chat-home-group-label__toggle--leading">
                <ArrowDown v-if="isGroupExpanded(group.groupCode)" />
                <ArrowRight v-else />
              </el-icon>
              <el-icon><FolderOpened /></el-icon>
              <input
                :ref="setGroupRenameInput"
                v-model="editingGroupName"
                class="chat-home-group-rename-input"
                type="text"
                maxlength="128"
                aria-label="分组名称"
                :disabled="groupRenameSubmitting"
                @click.stop
                @keydown.enter.prevent="submitGroupRename(group)"
                @keydown.esc.prevent="cancelGroupRename"
              />
              <small>{{ group.conversations.length }}</small>
              <button
                class="chat-home-group-rename-action"
                type="button"
                aria-label="保存分组名称"
                title="保存"
                :disabled="groupRenameSubmitting"
                @click.stop="submitGroupRename(group)"
              >
                <el-icon><Check /></el-icon>
              </button>
              <button
                class="chat-home-group-rename-action"
                type="button"
                aria-label="取消重命名"
                title="取消"
                :disabled="groupRenameSubmitting"
                @click.stop="cancelGroupRename"
              >
                <el-icon><CloseBold /></el-icon>
              </button>
            </div>
            <template v-else>
              <button
                :class="[
                  'chat-home-group-label',
                  'chat-home-group-label--button',
                  { 'is-current': currentGroupId === group.groupCode && !isConversationMode },
                ]"
                type="button"
                :disabled="isStreaming"
                :aria-expanded="isGroupExpanded(group.groupCode)"
                :aria-controls="groupConversationListId(group.groupCode)"
                @click="toggleGroup(group.groupCode)"
              >
                <el-icon class="chat-home-group-label__toggle chat-home-group-label__toggle--leading">
                  <ArrowDown v-if="isGroupExpanded(group.groupCode)" />
                  <ArrowRight v-else />
                </el-icon>
                <el-icon><FolderOpened /></el-icon>
                <span>{{ group.label }}</span>
                <small>{{ group.conversations.length }}</small>
              </button>
              <button
                class="chat-home-group__more"
                type="button"
                aria-label="分组操作"
                :disabled="isStreaming"
                @click.stop="toggleGroupMenu(group.groupCode)"
              >
                <el-icon><MoreFilled /></el-icon>
              </button>
              <div
                v-if="openGroupMenuCode === group.groupCode"
                class="chat-home-group__action-menu"
                @click.stop
              >
                <button type="button" @click="openRenameGroup(group)">重命名</button>
                <button class="is-danger" type="button" @click="removeGroup(group)">删除分组</button>
              </div>
            </template>
          </div>

          <div
            v-if="isSidebarSectionExpanded(group)"
            :id="group.groupCode
              ? groupConversationListId(group.groupCode)
              : (group.isPinned ? 'chat-home-pinned-list' : (group.isRecent ? 'chat-home-recent-list' : undefined))"
          >
            <div
              v-for="conversation in group.conversations"
              :key="conversation.id"
              class="chat-home-thread-wrap"
            >
              <button
                :class="[
                  'chat-home-thread',
                  {
                    'is-current': activeConversation === conversation.id,
                    'chat-home-thread--nested': Boolean(group.groupCode),
                  },
                ]"
                type="button"
                :disabled="isStreaming"
                @click="router.push(conversationRoute(conversation))"
              >
                <div class="chat-home-thread__leading">
                  <div class="chat-home-thread__copy">
                    <span class="chat-home-thread__title">{{ conversation.title }}</span>
                  </div>
                </div>
                <span class="chat-home-thread__meta">{{ conversation.meta }}</span>
              </button>
              <button
                class="chat-home-thread__more"
                type="button"
                aria-label="会话操作"
                :disabled="isStreaming"
                @click.stop="toggleConversationMenu(conversation.id)"
              >
                <el-icon><MoreFilled /></el-icon>
              </button>
              <div v-if="openConversationMenuCode === conversation.id" class="chat-home-thread__action-menu" @click.stop>
                <button type="button" @click="handleConversationCommand('pin', conversation)">
                  {{ conversation.pinned ? '取消置顶' : '置顶' }}
                </button>
                <button type="button" @click="handleConversationCommand('rename', conversation)">重命名</button>
                <button type="button" @click="handleConversationCommand('move', conversation)">移动到分组</button>
                <button class="is-danger" type="button" @click="handleConversationCommand('delete', conversation)">删除</button>
              </div>
            </div>
          </div>
        </template>

        <div v-if="!isLoadingList && conversationList.length === 0" class="chat-home-thread-empty">
          暂无会话
        </div>
      </div>

      <div class="chat-home-sidebar__footer">
        <div class="chat-home-user-menu-anchor chat-home-user-menu-anchor--sidebar">
            <button class="chat-home-user" type="button" @click.stop="toggleUserMenu('sidebar')">
              <div class="chat-home-user__avatar" :aria-label="`${currentUserName} 的头像`" role="img">
                <img v-if="currentUserAvatarUrl" class="chat-home-user-avatar-image" :src="currentUserAvatarUrl" :alt="currentUserName" />
                <span v-else>{{ currentUserAvatarText }}</span>
              </div>
              <div v-if="sidebarExpanded" class="chat-home-user__copy">
                <strong>{{ currentUserName }}</strong>
              </div>
            </button>
          <div
            v-if="activeUserMenu === 'sidebar'"
            class="chat-home-user-menu chat-home-user-menu--sidebar"
            @click.stop
          >
            <div class="chat-home-user-menu__profile">
              <div class="chat-home-user-menu__avatar" :aria-label="`${currentUserName} 的头像`" role="img">
                <img v-if="currentUserAvatarUrl" class="chat-home-user-avatar-image" :src="currentUserAvatarUrl" :alt="currentUserName" />
                <span v-else>{{ currentUserAvatarText }}</span>
              </div>
              <div class="chat-home-user-menu__identity">
                <div class="chat-home-user-menu__name">{{ currentUserName }}</div>
                <div class="chat-home-user-menu__status">
                  <span class="chat-home-user-menu__status-dot"></span>
                  <span>在线</span>
                </div>
              </div>
            </div>

            <button class="chat-home-user-menu__status-action" type="button">
              <el-icon><Check /></el-icon>
              <span>更新您的状态</span>
            </button>

            <div class="chat-home-user-menu__section">
              <div class="chat-home-user-menu__section-title">
                <el-icon><Setting /></el-icon>
                <span>设置</span>
              </div>
              <div class="chat-home-user-menu__theme-label">主题</div>
              <div class="chat-home-user-menu__theme-switch">
                <button
                  :class="['chat-home-user-menu__theme-option', { 'is-active': activeTheme === 'dark' }]"
                  type="button"
                  @click="selectTheme('dark')"
                >
                  暗色
                </button>
                <button
                  :class="['chat-home-user-menu__theme-option', { 'is-active': activeTheme === 'light' }]"
                  type="button"
                  @click="selectTheme('light')"
                >
                  浅色
                </button>
              </div>
            </div>

            <div class="chat-home-user-menu__list">
              <button class="chat-home-user-menu__item" type="button" @click="toggleDeveloperMode">
                <el-icon><Operation /></el-icon>
                <span>{{ developerModeEnabled ? '退出开发者模式' : '进入开发者模式' }}</span>
              </button>
              <button
                v-for="item in userMenuItems"
                :key="item.key"
                class="chat-home-user-menu__item"
                type="button"
                @click="handleUserMenuAction(item.key)"
              >
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </button>
            </div>

            <button class="chat-home-user-menu__item chat-home-user-menu__item--logout" type="button" @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>
              <span>登出</span>
            </button>
          </div>
        </div>
      </div>
    </aside>

    <button
      class="chat-home-shell__toggle"
      type="button"
      :aria-label="sidebarExpanded ? '收起侧边栏' : '展开侧边栏'"
      :title="sidebarExpanded ? '收起侧边栏' : '展开侧边栏'"
      @click="sidebarExpanded = !sidebarExpanded"
    >
      <el-icon>
        <ArrowLeftBold v-if="sidebarExpanded" />
        <ArrowRightBold v-else />
      </el-icon>
    </button>

    <main class="chat-home-main">
      <header class="chat-home-topbar">
        <div class="chat-home-topbar__left" aria-hidden="true"></div>

        <div class="chat-home-topbar__right">
          <button class="ghost-icon-button" type="button"><el-icon><MoreFilled /></el-icon></button>
          <button class="ghost-icon-button" type="button"><el-icon><Operation /></el-icon></button>
          <div class="chat-home-user-menu-anchor">
            <button class="avatar-chip" type="button" :aria-label="`${currentUserName} 的头像菜单`" @click.stop="toggleUserMenu('topbar')">
              <img v-if="currentUserAvatarUrl" class="chat-home-user-avatar-image" :src="currentUserAvatarUrl" :alt="currentUserName" />
              <span v-else>{{ currentUserAvatarText }}</span>
            </button>
            <div v-if="activeUserMenu === 'topbar'" class="chat-home-user-menu" @click.stop>
              <div class="chat-home-user-menu__profile">
                <div class="chat-home-user-menu__avatar" :aria-label="`${currentUserName} 的头像`" role="img">
                  <img v-if="currentUserAvatarUrl" class="chat-home-user-avatar-image" :src="currentUserAvatarUrl" :alt="currentUserName" />
                  <span v-else>{{ currentUserAvatarText }}</span>
                </div>
                <div class="chat-home-user-menu__identity">
                  <div class="chat-home-user-menu__name">{{ currentUserName }}</div>
                  <div class="chat-home-user-menu__status">
                    <span class="chat-home-user-menu__status-dot"></span>
                    <span>在线</span>
                  </div>
                </div>
              </div>

              <button class="chat-home-user-menu__status-action" type="button">
                <el-icon><Check /></el-icon>
                <span>更新您的状态</span>
              </button>

              <div class="chat-home-user-menu__section">
                <div class="chat-home-user-menu__section-title">
                  <el-icon><Setting /></el-icon>
                  <span>设置</span>
                </div>
                <div class="chat-home-user-menu__theme-label">主题</div>
                <div class="chat-home-user-menu__theme-switch">
                  <button
                    :class="['chat-home-user-menu__theme-option', { 'is-active': activeTheme === 'dark' }]"
                    type="button"
                    @click="selectTheme('dark')"
                  >
                    暗色
                  </button>
                  <button
                    :class="['chat-home-user-menu__theme-option', { 'is-active': activeTheme === 'light' }]"
                    type="button"
                    @click="selectTheme('light')"
                  >
                    浅色
                  </button>
                </div>
              </div>

              <div class="chat-home-user-menu__list">
                <button class="chat-home-user-menu__item" type="button" @click="toggleDeveloperMode">
                  <el-icon><Operation /></el-icon>
                  <span>{{ developerModeEnabled ? '退出开发者模式' : '进入开发者模式' }}</span>
                </button>
                <button
                  v-for="item in userMenuItems"
                  :key="item.key"
                  class="chat-home-user-menu__item"
                  type="button"
                  @click="handleUserMenuAction(item.key)"
                >
                  <el-icon><component :is="item.icon" /></el-icon>
                  <span>{{ item.label }}</span>
                </button>
              </div>

              <button class="chat-home-user-menu__item chat-home-user-menu__item--logout" type="button" @click="handleLogout">
                <el-icon><SwitchButton /></el-icon>
                <span>登出</span>
              </button>
            </div>
          </div>
        </div>
      </header>

      <section v-if="!isConversationMode" class="chat-home-welcome">
        <div class="chat-home-welcome-stage">
          <div v-if="currentGroupId" class="chat-home-group-context">
            <el-icon><FolderOpened /></el-icon>
            <span>分组：{{ currentGroupName }}</span>
          </div>
          <div class="chat-home-welcome-model chat-home-welcome-model--auto-routing">
            <div class="chat-home-welcome-model__avatar">AI</div>
            <div class="chat-home-welcome-model__copy">
              <div class="chat-home-welcome-model__name">今天想完成什么？</div>
              <div class="chat-home-welcome-model__description">
                {{ welcomeModelDescription }}
              </div>
            </div>
          </div>
          <div v-if="modelAvailabilityMessage" class="chat-workspace-alert" role="alert">
            <span>{{ modelAvailabilityMessage }}</span>
            <button type="button" @click="loadEnabledModelList">重新加载模型</button>
          </div>
          <div class="chat-home-composer chat-home-composer--floating chat-home-composer--welcome chat-home-composer--model-selectable">
            <textarea
              ref="welcomeTextarea"
              v-model="prompt"
              placeholder="描述你想完成的任务..."
              rows="1"
              @keydown="handlePromptKeydown"
            ></textarea>
            <div class="chat-home-composer__footer">
              <div class="chat-home-composer__tools">
                <button class="composer-tool-button" type="button"><span>+</span></button>
                <button class="composer-tool-button" type="button">
                  <el-icon><Operation /></el-icon>
                </button>
              </div>
              <div class="chat-home-composer__actions">
                <el-select
                  v-model="selectedModel"
                  class="chat-home-model-switcher chat-home-model-switcher--composer"
                  :style="{ '--chat-composer-model-content-width': composerModelContentWidth }"
                  :loading="isLoadingModels"
                  :disabled="isLoadingModels || isStreaming"
                  :no-data-text="modelSelectEmptyText"
                  placeholder="选择模型"
                  filterable
                  aria-label="选择对话模型"
                  title="切换对话模型"
                  @visible-change="handleModelDropdownVisible"
                >
                  <el-option
                    v-for="model in modelOptions"
                    :key="model.id"
                    :label="model.modelName || model.modelCode || model.apiModel"
                    :value="model.id"
                  >
                    <div class="chat-home-model-option">
                      <span>{{ model.modelName || model.modelCode || model.apiModel }}</span>
                      <small>{{ model.apiModel || model.modelCode }}</small>
                    </div>
                  </el-option>
                </el-select>
                <button class="composer-icon-button" type="button"><el-icon><Microphone /></el-icon></button>
                <button
                  :class="['composer-send-button', { 'is-stop': isStreaming }]"
                  type="button"
                  :disabled="isPrimaryActionDisabled"
                  :aria-label="isStreaming ? '停止当前任务' : '发送消息'"
                  :title="isStreaming ? '停止当前任务' : '发送消息'"
                  @click="handlePrimaryAction"
                >
                  <el-icon><CloseBold v-if="isStreaming" /><Promotion v-else /></el-icon>
                </button>
              </div>
            </div>
          </div>

          <div class="chat-home-welcome-suggestions">
            <div class="chat-home-welcome-suggestions__header">建议</div>
            <button
              v-for="item in welcomeSuggestions"
              :key="item.title"
              class="chat-home-welcome-suggestion"
              type="button"
              :disabled="isStreaming"
              @click="applySuggestion(item.prompt)"
            >
              <strong>{{ item.title }}</strong>
              <span>{{ item.subtitle }}</span>
            </button>
          </div>
        </div>
      </section>

      <section v-else class="chat-home-conversation">
        <div
          ref="conversationScrollContainer"
          class="chat-home-center-column chat-home-center-column--conversation"
        >
          <ConversationContextSummaryBar
            :context="conversationContext"
            :counts="conversationContextCounts"
            :loading="isLoadingConversationContext"
            @open="openConversationContext"
          />
          <div v-if="chatMessages.length === 0 && !isLoadingDetail" class="chat-home-assistant">
            <div class="chat-home-assistant__avatar">AI</div>
            <div class="chat-home-assistant__body">
              <div class="chat-home-assistant__title">{{ ASSISTANT_DISPLAY_NAME }}</div>
              <div class="chat-home-assistant__meta">{{ currentSessionName || '新会话' }}</div>
              <div class="chat-home-assistant__text">你好！有什么我可以帮你的吗？</div>
              <div class="chat-home-assistant__actions">
                <button
                  v-for="index in 6"
                  :key="index"
                  class="ghost-inline-icon"
                  type="button"
                >
                  <span></span>
                </button>
              </div>
            </div>
          </div>

          <div v-if="chatMessages.length === 0 && !isLoadingDetail" class="chat-home-followups">
            <button
              v-for="card in welcomeCards.slice(0, 3)"
              :key="card"
              class="chat-home-followup-line"
              type="button"
              :disabled="isStreaming"
              @click="applySuggestion(card)"
            >
              {{ card }}
            </button>
          </div>

          <div v-if="modelAvailabilityMessage" class="chat-workspace-alert" role="alert">
            <span>{{ modelAvailabilityMessage }}</span>
            <button type="button" @click="loadEnabledModelList">重新加载模型</button>
          </div>
          <div v-if="conversationError" class="chat-home-feedback chat-workspace-alert" role="alert">
            <span>{{ conversationError }}</span>
          </div>
          <div v-else-if="isLoadingDetail" class="chat-home-feedback">会话加载中...</div>
          <div v-if="hasOlderHistory" class="chat-history-loader">
            <el-button
              link
              size="small"
              :loading="isLoadingHistory"
              @click="loadOlderConversationHistory"
            >
              加载更早记录
            </el-button>
          </div>
          <div class="chat-home-message-list">
            <article
              v-for="message in chatMessages"
              :key="message.id"
              :class="['chat-home-message', `is-${message.role}`]"
              :data-round-code="message.roundCode || undefined"
            >
              <template v-if="message.role === 'assistant'">
                <div class="chat-home-message__assistant-row">
                  <div class="chat-home-assistant__avatar chat-home-assistant__avatar--small">AI</div>
                  <div class="chat-home-message__assistant-copy">
                    <div class="chat-home-message__assistant-name">{{ message.actorName || ASSISTANT_DISPLAY_NAME }}</div>
                    <RunActivityTimeline
                      v-if="message.activities?.length"
                      :activities="message.activities"
                      :run-status="message.status"
                    />
                    <div
                      v-if="message.content"
                      class="chat-home-message__assistant-text"
                      v-html="renderMarkdown(message.content)"
                    ></div>
                    <ChatArtifactList
                      v-if="message.artifacts?.length"
                      :artifacts="message.artifacts"
                    />
                    <ChatMessageErrorCard
                      v-if="message.error"
                      :error="message.error"
                      :retry-available="canRetryMessage(message)"
                      :retry-disabled="isStreaming"
                      @retry="retryMessage(message)"
                    />
                    <div
                      v-if="isAssistantRoundEnded(message)"
                      class="chat-home-feedback"
                      aria-label="回复操作"
                    >
                      <button
                        class="chat-home-feedback__button"
                        type="button"
                        @click="showPlannedMessageAction('点赞')"
                      >
                        <svg class="chat-home-feedback__icon" viewBox="0 0 16 16" aria-hidden="true">
                          <path d="M6.2 6.3 7 2.5c.1-.5.5-.9 1-.9.7 0 1.2.5 1.2 1.2v2.5h3.1c.8 0 1.4.7 1.3 1.5l-.6 4.5c-.1.9-.9 1.5-1.8 1.5H6.2V6.3Z" />
                          <path d="M2.3 6.4h2.4v6.4H2.3V6.4Z" />
                        </svg>
                        <span>点赞</span>
                      </button>
                      <button
                        class="chat-home-feedback__button"
                        type="button"
                        @click="showPlannedMessageAction('反馈')"
                      >
                        <el-icon><ChatDotRound /></el-icon>
                        <span>反馈</span>
                      </button>
                    </div>
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="chat-home-message__user-row">
                  <div class="chat-home-user-bubble">{{ message.content }}</div>
                  <div class="chat-home-message__user-avatar" :aria-label="`${currentUserName} 的头像`" role="img">
                    <img v-if="currentUserAvatarUrl" class="chat-home-user-avatar-image" :src="currentUserAvatarUrl" :alt="currentUserName" />
                    <span v-else>{{ currentUserAvatarText }}</span>
                  </div>
                </div>
              </template>
            </article>
          </div>
        </div>

        <div class="chat-home-composer chat-home-composer--floating chat-home-composer--conversation chat-home-composer--model-selectable">
          <textarea
            ref="conversationTextarea"
            v-model="prompt"
            placeholder="继续描述需求或补充信息..."
            rows="1"
            @keydown="handlePromptKeydown"
          ></textarea>
          <div class="chat-home-composer__footer">
            <div class="chat-home-composer__tools">
              <button class="composer-tool-button" type="button"><span>+</span></button>
              <button class="composer-tool-button" type="button">
                <el-icon><Operation /></el-icon>
              </button>
            </div>
            <div class="chat-home-composer__actions">
              <el-select
                v-model="selectedModel"
                class="chat-home-model-switcher chat-home-model-switcher--composer"
                :style="{ '--chat-composer-model-content-width': composerModelContentWidth }"
                :loading="isLoadingModels"
                :disabled="isLoadingModels || isStreaming"
                :no-data-text="modelSelectEmptyText"
                placeholder="选择模型"
                filterable
                aria-label="选择对话模型"
                title="切换对话模型"
                @visible-change="handleModelDropdownVisible"
              >
                <el-option
                  v-for="model in modelOptions"
                  :key="model.id"
                  :label="model.modelName || model.modelCode || model.apiModel"
                  :value="model.id"
                >
                  <div class="chat-home-model-option">
                    <span>{{ model.modelName || model.modelCode || model.apiModel }}</span>
                    <small>{{ model.apiModel || model.modelCode }}</small>
                  </div>
                </el-option>
              </el-select>
              <button class="composer-icon-button" type="button"><el-icon><Microphone /></el-icon></button>
              <button
                :class="['composer-send-button', { 'is-stop': isStreaming }]"
                type="button"
                :disabled="isPrimaryActionDisabled"
                :aria-label="isStreaming ? '停止当前任务' : '发送消息'"
                :title="isStreaming ? '停止当前任务' : '发送消息'"
                @click="handlePrimaryAction"
              >
                <el-icon><CloseBold v-if="isStreaming" /><Promotion v-else /></el-icon>
              </button>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>

  <AppDialog
    v-model="renameDialogVisible"
    title="重命名会话"
    size="small"
    action-mode="confirm"
    confirm-text="保存"
    :confirming="renameSubmitting"
    :close-on-press-escape="!renameSubmitting"
    :show-close="!renameSubmitting"
    @confirm="submitRenameConversation"
    @closed="resetRenameConversation"
  >
    <el-input
      v-model="renameSessionName"
      maxlength="100"
      show-word-limit
      autofocus
      @keyup.enter="submitRenameConversation"
    />
  </AppDialog>

  <AppDialog
    v-model="groupDialogVisible"
    title="新建分组"
    size="small"
    action-mode="confirm"
    confirm-text="保存"
    :confirming="groupDialogSubmitting"
    :close-on-press-escape="!groupDialogSubmitting"
    :show-close="!groupDialogSubmitting"
    @confirm="submitGroupDialog"
    @closed="resetGroupDialog"
  >
    <el-input
      v-model="groupNameInput"
      maxlength="128"
      show-word-limit
      autofocus
      placeholder="例如：数据分析、项目规划"
      @keyup.enter="submitGroupDialog"
    />
  </AppDialog>

  <AppDialog
    v-model="moveDialogVisible"
    title="移动会话"
    size="small"
    action-mode="confirm"
    confirm-text="移动"
    :confirming="moveDialogSubmitting"
    :close-on-press-escape="!moveDialogSubmitting"
    :show-close="!moveDialogSubmitting"
    @confirm="submitMoveConversation"
    @closed="resetMoveConversation"
  >
    <el-select v-model="moveTargetGroupCode" class="chat-home-move-select" placeholder="选择目标分组">
      <el-option label="未分组" value="" />
      <el-option
        v-for="group in groupList"
        :key="group.groupCode"
        :label="group.groupName"
        :value="group.groupCode"
      />
    </el-select>
  </AppDialog>

  <AppDialog
    v-model="memoryCorrectionDialogVisible"
    title="纠正记忆"
    description="提交后，记忆平台会以新内容重新整理这条记忆。"
    size="medium"
    action-mode="confirm"
    confirm-text="提交纠正"
    :confirming="memoryCorrectionSubmitting"
    :close-on-press-escape="!memoryCorrectionSubmitting"
    :show-close="!memoryCorrectionSubmitting"
    @confirm="submitMemoryCorrection"
    @closed="resetMemoryCorrection"
  >
    <el-input
      v-model="memoryCorrectionContent"
      type="textarea"
      :rows="6"
      maxlength="2000"
      show-word-limit
      autofocus
      placeholder="输入希望记忆平台保留的内容"
    />
  </AppDialog>

  <ConversationContextDrawer
    v-model="contextDrawerVisible"
    :session-code="currentSessionCode"
    :context="conversationContext"
    :long-term-list="conversationLongTermList"
    :loading="isLoadingConversationContext || isLoadingLongTermMemories"
    :error="conversationContextError"
    :action-loading="conversationContextActionLoading"
    @refresh="refreshConversationContextView"
    @disable="handleDisableMemory"
    @restore="handleRestoreMemory"
    @correct="openMemoryCorrection"
    @promote="handlePromoteMemory"
    @exclude="handleExcludeMemory"
    @forget="handleForgetMemory"
    @source="handleMemorySource"
    @clear-long-term="handleClearLongTermMemory"
  />
</template>

<style scoped lang="scss">
@use '../../../styles/chat-home';

.chat-workspace-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 860px;
  color: var(--chat-text-muted);
  font-size: 13px;
}

.chat-workspace-alert {
  color: var(--chat-danger, var(--app-danger));
}

.chat-workspace-alert button {
  padding: 4px 9px;
  border: 1px solid var(--chat-panel-border);
  border-radius: 999px;
  background: var(--chat-main-bg);
  color: inherit;
  cursor: pointer;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.chat-workspace-alert button:hover,
.chat-workspace-alert button:focus-visible {
  border-color: currentcolor;
  background: var(--chat-soft-bg);
  outline: none;
}

.chat-home-welcome-stage > .chat-workspace-alert {
  align-self: center;
}

.chat-history-loader {
  display: flex;
  justify-content: center;
  width: min(760px, calc(100% - 48px));
  margin: 0 auto;
  padding: 0 0 var(--app-space-2);
}

.chat-history-loader :deep(.el-button) {
  color: var(--chat-text-muted);
}

.chat-history-loader :deep(.el-button:hover),
.chat-history-loader :deep(.el-button:focus-visible) {
  color: var(--app-accent);
}

.composer-send-button.is-stop {
  background: var(--chat-danger);
}

@media (prefers-reduced-motion: reduce) {
  .chat-workspace-alert button {
    transition: none;
  }
}
</style>
