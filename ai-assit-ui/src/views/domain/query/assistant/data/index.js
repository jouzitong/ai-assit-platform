import {
  createAiChatConversation,
  deleteAiChatConversation,
  detailAiChatConversation,
  listEnabledAiChatModels,
  listAiChatConversations,
  listAiChatProviders,
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

export async function fetchAssistantModels() {
  return listEnabledAiChatModels()
}

export async function fetchAssistantProviders(payload = { enabled: true }) {
  return listAiChatProviders(payload)
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
