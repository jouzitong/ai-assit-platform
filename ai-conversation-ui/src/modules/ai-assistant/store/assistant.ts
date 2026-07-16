import { computed, reactive } from 'vue'
import {
  activityFromTransportEvent,
  artifactsFromTransportEvent,
  normalizeHistoricalActivities,
  normalizeHistoricalArtifacts,
  upsertArtifact,
  upsertRunActivity,
} from '../../ai-chat/composables/useChatRun'
import type {
  ChatArtifact,
  ChatConversationRound,
  ChatEnabledModel,
  ChatRunStatus,
  ChatSessionItem,
  ChatTransportStreamResult,
  ChatUiError,
  ChatUiMessage,
} from '../../ai-chat/types'
import type { ChatTransportStreamEvent } from '../../ai-chat/api'
import {
  ChatStreamInterruptedError,
  createPageAssistantTransportRequest,
  fetchPageAssistantConversationDetail,
  fetchPageAssistantModels,
  fetchPageAssistantRunStatus,
  fetchPageAssistantSessions,
  reconnectPageAssistantTransport,
  stopPageAssistantRun,
  streamSettingsAssistantTransport,
} from '../api'
import { captureAgentPageContext } from '../services/pageContext'
import type { AgentPageContext } from '../types'

const MODEL_STORAGE_KEY = 'ai-page-assistant:model-id'
const SESSION_STORAGE_KEY = 'ai-page-assistant:session-code'
const FRESH_SESSION_STORAGE_KEY = 'ai-page-assistant:start-fresh'
const MAX_RECONNECT_ATTEMPTS = 3
const PLACEHOLDERS = new Set(['正在连接 AI...', '正在思考...', '正在生成回复...'])

interface AiAssistantState {
  open: boolean
  models: ChatEnabledModel[]
  selectedModelId?: number
  modelsLoaded: boolean
  modelsLoading: boolean
  modelLoadError: string
  sessions: ChatSessionItem[]
  sessionCode: string
  historyLoaded: boolean
  historyLoading: boolean
  historyError: string
  messages: ChatUiMessage[]
  draft: string
  running: boolean
  stopping: boolean
  runError: string
}

const state = reactive<AiAssistantState>({
  open: false,
  models: [],
  selectedModelId: undefined,
  modelsLoaded: false,
  modelsLoading: false,
  modelLoadError: '',
  sessions: [],
  sessionCode: '',
  historyLoaded: false,
  historyLoading: false,
  historyError: '',
  messages: [],
  draft: '',
  running: false,
  stopping: false,
  runError: '',
})

const selectedModel = computed(() =>
  state.models.find(model => model.id === state.selectedModelId) || null,
)

let currentRunId = ''
let currentRoundCode = ''
let lastEventId = ''
let activeAssistantMessageId = ''
let activeController: AbortController | null = null
let activeSeenEventIds = new Set<string>()
let stopRequested = false
let stopRequestInFlight = false

function storageGet(key: string) {
  try {
    return window.localStorage.getItem(key) || ''
  } catch {
    return ''
  }
}

function storageSet(key: string, value: string) {
  try {
    if (value) window.localStorage.setItem(key, value)
    else window.localStorage.removeItem(key)
  } catch {
    // Storage is an enhancement; the assistant still works without it.
  }
}

function setSessionCode(sessionCode?: string) {
  if (!sessionCode) return
  state.sessionCode = sessionCode
  storageSet(SESSION_STORAGE_KEY, sessionCode)
  storageSet(FRESH_SESSION_STORAGE_KEY, '')
}

function parseRecord(value?: string | null): Record<string, unknown> | undefined {
  if (!value?.trim()) return undefined
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : undefined
  } catch {
    return undefined
  }
}

