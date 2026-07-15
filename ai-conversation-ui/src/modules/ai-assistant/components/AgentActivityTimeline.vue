<script setup lang="ts">
import {
  ArrowDownBold,
  ArrowRightBold,
  CircleCheckFilled,
  CloseBold,
  Connection,
  DataAnalysis,
  Document,
  Loading,
  Operation,
  Search,
  WarningFilled,
} from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, ref, useId, watch } from 'vue'
import type {
  AiAssistantActivity,
  AiAssistantActivityKind,
  AiAssistantMessage,
} from '../types'

const props = defineProps<{
  activities: AiAssistantActivity[]
  messageStatus: AiAssistantMessage['status']
}>()

const contentId = `agent-activity-${useId()}`
const expanded = ref(props.messageStatus === 'pending' || props.messageStatus === 'error')
let collapseTimer = 0
const currentActivity = computed(() => (
  [...props.activities].reverse().find(activity => activity.status === 'running')
  || props.activities.at(-1)
))
const completedCount = computed(() => props.activities.filter(activity => activity.status === 'complete').length)
const failedCount = computed(() => props.activities.filter(activity => activity.status === 'error').length)
const cancelledCount = computed(() => props.activities.filter(activity => activity.status === 'cancelled').length)
const finalSummary = computed(() => (
  [...props.activities].reverse().find(activity => activity.kind === 'summary' && activity.status === 'complete')
))
const overviewStatus = computed<'running' | 'complete' | 'error' | 'cancelled'>(() => {
  if (props.messageStatus === 'pending') return 'running'
  if (props.messageStatus === 'error' || failedCount.value) return 'error'
  if (props.messageStatus === 'cancelled' || cancelledCount.value) return 'cancelled'
  return 'complete'
})
const elapsedLabel = computed(() => {
  if (!props.activities.length || props.messageStatus === 'pending') return ''
  const startedAt = Math.min(...props.activities.map(activity => Date.parse(activity.startedAt)).filter(Number.isFinite))
  const completedAt = Math.max(...props.activities.map(activity => Date.parse(activity.completedAt || activity.startedAt)).filter(Number.isFinite))
  if (!Number.isFinite(startedAt) || !Number.isFinite(completedAt)) return ''
  const duration = Math.max(0, completedAt - startedAt)
  if (duration < 1_000) return '< 1 秒'
  return `${Math.round(duration / 100) / 10} 秒`
})
const summaryTitle = computed(() => {
  if (props.messageStatus === 'pending') return currentActivity.value?.title || 'Agent 正在处理'
  if (props.messageStatus === 'error') return '执行过程出现问题'
  if (props.messageStatus === 'cancelled') return '执行过程已停止'
  if (failedCount.value) return `处理完成，${failedCount.value} 个步骤未完成`
  if (cancelledCount.value) return `处理完成，${cancelledCount.value} 个步骤已停止`
  return finalSummary.value?.title || '本轮处理完成'
})
const summaryMeta = computed(() => {
  if (props.messageStatus === 'pending') return `${completedCount.value}/${props.activities.length} 步完成`
  return [
    `${props.activities.length} 步`,
    ...(failedCount.value ? [`${failedCount.value} 项未完成`] : []),
    elapsedLabel.value,
  ].filter(Boolean).join(' · ')
})

const kindIcons: Record<AiAssistantActivityKind, typeof Document> = {
  context: Document,
  model: Connection,
  reasoning: DataAnalysis,
  tool: Operation,
  knowledge: Search,
  summary: CircleCheckFilled,
}

function statusIcon(activity: AiAssistantActivity) {
  if (activity.status === 'running') return Loading
  if (activity.status === 'error') return WarningFilled
  if (activity.status === 'cancelled') return CloseBold
  return kindIcons[activity.kind]
}

function activityDuration(activity: AiAssistantActivity) {
  if (activity.status === 'running') return '进行中'
  if (!activity.completedAt) return ''
  const duration = Date.parse(activity.completedAt) - Date.parse(activity.startedAt)
  if (!Number.isFinite(duration) || duration < 0) return ''
  if (duration < 1_000) return '< 1s'
  return `${Math.round(duration / 100) / 10}s`
}

function clearCollapseTimer() {
  if (collapseTimer) window.clearTimeout(collapseTimer)
  collapseTimer = 0
}

function toggleExpanded() {
  clearCollapseTimer()
  expanded.value = !expanded.value
}

watch(
  () => props.messageStatus,
  (status) => {
    clearCollapseTimer()
    if (status === 'pending' || status === 'error') {
      expanded.value = true
      return
    }
    collapseTimer = window.setTimeout(() => {
      expanded.value = false
      collapseTimer = 0
    }, 450)
  },
)

onBeforeUnmount(clearCollapseTimer)
</script>

