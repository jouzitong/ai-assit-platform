export type DefinitionStatus = 'DRAFT' | 'PUBLISHED' | 'DEPRECATED' | 'ARCHIVED' | string

export interface PageInfo {
  page?: number
  size?: number
  total?: number
}

export interface PageResult<T> {
  list?: T[]
  pageInfo?: PageInfo
}

export interface CatalogQuery {
  [key: string]: string | number | boolean | undefined
  page?: number
  size?: number
  keyword?: string
  status?: string
  enabled?: boolean
}

export interface CatalogItem {
  id?: string | number
  code: string
  name: string
  description?: string
  status?: DefinitionStatus
  enabled?: boolean
  currentPublishedVersion?: number
  draftVersion?: number
  version?: number
  createTime?: string
  updateTime?: string
}

export interface ValidationIssue {
  code?: string
  path?: string
  message: string
  severity?: 'INFO' | 'WARNING' | 'ERROR' | string
}

export interface ValidationReport {
  valid?: boolean
  compatible?: boolean
  issues?: ValidationIssue[]
  warnings?: ValidationIssue[]
  message?: string
}

export interface DefinitionRef {
  ref: string
  version?: string | number
  contentHash?: string
  required?: boolean
  enabled?: boolean
  alias?: string
}

export interface GuardrailRef extends DefinitionRef {
  execution?: 'blocking' | 'parallel' | string
}

export interface AgentCollaborationRef {
  targetAgentRef: string
  mode: 'AS_TOOL' | 'HANDOFF'
  toolName?: string
  description?: string
}

export interface AgentManifestSpec {
  instructions: {
    type: 'inline' | 'promptRef'
    text?: string
    ref?: string
  }
  model: {
    ref: string
    settings?: Record<string, unknown>
  }
  output?: {
    mode?: 'text' | 'jsonSchema' | 'artifactSet'
    workflowRef?: string
    schema?: Record<string, unknown> | null
  }
  toolRefs: DefinitionRef[]
  skillRefs: DefinitionRef[]
  knowledgeRefs: DefinitionRef[]
  mcpRefs: DefinitionRef[]
  collaboration: {
    agentTools: AgentCollaborationRef[]
    handoffs: AgentCollaborationRef[]
  }
  guardrails?: {
    input?: GuardrailRef[]
    output?: GuardrailRef[]
  }
  runtimeDefaults: {
    maxTurns?: number
    timeoutMs?: number
    maxAgentDepth?: number
    toolConcurrency?: number
    stateStrategy?: 'applicationReplay' | string
    tracing?: {
      enabled?: boolean
      includeSensitiveData?: boolean
      workflowName?: string
    }
  }
  extensions?: Record<string, unknown>
}

export interface AgentDefinition extends CatalogItem {
  apiVersion?: string
  kind?: 'Agent'
  labels?: Record<string, string>
  metadata?: {
    code?: string
    version?: number
    name?: string
    description?: string
    labels?: Record<string, string>
  }
  manifest?: {
    apiVersion?: string
    kind?: 'Agent'
    metadata?: {
      code?: string
      name?: string
      description?: string
      labels?: Record<string, string>
    }
    spec?: Partial<AgentManifestSpec>
  }
  spec?: Partial<AgentManifestSpec>
}

export interface AvailableAgent {
  code: string
  name: string
  description?: string
  version?: number
}

export interface AgentEntryBinding {
  entryCode: string
  agentCode: string
  versionStrategy?: 'LATEST_PUBLISHED' | 'PINNED' | string
  pinnedVersion?: number | null
  enabled?: boolean
  updateTime?: string
}

export interface ArtifactContract {
  code: string
  name?: string
  artifactType: string
  contentFormat?: string
  required: boolean
  visible: boolean
  schemaRef?: string
  templateRef?: string
  inlineSchema?: Record<string, unknown> | null
  inlineTemplate?: string
}