function textValue(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function normalizeError(value: unknown, fallback?: string): ChatUiError {
  const record = value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
  const nested = record.error && typeof record.error === 'object' && !Array.isArray(record.error)
    ? record.error as Record<string, unknown>
    : record
  return {
    code: textValue(nested.code) || 'PAGE_ASSISTANT_FAILED',
    userMessage: textValue(nested.userMessage) || '页面助手处理失败，请稍后重试。',
    detail: textValue(nested.detail) || textValue(nested.reason) || textValue(fallback),
    retryable: typeof nested.retryable === 'boolean' ? nested.retryable : true,
    traceId: textValue(nested.traceId),
  }
}

function historicalError(round: ChatConversationRound) {
  const failed = [...(round.messages || [])].reverse().find(message =>
    message.role?.toUpperCase() === 'ASSISTANT' && message.status?.toUpperCase() === 'FAILED',
  )
  return normalizeError(parseRecord(failed?.extJson), failed?.content)
}

function flattenHistory(rounds: ChatConversationRound[]) {
  return rounds.flatMap((round) => {
    const visible = (round.messages || []).filter(message => message.displayLevel?.toUpperCase() !== 'HIDDEN')
    const messages: ChatUiMessage[] = visible.flatMap((message) => {
      const role = message.role?.toUpperCase()
      if ((role !== 'USER' && role !== 'ASSISTANT') || !message.content?.trim()) return []
      const ext = parseRecord(message.extJson)
      return [{
        id: message.messageCode || `${round.round?.roundCode || 'round'}-${message.sortNo || 0}`,
        role: role === 'ASSISTANT' ? 'assistant' : 'user',
        content: message.content,
        roundCode: message.roundCode || round.round?.roundCode,
        status: message.status || round.round?.status || undefined,
        actorName: role === 'ASSISTANT'
          ? textValue(ext?.agentName) || textValue(ext?.agentCode) || '页面助手'
          : undefined,
      } satisfies ChatUiMessage]
    })
    const activities = normalizeHistoricalActivities(round.activities)
    const artifacts = normalizeHistoricalArtifacts(round.artifacts)
    let owner = [...messages].reverse().find(message => message.role === 'assistant')
    if (!owner && (activities.length || artifacts.length)) {
      owner = {
        id: `${round.round?.roundCode || 'round'}-assistant-run`,
        role: 'assistant',
        content: '',
        roundCode: round.round?.roundCode,
        status: round.round?.status || undefined,
        actorName: '页面助手',
      }
      messages.push(owner)
    }
    if (owner) {
      owner.activities = activities
      owner.artifacts = artifacts
    }
    const failed = round.round?.status?.toUpperCase() === 'FAILED'
      || visible.some(message => message.status?.toUpperCase() === 'FAILED')
    if (failed) {
      if (!owner) {
        owner = {
          id: `${round.round?.roundCode || 'round'}-assistant-error`,
          role: 'assistant',
          content: '',
          roundCode: round.round?.roundCode,
          actorName: '页面助手',
        }
        messages.push(owner)
      }
      owner.status = 'FAILED'
      owner.error = historicalError(round)
    }
    return messages
  })
}

async function loadModels(force = false) {
  if (state.modelsLoading || (state.modelsLoaded && !force)) return
  state.modelsLoading = true
  state.modelLoadError = ''
  try {
    state.models = (await fetchPageAssistantModels()).filter(model => Number(model.id) > 0)
    const savedId = Number(storageGet(MODEL_STORAGE_KEY))
    const desired = state.models.find(model => model.id === state.selectedModelId)
      || state.models.find(model => model.id === savedId)
      || state.models[0]
    state.selectedModelId = desired?.id
    if (!state.models.length) state.modelLoadError = '系统暂无已启用模型'
  } catch (error) {
    state.models = []
    state.selectedModelId = undefined
    state.modelLoadError = error instanceof Error ? error.message : '模型列表加载失败'
  } finally {
    state.modelsLoaded = true
    state.modelsLoading = false
  }
}

function setSelectedModel(modelId?: number) {
  state.selectedModelId = modelId
  storageSet(MODEL_STORAGE_KEY, modelId ? String(modelId) : '')
}

async function loadHistory(force = false) {
  if (state.historyLoading || (state.historyLoaded && !force)) return
  state.historyLoading = true
  state.historyError = ''
  try {
    state.sessions = await fetchPageAssistantSessions()
    if (storageGet(FRESH_SESSION_STORAGE_KEY)) {
      state.sessionCode = ''
      state.messages = []
      return
    }
    const saved = state.sessionCode || storageGet(SESSION_STORAGE_KEY)
    const session = state.sessions.find(item => item.sessionCode === saved) || state.sessions[0]
    if (!session) {
      state.sessionCode = ''
      state.messages = []
      return
    }
    setSessionCode(session.sessionCode)
    const detail = await fetchPageAssistantConversationDetail(session.sessionCode)
    state.messages = flattenHistory(detail.rounds || [])
  } catch (error) {
    state.historyError = error instanceof Error ? error.message : '页面助手历史加载失败'
  } finally {
    state.historyLoaded = true
    state.historyLoading = false
  }
}

function openAssistant() {
  state.open = true
  void loadModels()
  void loadHistory()
}

function closeAssistant() {
  state.open = false
}

function clearMessages() {
  if (state.running) return
  state.messages = []
  state.sessionCode = ''
  state.runError = ''
  state.historyError = ''
  storageSet(SESSION_STORAGE_KEY, '')
  storageSet(FRESH_SESSION_STORAGE_KEY, '1')
}

function findAssistantMessage() {
  return state.messages.find(message => message.id === activeAssistantMessageId)
}

function preserveContent(fallback: string) {
  const content = findAssistantMessage()?.content || ''
  return !content || PLACEHOLDERS.has(content) ? fallback : content
}

function updateAssistant(patch: Partial<ChatUiMessage>) {
  const message = findAssistantMessage()
  if (!message) return
  Object.assign(message, patch)
}

function syncResult(result?: ChatTransportStreamResult) {
  if (!result) return
  currentRunId = result.runId || currentRunId
  lastEventId = result.lastEventId || lastEventId
  currentRoundCode = result.roundCode || currentRoundCode
  setSessionCode(result.sessionCode)
}

async function requestStop() {
  if (!currentRunId || stopRequestInFlight) return
  stopRequestInFlight = true
  try {
    const accepted = await stopPageAssistantRun(currentRunId)
    if (!accepted) throw new Error('停止请求未被接受，请稍后重试。')
  } catch (error) {
    stopRequested = false
    state.stopping = false
    state.runError = error instanceof Error ? error.message : '停止页面助手失败'
  } finally {
    stopRequestInFlight = false
  }
}

function handleTransportEvent(event: ChatTransportStreamEvent) {
  const { data, event: eventName } = event
  const payload = data.payload || {}
  lastEventId = data.eventId || event.id || lastEventId
  currentRunId = data.runId || currentRunId
  currentRoundCode = data.roundCode || currentRoundCode
  setSessionCode(data.sessionCode)

  const message = findAssistantMessage()
  const activity = activityFromTransportEvent(eventName, payload, data.eventId || event.id, data.timestamp)
  if (activity) updateAssistant({ activities: upsertRunActivity(message?.activities || [], activity) })
  const nextArtifacts = artifactsFromTransportEvent(eventName, payload)
  if (nextArtifacts.length) {
    updateAssistant({
      artifacts: nextArtifacts.reduce<ChatArtifact[]>(
        (items, artifact) => upsertArtifact(items, artifact),
        message?.artifacts || [],
      ),
    })
  }

  if (eventName === 'run.accepted') {
    if (stopRequested) void requestStop()
    return
  }
  if (eventName === 'run.started' || eventName === 'assistant.started' || eventName === 'thinking.started') {
    updateAssistant({ content: preserveContent('正在思考...'), status: 'RUNNING' })
    return
  }
  if (eventName === 'assistant.message.delta') {
    const transportMessage = payload.message as { content?: Array<{ text?: string; markdown?: string }>; append?: boolean } | undefined
    const content = (transportMessage?.content || []).map(item => item.markdown || item.text || '').join('\n')
    if (content) {
      const previous = preserveContent('')
      updateAssistant({
        content: transportMessage?.append ? `${previous}${content}` : content,
        roundCode: data.roundCode,
        status: 'RUNNING',
      })
    }
    return
  }
  if (eventName === 'round.completed') {
    const round = payload.round as { assistant?: { messages?: Array<{ content?: Array<{ text?: string; markdown?: string }> }> } } | undefined
    const content = (round?.assistant?.messages || [])
      .flatMap(item => item.content || [])
      .map(item => item.markdown || item.text || '')
      .join('\n')
    updateAssistant({ content: content || preserveContent('回复已完成。'), status: 'COMPLETED', error: undefined })
    return
  }
  if (eventName === 'assistant.input_required') {
    const input = payload.input as { message?: string } | undefined
    updateAssistant({ content: preserveContent(input?.message || '请补充更多信息后继续。'), status: 'WAITING_INPUT' })
    return
  }
  if (eventName === 'round.failed') {
    updateAssistant({ content: preserveContent(''), status: 'FAILED', error: normalizeError(payload.error) })
    return
  }
  if (eventName === 'round.cancelled') {
    updateAssistant({ content: preserveContent('任务已取消。'), status: 'CANCELLED', error: undefined })
  }
}

function applyRunStatus(run: ChatRunStatus) {
  setSessionCode(run.sessionCode)
  currentRoundCode = run.roundCode || currentRoundCode
  const status = run.status?.toLowerCase()
  if (status === 'completed') updateAssistant({ content: preserveContent('回复已完成。'), status: 'COMPLETED' })
  else if (status === 'failed') updateAssistant({ status: 'FAILED', error: normalizeError(run.errorInfo, run.error) })
  else if (status === 'cancelled') updateAssistant({ content: preserveContent('任务已取消。'), status: 'CANCELLED' })
  else if (status === 'waiting_input') updateAssistant({ status: 'WAITING_INPUT' })
  else return false
  return true
}

function wait(delay: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const timer = window.setTimeout(resolve, delay)
    signal.addEventListener('abort', () => {
      window.clearTimeout(timer)
      reject(new DOMException('Aborted', 'AbortError'))
    }, { once: true })
  })
}

