export type CapabilityIdentity = {
  key?: string
  name?: string
}

const BUILTIN_TOOL_DISPLAY_NAMES: Record<string, string> = {
  data_preview_query_tool: '查询数据预览',
  data_format_validate_tool: '校验数据格式',
  knowledge_base_search_tool: '检索知识库',
  load_skill_resource: '读取技能资源',
  render_json_validate_tool: '校验 Render JSON',
  web_search_tool: '搜索网页',
}

const BUILTIN_TOOL_CALL_REASONS: Record<string, string> = {
  data_preview_query_tool: '需要核对授权数据的字段、结构和实际记录。',
  data_format_validate_tool: '需要确认数据格式符合后续处理要求。',
  knowledge_base_search_tool: '当前任务需要补充已授权知识库中的业务语义和事实依据。',
  load_skill_resource: '需要读取已选技能的执行规范和资源内容。',
  render_json_validate_tool: '需要确认生成内容符合页面渲染结构与组件契约。',
  web_search_tool: '当前任务需要补充公开网页中的最新信息或事实依据。',
}

const BUILTIN_SKILL_DISPLAY_NAMES: Record<string, string> = {
  'application-build-release': '应用构建与发布',
  'data-analysis': '数据分析',
  'render-json-generation': 'Render JSON 生成',
  'semantic-data-contract': '语义数据契约',
}

function firstText(...values: unknown[]) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value.trim()
    if (typeof value === 'number' && Number.isFinite(value)) return String(value)
  }
  return undefined
}

function capabilityCode(value?: string) {
  if (!value) return ''
  const path = value.includes('://') ? value.split('://', 2)[1] || '' : value
  const parts = path.split('/').filter(Boolean)
  const terminal = /^v\d+$/i.test(parts.at(-1) || '') ? parts.at(-2) : parts.at(-1)
  return (terminal || '').replace(/@v?\d+$/i, '')
}

function capabilityDisplayName(
  key: string | undefined,
  configuredName: string | undefined,
  builtInNames: Record<string, string>,
) {
  return builtInNames[key || ''] || builtInNames[capabilityCode(key)] || configuredName || key
}

export function toolDisplayName(key?: string, configuredName?: string) {
  return capabilityDisplayName(key, configuredName, BUILTIN_TOOL_DISPLAY_NAMES)
}

export function toolCallReason(key?: string, configuredName?: string) {
  const normalizedKey = key || ''
  const builtInReason = BUILTIN_TOOL_CALL_REASONS[normalizedKey]
    || BUILTIN_TOOL_CALL_REASONS[capabilityCode(normalizedKey)]
  if (builtInReason) return builtInReason
  const displayName = toolDisplayName(key, configuredName)
  if (normalizedKey.startsWith('ask_') && displayName) {
    return `当前任务需要“${displayName}”的专业能力，因此发起协作。`
  }
  if (displayName) {
    return `当前步骤需要通过“${displayName}”补充、验证或执行任务所需信息。`
  }
  return '当前步骤需要调用工具补充、验证或执行任务所需信息。'
}

export function skillDisplayName(key?: string, configuredName?: string) {
  return capabilityDisplayName(key, configuredName, BUILTIN_SKILL_DISPLAY_NAMES)
}

export function toolIdentity(source: Record<string, unknown>): CapabilityIdentity {
  const key = firstText(source.toolKey, source.toolCode)
  return {
    key,
    name: toolDisplayName(key, firstText(source.toolName)),
  }
}

export function skillIdentity(source: Record<string, unknown>): CapabilityIdentity {
  const key = firstText(source.skillKey, source.skillRef)
  return {
    key,
    name: skillDisplayName(key, firstText(source.skillName)),
  }
}
