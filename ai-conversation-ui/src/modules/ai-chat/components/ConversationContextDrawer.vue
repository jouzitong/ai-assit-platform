<script setup lang="ts">
import {
  Collection,
  Delete,
  InfoFilled,
  RefreshRight,
  WarningFilled,
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import { AppDrawer } from '../../../components'
import {
  CLEAR_LONG_TERM_MEMORY_ACTION_KEY,
  conversationMemoryActionKey,
} from '../composables/useConversationContext'
import type {
  ChatMemoryContextResponse,
  ChatMemoryItem,
  ChatMemoryListResponse,
} from '../types'
import ConversationMemoryItem from './ConversationMemoryItem.vue'

const props = withDefaults(defineProps<{
  modelValue: boolean
  sessionCode?: string
  context?: ChatMemoryContextResponse | null
  longTermList?: ChatMemoryListResponse | null
  loading?: boolean
  error?: string
  actionLoading?: Record<string, boolean>
}>(), {
  sessionCode: '',
  context: null,
  longTermList: null,
  loading: false,
  error: '',
  actionLoading: () => ({}),
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  refresh: []
  disable: [item: ChatMemoryItem]
  restore: [item: ChatMemoryItem]
  correct: [item: ChatMemoryItem]
  promote: [item: ChatMemoryItem]
  exclude: [item: ChatMemoryItem]
  forget: [item: ChatMemoryItem]
  source: [item: ChatMemoryItem]
  'clear-long-term': []
}>()

function normalizeItems(items?: ChatMemoryItem[] | null) {
  return Array.isArray(items) ? items : []
}

function deduplicate(items: ChatMemoryItem[]) {
  const indexed = new Map<string, ChatMemoryItem>()
  items.forEach((item, index) => {
    const key = item.memoryRef || `${item.scope || 'UNKNOWN'}-${item.sourceRoundCode || index}`
    indexed.set(key, item)
  })
  return [...indexed.values()]
}

const sessionMemories = computed(() => normalizeItems(props.context?.sessionMemories))
const longTermMemories = computed(() => {
  const contextItems = normalizeItems(props.context?.longTermMemories)
  return contextItems.length ? contextItems : normalizeItems(props.longTermList?.items)
})
const otherMemories = computed(() => deduplicate([
  ...normalizeItems(props.context?.processingMemories),
  ...normalizeItems(props.context?.disabledMemories),
  ...normalizeItems(props.longTermList?.processingItems),
]))
const hasAnyMemory = computed(() => (
  sessionMemories.value.length + longTermMemories.value.length + otherMemories.value.length > 0
))
const providerStatus = computed(() => props.context?.providerStatus?.trim().toUpperCase()
  || props.longTermList?.providerStatus?.trim().toUpperCase()
  || '')
const providerNotice = computed(() => {
  const notices: Record<string, string> = {
    UNAVAILABLE: '记忆服务暂不可用。聊天仍会使用近期原文，不会被阻断。',
    BINDING_UNAVAILABLE: '个人记忆空间正在准备，稍后刷新即可查看。',
    DISABLED: '记忆功能当前未开启，聊天历史仍会正常保存。',
    SECURITY_REJECTED: '记忆内容未通过归属校验，系统已停止展示。',
  }
  return notices[providerStatus.value] || ''
})
const clearLoading = computed(() => Boolean(
  props.actionLoading[CLEAR_LONG_TERM_MEMORY_ACTION_KEY],
))

function itemLoading(item: ChatMemoryItem) {
  return Boolean(props.actionLoading[conversationMemoryActionKey(item.memoryRef)])
}
</script>

<template>
  <AppDrawer
    :model-value="modelValue"
    title="上下文与记忆"
    description="查看当前回答使用的会话记忆，并管理跨会话长期记忆。"
    size="large"
    :show-footer="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="conversation-context-drawer">
      <div class="conversation-context-drawer__toolbar">
        <p>记忆正文与提炼结果由记忆平台保存，本系统仅负责安全引用和使用策略。</p>
        <el-button plain size="small" :loading="loading" @click="emit('refresh')">
          <el-icon><RefreshRight /></el-icon>
          刷新
        </el-button>
      </div>

      <div v-if="providerNotice" class="conversation-context-drawer__notice" role="status">
        <el-icon aria-hidden="true"><WarningFilled /></el-icon>
        <span>{{ providerNotice }}</span>
      </div>
      <div v-if="error" class="conversation-context-drawer__notice is-error" role="alert">
        <el-icon aria-hidden="true"><WarningFilled /></el-icon>
        <span>{{ error }}</span>
      </div>

      <el-skeleton v-if="loading && !context" :rows="5" animated />

      <template v-else>
        <section class="conversation-context-drawer__section" aria-labelledby="session-memory-title">
          <header>
            <div>
              <el-icon aria-hidden="true"><Collection /></el-icon>
              <h3 id="session-memory-title">当前会话</h3>
            </div>
            <span>{{ sessionMemories.length }} 条</span>
          </header>
          <p class="conversation-context-drawer__section-description">
            仅用于当前会话的事实、偏好和经历，不会自动跨会话生效。
          </p>
          <div v-if="sessionMemories.length" class="conversation-context-drawer__list">
            <ConversationMemoryItem
              v-for="item in sessionMemories"
              :key="item.memoryRef"
              :item="item"
              :current-session-code="sessionCode"
              :loading="itemLoading(item)"
              @disable="emit('disable', $event)"
              @restore="emit('restore', $event)"
              @correct="emit('correct', $event)"
              @promote="emit('promote', $event)"
              @exclude="emit('exclude', $event)"
              @forget="emit('forget', $event)"
              @source="emit('source', $event)"
            />
          </div>
          <p v-else class="conversation-context-drawer__empty">当前会话暂无可用记忆。</p>
        </section>

        <section class="conversation-context-drawer__section" aria-labelledby="long-term-memory-title">
          <header>
            <div>
              <el-icon aria-hidden="true"><Collection /></el-icon>
              <h3 id="long-term-memory-title">长期记忆</h3>
            </div>
            <span>{{ longTermMemories.length }} 条</span>
          </header>
          <p class="conversation-context-drawer__section-description">
            只有用户明确确认的长期事实、偏好和工作方式，才会在其他会话中使用。
          </p>
          <div v-if="longTermMemories.length" class="conversation-context-drawer__list">
            <ConversationMemoryItem
              v-for="item in longTermMemories"
              :key="item.memoryRef"
              :item="item"
              :current-session-code="sessionCode"
              :loading="itemLoading(item)"
              @disable="emit('disable', $event)"
              @restore="emit('restore', $event)"
              @correct="emit('correct', $event)"
              @promote="emit('promote', $event)"
              @exclude="emit('exclude', $event)"
              @forget="emit('forget', $event)"
              @source="emit('source', $event)"
            />
          </div>
          <p v-else class="conversation-context-drawer__empty">暂无已确认的长期记忆。</p>
        </section>

        <section v-if="otherMemories.length" class="conversation-context-drawer__section" aria-labelledby="other-memory-title">
          <header>
            <div>
              <el-icon aria-hidden="true"><RefreshRight /></el-icon>
              <h3 id="other-memory-title">处理中与已停用</h3>
            </div>
            <span>{{ otherMemories.length }} 条</span>
          </header>
          <div class="conversation-context-drawer__list">
            <ConversationMemoryItem
              v-for="item in otherMemories"
              :key="item.memoryRef || `${item.scope}-${item.sourceRoundCode}`"
              :item="item"
              :current-session-code="sessionCode"
              :loading="itemLoading(item)"
              @disable="emit('disable', $event)"
              @restore="emit('restore', $event)"
              @correct="emit('correct', $event)"
              @promote="emit('promote', $event)"
              @exclude="emit('exclude', $event)"
              @forget="emit('forget', $event)"
              @source="emit('source', $event)"
            />
          </div>
        </section>

        <div v-if="!hasAnyMemory && !providerNotice" class="conversation-context-drawer__empty-state">
          <el-icon aria-hidden="true"><InfoFilled /></el-icon>
          <strong>还没有形成可管理的记忆</strong>
          <p>继续聊天后，记忆平台会按配置异步整理可复用信息。</p>
        </div>

        <section class="conversation-context-drawer__privacy" aria-labelledby="memory-privacy-title">
          <div>
            <h3 id="memory-privacy-title">数据与隐私</h3>
            <p>清空长期记忆不会删除会话、消息、产物或活动记录，只会重建个人长期记忆空间。</p>
          </div>
          <el-button
            type="danger"
            plain
            :loading="clearLoading"
            :disabled="longTermMemories.length === 0 && !clearLoading"
            @click="emit('clear-long-term')"
          >
            <el-icon><Delete /></el-icon>
            清空长期记忆
          </el-button>
        </section>
      </template>
    </div>
  </AppDrawer>
</template>

<style scoped lang="scss">
.conversation-context-drawer {
  container-type: inline-size;
  display: grid;
  gap: var(--app-space-6);
}

.conversation-context-drawer__toolbar,
.conversation-context-drawer__notice,
.conversation-context-drawer__section > header,
.conversation-context-drawer__section > header > div,
.conversation-context-drawer__privacy {
  display: flex;
  align-items: center;
}

.conversation-context-drawer__toolbar,
.conversation-context-drawer__privacy {
  justify-content: space-between;
  gap: var(--app-space-4);
}

.conversation-context-drawer__toolbar > p,
.conversation-context-drawer__privacy p,
.conversation-context-drawer__section-description,
.conversation-context-drawer__empty,
.conversation-context-drawer__empty-state p {
  margin: 0;
  color: var(--chat-text-muted);
  font-size: var(--app-font-size-body);
  line-height: var(--app-line-height-loose);
}

.conversation-context-drawer__notice {
  align-items: flex-start;
  gap: var(--app-space-2);
  padding: var(--app-space-3);
  border: 1px solid var(--app-warning-border);
  border-radius: var(--app-radius-md);
  background: var(--app-warning-bg);
  color: var(--app-warning);
  font-size: var(--app-font-size-body);
  line-height: var(--app-line-height-body);
}

.conversation-context-drawer__notice.is-error {
  border-color: var(--app-danger);
  background: var(--chat-soft-bg);
  color: var(--app-danger);
}

.conversation-context-drawer__section {
  display: grid;
  gap: var(--app-space-3);
}

.conversation-context-drawer__section > header {
  justify-content: space-between;
  gap: var(--app-space-3);
  padding-bottom: var(--app-space-2);
  border-bottom: 1px solid var(--chat-followup-border);
}

.conversation-context-drawer__section > header > div {
  gap: var(--app-space-2);
  color: var(--chat-text-title);
}

.conversation-context-drawer__section h3,
.conversation-context-drawer__privacy h3 {
  margin: 0;
  color: var(--chat-text-title);
  font-size: var(--app-font-size-body-lg);
  font-weight: 700;
  line-height: var(--app-line-height-tight);
}

.conversation-context-drawer__section > header > span {
  color: var(--chat-text-muted);
  font-size: var(--app-font-size-caption);
}

.conversation-context-drawer__list {
  display: grid;
  gap: var(--app-space-3);
}

.conversation-context-drawer__empty {
  padding: var(--app-space-4);
  border: 1px dashed var(--chat-panel-border);
  border-radius: var(--app-radius-md);
  text-align: center;
}

.conversation-context-drawer__empty-state {
  display: grid;
  justify-items: center;
  gap: var(--app-space-2);
  padding: var(--app-space-6);
  border: 1px dashed var(--chat-panel-border);
  border-radius: var(--app-radius-lg);
  color: var(--chat-text-muted);
  text-align: center;
}

.conversation-context-drawer__empty-state > .el-icon {
  font-size: var(--app-font-size-title-lg);
}

.conversation-context-drawer__empty-state > strong {
  color: var(--chat-text-secondary);
  font-size: var(--app-font-size-body-lg);
}

.conversation-context-drawer__privacy {
  align-items: flex-start;
  padding: var(--app-space-4);
  border: 1px solid var(--chat-panel-border);
  border-radius: var(--app-radius-lg);
  background: var(--chat-soft-bg);
}

.conversation-context-drawer__privacy > div {
  display: grid;
  gap: var(--app-space-2);
}

@container (max-width: 560px) {
  .conversation-context-drawer__toolbar,
  .conversation-context-drawer__privacy {
    align-items: stretch;
    flex-direction: column;
  }

  .conversation-context-drawer__toolbar .el-button,
  .conversation-context-drawer__privacy .el-button {
    align-self: flex-start;
  }
}
</style>
