<script setup lang="ts">
import type { ChatRunActivity } from '../types'

defineProps<{
  activities: ChatRunActivity[]
}>()

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
</script>

<template>
  <div class="run-activity-timeline" aria-label="Agent 运行活动">
    <div
      v-for="activity in activities"
      :key="`${activity.kind}:${activity.id}`"
      :class="['run-activity-timeline__item', `is-${activity.status || 'pending'}`]"
    >
      <span class="run-activity-timeline__dot" aria-hidden="true"></span>
      <div class="run-activity-timeline__body">
        <div class="run-activity-timeline__heading">
          <span class="run-activity-timeline__kind">{{ kindLabels[activity.kind] }}</span>
          <strong>{{ activity.title }}</strong>
          <span v-if="activity.status" class="run-activity-timeline__status">{{ statusLabel(activity.status) }}</span>
        </div>
        <div v-if="activity.agentCode" class="run-activity-timeline__meta">
          {{ activity.agentCode }}<template v-if="activity.agentVersion"> · v{{ activity.agentVersion }}</template>
        </div>
        <p v-if="activity.detail">{{ activity.detail }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.run-activity-timeline {
  display: grid;
  gap: 8px;
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px solid var(--chat-panel-border);
  border-radius: 12px;
  background: var(--chat-soft-bg);
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
</style>
