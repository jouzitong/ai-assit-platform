<script setup lang="ts">
import { ArrowRight, Close } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import CollapsibleMarkdown from './CollapsibleMarkdown.vue'
import type { ChatRunActivity } from '../types'

type ActivityFact = {
  label: string
  value: string
}

type ActivityList = {
  label: string
  items: string[]
  ordered?: boolean
}

type ActivityView = {
  activity: ChatRunActivity
  detail: string
  facts: ActivityFact[]
  lists: ActivityList[]
}

const props = defineProps<{
  activities: ChatRunActivity[]
  runStatus?: string
}>()

const drawerVisible = ref(false)
const now = ref(Date.now())
let clock: number | undefined

const visibleActivities = computed(() => props.activities.filter(isMeaningfulActivity))
const activityViews = computed(() => visibleActivities.value.map(buildActivityView))
const authoritativeOutcomeStatus = computed(() => {
  const result = [...visibleActivities.value].reverse().find(activity => activity.kind === 'execution')
  return textValue(result?.metadata?.outcomeStatus, result?.metadata?.resultStatus, result?.status)
})
const normalizedRunStatus = computed(() => (
  authoritativeOutcomeStatus.value || props.runStatus || ''
).trim().toLowerCase())
const runCompleted = computed(() => ['success', 'succeeded', 'completed'].includes(normalizedRunStatus.value))
const runFailed = computed(() => ['failed', 'error'].includes(normalizedRunStatus.value))
const runCancelled = computed(() => ['cancelled', 'canceled'].includes(normalizedRunStatus.value))
const runPartial = computed(() => normalizedRunStatus.value === 'partial')
const runInputRequired = computed(() => ['input_required', 'waiting_input'].includes(normalizedRunStatus.value))
const runSettled = computed(() => (
  runCompleted.value || runFailed.value || runCancelled.value || runPartial.value || runInputRequired.value
))
const failedCount = computed(() => visibleActivities.value.filter(activity => normalizedActivityStatus(activity) === 'failed').length)
const cancelledCount = computed(() => visibleActivities.value.filter(activity => normalizedActivityStatus(activity) === 'cancelled').length)
const runningCount = computed(() => visibleActivities.value.filter((activity) =>
  ['pending', 'running'].includes(normalizedActivityStatus(activity)),
).length)
const allCompleted = computed(() => (
  visibleActivities.value.length > 0
  && visibleActivities.value.every(activity => normalizedActivityStatus(activity) === 'success')
))
const summaryStatus = computed(() => {
  if (runFailed.value) return '执行失败'
  if (runCancelled.value) return '已取消'
  if (runInputRequired.value) return '等待补充信息'
  if (runPartial.value) return '部分完成'
  if (runCompleted.value) return '已完成'
  if (failedCount.value) return `${failedCount.value} 项失败`
  if (runningCount.value) return `${runningCount.value} 项进行中`
  if (cancelledCount.value) return `${cancelledCount.value} 项已取消`
  return allCompleted.value ? '已完成' : '需要关注'
})

const durationSeconds = computed(() => {
  const timestamps = visibleActivities.value
    .flatMap(activity => [activity.timestamp, activity.startedAt, activity.finishedAt])
    .map(value => Date.parse(value || ''))
    .filter(timestamp => Number.isFinite(timestamp))
  if (!timestamps.length) return undefined
  const startedAt = Math.min(...timestamps)
  const finishedAt = runSettled.value ? Math.max(...timestamps) : Math.max(now.value, ...timestamps)
  return Math.max(0, Math.floor((finishedAt - startedAt) / 1000))
})
const durationText = computed(() => {
  if (durationSeconds.value === undefined) return '时长待同步'
  const minutes = Math.floor(durationSeconds.value / 60)
  const seconds = durationSeconds.value % 60
  return `已处理 ${minutes > 0 ? `${minutes}分${seconds}秒` : `${seconds}秒`}`
})

onMounted(() => {
  clock = window.setInterval(() => {
    if (!runSettled.value) now.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  if (clock !== undefined) window.clearInterval(clock)
})

function statusLabel(status?: string) {
  const labels: Record<string, string> = {
    pending: '待处理',
    running: '进行中',
    success: '已完成',
    succeeded: '已完成',
    complete: '已完成',
    completed: '已完成',
    done: '已完成',
    error: '失败',
    failed: '失败',
    partial: '部分完成',
    input_required: '待补充信息',
    waiting_input: '待补充信息',
    passed: '通过',
    skipped: '已跳过',
    cancelled: '已取消',
    canceled: '已取消',
  }
  return status ? labels[status.toLowerCase()] || '处理中' : '待处理'
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

function textValue(...values: unknown[]) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) return value.trim()
    if (typeof value === 'number' && Number.isFinite(value)) return String(value)
  }
  return ''
}