async function streamWithRecovery(payload: ReturnType<typeof createPageAssistantTransportRequest>, controller: AbortController) {
  try {
    const result = await streamSettingsAssistantTransport(payload, handleTransportEvent, {
      signal: controller.signal,
      seenEventIds: activeSeenEventIds,
    })
    syncResult(result)
    return
  } catch (initialError) {
    if (initialError instanceof ChatStreamInterruptedError) syncResult(initialError.result)
    let lastError: unknown = initialError
    for (let attempt = 1; attempt <= MAX_RECONNECT_ATTEMPTS && currentRunId; attempt += 1) {
      await wait(attempt * 400, controller.signal)
      try {
        const run = await fetchPageAssistantRunStatus(currentRunId)
        if (applyRunStatus(run)) return
      } catch (error) {
        lastError = error
      }
      try {
        const result = await reconnectPageAssistantTransport({
          runId: currentRunId,
          lastEventId: lastEventId || undefined,
          sessionCode: state.sessionCode || undefined,
          roundCode: currentRoundCode || undefined,
        }, handleTransportEvent, {
          signal: controller.signal,
          seenEventIds: activeSeenEventIds,
        })
        syncResult(result)
        return
      } catch (error) {
        if (error instanceof ChatStreamInterruptedError) syncResult(error.result)
        lastError = error
      }
    }
    throw lastError
  }
}

