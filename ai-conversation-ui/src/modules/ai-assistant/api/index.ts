import { requestRaw } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'
import {
  ChatStreamInterruptedError,
  PAGE_ASSISTANT_BUSINESS_TYPE,
  consumeChatTransportStream,
  createChatTransportRequest,
  fetchChatRunStatus,
  fetchConversationDetail,
  fetchConversationList,
  fetchEnabledModels,
  reconnectChatTransport,
  stopChatRun,
} from '../../ai-chat/api'
import type {
  ChatEnabledModel,
  ChatTransportRequest,
  ChatTransportStreamOptions,
  ChatTransportStreamResult,
} from '../../ai-chat/types'
import type { ChatTransportStreamEvent } from '../../ai-chat/api'
import type { AgentPageContext } from '../types'

const CHAT_API_PREFIX = getBackendService(SERVICE_NAMES.CHAT).gatewayPrefix
const SETTINGS_ASSISTANT_API_PREFIX = `${CHAT_API_PREFIX}/api/chat/settings-assistant`

export { ChatStreamInterruptedError }

export type PageAssistantTransportRequest = Omit<ChatTransportRequest, 'clientContext'> & {
  clientContext: ChatTransportRequest['clientContext'] & {
    assistantContext: {
      schemaVersion: 'page-assistant-context.v1'
      surface: 'PAGE_ASSISTANT'
      pageContext: AgentPageContext
    }
  }
}

export function fetchPageAssistantModels(): Promise<ChatEnabledModel[]> {
  return fetchEnabledModels()
}

export function fetchPageAssistantSessions() {
  return fetchConversationList({ businessType: PAGE_ASSISTANT_BUSINESS_TYPE })
}

export function fetchPageAssistantConversationDetail(sessionCode: string) {
  return fetchConversationDetail({ sessionCode })
}

export function createPageAssistantTransportRequest(input: {
  sessionCode?: string
  modelId: number
  message: string
  route: string
  pageContext: AgentPageContext
}): PageAssistantTransportRequest {
  const request = createChatTransportRequest({
    sessionCode: input.sessionCode,
    modelId: input.modelId,
    message: input.message,
  }, input.route)
  return {
    ...request,
    clientContext: {
      ...request.clientContext,
      assistantContext: {
        schemaVersion: 'page-assistant-context.v1',
        surface: 'PAGE_ASSISTANT',
        pageContext: input.pageContext,
      },
    },
  }
}

export async function streamSettingsAssistantTransport(
  payload: PageAssistantTransportRequest,
  onEvent: (event: ChatTransportStreamEvent) => void,
  options: ChatTransportStreamOptions = {},
): Promise<ChatTransportStreamResult> {
  const endpoint = payload.sessionCode
    ? `${SETTINGS_ASSISTANT_API_PREFIX}/sessions/${encodeURIComponent(payload.sessionCode)}/rounds/stream`
    : `${SETTINGS_ASSISTANT_API_PREFIX}/rounds/stream`
  const response = await requestRaw(endpoint, {
    method: 'POST',
    headers: { Accept: 'text/event-stream' },
    body: JSON.stringify(payload),
    signal: options.signal,
  })
  return consumeChatTransportStream(response, onEvent, options)
}

export const reconnectPageAssistantTransport = reconnectChatTransport
export const fetchPageAssistantRunStatus = fetchChatRunStatus
export const stopPageAssistantRun = stopChatRun
