<script setup lang="ts">
import { ArrowRight, Close } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { ChatRunActivity } from '../types'

const props = defineProps<{
  activities: ChatRunActivity[]
  runStatus?: string
}>()

const drawerVisible = ref(false)
const now = ref(Date.now())
let clock: number | undefined

const visibleActivities = computed(() => props.activities.filter(isMeaningfulActivity))
const normalizedRunStatus = computed(() => props.runStatus?.trim().toLowerCase() || '')
const runCompleted = computed(() => ['success', 'succeeded', 'completed'].includes(normalizedRunStatus.value))
const runFailed = computed(() => ['failed', 'error'].includes(normalizedRunStatus.value))
const runCancelled = computed(() => ['cancelled', 'canceled'].includes(normalizedRunStatus.value))
const runSettled = computed(() => runCompleted.value || runFailed.value || runCancelled.value)
const failedCount = computed(() => visibleActivities.value.filter(activity => activity.status === 'failed').length)
const cancelledCount = computed(() => visibleActivities.value.filter(activity => activity.status === 'cancelled').length)
const runningCount = computed(() => visibleActivities.value.filter((activity) =>
  !activity.status || activity.status === 'pending' || activity.status === 'running',
).length)
const allCompleted = computed(() => (
  visibleActivities.value.length > 0
  && visibleActivities.value.every(activity => activity.status === 'success')
))
const summaryStatus = computed(() => {
  if (runFailed.value) return '执行失败'
  if (runCancelled.value) return '已取消'
  if (runCompleted.value) return '已完成'
  if (failedCount.value) return `${failedCount.value} 项失败`
  if (runningCount.value) return `${runningCount.value} 项进行中`
  if (cancelledCount.value) return `${cancelledCount.value} 项已取消`
  return allCompleted.value ? '已完成' : '需要关注'
})

const durationSeconds = computed(() => {
  const timestamps = visibleActivities.value
    .map(activity => Date.parse(activity.timestamp || ''))
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
    cancelled: '已取消',
    canceled: '已取消',
  }
  return status ? labels[status.toLowerCase()] || '处理中' : '待处理'
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

function displayedStatus(activity: ChatRunActivity) {
  if (runCompleted.value && (!activity.status || activity.status === 'pending' || activity.status === 'running')) {
    return 'success'
  }
  return activity.status || 'pending'
}

function timelineStatus(activity: ChatRunActivity) {
  const status = displayedStatus(activity)
  return status === 'success' ? 'done' : status
}
</script>

<template>
  <div class="run-activity-timeline">
    <button
      class="run-activity-timeline__summary"
      type="button"
      :aria-label="`查看 AI 思考过程，${durationText}，共 ${visibleActivities.length} 项，${summaryStatus}`"
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
      size="420px"
      :show-close="false"
      :with-header="false"
    >
      <header class="run-activity-drawer__header">
        <div class="run-activity-drawer__header-copy">
          <h2>思考过程</h2>
          <p>{{ durationText }} · {{ visibleActivities.length }} 个活动</p>
        </div>
        <button
          class="run-activity-drawer__close"
          type="button"
          aria-label="关闭思考过程"
          @click="drawerVisible = false"
        >
          <el-icon><Close /></el-icon>
        </button>
      </header>

      <div class="run-activity-drawer__timeline" aria-label="AI 执行活动">
        <div
          v-for="activity in visibleActivities"
          :key="`${activity.kind}:${activity.id}`"
          :class="['run-activity-drawer__item', `is-${timelineStatus(activity)}`]"
        >
          <span class="run-activity-drawer__marker" aria-hidden="true"></span>
          <div class="run-activity-drawer__item-header">
            <strong>{{ activity.title }}</strong>
            <span class="run-activity-drawer__status">{{ statusLabel(displayedStatus(activity)) }}</span>
          </div>
          <p v-if="activity.detail" class="run-activity-drawer__detail">{{ activity.detail }}</p>
          <div class="run-activity-drawer__meta" aria-label="活动执行信息">
            <time v-if="activityTimeText(activity)" :datetime="activity.timestamp">
              时间 {{ activityTimeText(activity) }}
            </time>
            <span v-else>时间待同步</span>
            <span v-if="durationLabel(activity.durationMs)">耗时 {{ durationLabel(activity.durationMs) }}</span>
            <span class="run-activity-drawer__confidence">
              可信度 {{ percentageLabel(activity.confidence) || '未评分' }}
            </span>
            <span v-if="percentageLabel(activity.confidenceThreshold)">
              评分阈值 {{ percentageLabel(activity.confidenceThreshold) }}
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
