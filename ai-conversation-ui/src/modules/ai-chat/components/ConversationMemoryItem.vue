<script setup lang="ts">
import {
  Clock,
  Delete,
  EditPen,
  FolderOpened,
  Promotion,
  RefreshRight,
  Remove,
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import type { ChatMemoryItem } from '../types'

const props = withDefaults(defineProps<{
  item: ChatMemoryItem
  currentSessionCode?: string
  loading?: boolean
}>(), {
  currentSessionCode: '',
  loading: false,
})

defineEmits<{
  disable: [item: ChatMemoryItem]
  restore: [item: ChatMemoryItem]
  correct: [item: ChatMemoryItem]
  promote: [item: ChatMemoryItem]
  exclude: [item: ChatMemoryItem]
  forget: [item: ChatMemoryItem]
  source: [item: ChatMemoryItem]
}>()

const scope = computed(() => props.item.scope?.trim().toUpperCase() || '')
const status = computed(() => props.item.status?.trim().toUpperCase() || '')
const memoryType = computed(() => props.item.memoryType?.trim().toUpperCase() || '')
const hasReference = computed(() => Boolean(props.item.memoryRef?.trim()))
const hasSource = computed(() => Boolean(
  props.item.sourceSessionCode?.trim() && props.item.sourceRoundCode?.trim(),
))
const canDisable = computed(() => status.value === 'ACTIVE' && hasReference.value)
const canRestore = computed(() => status.value === 'DISABLED' && hasReference.value)
const canCorrect = computed(() => status.value === 'ACTIVE' && hasReference.value)
const canPromote = computed(() => (
  scope.value === 'SESSION'
  && status.value === 'ACTIVE'
  && memoryType.value !== 'RAW'
  && hasReference.value
))
const canExclude = computed(() => (
  scope.value === 'LONG_TERM'
  && status.value === 'ACTIVE'
  && Boolean(props.currentSessionCode)
  && !props.item.excludedFromSession
  && hasReference.value
))
const canForget = computed(() => (
  !['PROCESSING', 'FORGOTTEN'].includes(status.value) && hasReference.value
))
const hasActions = computed(() => (
  canDisable.value
  || canRestore.value
  || canCorrect.value
  || canPromote.value
  || canExclude.value
  || canForget.value
))

const typeLabel = computed(() => {
  const labels: Record<string, string> = {
    RAW: '对话原文',
    SEMANTIC: '事实与偏好',
    EPISODIC: '经历与事件',
    PROCEDURAL: '工作方式',
  }
  return labels[memoryType.value] || '记忆'
})

const statusLabel = computed(() => {
  const labels: Record<string, string> = {
    ACTIVE: '使用中',
    DISABLED: '已停用',
    PROCESSING: '处理中',
    FAILED: '处理失败',
    FORGOTTEN: '已删除',
  }
  return props.item.excludedFromSession
    ? '本会话已排除'
    : labels[status.value] || '状态未知'
})

const statusType = computed<'success' | 'warning' | 'danger' | 'info'>(() => {
  if (status.value === 'ACTIVE' && !props.item.excludedFromSession) return 'success'
  if (status.value === 'FAILED') return 'danger'
  if (status.value === 'PROCESSING') return 'warning'
  return 'info'
})

const content = computed(() => {
  const normalized = props.item.content?.trim()
  if (normalized) return normalized
  return status.value === 'PROCESSING'
    ? '记忆正在整理，正文尚未生成。'
    : '暂无可展示的记忆内容。'
})

const sourceLabel = computed(() => {
  if (!props.item.sourceSessionCode) return ''
  const currentSource = props.item.sourceSessionCode === props.currentSessionCode
    ? '当前会话'
    : `会话 ${props.item.sourceSessionCode}`
  return props.item.sourceRoundCode
    ? `${currentSource} · 回合 ${props.item.sourceRoundCode}`
    : currentSource
})

const createdAtLabel = computed(() => {
  if (!props.item.createdAt) return ''
  const value = new Date(props.item.createdAt)
  if (Number.isNaN(value.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(value)
})
</script>

<template>
  <article class="conversation-memory-item" :aria-busy="loading">
    <header class="conversation-memory-item__header">
      <div class="conversation-memory-item__labels">
        <el-tag size="small" effect="plain">{{ typeLabel }}</el-tag>
        <el-tag size="small" effect="light" :type="statusType">{{ statusLabel }}</el-tag>
      </div>
      <span class="conversation-memory-item__scope">
        {{ scope === 'LONG_TERM' ? '长期记忆' : '当前会话' }}
      </span>
    </header>

    <p class="conversation-memory-item__content" :title="item.content || undefined">
      {{ content }}
    </p>

    <div v-if="sourceLabel || createdAtLabel" class="conversation-memory-item__meta">
      <button
        v-if="hasSource"
        type="button"
        :disabled="loading"
        title="查看记忆来源"
        @click="$emit('source', item)"
      >
        <el-icon aria-hidden="true"><FolderOpened /></el-icon>
        <span>{{ sourceLabel }}</span>
      </button>
      <span v-else-if="sourceLabel">
        <el-icon aria-hidden="true"><FolderOpened /></el-icon>
        {{ sourceLabel }}
      </span>
      <time v-if="createdAtLabel" :datetime="item.createdAt || undefined">
        <el-icon aria-hidden="true"><Clock /></el-icon>
        {{ createdAtLabel }}
      </time>
    </div>

    <footer v-if="hasActions" class="conversation-memory-item__actions" aria-label="记忆操作">
      <el-button v-if="canDisable" link size="small" :loading="loading" @click="$emit('disable', item)">
        <el-icon><Remove /></el-icon>
        停用
      </el-button>
      <el-button v-if="canRestore" link size="small" :loading="loading" @click="$emit('restore', item)">
        <el-icon><RefreshRight /></el-icon>
        恢复
      </el-button>
      <el-button v-if="canCorrect" link size="small" :disabled="loading" @click="$emit('correct', item)">
        <el-icon><EditPen /></el-icon>
        纠正
      </el-button>
      <el-button v-if="canPromote" link size="small" :disabled="loading" @click="$emit('promote', item)">
        <el-icon><Promotion /></el-icon>
        保存为长期记忆
      </el-button>
      <el-button v-if="canExclude" link size="small" :disabled="loading" @click="$emit('exclude', item)">
        <el-icon><Remove /></el-icon>
        本会话不使用
      </el-button>
      <el-button
        v-if="canForget"
        link
        type="danger"
        size="small"
        :disabled="loading"
        @click="$emit('forget', item)"
      >
        <el-icon><Delete /></el-icon>
        永久删除
      </el-button>
    </footer>
  </article>
</template>

<style scoped lang="scss">
.conversation-memory-item {
  display: grid;
  gap: var(--app-space-3);
  padding: var(--app-space-4);
  border: 1px solid var(--chat-panel-border);
  border-radius: var(--app-radius-lg);
  background: var(--chat-main-bg);
}

.conversation-memory-item[aria-busy='true'] {
  opacity: 0.72;
}

.conversation-memory-item__header,
.conversation-memory-item__labels,
.conversation-memory-item__meta,
.conversation-memory-item__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.conversation-memory-item__header {
  justify-content: space-between;
  gap: var(--app-space-2);
}

.conversation-memory-item__labels,
.conversation-memory-item__meta,
.conversation-memory-item__actions {
  gap: var(--app-space-2);
}

.conversation-memory-item__scope {
  color: var(--chat-text-muted);
  font-size: var(--app-font-size-caption);
}

.conversation-memory-item__content {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--chat-text-body);
  font-size: var(--app-font-size-body-lg);
  line-height: var(--app-line-height-loose);
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 5;
}

.conversation-memory-item__meta {
  color: var(--chat-text-muted);
  font-size: var(--app-font-size-caption);
  line-height: var(--app-line-height-body);
}

.conversation-memory-item__meta > button,
.conversation-memory-item__meta > span,
.conversation-memory-item__meta > time {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: var(--app-space-1);
}

.conversation-memory-item__meta > button {
  max-width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--app-accent);
  font: inherit;
  cursor: pointer;
}

.conversation-memory-item__meta > button > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-memory-item__meta > button:hover:not(:disabled),
.conversation-memory-item__meta > button:focus-visible {
  text-decoration: underline;
  text-underline-offset: var(--app-space-hairline);
  outline: none;
}

.conversation-memory-item__actions {
  padding-top: var(--app-space-2);
  border-top: 1px solid var(--chat-followup-border);
}

.conversation-memory-item__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
