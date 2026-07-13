<script setup lang="ts">
import { RefreshRight, WarningFilled } from '@element-plus/icons-vue'
import { computed } from 'vue'
import type { ChatUiError } from '../types'

const props = withDefaults(defineProps<{
  error: ChatUiError
  retryAvailable?: boolean
  retryDisabled?: boolean
}>(), {
  retryAvailable: true,
  retryDisabled: false,
})

defineEmits<{
  retry: []
}>()

const hasTechnicalDetail = computed(() => Boolean(
  props.error.detail || props.error.code || props.error.traceId,
))
</script>

<template>
  <section class="chat-message-error" role="alert" aria-label="本轮处理失败">
    <el-icon class="chat-message-error__icon" aria-hidden="true"><WarningFilled /></el-icon>
    <div class="chat-message-error__body">
      <strong>本轮处理失败</strong>
      <p>{{ error.userMessage }}</p>

      <div class="chat-message-error__actions">
        <button
          v-if="error.retryable !== false && retryAvailable"
          type="button"
          :disabled="retryDisabled"
          @click="$emit('retry')"
        >
          <el-icon aria-hidden="true"><RefreshRight /></el-icon>
          <span>重试本轮</span>
        </button>

        <details v-if="hasTechnicalDetail" class="chat-message-error__details">
          <summary>查看异常详情</summary>
          <dl>
            <template v-if="error.detail">
              <dt>详情</dt>
              <dd>{{ error.detail }}</dd>
            </template>
            <template v-if="error.code">
              <dt>错误码</dt>
              <dd>{{ error.code }}</dd>
            </template>
            <template v-if="error.traceId">
              <dt>追踪标识</dt>
              <dd>{{ error.traceId }}</dd>
            </template>
          </dl>
        </details>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.chat-message-error {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: min(100%, 720px);
  margin-top: 12px;
  padding: 12px 14px;
  border: 1px solid var(--chat-panel-border);
  border-left: 3px solid var(--app-danger);
  border-radius: 10px;
  background: var(--chat-soft-bg);
  color: var(--chat-text-body);
}

.chat-message-error__icon {
  flex: 0 0 auto;
  margin-top: 2px;
  color: var(--app-danger);
  font-size: 16px;
}

.chat-message-error__body {
  min-width: 0;
  flex: 1;
}

.chat-message-error__body > strong {
  display: block;
  color: var(--chat-text-title);
  font-size: 13px;
  line-height: 1.5;
}

.chat-message-error__body > p {
  margin: 3px 0 0;
  color: var(--chat-text-body);
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.chat-message-error__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 9px;
}

.chat-message-error__actions > button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 4px 10px;
  border: 1px solid var(--chat-panel-border);
  border-radius: 999px;
  background: var(--chat-main-bg);
  color: var(--chat-text-secondary);
  font: inherit;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;
}

.chat-message-error__actions > button:hover:not(:disabled),
.chat-message-error__actions > button:focus-visible {
  border-color: var(--chat-primary-soft);
  background: var(--chat-primary-tint);
  color: var(--chat-primary);
  outline: none;
}

.chat-message-error__actions > button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.chat-message-error__details {
  min-width: 0;
  color: var(--chat-text-muted);
  font-size: 12px;
}

.chat-message-error__details summary {
  min-height: 30px;
  padding: 6px 2px;
  cursor: pointer;
}

.chat-message-error__details summary:focus-visible {
  border-radius: 4px;
  outline: 2px solid var(--chat-primary-soft);
  outline-offset: 2px;
}

.chat-message-error__details dl {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 4px 10px;
  width: min(100%, 620px);
  margin: 6px 0 0;
  padding: 9px 10px;
  border: 1px solid var(--chat-panel-border);
  border-radius: 8px;
  background: var(--chat-main-bg);
}

.chat-message-error__details dt {
  color: var(--chat-text-muted);
  font-weight: 600;
}

.chat-message-error__details dd {
  min-width: 0;
  margin: 0;
  color: var(--chat-text-secondary);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

@media (max-width: 560px) {
  .chat-message-error {
    padding: 10px 11px;
  }

  .chat-message-error__details {
    flex-basis: 100%;
  }

  .chat-message-error__details dl {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-message-error__actions > button {
    transition: none;
  }
}
</style>
