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

function activityKind(eventName: string, activity: Record<string, unknown>): ChatRunActivityKind | undefined {
  const normalizedType = text(activity.activityType)?.toUpperCase() || ''
  if (eventName.startsWith('handoff.') || normalizedType.includes('HANDOFF')) return 'handoff'
  if (eventName.startsWith('agent.') || normalizedType.includes('AGENT')) return 'agent'
  if (eventName.startsWith('tool.') || normalizedType.includes('TOOL')) return 'tool'
  if (eventName.startsWith('skill.') || normalizedType.includes('SKILL')) return 'skill'
  if (eventName.startsWith('artifact.') || eventName.startsWith('artifacts.') || normalizedType.includes('ARTIFACT')) return 'artifact'
  if (eventName.startsWith('check.') || normalizedType.includes('CHECK') || normalizedType.includes('VALIDATION')) return 'check'
  if (eventName.startsWith('thinking.') && Object.keys(activity).length) return 'thinking'
  return undefined
}

function activityTitle(kind: ChatRunActivityKind, eventName: string, source: Record<string, unknown>) {
  const explicit = text(source.title, source.activityName, source.message)
  if (explicit) return explicit
  const labels: Record<ChatRunActivityKind, string> = {
    agent: eventName.endsWith('completed') ? 'Agent 已完成' : 'Agent 正在执行',
    handoff: eventName.endsWith('completed') ? 'Agent 协作交接完成' : '正在交接给协作 Agent',
    tool: eventName.endsWith('completed') ? 'Tool 调用完成' : eventName.endsWith('failed') ? 'Tool 调用失败' : '正在调用 Tool',
    skill: '已加载 Skill',
    artifact: '产物已更新',
    check: eventName.endsWith('completed') ? '产物检查完成' : '正在检查产物',
    thinking: 'Agent 正在分析',
  }
  return labels[kind]
}

function normalizedStatus(eventName: string, value: unknown) {
  const status = text(value)?.toLowerCase()
  if (status === 'completed' || status === 'success' || status === 'succeeded') return 'success'
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
  if (
    eventName.startsWith('thinking.')
    && !Object.keys(nestedActivity).length
    && payload.progressType !== 'ACTIVITY'
    && payload.action !== 'activity.updated'
  ) {
    return undefined
  }
  const source = Object.keys(nestedActivity).length ? { ...payload, ...nestedActivity } : payload
  const kind = activityKind(eventName, source)
  if (!kind) return undefined

  const agentCode = text(source.agentCode)
  const identity = text(
    source.activityCode,
    source.id,
    source.callId,
    source.toolCode,
    source.skillRef,
    source.artifactCode,
    source.codeRef,
    agentCode && `agent:${agentCode}`,
    eventId,
    `${eventName}:${timestamp || Date.now()}`,
  ) as string
  const detail = safeDetail(text(
    source.outputSummary,
    source.inputSummary,
    source.description,
    source.resourcePath,
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
    visibleFlag: booleanValue(source.visibleFlag),
    visible: booleanValue(source.visible),
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
