import type {
  ListRendererData,
  ListRendererSchema,
  RendererQueryState,
} from '../schema'
import type { RuntimeRendererEvent } from './event-dispatcher'

export interface RenderRuntimeScope {
  code?: string
  document?: Record<string, unknown> | null
  schema?: ListRendererSchema | null
  query?: Partial<RendererQueryState>
  params: Record<string, unknown>
  requestPlans: unknown[]
  data: Partial<ListRendererData>
  state: Record<string, unknown>
  events: RuntimeRendererEvent[]
}

export function createRenderRuntimeScope(seed: Partial<RenderRuntimeScope> = {}): RenderRuntimeScope {
  return {
    code: seed.code,
    document: seed.document || null,
    schema: seed.schema || null,
    query: seed.query || {},
    params: seed.params || {},
    requestPlans: seed.requestPlans || [],
    data: seed.data || {},
    state: seed.state || {},
    events: seed.events || [],
  }
}

export function patchRenderRuntimeScope(scope: RenderRuntimeScope, patch: Partial<RenderRuntimeScope>) {
  if (Object.prototype.hasOwnProperty.call(patch, 'code')) {
    scope.code = patch.code
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'document')) {
    scope.document = patch.document || null
  }
  if (Object.prototype.hasOwnProperty.call(patch, 'schema')) {
    scope.schema = patch.schema || null
  }
  if (patch.query) {
    scope.query = patch.query
  }
  if (patch.params) {
    scope.params = patch.params
  }
  if (patch.requestPlans) {
    scope.requestPlans = patch.requestPlans
  }
  if (patch.data) {
    scope.data = patch.data
  }
  if (patch.state) {
    scope.state = patch.state
  }
  if (patch.events) {
    scope.events = patch.events
  }
  return scope
}

export function recordRuntimeEvent(scope: RenderRuntimeScope, event: RuntimeRendererEvent) {
  scope.events = [...scope.events, event].slice(-50)
  return scope.events
}
