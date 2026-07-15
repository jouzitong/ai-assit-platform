export type AgentJsonPrimitive = string | number | boolean | null

export type AgentJsonValue =
  | AgentJsonPrimitive
  | AgentJsonValue[]
  | { [key: string]: AgentJsonValue }

export interface BrowserAgentModel {
  id: number
  modelCode: string
  modelName?: string
  apiModel: string
  clientType?: number
  baseUrl?: string
  apiKey?: string
}

export interface AgentPageActionDefinition {
  name: string
  toolName: string
  description: string
  parameters: Record<string, {
    description: string
    types: Array<'string' | 'number' | 'boolean'>
    required?: boolean
    enum?: AgentJsonPrimitive[]
  }>
}

export interface AgentPageSnapshot {
  pageId: string
  title: string
  description?: string
  state?: AgentJsonValue
}

export interface AgentPageActionResult {
  success: boolean
  message: string
  details?: AgentJsonValue
}

export interface AgentPageCapability {
  id: string
  title: string
  description?: string
  actions?: AgentPageActionDefinition[]
  getSnapshot: () => AgentPageSnapshot | Promise<AgentPageSnapshot>
  executeAction?: (action: string, payload: Record<string, AgentJsonPrimitive>) => AgentPageActionResult | Promise<AgentPageActionResult>
}

export interface AgentDomFormField {
  fieldId: string
  formId: string
  label: string
  control: string
  value: AgentJsonValue
  writable: boolean
  required: boolean
  sensitive: boolean
  options?: string[]
}

export interface AgentDomFormSnapshot {
  formId: string
  title: string
  fields: AgentDomFormField[]
}

export interface AgentDomTableSnapshot {
  title: string
  columns: string[]
  rows: string[][]
}

export interface AgentDomPageSnapshot {
  route: string
  title: string
  activeDialog?: { title: string }
  visibleText: string
  forms: AgentDomFormSnapshot[]
  tables: AgentDomTableSnapshot[]
}

export interface AgentPageContext {
  capturedAt: string
  page: AgentDomPageSnapshot
  registeredCapability?: AgentPageSnapshot
  availablePageActions: AgentPageActionDefinition[]
}

export interface AgentFormPatchChange {
  fieldId: string
  value: AgentJsonPrimitive
}

export interface AgentFormPatchResult {
  applied: Array<{ fieldId: string; label: string }>
  rejected: Array<{ fieldId: string; reason: string }>
}

export type AiAssistantMessageRole = 'user' | 'assistant'

export interface AiAssistantMessage {
  id: string
  role: AiAssistantMessageRole
  content: string
  createdAt: string
  status: 'complete' | 'pending' | 'error'
}

export interface RunBrowserPageAgentInput {
  model: BrowserAgentModel
  prompt: string
  history: AiAssistantMessage[]
  signal?: AbortSignal
  onActivity?: (message: string) => void
}