async function sendMessage() {
  const content = state.draft.trim()
  if (!content || state.running || state.historyLoading) return false
  if (!state.selectedModelId) {
    state.runError = state.modelLoadError || '请先选择一个系统已启用模型。'
    return false
  }

  state.runError = ''
  let pageContext: AgentPageContext
  try {
    pageContext = await captureAgentPageContext()
  } catch (error) {
    state.runError = error instanceof Error
      ? `当前页面上下文读取失败：${error.message}`
      : '当前页面上下文读取失败，请刷新页面后重试。'
    return false
  }
  state.draft = ''
  currentRunId = ''
  currentRoundCode = ''
  lastEventId = ''
  activeSeenEventIds = new Set<string>()
  stopRequested = false
  state.running = true
  state.stopping = false
  const timestamp = Date.now()
  activeAssistantMessageId = `page-assistant-${timestamp}`
  state.messages.push({ id: `page-user-${timestamp}`, role: 'user', content })
  state.messages.push({
    id: activeAssistantMessageId,
    role: 'assistant',
    actorName: '页面助手',
    content: '正在连接 AI...',
    status: 'RUNNING',
    activities: [],
    artifacts: [],
  })
  const controller = new AbortController()
  activeController = controller

  try {
    await streamWithRecovery(createPageAssistantTransportRequest({
      sessionCode: state.sessionCode || undefined,
      modelId: state.selectedModelId,
      message: content,
      route: window.location.pathname,
      pageContext,
    }), controller)
    if (state.sessionCode) await loadHistory(true)
    return true
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'AbortError')) {
      const uiError = normalizeError(undefined, error instanceof Error ? error.message : '页面助手连接失败')
      updateAssistant({ content: preserveContent(''), status: 'FAILED', error: uiError })
      state.runError = uiError.userMessage
    }
    return false
  } finally {
    if (activeController === controller) activeController = null
    state.running = false
    state.stopping = false
    stopRequested = false
    activeAssistantMessageId = ''
  }
}

async function stopRun() {
  if (!state.running || state.stopping) return
  stopRequested = true
  state.stopping = true
  await requestStop()
}

export function useAiAssistantStore() {
  return {
    state,
    selectedModel,
    loadModels,
    setSelectedModel,
    loadHistory,
    openAssistant,
    closeAssistant,
    clearMessages,
    stopRun,
    sendMessage,
  }
}
