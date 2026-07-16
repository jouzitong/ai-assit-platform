<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'
import { computed, ref, watch } from 'vue'
import type { ChatRunActivity } from '../types'

const props = defineProps<{
  activities: ChatRunActivity[]
  runStatus?: string
}>()

const isOpen = ref(true)
const normalizedRunStatus = computed(() => props.runStatus?.trim().toLowerCase() || '')
const runCompleted = computed(() => ['success', 'succeeded', 'completed'].includes(normalizedRunStatus.value))
const runFailed = computed(() => ['failed', 'error'].includes(normalizedRunStatus.value))
const runCancelled = computed(() => ['cancelled', 'canceled'].includes(normalizedRunStatus.value))
const failedCount = computed(() => props.activities.filter(activity => activity.status === 'failed').length)
const cancelledCount = computed(() => props.activities.filter(activity => activity.status === 'cancelled').length)
const runningCount = computed(() => props.activities.filter((activity) =>
  !activity.status || activity.status === 'pending' || activity.status === 'running',
).length)
const allCompleted = computed(() => (
  props.activities.length > 0
  && props.activities.every(activity => activity.status === 'success')
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

watch([runCompleted, runFailed, runCancelled, allCompleted], ([completed, failed, cancelled, activitiesCompleted]) => {
  isOpen.value = failed || cancelled || !(completed || activitiesCompleted)
}, { immediate: true })

function handleToggle(event: Event) {
  isOpen.value = (event.currentTarget as HTMLDetailsElement).open
}

const kindLabels: Record<ChatRunActivity['kind'], string> = {
  agent: 'Agent',
  handoff: '协作',
  tool: 'Tool',
  skill: 'Skill',
  artifact: '产物',
  check: '检查',
  thinking: '分析',
}

function statusLabel(status?: string) {
  const labels: Record<string, string> = {
    pending: '等待中',
    running: '进行中',
    success: '已完成',
    failed: '失败',
    cancelled: '已取消',
  }
  return status ? labels[status] || status : ''
}

function displayedStatus(activity: ChatRunActivity) {
  if (runCompleted.value && (!activity.status || activity.status === 'pending' || activity.status === 'running')) {
    return 'success'
  }
  return activity.status
}
</script>

<template>
  <details
    class="run-activity-timeline"
    :open="isOpen"
    @toggle="handleToggle"
  >
    <summary class="run-activity-timeline__summary">
      <span class="run-activity-timeline__summary-title">
        <el-icon class="run-activity-timeline__summary-icon" aria-hidden="true"><ArrowRight /></el-icon>
        <strong>执行过程</strong>
      </span>
      <span class="run-activity-timeline__summary-status">{{ activities.length }} 项 · {{ summaryStatus }}</span>
    </summary>
    <div class="run-activity-timeline__content" aria-label="Agent 运行活动">
      <div
        v-for="activity in activities"
        :key="`${activity.kind}:${activity.id}`"
        :class="['run-activity-timeline__item', `is-${displayedStatus(activity) || 'pending'}`]"
      >
        <span class="run-activity-timeline__dot" aria-hidden="true"></span>
        <div class="run-activity-timeline__body">
          <div class="run-activity-timeline__heading">
            <span class="run-activity-timeline__kind">{{ kindLabels[activity.kind] }}</span>
            <strong>{{ activity.title }}</strong>
            <span v-if="displayedStatus(activity)" class="run-activity-timeline__status">
              {{ statusLabel(displayedStatus(activity)) }}
            </span>
          </div>
          <div v-if="activity.agentCode" class="run-activity-timeline__meta">
            {{ activity.agentCode }}<template v-if="activity.agentVersion"> · v{{ activity.agentVersion }}</template>
          </div>
          <p v-if="activity.detail">{{ activity.detail }}</p>
        </div>
      </div>
    </div>
  </details>
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
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  color: var(--chat-text-primary);
  cursor: pointer;
  font-size: 12px;
  list-style: none;
}

.run-activity-timeline__summary::-webkit-details-marker {
  display: none;
}

.run-activity-timeline__summary-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.run-activity-timeline__summary-icon {
  transition: transform 0.2s ease;
}

.run-activity-timeline[open] .run-activity-timeline__summary-icon {
  transform: rotate(90deg);
}

.run-activity-timeline__summary-status {
  color: var(--chat-text-muted);
  font-size: 11px;
  font-weight: 400;
}

.run-activity-timeline__summary:focus-visible {
  border-radius: 12px;
  outline: 2px solid var(--app-accent);
  outline-offset: 2px;
}

.run-activity-timeline__content {
  display: grid;
  gap: 8px;
  padding: 0 12px 10px;
}

.run-activity-timeline:not([open]) .run-activity-timeline__content {
  display: none;
}

.run-activity-timeline__item {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 8px;
}

.run-activity-timeline__dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--chat-text-muted);
}

.run-activity-timeline__item.is-running .run-activity-timeline__dot {
  background: var(--app-accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-accent) 16%, transparent);
}

.run-activity-timeline__item.is-success .run-activity-timeline__dot { background: var(--app-success); }
.run-activity-timeline__item.is-failed .run-activity-timeline__dot { background: var(--app-danger); }

.run-activity-timeline__heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  color: var(--chat-text-primary);
  font-size: 12px;
}

.run-activity-timeline__kind,
.run-activity-timeline__status {
  color: var(--chat-text-muted);
  font-size: 11px;
}

.run-activity-timeline__kind {
  padding: 2px 6px;
  border-radius: 999px;
  background: var(--chat-bubble-bg);
}

.run-activity-timeline__meta,
.run-activity-timeline p {
  margin: 3px 0 0;
  color: var(--chat-text-muted);
  font-size: 11px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

@media (prefers-reduced-motion: reduce) {
  .run-activity-timeline__summary-icon {
    transition: none;
  }
}
</style>