function numberValue(...values: unknown[]) {
  for (const value of values) {
    if (typeof value === 'number' && Number.isFinite(value)) return value
    if (typeof value === 'string' && value.trim()) {
      const parsed = Number(value)
      if (Number.isFinite(parsed)) return parsed
    }
  }
  return undefined
}

function stringValues(value: unknown) {
  if (!Array.isArray(value)) return []
  return value.flatMap(item => typeof item === 'string' && item.trim() ? [item.trim()] : [])
}

function recordValues(value: unknown) {
  if (!Array.isArray(value)) return []
  return value.map(asRecord).filter(item => Object.keys(item).length)
}

function addFact(facts: ActivityFact[], label: string, value: unknown) {
  const normalized = textValue(value)
  if (normalized) facts.push({ label, value: normalized })
}

function addList(lists: ActivityList[], label: string, items: string[], ordered = false) {
  const normalized = items.filter(Boolean)
  if (normalized.length) lists.push({ label, items: normalized, ordered })
}

function normalizedActivityStatus(activity: ChatRunActivity) {
  const status = activity.status?.trim().toLowerCase()
  if (!status) return 'pending'
  if (['complete', 'completed', 'done', 'succeeded'].includes(status)) return 'success'
  if (status === 'error') return 'failed'
  if (status === 'canceled') return 'cancelled'
  if (status === 'waiting_input') return 'input_required'
  return status
}

function routeModeLabel(mode: string) {
  const labels: Record<string, string> = {
    DIRECT: '由当前智能体直接处理',
    TOOL: '先调用工具补充或验证信息',
    DELEGATE: '建议交由协作智能体处理',
    CLARIFY: '先向用户确认缺失信息',
  }
  return labels[mode.toUpperCase()] || mode
}

function routeSummary(route: Record<string, unknown>) {
  const parts = [routeModeLabel(textValue(route.mode) || 'DIRECT')]
  const agentCode = textValue(route.agentCode)
  const toolCodes = stringValues(route.toolCodes)
  const knowledgeBaseCodes = stringValues(route.knowledgeBaseCodes)
  if (agentCode) parts.push(`Agent：${agentCode}`)
  if (toolCodes.length) parts.push(`工具：${toolCodes.join('、')}`)
  if (knowledgeBaseCodes.length) parts.push(`知识库：${knowledgeBaseCodes.join('、')}`)
  return parts.join('；')
}

function readinessLevelLabel(level: string) {
  const labels: Record<string, string> = {
    READY: '可执行',
    PARTIAL: '部分就绪',
    LOW: '低就绪',
  }
  return labels[level.toUpperCase()] || level
}

function remediationLabel(action: string) {
  const labels: Record<string, string> = {
    QUERY_KNOWLEDGE_BASE: '查询知识库',
    USE_TOOL: '调用工具',
    DELEGATE: '转交协作智能体',
    ASK_USER: '请求用户补充',
    CONTINUE_WITH_CAVEAT: '带限制继续执行',
  }
  return labels[action.toUpperCase()] || action
}

function nextActionLabel(type: string) {
  const labels: Record<string, string> = {
    DELIVER_RESULT: '交付结果',
    REVIEW_REMAINING_ISSUES: '复核剩余问题',
    REQUEST_USER_INPUT: '请求用户补充',
    STOP_AND_REPORT_FAILURE: '停止并报告失败',
    NONE: '无需继续操作',
  }
  return labels[type.toUpperCase()] || type
}

function checkerLabel(type: string) {
  const labels: Record<string, string> = {
    REQUIRED: '必需产物检查',
    JSON_SCHEMA: '结构校验',
    TOOL: '工具校验',
    AGENT: '智能体复核',
    RUNTIME: '执行检查',
  }
  return labels[type.toUpperCase()] || type
}

function checkResultLabel(metadata: Record<string, unknown>) {
  const status = textValue(metadata.checkStatus)
  if (status) return statusLabel(status)
  if (metadata.passed === true) return '通过'
  if (metadata.passed === false) return '未通过'
  return ''
}