<template>
  <section class="agent-activity-timeline" :class="`is-${overviewStatus}`">
    <span class="agent-activity-timeline__sr-status" role="status" aria-live="polite" aria-atomic="true">
      {{ summaryTitle }}
    </span>
    <button
      class="agent-activity-timeline__toggle"
      type="button"
      :aria-expanded="expanded"
      :aria-controls="contentId"
      @click="toggleExpanded"
    >
      <span class="agent-activity-timeline__summary-icon" aria-hidden="true">
        <el-icon v-if="overviewStatus === 'running'" class="is-spinning"><Loading /></el-icon>
        <el-icon v-else-if="overviewStatus === 'error'"><WarningFilled /></el-icon>
        <el-icon v-else-if="overviewStatus === 'cancelled'"><CloseBold /></el-icon>
        <el-icon v-else><CircleCheckFilled /></el-icon>
      </span>
      <span class="agent-activity-timeline__summary">
        <strong>{{ summaryTitle }}</strong>
        <small>{{ summaryMeta }}</small>
      </span>
      <el-icon class="agent-activity-timeline__chevron" aria-hidden="true">
        <ArrowDownBold v-if="expanded" />
        <ArrowRightBold v-else />
      </el-icon>
    </button>

    <div
      :id="contentId"
      class="agent-activity-timeline__expand"
      :class="{ 'is-expanded': expanded }"
      :aria-hidden="!expanded"
    >
      <div class="agent-activity-timeline__expand-clip">
        <div class="agent-activity-timeline__content">
          <ol aria-label="Agent 执行活动">
            <li
              v-for="activity in activities"
              :key="activity.id"
              :class="`is-${activity.status}`"
            >
              <span class="agent-activity-timeline__marker" aria-hidden="true">
                <el-icon :class="{ 'is-spinning': activity.status === 'running' }">
                  <component :is="statusIcon(activity)" />
                </el-icon>
              </span>
              <div class="agent-activity-timeline__item-content">
                <strong>{{ activity.title }}</strong>
                <p v-if="activity.detail">{{ activity.detail }}</p>
              </div>
              <span class="agent-activity-timeline__duration">{{ activityDuration(activity) }}</span>
            </li>
          </ol>
          <p class="agent-activity-timeline__notice">展示的是可验证的执行记录，不包含模型内部思维链。</p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.agent-activity-timeline {
  width: min(100%, 560px);
  overflow: hidden;
  border: 1px solid var(--app-border-subtle);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-muted);
}

.agent-activity-timeline__sr-status {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.agent-activity-timeline__toggle {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  width: 100%;
  gap: var(--app-space-2);
  padding: var(--app-space-2) var(--app-space-3);
  border: 0;
  background: transparent;
  color: var(--app-text);
  text-align: left;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.agent-activity-timeline__toggle:hover {
  background: var(--app-accent-bg);
}

.agent-activity-timeline__toggle:focus-visible {
  outline: 2px solid var(--app-accent-border);
  outline-offset: -2px;
}

.agent-activity-timeline__summary-icon,
.agent-activity-timeline__marker {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--app-accent);
}

.agent-activity-timeline__summary-icon {
  width: var(--app-control-height-sm);
  height: var(--app-control-height-sm);
  border-radius: var(--app-radius-round);
  background: var(--app-accent-bg);
}

.agent-activity-timeline__summary {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--app-space-1);
}

.agent-activity-timeline__summary strong,
.agent-activity-timeline__item-content strong {
  overflow-wrap: anywhere;
  color: var(--app-title);
  font-size: var(--app-font-size-body);
  line-height: var(--app-line-height-body);
}

.agent-activity-timeline__summary small,
.agent-activity-timeline__duration,
.agent-activity-timeline__notice {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.agent-activity-timeline__chevron {
  color: var(--app-text-muted);
  transition: color 0.2s ease;
}

.agent-activity-timeline__expand {
  display: grid;
  grid-template-rows: 0fr;
  opacity: 0;
  transition: grid-template-rows 0.24s ease, opacity 0.18s ease;
}

.agent-activity-timeline__expand.is-expanded {
  grid-template-rows: 1fr;
  opacity: 1;
}

.agent-activity-timeline__expand-clip {
  min-height: 0;
  overflow: hidden;
}

.agent-activity-timeline__content {
  border-top: 1px solid var(--app-border-subtle);
}

.agent-activity-timeline__content ol {
  display: grid;
  gap: 0;
  margin: 0;
  padding: var(--app-space-3);
  list-style: none;
}

.agent-activity-timeline__content li {
  position: relative;
  display: grid;
  grid-template-columns: var(--app-control-height-sm) minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--app-space-2);
  min-width: 0;
  padding-bottom: var(--app-space-3);
}

.agent-activity-timeline__content li:last-child {
  padding-bottom: 0;
}

.agent-activity-timeline__content li:not(:last-child)::after {
  position: absolute;
  top: var(--app-control-height-sm);
  bottom: 0;
  left: calc(var(--app-control-height-sm) / 2);
  width: 1px;
  background: var(--app-border);
  content: '';
}

.agent-activity-timeline__marker {
  width: var(--app-control-height-sm);
  height: var(--app-control-height-sm);
  border: 1px solid var(--app-accent-border);
  border-radius: var(--app-radius-round);
  background: var(--app-surface-solid);
}

.agent-activity-timeline__item-content {
  min-width: 0;
  padding-top: var(--app-space-tight);
}

.agent-activity-timeline__item-content p {
  margin: var(--app-space-1) 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
  line-height: var(--app-line-height-body);
  overflow-wrap: anywhere;
}

.agent-activity-timeline__duration {
  padding-top: var(--app-space-tight);
  white-space: nowrap;
}

.agent-activity-timeline__notice {
  margin: 0;
  padding: 0 var(--app-space-3) var(--app-space-3);
  line-height: var(--app-line-height-body);
}

.agent-activity-timeline.is-error .agent-activity-timeline__summary-icon,
.agent-activity-timeline__content li.is-error .agent-activity-timeline__marker {
  border-color: var(--app-warning-border);
  background: var(--app-warning-bg);
  color: var(--app-warning);
}

.agent-activity-timeline.is-cancelled .agent-activity-timeline__summary-icon,
.agent-activity-timeline__content li.is-cancelled .agent-activity-timeline__marker {
  color: var(--app-text-muted);
}

.is-spinning {
  animation: agent-activity-spin 1s linear infinite;
}

@keyframes agent-activity-spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .agent-activity-timeline__toggle,
  .agent-activity-timeline__chevron,
  .agent-activity-timeline__expand {
    transition: none;
  }

  .is-spinning {
    animation: none;
  }
}
</style>
