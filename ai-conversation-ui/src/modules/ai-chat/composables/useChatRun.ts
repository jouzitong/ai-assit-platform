import type { ChatArtifact, ChatRunActivity, ChatRunActivityKind } from '../types'

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
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
  'confidence.assessment.completed': {
    title: '可信度评估完成',
    detail: '已完成当前回答的可信度评分。',
  },
  'confidence.retrieval.started': {
    title: '正在补充知识依据',
    detail: '正在检索授权知识库，以补充低可信度回答的事实依据。',
  },
  'confidence.retrieval.completed': {
    title: '知识库检索完成',
    detail: '已完成知识库检索。',
  },
  'confidence.retrieval.skipped': {
    title: '已跳过知识库检索',
    detail: '当前没有可用的授权知识库。',
  },
  'confidence.reanalysis.started': {
    title: '正在重新分析',
    detail: '正在根据补充依据重新分析低可信度回答。',
  },
  'confidence.reanalysis.completed': {
    title: '重新分析完成',
    detail: '已完成回答修正和可信度复评。',
  },
  'tool.started': { title: '开始调用工具', detail: '正在执行本轮所需工具。' },
  'tool.completed': { title: '工具调用完成', detail: '工具已执行完成。' },
  'tool.failed': { title: '工具调用失败', detail: '工具执行失败。' },
  'handoff.requested': { title: '正在协作交接', detail: '正在将任务交接给协作智能体。' },
  'handoff.completed': { title: '协作交接完成', detail: '任务已完成协作交接。' },
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
  if (localized) return localized.title
  const explicit = text(source.title, source.activityName, source.statusText, source.message)
  if (explicit) return explicit
  const labels: Record<ChatRunActivityKind, string> = {
    agent: eventName.endsWith('completed') ? '智能体执行完成' : '智能体正在执行',
    handoff: eventName.endsWith('completed') ? '智能体协作交接完成' : '正在交接给协作智能体',
    tool: eventName.endsWith('completed') ? '工具调用完成' : eventName.endsWith('failed') ? '工具调用失败' : '正在调用工具',
    skill: '已加载技能',
    artifact: '产物已更新',
    check: eventName.endsWith('completed') ? '产物检查完成' : '正在检查产物',
    thinking: '智能体正在分析',
  }
  return labels[kind]
}

function normalizedStatus(eventName: string, value: unknown) {
  const status = text(value)?.toLowerCase()
  if (status === 'complete' || status === 'completed' || status === 'done' || status === 'success' || status === 'succeeded') return 'success'
  if (status === 'error' || status === 'failed') return 'failed'
  if (status === 'cancelled' || status === 'canceled') return 'cancelled'
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
  const persistedDetail = asRecord(nestedActivity.detail)
  if (
    eventName.startsWith('thinking.')
    && eventName !== 'thinking.updated'
    && !Object.keys(nestedActivity).length
    && payload.progressType !== 'ACTIVITY'
    && payload.action !== 'activity.updated'
  ) {
    return undefined
  }
  const source = {
    ...payload,
    ...thinking,
    ...persistedDetail,
    ...nestedActivity,
  }
  const kind = activityKind(eventName, source)
  if (!kind) return undefined

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
  const localized = activityCopy(eventName, source)
  const detail = safeDetail(localized?.detail || text(
    source.outputSummary,
    source.inputSummary,
    source.description,
    source.resourcePath,
    source.statusText,
    source.message,
  ))

  return {
    id: identity,
    kind,
    title: activityTitle(kind, eventName, source),
    detail,
    status: normalizedStatus(eventName, source.status),
    agentCode,
    agentVersion: numberValue(source.agentVersion),
    timestamp,
    durationMs: numberValue(source.durationMs),
    confidence: numberValue(source.confidence),
    confidenceThreshold: numberValue(source.threshold),
    metadata: source,
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
  updated[index] = { ...updated[index], ...next }
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
    const source = asRecord(item)
    const artifact = normalizeArtifact(source)
    return artifact && isDisplayableArtifact(artifact)
      ? [{ ...artifact, extJson: text(source.extJson) }]
      : []
  })
}

export function normalizeHistoricalActivities(value: unknown): ChatRunActivity[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((item, index) => {
    const source = asRecord(item)
    const activity = activityFromTransportEvent(
      'thinking.updated',
      { progressType: 'ACTIVITY', action: 'activity.updated', activity: source },
      text(source.id, source.activityCode, `history:${index}`),
      text(source.timestamp, source.createdAt, source.createTime, source.updateTime),
    )
    return activity ? [activity] : []
  })
}
