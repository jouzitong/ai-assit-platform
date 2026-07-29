import type {
  FormRendererAction,
  FormRendererMode,
} from '../renderers/form/types'

export type RenderRuntimeNodeKind = 'layout' | 'renderer' | 'static' | 'unknown'

export interface RenderRuntimeNodeEvent {
  type: string
  timestamp: string
  payload?: unknown
}

export interface RenderRuntimeNodeScope {
  id: string
  key?: string
  component: string
  kind: RenderRuntimeNodeKind
  path: string
  schema: Record<string, unknown> | null
  query: Record<string, unknown>
  requestPlans: unknown[]
  data: Record<string, unknown>
  state: Record<string, unknown>
  events: RenderRuntimeNodeEvent[]
}

export interface RenderRuntimeActionPayload {
  action: FormRendererAction
  nodeId: string
  component: string
  schema: Record<string, unknown> | null
  values: Record<string, unknown>
  formMode: FormRendererMode
}
