import { computed, reactive } from 'vue'
import type { AiAssistantMessage, BrowserAgentModel } from '../types'

const DISABLED_REASON = '页面助手已停止浏览器直连模型；请接入服务端 SETTINGS_ASSISTANT Agent 后再启用。'

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
  modelsLoaded: true,
  modelsLoading: false,
  modelLoadError: DISABLED_REASON,
  messages: [],
  draft: '',
  running: false,
  runError: '',
})

const selectedModel = computed<BrowserAgentModel | null>(() => null)

async function loadModels() {
  state.models = []
  state.modelsLoaded = true
  state.modelsLoading = false
  state.modelLoadError = DISABLED_REASON
}

function setSelectedModel() {
  state.selectedModelCode = ''
}

function openAssistant() {
  state.open = true
  void loadModels()
}

function closeAssistant() {
  state.open = false
}

function clearMessages() {
  state.messages = []
  state.runError = ''
}

function stopRun() {
  state.running = false
}

async function sendMessage() {
  state.runError = DISABLED_REASON
  state.modelLoadError = DISABLED_REASON
  return false
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
