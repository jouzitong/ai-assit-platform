import {
  createAiChatConversation,
  deleteAiChatConversation,
  detailAiChatConversation,
  listEnabledAiChatModels,
  listAiChatConversations,
  pinAiChatConversation,
  queryAiChat,
  queryAiChatStream,
  renameAiChatConversation
} from '../../../../../api/aiChat'

export {
  barSeries,
  initialExecutions,
  initialHistoryList,
  initialStages,
  models,
  pieSegments,
  placeholder,
  resultRows
} from './index-static'

function buildStageSummary(stages = []) {
  const total = stages.length
  const done = stages.filter((item) => item.status === 'done').length
  return `${done}/${total} 已完成`
}

function extractUserInput(summary) {
  const text = String(summary || '').trim()
  if (!text) return ''
  return text.startsWith('最近输入：') ? text.slice('最近输入：'.length).trim() : text
}

export function buildAssistantSessionModel(conversations = []) {
  const active = conversations.find((item) => item.active) || conversations[0] || null
  return {
    code: active?.id || '',
    name: active?.title || '智能问数',
    summary: active?.summary || '继续输入问题，系统会沿用当前会话上下文继续分析。',
    tag: active?.tag || '智能问数',
    activeRoundCode: active?.id ? `${active.id}-round-1` : ''
  }
}

function buildAssistantRounds({
  session,
  conversations = [],
  stages = [],
  executions = [],
  resultRows = [],
  selectedModel = ''
} = {}) {
  const activeThinking = executions.find((item) => item.active) || executions[0] || null
  const completedCount = stages.filter((item) => item.status === 'done').length
  const isProcessing = stages.some((item) => item.status === 'running') || executions.some((item) => item.active)
  const pendingList = stages
    .filter((item) => item.status !== 'done')
    .map((item) => ({
      code: item.name,
      name: item.name,
      desc: item.desc,
      status: item.status
    }))
  return [
    {
      code: session.activeRoundCode || `${session.code}-round-1`,
      userInput: extractUserInput(session.summary),
      status: isProcessing ? 'running' : 'done',
      progress: {
        summary: buildStageSummary(stages),
        completed: completedCount,
        total: stages.length,
        isProcessing,
        stages
      },
      thinking: {
        collapsed: !isProcessing,
        title: activeThinking?.title || '执行计划生成',
        detail: activeThinking?.detail || '系统正在整理分析步骤。',
        steps: executions
      },
      result: {
        title: 'AI 执行结果预览',
        rows: resultRows,
        total: resultRows.length,
        model: selectedModel
      },
      pendingList
    }
  ]
}

export function buildAssistantSessionState({
  conversations = [],
  stages = [],
  executions = [],
  resultRows = [],
  selectedModel = '',
  prompt = '',
  placeholder = ''
} = {}) {
  const session = buildAssistantSessionModel(conversations)
  const rounds = buildAssistantRounds({
    session,
    conversations,
    stages,
    executions,
    resultRows,
    selectedModel
  })
  return {
    session,
    draft: {
      text: prompt,
      placeholder,
      selectedModel
    },
    rounds
  }
}

export function buildAssistantQueryViewModel(payload = {}) {
  const sessionState = buildAssistantSessionState(payload)
  const currentRound = sessionState.rounds.find((item) => item.code === sessionState.session.activeRoundCode) || sessionState.rounds[0] || null
  return {
    session: sessionState.session,
    draft: sessionState.draft,
    rounds: sessionState.rounds,
    currentRound
  }
}

export async function fetchAssistantModels() {
  return listEnabledAiChatModels()
}

export async function fetchAssistantConversationList(payload = {}) {
  return listAiChatConversations(payload)
}

export async function fetchAssistantConversationDetail(payload) {
  return detailAiChatConversation(payload)
}

export async function createAssistantConversation(payload = {}) {
  return createAiChatConversation(payload)
}

export async function renameAssistantConversation(payload) {
  return renameAiChatConversation(payload)
}

export async function pinAssistantConversation(payload) {
  return pinAiChatConversation(payload)
}

export async function deleteAssistantConversation(payload) {
  return deleteAiChatConversation(payload)
}

export async function queryAssistantConversation(payload) {
  return queryAiChat(payload)
}

export async function queryAssistantConversationStream(payload) {
  return queryAiChatStream(payload)
}