function remediationItems(value: unknown) {
  return recordValues(value).map((item) => {
    const description = textValue(item.description)
    if (description) return description
    const action = remediationLabel(textValue(item.action))
    const target = textValue(item.targetCode)
    return target ? `${action}：${target}` : action
  }).filter(Boolean)
}

function issueItems(value: unknown) {
  return recordValues(value).map((item) => {
    const message = textValue(item.message, item.description)
    const code = textValue(item.checkCode)
    if (code && message) return `${code}：${message}`
    return message || code
  }).filter(Boolean)
}

function buildActivityView(activity: ChatRunActivity): ActivityView {
  const metadata = asRecord(activity.metadata)
  const analysis = Object.keys(asRecord(activity.analysis)).length
    ? asRecord(activity.analysis)
    : asRecord(metadata.analysis)
  const facts: ActivityFact[] = []
  const lists: ActivityList[] = []
  let detail = activityDetail(activity)

  if (Object.keys(analysis).length) {
    detail = ''
    const analysisStatus = textValue(analysis.status, metadata.analysisStatus)
    if (analysisStatus && analysisStatus.toUpperCase() !== 'SUCCESS') {
      addFact(facts, '分析状态', analysisStatus.toUpperCase() === 'DEGRADED' ? '已降级，继续按安全方案执行' : analysisStatus)
    }
    addFact(facts, '目标', analysis.goal)
    addFact(facts, '交付物', analysis.deliverable)

    const route = asRecord(analysis.route)
    if (Object.keys(route).length) {
      addFact(facts, '建议路线', routeSummary(route))
      addFact(facts, '路线依据', route.rationale)
    }

    const confidence = asRecord(analysis.confidence)
    const confidenceParts = [
      ['意图清晰度', numberValue(confidence.intentClarity)],
      ['上下文充分度', numberValue(confidence.contextSufficiency)],
      ['路线匹配度', numberValue(confidence.routeFit)],
    ].flatMap(([label, score]) => typeof score === 'number' ? [`${label} ${percentageLabel(score)}`] : [])
    addFact(facts, '置信度构成', confidenceParts.join(' · '))

    const readiness = asRecord(analysis.executionReadiness)
    const readinessScore = numberValue(activity.executionReadiness, readiness.score)
    const readinessLevel = readinessLevelLabel(textValue(readiness.level, metadata.executionReadinessLevel))
    const readinessSummary = [percentageLabel(readinessScore), readinessLevel].filter(Boolean).join(' · ')
    addFact(facts, '执行就绪度', readinessSummary)
    addFact(facts, '就绪度依据', readiness.reason)
    addFact(facts, '降级原因', analysis.degradedReason)

    addList(lists, '关键约束', stringValues(analysis.constraints))
    addList(lists, '当前缺口', stringValues(analysis.gaps))
    addList(lists, '置信度依据', activity.confidenceBasis?.length
      ? activity.confidenceBasis
      : stringValues(confidence.basis))
    addList(lists, '成功标准', stringValues(analysis.successCriteria), true)
    addList(lists, '验证计划', stringValues(analysis.validationPlan), true)
    addList(lists, '低就绪度补救方案', remediationItems(analysis.lowReadinessRemediation), true)
    addList(lists, '分析校验提示', stringValues(analysis.validationWarnings))
  }

  if (activity.kind === 'check') {
    addFact(facts, '检查项', metadata.checkCode)
    addFact(facts, '检查对象', metadata.targetArtifact)
    const checkerType = textValue(metadata.checkerType)
    addFact(facts, '检查方式', checkerType ? checkerLabel(checkerType) : '')
    addFact(facts, '检查结果', checkResultLabel(metadata))
    addFact(facts, normalizedActivityStatus(activity) === 'failed' ? '失败原因' : '检查结论', activity.outputSummary)
    if (activity.outputSummary) detail = ''
  }

  if (activity.kind === 'repair') {
    const attempt = numberValue(metadata.repairAttempt, metadata.attempt)
    const maximum = numberValue(metadata.maxRepairAttempts)
    if (attempt !== undefined) {
      addFact(facts, '补救轮次', maximum !== undefined ? `第 ${attempt} 次，共允许 ${maximum} 次` : `第 ${attempt} 次`)
    }
    const artifactCount = numberValue(metadata.artifactCount)
    if (artifactCount !== undefined) addFact(facts, '修复产物', `${artifactCount} 个`)
    addList(lists, '补救依据', activity.inputSummary ? [activity.inputSummary] : [])
  }

  if (activity.kind === 'execution') {
    const checks = asRecord(metadata.checks)
    const total = numberValue(checks.total)
    const passed = numberValue(checks.passed)
    const failed = numberValue(checks.failed)
    const blockingFailed = numberValue(checks.blockingFailed)
    if (total !== undefined) {
      const checkSummary = [`${passed || 0}/${total} 项通过`]
      if (failed) checkSummary.push(`${failed} 项未通过`)
      if (blockingFailed) checkSummary.push(`${blockingFailed} 项为阻断问题`)
      addFact(facts, '检查汇总', checkSummary.join('，'))
    }
    addFact(facts, '最终结果', metadata.resultSummary)
    const completionCoverage = numberValue(metadata.completionCoverage)
    if (completionCoverage !== undefined) addFact(facts, '完成覆盖度', percentageLabel(completionCoverage))
    const repairAttempts = numberValue(metadata.remediationAttempts, metadata.repairAttempts)
    if (repairAttempts !== undefined) addFact(facts, '自动补救', `${repairAttempts} 次`)

    const nextAction = asRecord(metadata.nextAction)
    if (Object.keys(nextAction).length) {
      const actionType = nextActionLabel(textValue(nextAction.type))
      const actionDescription = textValue(nextAction.description)
      addFact(facts, '下一步', [actionType, actionDescription].filter(Boolean).join('：'))
    }

    addList(lists, '已通过检查', stringValues(metadata.checksPassed))
    const remainingIssues = issueItems(metadata.remainingIssues)
    addList(
      lists,
      normalizedActivityStatus(activity) === 'failed' ? '失败原因' : '剩余问题',
      remainingIssues.length ? remainingIssues : stringValues(metadata.checksFailed),
    )
  }

  return { activity, detail, facts, lists }
}

