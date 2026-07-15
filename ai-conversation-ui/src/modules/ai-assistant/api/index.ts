import { request } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'
import type { BrowserAgentModel } from '../types'

const CHAT_API_PREFIX = getBackendService(SERVICE_NAMES.CHAT).gatewayPrefix

export function fetchBrowserAgentModels() {
  return request<BrowserAgentModel[]>(`${CHAT_API_PREFIX}/api/v1/chat/models/browser-agent`, {
    method: 'GET',
  })
}
