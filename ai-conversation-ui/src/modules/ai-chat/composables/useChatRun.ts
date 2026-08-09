import type { ChatArtifact, ChatRunActivity, ChatRunActivityKind } from '../types'
import { skillIdentity, toolIdentity } from '../utils/capabilityIdentity'

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

function parsedRecord(value: unknown): Record<string, unknown> {
  if (typeof value !== 'string' || !value.trim()) return asRecord(value)
  try {
    return asRecord(JSON.parse(value))
  } catch {
    return {}
  }
}

function stringValues(value: unknown) {
  if (!Array.isArray(value)) return []
  return value.flatMap(item => typeof item === 'string' && item.trim() ? [item.trim()] : [])
}

function text(...values: unknown[]) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
    if (typeof value === 'number') {
      return String(value)
    }
  }
  return undefined
}

function numberValue(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : undefined
  }
  return undefined
}

function booleanValue(value: unknown) {
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value !== 0
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    if (normalized === 'true' || normalized === '1') return true
    if (normalized === 'false' || normalized === '0') return false
  }
  return undefined
}

function safeDetail(value?: string) {
  if (!value) return undefined
  const redacted = value
    .replace(/\bbearer\s+[a-z0-9._~+/=-]+/gi, 'Bearer [已隐藏]')
    .replace(/\bsk-[a-z0-9_-]+\b/gi, '[已隐藏]')
    .replace(
      /(["']?(?:authorization|api[ _-]?key|secret|password|access[_-]?token|refresh[_-]?token|token)["']?\s*[:=]\s*["']?)([^"'\s,;}]+)/gi,
      '$1[已隐藏]',
    )
  return redacted.length > 1000 ? `${redacted.slice(0, 999)}…` : redacted
}

type ActivityCopy = {
  title: string
  detail?: string
}

const ACTIVITY_COPY_BY_EVENT: Record<string, ActivityCopy> = {
  'confidence.assessment.started': {
    title: '最终可信度评估',
    detail: '正在使用已确认的知识证据评估最终回答。',
  },
  'confidence.assessment.completed': {
    title: '最终可信度评估',
    detail: '已完成最终回答的可信度评估。',
  },
  'confidence.assessment.skipped': {
    title: '最终可信度评估',
    detail: '当前回答暂不具备可信度评分条件，已保留未评分状态。',
  },
  'confidence.evidence_check.started': {
    title: '检查证据充分性',
    detail: '正在确认现有知识证据是否足以支持最终可信度评估。',
  },
  'confidence.evidence_check.completed': {
    title: '检查证据充分性',
    detail: '已完成知识证据充分性检查。',
  },
  'confidence.retrieval.started': {
    title: '补充知识依据',
    detail: '正在检索授权知识库，以补充当前回答的事实依据。',
  },
  'confidence.retrieval.completed': {
    title: '补充知识依据',
    detail: '已完成知识库检索。',
  },
  'confidence.retrieval.skipped': {
    title: '补充知识依据',
    detail: '当前没有可用的授权知识库。',
  },
  'confidence.reanalysis.started': {
    title: '基于证据重新整理回答',
    detail: '正在结合已获取的知识证据重新整理回答。',
  },
  'confidence.reanalysis.completed': {
    title: '基于证据重新整理回答',
    detail: '已根据可用知识证据重新整理回答。',
  },
  'tool.started': { title: '开始调用工具', detail: '正在执行本轮所需工具。' },
  'tool.completed': { title: '工具调用完成', detail: '工具已执行完成。' },
  'tool.failed': { title: '工具调用失败', detail: '工具执行失败。' },
  'handoff.requested': { title: '正在协作交接', detail: '正在将任务交接给协作智能体。' },
  'handoff.completed': { title: '协作交接完成', detail: '任务已完成协作交接。' },
  'check.started': { title: '开始验收检查', detail: '正在检查本轮产物是否满足交付要求。' },
  'check.completed': { title: '验收检查完成', detail: '本轮产物检查已完成。' },
  'artifact.repair.requested': { title: '开始自动补救', detail: '正在根据未通过的检查修复产物。' },
  'artifact.repair.completed': { title: '自动补救完成', detail: '产物修复已完成，等待重新检查。' },
  'artifact.repair.failed': { title: '自动补救失败', detail: '产物修复失败，本次自动补救已停止。' },
  'execution.result.completed': { title: '执行结果', detail: '本轮执行、检查和补救结果已汇总。' },
}

const LEGACY_ACTIVITY_COPY_BY_MESSAGE: Record<string, ActivityCopy> = {
  'completed confidence assessment': ACTIVITY_COPY_BY_EVENT['confidence.assessment.completed'],
  'retrieving knowledge for low-confidence result': ACTIVITY_COPY_BY_EVENT['confidence.retrieval.started'],
  'knowledge retrieval completed': ACTIVITY_COPY_BY_EVENT['confidence.retrieval.completed'],
  'knowledge retrieval failed': { title: '知识库检索失败', detail: '未能从知识库取得补充依据。' },
  'no authorized knowledge base is available': ACTIVITY_COPY_BY_EVENT['confidence.retrieval.skipped'],
  'reanalyzing low-confidence result': ACTIVITY_COPY_BY_EVENT['confidence.reanalysis.started'],
  'reanalysis completed': ACTIVITY_COPY_BY_EVENT['confidence.reanalysis.completed'],
  'agent tool execution started': ACTIVITY_COPY_BY_EVENT['tool.started'],
  'agent tool execution completed': ACTIVITY_COPY_BY_EVENT['tool.completed'],
  'agent tool execution failed': ACTIVITY_COPY_BY_EVENT['tool.failed'],
  'agent handoff requested': ACTIVITY_COPY_BY_EVENT['handoff.requested'],
  'agent handoff completed': ACTIVITY_COPY_BY_EVENT['handoff.completed'],
}

function activityCopy(eventName: string, source: Record<string, unknown>) {
  const platformEventType = text(source.platformEventType)?.toLowerCase()
  const eventCopy = ACTIVITY_COPY_BY_EVENT[platformEventType || eventName.toLowerCase()]
  if (eventCopy) return eventCopy
  const legacyMessage = text(source.title, source.activityName, source.statusText, source.message)?.toLowerCase()
  return legacyMessage ? LEGACY_ACTIVITY_COPY_BY_MESSAGE[legacyMessage] : undefined
}

function activityKind(eventName: string, activity: Record<string, unknown>): ChatRunActivityKind | undefined {
  const normalizedType = text(activity.activityType)?.toUpperCase() || ''
  if (eventName.startsWith('artifact.repair.') || normalizedType.includes('REPAIR')) return 'repair'
  if (eventName.startsWith('execution.result.') || normalizedType.includes('EXECUTION_RESULT')) return 'execution'
  if (eventName.startsWith('handoff.') || normalizedType.includes('HANDOFF')) return 'handoff'
  if (eventName.startsWith('agent.') || normalizedType.includes('AGENT')) return 'agent'
  if (eventName.startsWith('tool.') || normalizedType.includes('TOOL')) return 'tool'
  if (eventName.startsWith('skill.') || normalizedType.includes('SKILL')) return 'skill'
  if (eventName.startsWith('artifact.') || eventName.startsWith('artifacts.') || normalizedType.includes('ARTIFACT')) return 'artifact'
  if (eventName.startsWith('check.') || normalizedType.includes('CHECK') || normalizedType.includes('VALIDATION')) return 'check'
  if (eventName.startsWith('confidence.')) return 'thinking'
  if (eventName.startsWith('thinking.') && Object.keys(activity).length) return 'thinking'
  return undefined
}

function activityTitle(kind: ChatRunActivityKind, eventName: string, source: Record<string, unknown>) {
  const localized = activityCopy(eventName, source)
  if (eventName.toLowerCase().startsWith('confidence.') && localized) return localized.title
  if (kind === 'skill') {
    const name = skillIdentity(source).name
    if (name) return `加载技能：${name}`
  }
  if (kind === 'tool') {
    const name = toolIdentity(source).name
    if (name) return `调用工具：${name}`
  }
  const stableName = text(source.activityName, source.title)
  if (stableName) return stableName
  if (localized) return localized.title
  const explicit = text(source.statusText, source.message)
  if (explicit) return explicit
  const labels: Record<ChatRunActivityKind, string> = {
    agent: eventName.endsWith('completed') ? '智能体执行完成' : '智能体正在执行',
    handoff: eventName.endsWith('completed') ? '智能体协作交接完成' : '正在交接给协作智能体',
    tool: eventName.endsWith('completed') ? '工具调用完成' : eventName.endsWith('failed') ? '工具调用失败' : '正在调用工具',
    skill: '已加载技能',
    artifact: '产物已更新',
    check: eventName.endsWith('completed') ? '产物检查完成' : '正在检查产物',
    repair: eventName.endsWith('completed')
      ? '产物修复完成'
      : eventName.endsWith('failed') ? '产物修复失败' : '正在修复产物',
    execution: '执行结果',
    thinking: '智能体正在分析',
  }
  return labels[kind]
}

function normalizedStatus(eventName: string, value: unknown) {
  const status = text(value)?.toLowerCase()
  if (status === 'complete' || status === 'completed' || status === 'done' || status === 'success' || status === 'succeeded') return 'success'
  if (status === 'error' || status === 'failed') return 'failed'
  if (status === 'cancelled' || status === 'canceled') return 'cancelled'
  if (status === 'partial') return 'partial'
  if (status === 'input_required' || status === 'waiting_input') return 'input_required'
  if (status === 'running' || status === 'started' || status === 'pending') return 'running'
  if (eventName.endsWith('completed') || eventName.endsWith('loaded')) return 'success'
  if (eventName.endsWith('failed')) return 'failed'
  return status || 'running'
}

export function activityFromTransportEvent(
  eventName: string,
  payloadValue: unknown,
  eventId?: string,
  timestamp?: string,
): ChatRunActivity | undefined {
  const payload = asRecord(payloadValue)
  const nestedActivity = asRecord(payload.activity)
  const thinking = asRecord(payload.thinking)
  const persistedDetail = {
    ...parsedRecord(payload.detailJson),
    ...parsedRecord(payload.detail),
    ...parsedRecord(nestedActivity.detailJson),
    ...parsedRecord(nestedActivity.detail),
  }
  const hasActivityIdentity = Boolean(text(
    payload.activityCode,
    payload.callId,
    payload.nodeCode,
    payload.activityType,
  ))
  if (
    eventName.startsWith('thinking.')
    && eventName !== 'thinking.updated'
    && !Object.keys(nestedActivity).length
    && !hasActivityIdentity
    && payload.progressType !== 'ACTIVITY'
    && payload.action !== 'activity.updated'
  ) {
    return undefined
  }
  const persistedExt = {
    ...parsedRecord(payload.ext),
    ...parsedRecord(persistedDetail.ext),
    ...parsedRecord(nestedActivity.ext),
  }
  const source = {
    ...payload,
    ...thinking,
    ...persistedDetail,
    ...persistedExt,
    ...nestedActivity,
  }
  const effectiveEventName = text(source.platformEventType) || eventName
  const kind = activityKind(effectiveEventName.toLowerCase(), source)
  if (!kind) return undefined
  const tool = toolIdentity(source)
  const skill = skillIdentity(source)

  const analysis = {
    ...parsedRecord(persistedDetail.analysis),
    ...parsedRecord(persistedExt.analysis),
    ...parsedRecord(payload.analysis),
    ...parsedRecord(nestedActivity.analysis),
    ...parsedRecord(source.analysis),
  }
  const confidenceDetail = {
    ...parsedRecord(persistedDetail.confidence),
    ...parsedRecord(source.confidence),
    ...parsedRecord(analysis.confidence),
  }

  const agentCode = text(source.agentCode)
  const progressType = text(source.progressType)
  const progressSource = text(source.source)
  const identity = text(
    source.activityCode,
    source.id,
    source.callId,
    source.toolCode,
    source.skillRef,
    source.artifactCode,
    source.codeRef,
    source.nodeCode,
    // Only explicit activity/node identifiers may merge lifecycle updates.
    // Generic progress events need their SSE id so separate events append to the timeline.
    eventId,
    progressType && progressSource && `${progressType}:${progressSource}`,
    source.action,
    agentCode && `agent:${agentCode}`,
    `${eventName}:${timestamp || Date.now()}`,
  ) as string
  const localized = activityCopy(effectiveEventName, source)
  const inputSummary = safeDetail(text(source.inputSummary))
  const rawOutputSummary = safeDetail(text(source.outputSummary))
  const callReason = safeDetail(text(source.callReason))
  const skillResourcePath = text(source.resourcePath)
  const outputSummary = kind === 'skill' && skill.name
    ? `已加载技能“${skill.name}”${skillResourcePath ? `的资源：${skillResourcePath}` : ''}。`
    : rawOutputSummary
  const detail = safeDetail(text(
    outputSummary,
    inputSummary,
    source.description,
    source.resourcePath,
    source.statusText,
    source.message,
    localized?.detail,
  ))
  const status = normalizedStatus(effectiveEventName, source.status)
  const startedAt = text(source.startedAt) || (status === 'running' ? timestamp : undefined)
  const finishedAt = text(source.finishedAt)
    || (['success', 'partial', 'input_required', 'failed', 'cancelled'].includes(status) ? timestamp : undefined)
  const confidence = numberValue(source.answerConfidence)
    ?? numberValue(source.confidence)
    ?? numberValue(confidenceDetail.overall)
  const explicitConfidenceBasis = stringValues(source.confidenceBasis)
  const confidenceBasis = explicitConfidenceBasis.length
    ? explicitConfidenceBasis
    : stringValues(confidenceDetail.basis)
  const executionReadinessDetail = parsedRecord(analysis.executionReadiness)
  const executionReadiness = numberValue(source.executionReadiness)
    ?? numberValue(executionReadinessDetail.score)
  const metadata = {
    ...source,
    transportEventType: eventName,
    platformEventType: effectiveEventName,
    ...(Object.keys(analysis).length ? { analysis } : {}),
    ...(Object.keys(confidenceDetail).length ? { confidenceDetail } : {}),
  }

  return {
    id: identity,
    kind,
    title: activityTitle(kind, effectiveEventName, source),
    detail,
    inputSummary,
    outputSummary,
    callReason,
    status,
    agentCode,
    agentVersion: numberValue(source.agentVersion),
    timestamp: startedAt || timestamp,
    startedAt,
    finishedAt,
    durationMs: numberValue(source.durationMs),
    confidence,
    confidenceThreshold: numberValue(source.threshold),
    confidenceBasis,
    executionReadiness,
    toolKey: tool.key,
    toolName: tool.name,
    skillKey: skill.key,
    skillName: skill.name,
    analysis: Object.keys(analysis).length ? analysis : undefined,
    metadata,
  }
}

function normalizeArtifact(value: unknown): ChatArtifact | undefined {
  const source = asRecord(value)
  const code = text(source.artifactCode, source.codeRef, source.code)
  if (!code && source.content === undefined && !source.title) return undefined
  return {
    artifactCode: text(source.artifactCode, source.code),
    codeRef: text(source.codeRef),
    artifactType: text(source.artifactType, source.type),
    title: text(source.title, code, 'Agent 产物'),
    content: source.content,
    contentFormat: text(source.contentFormat, source.format),
    status: text(source.status),
    stage: text(source.stage),
    seqNo: numberValue(source.seqNo),
    visibleFlag: booleanValue(source.visibleFlag),
    visible: booleanValue(source.visible),
    extJson: text(source.extJson),
  }
}

function artifactKey(value?: string) {
  return value?.trim()
}

function normalizedPrimaryAnswerIdentity(value?: string) {
  return value?.trim().toLowerCase().replace(/[\s_]+/g, '-')
}

function isPrimaryAnswerArtifact(artifact: ChatArtifact) {
  // final-answer is the acceptance/audit copy of the assistant message, not a secondary UI artifact.
  return [artifact.artifactCode, artifact.codeRef, artifact.title]
    .some(value => normalizedPrimaryAnswerIdentity(value) === 'final-answer')
}

export function isDisplayableArtifact(artifact: ChatArtifact) {
  if (artifact.visibleFlag === false || artifact.visible === false) return false
  return !isPrimaryAnswerArtifact(artifact)
}

export function artifactsFromTransportEvent(eventName: string, payloadValue: unknown): ChatArtifact[] {
  if (!eventName.startsWith('artifact.') && !eventName.startsWith('artifacts.')) return []
  const payload = asRecord(payloadValue)
  const candidates = Array.isArray(payload.artifacts)
    ? payload.artifacts
    : Object.keys(asRecord(payload.artifact)).length
      ? [payload.artifact]
      : [payload]
  // Keep hidden updates until upsert so a visibility change can evict an earlier visible version.
  return candidates.flatMap((candidate) => {
    const artifact = normalizeArtifact(candidate)
    return artifact ? [artifact] : []
  })
}

export function artifactFromTransportEvent(eventName: string, payloadValue: unknown): ChatArtifact | undefined {
  return artifactsFromTransportEvent(eventName, payloadValue)[0]
}

export function upsertRunActivity(items: ChatRunActivity[], next: ChatRunActivity) {
  const index = items.findIndex(item => item.id === next.id && item.kind === next.kind)
  if (index < 0) return [...items, next]
  const updated = [...items]
  const current = updated[index]
  const startedAt = current.startedAt || next.startedAt || current.timestamp || next.timestamp
  const finishedAt = next.finishedAt || current.finishedAt
  const measuredDuration = startedAt && finishedAt
    ? Math.max(0, Date.parse(finishedAt) - Date.parse(startedAt))
    : undefined
  updated[index] = {
    ...current,
    ...next,
    title: text(next.metadata?.activityName, next.metadata?.title) ? next.title : current.title || next.title,
    inputSummary: next.inputSummary || current.inputSummary,
    outputSummary: next.outputSummary || current.outputSummary,
    callReason: next.callReason || current.callReason,
    toolKey: next.toolKey || current.toolKey,
    toolName: next.toolName || current.toolName,
    skillKey: next.skillKey || current.skillKey,
    skillName: next.skillName || current.skillName,
    startedAt,
    finishedAt,
    timestamp: startedAt,
    durationMs: next.durationMs ?? current.durationMs ?? (
      measuredDuration !== undefined && Number.isFinite(measuredDuration) ? measuredDuration : undefined
    ),
    metadata: { ...current.metadata, ...next.metadata },
  }
  return updated
}

export function upsertArtifact(items: ChatArtifact[], next: ChatArtifact) {
  const displayableItems = items.filter(isDisplayableArtifact)
  const nextCode = artifactKey(next.artifactCode || next.codeRef)
  if (!isDisplayableArtifact(next)) {
    return nextCode
      ? displayableItems.filter(item => artifactKey(item.artifactCode || item.codeRef) !== nextCode)
      : displayableItems
  }
  const index = displayableItems.findIndex(
    item => artifactKey(item.artifactCode || item.codeRef) === nextCode,
  )
  if (!nextCode || index < 0) return [...displayableItems, next]
  const updated = [...displayableItems]
  updated[index] = { ...updated[index], ...next }
  return updated
}

export function normalizeHistoricalArtifacts(value: unknown): ChatArtifact[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    const source = parsedRecord(item)
    const artifact = normalizeArtifact(source)
    return artifact && isDisplayableArtifact(artifact)
      ? [{ ...artifact, extJson: text(source.extJson) }]
      : []
  })
}

export function normalizeHistoricalActivities(value: unknown): ChatRunActivity[] {
  if (!Array.isArray(value)) return []
  const activities = value.flatMap((item, index) => {
    const source = parsedRecord(item)
    const activity = activityFromTransportEvent(
      'thinking.updated',
      { progressType: 'ACTIVITY', action: 'activity.updated', activity: source },
      text(source.id, source.activityCode, `history:${index}`),
      text(source.timestamp, source.createdAt, source.createTime, source.updateTime),
    )
    return activity ? [activity] : []
  })
  return activities.reduce<ChatRunActivity[]>(
    (items, activity) => upsertRunActivity(items, activity),
    [],
  )
}
