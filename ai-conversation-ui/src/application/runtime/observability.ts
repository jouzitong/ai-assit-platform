export type RenderRuntimeNodeKind = 'layout' | 'renderer' | 'static' | 'unknown'

export interface RenderRuntimeNodeEvent {
  type: string
  timestamp: string
  payload?: unknown
}

export interface RenderRuntimeNodeScope {
  id: string
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