export interface WorkflowCheck {
  code: string
  name?: string
  targetArtifact: string
  checkerType: 'JSON_SCHEMA' | 'TOOL' | 'AGENT'
  checkerRef?: string
  severity: 'INFO' | 'WARNING' | 'ERROR'
  blocking: boolean
  retryable: boolean
  config?: Record<string, unknown>
}

export interface ArtifactWorkflowSpec {
  artifacts: ArtifactContract[]
  checks: WorkflowCheck[]
  completionPolicy?: {
    requireAllRequiredArtifacts?: boolean
    requireAllBlockingChecksPassed?: boolean
  }
  repairPolicy?: {
    maxRepairAttempts?: number
    onExhausted?: 'INPUT_REQUIRED' | 'FAILED' | string
  }
}

export interface ArtifactWorkflowDefinition extends CatalogItem {
  apiVersion?: string
  kind?: 'ArtifactWorkflow'
  metadata?: {
    code?: string
    version?: number
    name?: string
    description?: string
    labels?: Record<string, string>
  }
  spec?: ArtifactWorkflowSpec
  /** Compatibility projection accepted while old drafts are migrated. */
  artifacts?: ArtifactContract[]
  checks?: WorkflowCheck[]
  completionPolicy?: {
    requireAllRequiredArtifacts?: boolean
    requireAllBlockingChecksPassed?: boolean
  }
  repairPolicy?: {
    maxRepairAttempts?: number
    onExhausted?: 'INPUT_REQUIRED' | 'FAILED' | string
  }
}

export type SkillSourceType = 'FORM' | 'ZIP'

export interface SkillDefinition extends CatalogItem {
  sourceType?: SkillSourceType
  license?: string
  compatibility?: string
  compatibleRuntimes?: string[]
  content?: string
  toolRefs?: string[]
  manifest?: Record<string, unknown>
  files?: SkillFileNode[]
  entrypoint?: string
  packageUri?: string
  packageSha256?: string
  packageSize?: number
  scanStatus?: string
}

export interface SkillFileNode {
  name: string
  path?: string
  role?: string
  mediaType?: string
  size?: number
  children?: SkillFileNode[]
}

export interface SkillPackageInspection {
  draftId?: string
  valid?: boolean
  entrypoint?: string
  checksum?: string
  totalSize?: number
  manifest?: Record<string, unknown>
  skill?: Partial<SkillDefinition>
  files?: SkillFileNode[]
  compatibility?: ValidationReport
  risks?: ValidationIssue[]
  errors?: Array<string | ValidationIssue>
  warnings?: Array<string | ValidationIssue>
}

export interface ToolBinding {
  bindingType: 'HTTP' | 'MCP' | 'JAVA_INTERNAL' | 'HOSTED' | 'PYTHON_MODULE' | 'JAVASCRIPT_MODULE' | string
  runtimeType?: string
  endpointRef?: string
  packageUri?: string
  entrypoint?: string
  enabled?: boolean
  config?: Record<string, unknown>
}

export type ToolImplementationRuntime = 'PYTHON' | 'JAVASCRIPT'
export type ToolAgentRuntime = 'OPENAI_AGENTS_PYTHON' | 'OPENAI_AGENTS_TYPESCRIPT' | string

export interface ToolDefinition extends CatalogItem {
  executionMode?: 'MANAGED_CODE' | string
  implementationRuntime?: ToolImplementationRuntime
  compatibleAgentRuntimes?: ToolAgentRuntime[]
  sourceCode?: string
  runtimeConfig?: Record<string, unknown> | null
  inputSchema?: Record<string, unknown> | null
  outputSchema?: Record<string, unknown> | null
  permissionPolicy?: Record<string, unknown> | null
  approvalPolicy?: Record<string, unknown> | null
  timeoutMs?: number
  bindings?: ToolBinding[]
}

export interface DefinitionVersionResponse<T> {
  version?: number
  versionNo?: number
  definition?: T
  manifest?: T
}