function activityTimeText(activity: ChatRunActivity) {
  const timestamp = Date.parse(activity.timestamp || '')
  if (!Number.isFinite(timestamp)) return ''
  const date = new Date(timestamp)
  const part = (value: number) => String(value).padStart(2, '0')
  return `${part(date.getHours())}:${part(date.getMinutes())}:${part(date.getSeconds())}`
}

function durationLabel(durationMs?: number) {
  if (durationMs === undefined || !Number.isFinite(durationMs) || durationMs < 0) return ''
  if (durationMs < 1000) return `${Math.round(durationMs)}毫秒`
  const seconds = durationMs / 1000
  return `${seconds >= 10 ? Math.round(seconds) : Number(seconds.toFixed(1))}秒`
}

function percentageLabel(value?: number) {
  if (value === undefined || !Number.isFinite(value)) return ''
  const percentage = value >= 0 && value <= 1 ? value * 100 : value
  const normalized = Math.min(100, Math.max(0, percentage))
  return `${Number(normalized.toFixed(1))}%`
}

function isMeaningfulActivity(activity: ChatRunActivity) {
  const eventType = String(activity.metadata?.platformEventType || '').toLowerCase()
  if (['agent.started', 'agent.changed', 'agent.completed', 'round.completed'].includes(eventType)) {
    return false
  }
  // Compatibility for activities persisted before platformEventType was added.
  if (!eventType && activity.kind === 'agent') {
    return !(/\bstarted$/i.test(activity.title)
      || /\bcompleted$/i.test(activity.title)
      || /^execution moved to\b/i.test(activity.title))
  }
  return true
}

function activityDetail(activity: ChatRunActivity) {
  if (activity.outputSummary) return activity.outputSummary
  if (activity.inputSummary) return `执行内容：${activity.inputSummary}`
  return activity.detail || ''
}

function displayedStatus(activity: ChatRunActivity) {
  const status = normalizedActivityStatus(activity)
  if (runCompleted.value && (status === 'pending' || status === 'running')) {
    return 'success'
  }
  return status
}

function timelineStatus(activity: ChatRunActivity) {
  const status = displayedStatus(activity)
  return status === 'success' ? 'done' : status
}

function confidenceLabel(activity: ChatRunActivity) {
  if (activity.kind === 'execution') return '结果可信度'
  if (Object.keys(asRecord(activity.analysis)).length) return '理解置信度'
  return '可信度'
}
</script>

