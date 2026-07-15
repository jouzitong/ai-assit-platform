import { computed, reactive } from 'vue'
import { fetchBrowserAgentModels } from '../api'
import type { AiAssistantMessage, BrowserAgentModel } from '../types'

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
  activity: string
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
  activity: '',
  runError: '',
})

let activeController: AbortController | null = null

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
  state.activity = ''
}

function stopRun() {
  activeController?.abort()
}

async function sendMessage(content?: string) {
  const prompt = (content ?? state.draft).trim()
  const model = selectedModel.value
  if (!prompt || state.running || !model) return false

  const history = state.messages.filter(message => message.status === 'complete')
  const userMessage: AiAssistantMessage = {
    id: newMessageId(),
    role: 'user',
    content: prompt,
    createdAt: new Date().toISOString(),
    status: 'complete',
  }
  const assistantMessage: AiAssistantMessage = {
    id: newMessageId(),
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
    status: 'pending',
  }
  state.messages.push(userMessage, assistantMessage)
  state.draft = ''
  state.running = true
  state.runError = ''
  state.activity = '正在准备页面上下文'
  const controller = new AbortController()
  activeController = controller

  try {
    const { runBrowserPageAgent } = await import('../services/agentRunner')
    assistantMessage.content = await runBrowserPageAgent({
      model,
      prompt,
      history,
      signal: controller.signal,
      onActivity: message => { state.activity = message },
    })
    assistantMessage.status = 'complete'
    return true
  }
  catch (error) {
    const { describeBrowserAgentError } = await import('../services/agentRunner')
    const message = describeBrowserAgentError(error)
    assistantMessage.content = message
    assistantMessage.status = controller.signal.aborted ? 'complete' : 'error'
    if (!controller.signal.aborted) state.runError = message
    return false
  }
  finally {
    if (activeController === controller) activeController = null
    state.running = false
    state.activity = ''
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
