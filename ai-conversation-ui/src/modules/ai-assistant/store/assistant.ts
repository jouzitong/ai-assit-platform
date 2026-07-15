import { computed, reactive } from 'vue'
import { fetchBrowserAgentModels } from '../api'
import type {
  AgentActivityUpdate,
  AiAssistantActivity,
  AiAssistantActivityStatus,
  AiAssistantMessage,
  BrowserAgentModel,
} from '../types'

const SELECTED_MODEL_STORAGE_KEY = 'ai-assistant:selected-model-code'

interface AiAssistantState {
  open: boolean
  models: BrowserAgentModel[]
  selectedModelCode: string
  modelsLoaded: boolean
  modelsLoading: boolean
  modelLoadError: string
  messages: AiAssistantMessage[]
  draft: string
  running: boolean
  runError: string
}

const state = reactive<AiAssistantState>({
  open: false,
  models: [],
  selectedModelCode: '',
  modelsLoaded: false,
  modelsLoading: false,
  modelLoadError: '',
  messages: [],
  draft: '',
  running: false,
  runError: '',
})

let activeController: AbortController | null = null
let activeRunToken: symbol | null = null

const selectedModel = computed(() => state.models.find(model => model.modelCode === state.selectedModelCode) || null)

function newMessageId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `assistant-message-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function readStoredModelCode() {
  try {
    return window.localStorage.getItem(SELECTED_MODEL_STORAGE_KEY) || ''
  }
  catch {
    return ''
  }
}

function storeModelCode(modelCode: string) {
  try {
    window.localStorage.setItem(SELECTED_MODEL_STORAGE_KEY, modelCode)
  }
  catch {
    // Local storage can be unavailable in privacy-restricted browsers.
  }
}

async function loadModels(force = false) {
  if (state.modelsLoading || state.modelsLoaded && !force) return
  state.modelsLoading = true
  state.modelLoadError = ''
  try {
    const models = await fetchBrowserAgentModels()
    state.models = (models || []).filter(model => Boolean(model.id && model.modelCode && model.apiModel))
    state.modelsLoaded = true
    const preferred = state.selectedModelCode || readStoredModelCode()
    const resolved = state.models.find(model => model.modelCode === preferred) || state.models[0]
    state.selectedModelCode = resolved?.modelCode || ''
    if (resolved) storeModelCode(resolved.modelCode)
  }
  catch (error) {
    state.modelLoadError = error instanceof Error ? error.message : '模型列表加载失败'
    state.modelsLoaded = false
  }
  finally {
    state.modelsLoading = false
  }
}

function setSelectedModel(modelCode: string) {
  state.selectedModelCode = modelCode
  if (modelCode) storeModelCode(modelCode)
}

function openAssistant() {
  state.open = true
  void loadModels()
}

function closeAssistant() {
  state.open = false
}

function clearMessages() {
  if (state.running) return
  state.messages = []
  state.runError = ''
}

function stopRun() {
  if (!activeController || activeController.signal.aborted) return
  const pendingMessage = [...state.messages].reverse()
    .find(message => message.role === 'assistant' && message.status === 'pending')
  if (pendingMessage) {
    upsertActivity(pendingMessage, {
      id: 'stop-request',
      kind: 'summary',
      title: '正在停止本次处理',
      status: 'running',
    })
  }
  activeController.abort()
}

function upsertActivity(message: AiAssistantMessage, update: AgentActivityUpdate) {
  const activities = message.activities || (message.activities = [])
  const current = activities.find(activity => activity.id === update.id)
  const now = new Date().toISOString()
  if (!current) {
    activities.push({
      ...update,
      startedAt: now,
      ...(update.status === 'running' ? {} : { completedAt: now }),
    })
    return
  }

  current.kind = update.kind
  current.title = update.title
  current.status = update.status
  if (update.detail !== undefined) current.detail = update.detail
  if (update.status !== 'running' && !current.completedAt) current.completedAt = now
}

function settleRunningActivities(
  message: AiAssistantMessage,
  status: Exclude<AiAssistantActivityStatus, 'running'>,
) {
  const now = new Date().toISOString()
  message.activities?.forEach((activity) => {
    if (activity.status !== 'running') return
    activity.status = status
    activity.completedAt = now
    if (status === 'cancelled') activity.detail = activity.detail || '该步骤已随本次处理停止。'
    if (status === 'error') activity.detail = activity.detail || '该步骤因运行失败而中断。'
  })
}

function initialActivity(): AiAssistantActivity {
  return {
    id: 'prepare-run',
    kind: 'context',
    title: '正在准备 Agent 运行环境',
    status: 'running',
    startedAt: new Date().toISOString(),
  }
}

function completedConversationHistory(messages: AiAssistantMessage[]) {
  const history: AiAssistantMessage[] = []
  for (let index = 0; index < messages.length - 1; index += 1) {
    const userMessage = messages[index]
    const assistantMessage = messages[index + 1]
    if (
      userMessage?.role === 'user'
      && userMessage.status === 'complete'
      && assistantMessage?.role === 'assistant'
      && assistantMessage.status === 'complete'
    ) {
      history.push(userMessage, assistantMessage)
      index += 1
    }
  }
  return history
}

async function sendMessage(content?: string) {
  const prompt = (content ?? state.draft).trim()
  const model = selectedModel.value
  if (!prompt || state.running || !model) return false

  const history = completedConversationHistory(state.messages)
  const userMessage: AiAssistantMessage = {
    id: newMessageId(),
    role: 'user',
    content: prompt,
    createdAt: new Date().toISOString(),
    status: 'complete',
  }
  const assistantMessageDraft: AiAssistantMessage = {
    id: newMessageId(),
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
    status: 'pending',
    activities: [initialActivity()],
  }
  state.messages.push(userMessage, assistantMessageDraft)
  const assistantMessage = state.messages[state.messages.length - 1]
  if (!assistantMessage) return false
  state.draft = ''
  state.running = true
  state.runError = ''
  const controller = new AbortController()
  const runToken = Symbol('ai-assistant-run')
  activeController = controller
  activeRunToken = runToken
  const isCurrentRun = () => (
    activeRunToken === runToken
    && activeController === controller
    && !controller.signal.aborted
  )
  let describeAgentError: ((error: unknown) => string) | undefined

  try {
    const agentRunner = await import('../services/agentRunner')
    describeAgentError = agentRunner.describeBrowserAgentError
    if (controller.signal.aborted) throw new DOMException('Aborted', 'AbortError')
    if (!isCurrentRun()) return false
    upsertActivity(assistantMessage, {
      id: 'prepare-run',
      kind: 'context',
      title: 'Agent 运行环境已就绪',
      status: 'complete',
    })
    const output = await agentRunner.runBrowserPageAgent({
      model,
      prompt,
      history,
      signal: controller.signal,
      onActivity: activity => {
        if (isCurrentRun()) upsertActivity(assistantMessage, activity)
      },
    })
    if (controller.signal.aborted) throw new DOMException('Aborted', 'AbortError')
    if (!isCurrentRun()) return false
    assistantMessage.content = output
    settleRunningActivities(assistantMessage, 'complete')
    assistantMessage.status = 'complete'
    return true
  }
  catch (error) {
    const message = describeAgentError?.(error)
      || (controller.signal.aborted
        ? '已停止本次生成。'
        : error instanceof Error ? error.message : 'AI 助手运行失败，请稍后重试。')
    assistantMessage.content = message
    if (controller.signal.aborted) {
      settleRunningActivities(assistantMessage, 'cancelled')
      upsertActivity(assistantMessage, {
        id: 'stop-request',
        kind: 'summary',
        title: '本次处理已停止',
        status: 'cancelled',
      })
      assistantMessage.status = 'cancelled'
    }
    else {
      settleRunningActivities(assistantMessage, 'error')
      upsertActivity(assistantMessage, {
        id: 'run-error',
        kind: 'summary',
        title: '本次处理失败',
        detail: message,
        status: 'error',
      })
      assistantMessage.status = 'error'
      state.runError = message
    }
    return false
  }
  finally {
    if (activeController === controller) activeController = null
    if (activeRunToken === runToken) {
      activeRunToken = null
      state.running = false
    }
  }
}

export function useAiAssistantStore() {
  return {
    state,
    selectedModel,
    loadModels,
    setSelectedModel,
    openAssistant,
    closeAssistant,
    clearMessages,
    stopRun,
    sendMessage,
  }
}