<template>
  <div class="run-activity-timeline">
    <button
      class="run-activity-timeline__summary"
      type="button"
      :aria-label="`查看 AI 分析与执行过程，${durationText}，共 ${visibleActivities.length} 项，${summaryStatus}`"
      @click="drawerVisible = true"
    >
      <span class="run-activity-timeline__summary-title">
        <el-icon aria-hidden="true"><ArrowRight /></el-icon>
        <strong>执行过程</strong>
      </span>
      <span class="run-activity-timeline__summary-status">{{ visibleActivities.length }} 项 · {{ summaryStatus }}</span>
    </button>

    <el-drawer
      v-model="drawerVisible"
      class="run-activity-drawer"
      direction="rtl"
      size="520px"
      :show-close="false"
      :with-header="false"
    >
      <header class="run-activity-drawer__header">
        <div class="run-activity-drawer__header-copy">
          <h2>分析与执行</h2>
          <p>{{ durationText }} · {{ visibleActivities.length }} 个活动</p>
        </div>
        <button
          class="run-activity-drawer__close"
          type="button"
          aria-label="关闭分析与执行过程"
          @click="drawerVisible = false"
        >
          <el-icon><Close /></el-icon>
        </button>
      </header>

      <div class="run-activity-drawer__timeline" aria-label="AI 执行活动">
        <div
          v-for="view in activityViews"
          :key="`${view.activity.kind}:${view.activity.id}`"
          :class="['run-activity-drawer__item', `is-${timelineStatus(view.activity)}`]"
        >
          <span class="run-activity-drawer__marker" aria-hidden="true"></span>
          <div class="run-activity-drawer__item-header">
            <strong>{{ view.activity.title }}</strong>
            <span class="run-activity-drawer__status">{{ statusLabel(displayedStatus(view.activity)) }}</span>
          </div>
          <CollapsibleMarkdown
            v-if="view.detail"
            class="run-activity-drawer__detail"
            :content="view.detail"
          />

          <dl v-if="view.facts.length" class="run-activity-drawer__facts">
            <div
              v-for="fact in view.facts"
              :key="`${fact.label}:${fact.value}`"
              class="run-activity-drawer__fact"
            >
              <dt>{{ fact.label }}</dt>
              <dd>{{ fact.value }}</dd>
            </div>
          </dl>

          <section
            v-for="section in view.lists"
            :key="`${section.label}:${section.items.join('|')}`"
            class="run-activity-drawer__section"
          >
            <h3>{{ section.label }}</h3>
            <component :is="section.ordered ? 'ol' : 'ul'">
              <li v-for="item in section.items" :key="item">{{ item }}</li>
            </component>
          </section>

          <div class="run-activity-drawer__meta" aria-label="活动执行信息">
            <time v-if="activityTimeText(view.activity)" :datetime="view.activity.timestamp">
              时间 {{ activityTimeText(view.activity) }}
            </time>
            <span v-else>时间待同步</span>
            <span v-if="durationLabel(view.activity.durationMs)">
              耗时 {{ durationLabel(view.activity.durationMs) }}
            </span>
            <span
              v-if="percentageLabel(view.activity.confidence)"
              class="run-activity-drawer__confidence"
            >
              {{ confidenceLabel(view.activity) }} {{ percentageLabel(view.activity.confidence) }}
            </span>
            <span v-if="percentageLabel(view.activity.confidenceThreshold)">
              评分阈值 {{ percentageLabel(view.activity.confidenceThreshold) }}
            </span>
          </div>
        </div>
        <p v-if="!visibleActivities.length" class="run-activity-drawer__empty">
          本轮未记录可展示的分析活动。
        </p>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.run-activity-timeline {
  margin-top: 10px;
  border: 1px solid var(--chat-panel-border);
  border-radius: 12px;
  background: var(--chat-soft-bg);
}

.run-activity-timeline__summary {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border: 0;
  border-radius: 12px;
  padding: 10px 12px;
  background: transparent;
  color: var(--chat-text-primary);
  cursor: pointer;
  font-size: 12px;
  text-align: left;
}

.run-activity-timeline__summary-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.run-activity-timeline__summary-status {
  color: var(--chat-text-muted);
  font-size: 11px;
  font-weight: 400;
}

.run-activity-timeline__summary:focus-visible {
  outline: 2px solid var(--app-accent);
  outline-offset: 2px;
}

.run-activity-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 52px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--chat-panel-border);
}

