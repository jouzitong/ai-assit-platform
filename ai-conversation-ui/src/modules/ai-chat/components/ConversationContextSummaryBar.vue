<script setup lang="ts">
import { ArrowRight, Collection, WarningFilled } from '@element-plus/icons-vue'
import { computed } from 'vue'
import type { ChatMemoryContextResponse, ChatMemoryCounts } from '../types'

const props = withDefaults(defineProps<{
  context?: ChatMemoryContextResponse | null
  counts?: ChatMemoryCounts | null
  loading?: boolean
}>(), {
  context: null,
  counts: null,
  loading: false,
})

defineEmits<{
  open: []
}>()

const normalizedCounts = computed<ChatMemoryCounts>(() => ({
  sessionMemories: props.counts?.sessionMemories
    ?? props.context?.counts?.sessionMemories
    ?? props.context?.sessionMemories?.length
    ?? 0,
  longTermMemories: props.counts?.longTermMemories
    ?? props.context?.counts?.longTermMemories
    ?? props.context?.longTermMemories?.length
    ?? 0,
  processing: props.counts?.processing
    ?? props.context?.counts?.processing
    ?? props.context?.processingMemories?.length
    ?? 0,
  disabled: props.counts?.disabled
    ?? props.context?.counts?.disabled
    ?? props.context?.disabledMemories?.length
    ?? 0,
}))

const providerStatus = computed(() => props.context?.providerStatus?.trim().toUpperCase() || '')
const providerHint = computed(() => {
  const hints: Record<string, string> = {
    UNAVAILABLE: '记忆服务暂不可用，本次聊天仍会使用近期原文。',
    BINDING_UNAVAILABLE: '记忆空间正在准备，本次聊天仍可正常继续。',
    DISABLED: '记忆功能当前未开启。',
    SECURITY_REJECTED: '记忆内容暂不可用，本次聊天不受影响。',
  }
  return hints[providerStatus.value] || ''
})

const summary = computed(() => {
  const counts = normalizedCounts.value
  const parts = [
    `${counts.sessionMemories} 条会话记忆`,
    `${counts.longTermMemories} 条长期记忆`,
  ]
  if (counts.processing > 0) {
    parts.push(`${counts.processing} 条处理中`)
  }
  return `当前上下文：${parts.join(' · ')}`
})

const hasMemory = computed(() => {
  const counts = normalizedCounts.value
  return counts.sessionMemories + counts.longTermMemories + counts.processing + counts.disabled > 0
})

const visible = computed(() => Boolean(
  props.context && (hasMemory.value || providerHint.value),
))
</script>

<template>
  <section v-if="visible" class="conversation-context-summary" aria-label="当前对话上下文">
    <div class="conversation-context-summary__main">
      <el-icon class="conversation-context-summary__icon" aria-hidden="true">
        <WarningFilled v-if="providerHint" />
        <Collection v-else />
      </el-icon>
      <div class="conversation-context-summary__copy">
        <span>{{ summary }}</span>
        <small v-if="providerHint">{{ providerHint }}</small>
        <small v-else-if="context?.memoryLag">最新一轮记忆正在整理，完成后会自动更新。</small>
      </div>
    </div>
    <button type="button" :disabled="loading" @click="$emit('open')">
      <span>查看上下文</span>
      <el-icon aria-hidden="true"><ArrowRight /></el-icon>
    </button>
  </section>
</template>

<style scoped lang="scss">
.conversation-context-summary {
  container-type: inline-size;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-4);
  width: min(760px, calc(100% - 48px));
  margin: 0 auto var(--app-space-5);
  padding: var(--app-space-3) var(--app-space-4);
  border: 1px solid var(--chat-panel-border);
  border-radius: var(--app-radius-lg);
  background: var(--chat-soft-bg);
  color: var(--chat-text-secondary);
}

.conversation-context-summary__main {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: var(--app-space-2);
}

.conversation-context-summary__icon {
  flex: 0 0 auto;
  margin-top: var(--app-space-hairline);
  color: var(--app-accent);
  font-size: var(--app-font-size-title-sm);
}

.conversation-context-summary__copy {
  display: grid;
  min-width: 0;
  gap: var(--app-space-1);
  font-size: var(--app-font-size-body);
  line-height: var(--app-line-height-body);
}

.conversation-context-summary__copy > span {
  color: var(--chat-text-secondary);
  font-weight: 600;
}

.conversation-context-summary__copy > small {
  color: var(--chat-text-muted);
  font-size: var(--app-font-size-caption);
}

.conversation-context-summary > button {
  display: inline-flex;
  flex: 0 0 auto;
  min-height: var(--app-control-height-sm);
  align-items: center;
  gap: var(--app-space-1);
  padding: 0 var(--app-space-3);
  border: 1px solid var(--chat-panel-border);
  border-radius: var(--app-radius-round);
  background: var(--chat-main-bg);
  color: var(--app-accent);
  font: inherit;
  cursor: pointer;
}

.conversation-context-summary > button:hover:not(:disabled),
.conversation-context-summary > button:focus-visible {
  border-color: var(--app-accent-border);
  background: var(--app-accent-bg);
  outline: none;
}

.conversation-context-summary > button:disabled {
  cursor: wait;
  opacity: 0.6;
}

@container (max-width: 560px) {
  .conversation-context-summary {
    align-items: stretch;
    flex-direction: column;
  }

  .conversation-context-summary > button {
    align-self: flex-end;
  }
}
</style>
