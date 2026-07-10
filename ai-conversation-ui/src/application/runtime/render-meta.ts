import { request } from '../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../config/services'

const RENDER_META_ENDPOINT = `${getBackendService(SERVICE_NAMES.RENDER).gatewayPrefix}/api/v1/render/meta`

export function loadRenderMetaContent(code: string) {
  return request<Record<string, unknown>>(`${RENDER_META_ENDPOINT}/${encodeURIComponent(code)}`, {
    method: 'GET',
  })
}

export function upsertRenderMetaContent(code: string, content: Record<string, unknown>) {
  return request<Record<string, unknown>>(RENDER_META_ENDPOINT, {
    method: 'POST',
    body: JSON.stringify({
      code,
      content,
    }),
  })
}