.run-activity-drawer__header-copy {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.run-activity-drawer__header h2 {
  flex: none;
  margin: 0;
  color: var(--chat-text-primary);
  font-size: 14px;
  font-weight: 700;
  line-height: 22px;
}

.run-activity-drawer__header p {
  overflow: hidden;
  margin: 0;
  color: var(--chat-text-muted);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-activity-drawer__close {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--chat-text-muted);
  cursor: pointer;
  background: var(--chat-soft-bg);
  border: 0;
  border-radius: 50%;
}

.run-activity-drawer__close:hover {
  color: var(--chat-text-primary);
  background: var(--chat-hover-bg);
}

.run-activity-drawer__timeline {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: 18px 22px 24px;
  overflow-y: auto;
}

.run-activity-drawer__item {
  position: relative;
  padding: 0 0 22px 32px;
}

.run-activity-drawer__item::before {
  position: absolute;
  top: 19px;
  bottom: 0;
  left: 8px;
  width: 1px;
  content: '';
  background: var(--chat-panel-border);
}

.run-activity-drawer__item:last-child {
  padding-bottom: 0;
}

.run-activity-drawer__item:last-child::before {
  display: none;
}

.run-activity-drawer__marker {
  position: absolute;
  top: 5px;
  left: 2px;
  width: 13px;
  height: 13px;
  background: var(--chat-text-subtle);
  border: 2px solid var(--chat-main-bg);
  border-radius: 50%;
}

.run-activity-drawer__item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 22px;
}

.run-activity-drawer__item-header strong {
  min-width: 0;
  overflow: hidden;
  color: var(--chat-text-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.run-activity-drawer__status {
  flex: none;
  margin-left: auto;
  color: var(--chat-text-muted);
  font-size: 12px;
  line-height: 18px;
}

.run-activity-drawer__detail {
  margin: 4px 0 0;
  color: var(--chat-text-muted);
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.run-activity-drawer__facts {
  display: grid;
  gap: var(--app-space-2);
  margin: var(--app-space-3) 0 0;
  padding: var(--app-space-3);
  border: 1px solid var(--chat-panel-border);
  border-radius: var(--app-radius-md);
  background: var(--chat-soft-bg-alt);
}

.run-activity-drawer__fact {
  display: grid;
  grid-template-columns: minmax(5.5rem, auto) minmax(0, 1fr);
  gap: var(--app-space-2);
  align-items: start;
}

.run-activity-drawer__fact dt,
.run-activity-drawer__fact dd {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.run-activity-drawer__fact dt {
  color: var(--chat-text-subtle);
  font-weight: 500;
}

.run-activity-drawer__fact dd {
  color: var(--chat-text-primary);
}

.run-activity-drawer__section {
  margin-top: var(--app-space-3);
}

.run-activity-drawer__section h3 {
  margin: 0 0 var(--app-space-tight);
  color: var(--chat-text-muted);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
}

.run-activity-drawer__section ul,
.run-activity-drawer__section ol {
  display: grid;
  gap: var(--app-space-1);
  margin: 0;
  padding-left: var(--app-space-5);
  color: var(--chat-text-primary);
  font-size: 12px;
  line-height: 1.65;
}

.run-activity-drawer__section li {
  padding-left: var(--app-space-hairline);
  overflow-wrap: anywhere;
}

.run-activity-drawer__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-top: 6px;
  color: var(--chat-text-subtle);
  font-size: 12px;
  line-height: 18px;
}

.run-activity-drawer__confidence {
  color: var(--chat-text-primary);
  font-weight: 600;
}

.run-activity-drawer__empty {
  margin: 0;
  color: var(--chat-text-muted);
  font-size: 13px;
  line-height: 1.7;
  text-align: center;
}

.run-activity-drawer__item.is-done .run-activity-drawer__marker {
  background: var(--app-success);
}

.run-activity-drawer__item.is-running .run-activity-drawer__marker {
  background: var(--app-accent);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--app-accent) 16%, transparent);
}

.run-activity-drawer__item.is-failed .run-activity-drawer__marker {
  background: var(--app-danger);
}

.run-activity-drawer__item.is-partial .run-activity-drawer__marker,
.run-activity-drawer__item.is-input_required .run-activity-drawer__marker {
  background: var(--app-warning);
}

.run-activity-drawer__item.is-cancelled .run-activity-drawer__marker {
  background: var(--chat-text-subtle);
}

:deep(.run-activity-drawer) {
  max-width: 100%;
}

:deep(.run-activity-drawer .el-drawer__body) {
  display: flex;
  min-height: 0;
  flex-direction: column;
  padding: 0;
}

</style>
