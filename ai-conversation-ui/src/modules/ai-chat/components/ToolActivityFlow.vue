<script setup lang="ts">
import { computed } from 'vue'
import CollapsibleMarkdown from './CollapsibleMarkdown.vue'

const props = withDefaults(defineProps<{
  toolName: string
  reason?: string
  inputSummary?: string
  outputSummary?: string
  status?: string
}>(), {
  reason: '',
  inputSummary: '',
  outputSummary: '',
  status: '',
})

const normalizedStatus = computed(() => props.status.trim().toLowerCase())
const resultEmptyText = computed(() => {
  if (['pending', 'running'].includes(normalizedStatus.value)) {
    return '工具正在执行，结果返回后将在这里显示。'
  }
  if (['error', 'failed'].includes(normalizedStatus.value)) {
    return '工具调用失败，未返回可展示的结果。'
  }
  return '本次调用未返回可展示的结果。'
})
</script>

<template>
  <div class="tool-activity-flow" role="group" :aria-label="`${toolName}调用过程`">
    <section class="tool-activity-flow__step">
      <span class="tool-activity-flow__index" aria-hidden="true">1</span>
      <div class="tool-activity-flow__body">
        <h4>调用原因</h4>
        <p class="tool-activity-flow__copy">
          {{ reason || '当前步骤需要调用工具补充、验证或执行任务所需信息。' }}
        </p>
      </div>
    </section>

    <section class="tool-activity-flow__step">
      <span class="tool-activity-flow__index" aria-hidden="true">2</span>
      <div class="tool-activity-flow__body">
        <div class="tool-activity-flow__heading">
          <h4>调用工具</h4>
          <strong>{{ toolName }}</strong>
        </div>
        <div class="tool-activity-flow__payload">
          <span class="tool-activity-flow__payload-label">请求参数</span>
          <CollapsibleMarkdown
            v-if="inputSummary"
            class="tool-activity-flow__summary"
            :content="inputSummary"
            :max-lines="4"
          />
          <p v-else class="tool-activity-flow__empty">未记录可展示的请求参数。</p>
        </div>
      </div>
    </section>

    <section class="tool-activity-flow__step">
      <span class="tool-activity-flow__index" aria-hidden="true">3</span>
      <div class="tool-activity-flow__body">
        <h4>执行结果</h4>
        <div class="tool-activity-flow__payload">
          <CollapsibleMarkdown
            v-if="outputSummary"
            class="tool-activity-flow__summary"
            :content="outputSummary"
            :max-lines="2"
          />
          <p v-else class="tool-activity-flow__empty">{{ resultEmptyText }}</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.tool-activity-flow {
  display: grid;
  margin-top: var(--app-space-2);
}

.tool-activity-flow__step {
  position: relative;
  display: grid;
  grid-template-columns: var(--app-space-5) minmax(0, 1fr);
  gap: var(--app-space-2);
  padding-bottom: var(--app-space-3);
}

.tool-activity-flow__step:not(:last-child)::after {
  position: absolute;
  top: var(--app-space-5);
  bottom: 0;
  left: 9px;
  width: 1px;
  content: '';
  background: var(--chat-panel-border);
}

.tool-activity-flow__step:last-child {
  padding-bottom: 0;
}

.tool-activity-flow__index {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--app-space-5);
  height: var(--app-space-5);
  border: 1px solid var(--chat-panel-border);
  border-radius: 50%;
  background: var(--chat-main-bg);
  color: var(--chat-text-subtle);
  font-size: var(--app-font-size-caption);
  font-weight: 600;
  line-height: 1;
}

.tool-activity-flow__body {
  min-width: 0;
}

.tool-activity-flow__body h4 {
  margin: 0;
  color: var(--chat-text-muted);
  font-size: var(--app-font-size-caption);
  font-weight: 600;
  line-height: var(--app-line-height-body);
}

.tool-activity-flow__heading {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: var(--app-space-2);
}

.tool-activity-flow__heading strong {
  min-width: 0;
  overflow: hidden;
  color: var(--chat-text-primary);
  font-size: var(--app-font-size-caption);
  font-weight: 600;
  line-height: var(--app-line-height-body);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-activity-flow__copy,
.tool-activity-flow__empty {
  margin: var(--app-space-tight) 0 0;
  color: var(--chat-text-muted);
  font-size: var(--app-font-size-caption);
  line-height: var(--app-line-height-loose);
  overflow-wrap: anywhere;
}

.tool-activity-flow__payload {
  --collapsible-markdown-surface: var(--chat-soft-bg-alt);
  min-width: 0;
  margin-top: var(--app-space-1);
  padding: var(--app-space-2);
  border: 1px solid var(--chat-panel-border);
  border-radius: var(--app-radius-md);
  background: var(--chat-soft-bg-alt);
}

.tool-activity-flow__payload-label {
  display: block;
  margin-bottom: var(--app-space-tight);
  color: var(--chat-text-subtle);
  font-size: var(--app-font-size-caption);
  font-weight: 600;
  line-height: var(--app-line-height-body);
}

.tool-activity-flow__summary {
  color: var(--chat-text-primary);
  font-size: var(--app-font-size-caption);
  line-height: var(--app-line-height-loose);
}

.tool-activity-flow__payload .tool-activity-flow__empty {
  margin-top: 0;
}
</style>
