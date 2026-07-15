<script setup lang="ts">
import {
  ChatDotRound,
  CloseBold,
  Cpu,
  Delete,
  MagicStick,
  Promotion,
  RefreshRight,
} from '@element-plus/icons-vue'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { activeAgentPageCapability } from '../services/pageCapabilityRegistry'
import { useAiAssistantStore } from '../store/assistant'

const route = useRoute()
const router = useRouter()
const assistant = useAiAssistantStore()
const messagesRef = ref<HTMLElement | null>(null)

const contextTitle = computed(() => activeAgentPageCapability.value?.title || String(route.meta.title || document.title || route.path))
const canSend = computed(() => Boolean(
  assistant.state.draft.trim()
  && assistant.selectedModel.value
  && !assistant.state.running,
))
const emptyDescription = computed(() => {
  if (assistant.state.modelLoadError) return `模型加载失败：${assistant.state.modelLoadError}`
  if (assistant.state.modelsLoaded && !assistant.state.models.length) return '暂无可用于浏览器 Agent 的已启用模型'
  return '选择本地模型后，可以分析当前页面，或按你的要求回填当前表单。'
})

const quickPrompts = [
  '分析当前页面的数据结构、筛选条件和异常点',
  '总结当前页面最值得关注的信息',
  '根据我的要求填写当前打开的表单，但不要提交',
]

function scrollToLatest() {
  void nextTick(() => {
    if (!messagesRef.value) return
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  void assistant.sendMessage()
}

function useQuickPrompt(prompt: string) {
  assistant.state.draft = prompt
}

async function openModelSettings() {
  assistant.closeAssistant()
  await router.push('/settings/system/ai-platform/model')
}

watch(
  () => [assistant.state.messages.length, assistant.state.activity, assistant.state.messages.at(-1)?.content],
  scrollToLatest,
)
onBeforeUnmount(() => {
  assistant.stopRun()
  assistant.closeAssistant()
})
</script>

<template>
  <Teleport to="body">
    <div class="assistant-host" data-ai-assistant-root>
      <button
        v-if="!assistant.state.open"
        class="assistant-trigger"
        type="button"
        aria-label="打开 AI 页面助手"
        :aria-expanded="assistant.state.open"
        title="AI 页面助手"
        @click="assistant.openAssistant"
      >
        <el-icon><Cpu /></el-icon>
        <span class="assistant-trigger__status" aria-hidden="true" />
      </button>

      <Transition name="assistant-flyout">
        <aside
          v-if="assistant.state.open"
          class="assistant-flyout"
          role="complementary"
          aria-label="AI 页面助手"
          data-ai-assistant-panel
        >
          <section class="assistant-panel">
      <header class="assistant-panel__header">
        <div class="assistant-panel__identity">
          <span class="assistant-panel__avatar"><el-icon><Cpu /></el-icon></span>
          <div>
            <strong>AI 页面助手</strong>
            <span><el-icon><ChatDotRound /></el-icon>{{ contextTitle }}</span>
          </div>
        </div>
        <div class="assistant-panel__header-actions">
          <el-button
            text
            circle
            :icon="Delete"
            :disabled="assistant.state.running || !assistant.state.messages.length"
            aria-label="清空对话"
            title="清空对话"
            @click="assistant.clearMessages"
          />
          <el-button
            text
            circle
            :icon="CloseBold"
            aria-label="关闭 AI 页面助手"
            title="关闭"
            @click="assistant.closeAssistant"
          />
        </div>
      </header>

      <div class="assistant-panel__model-row">
        <el-select
          :model-value="assistant.state.selectedModelCode"
          :loading="assistant.state.modelsLoading"
          :disabled="assistant.state.running"
          filterable
          placeholder="选择本地模型"
          aria-label="选择本地模型"
          @update:model-value="assistant.setSelectedModel"
        >
          <el-option
            v-for="model in assistant.state.models"
            :key="model.id"
            :label="model.modelName || model.modelCode"
            :value="model.modelCode"
          >
            <div class="assistant-model-option">
              <strong>{{ model.modelName || model.modelCode }}</strong>
              <span>{{ model.apiModel }} · {{ model.baseUrl || 'OpenAI 默认地址' }}</span>
            </div>
          </el-option>
        </el-select>
        <el-button
          :icon="RefreshRight"
          :loading="assistant.state.modelsLoading"
          :disabled="assistant.state.running"
          aria-label="刷新模型列表"
          title="刷新模型列表"
          @click="assistant.loadModels(true)"
        />
      </div>

      <main ref="messagesRef" class="assistant-panel__messages" aria-live="polite">
        <section v-if="!assistant.state.messages.length" class="assistant-empty">
          <span class="assistant-empty__icon"><el-icon><MagicStick /></el-icon></span>
          <div>
            <strong>分析页面，也能填写草稿</strong>
            <p>{{ emptyDescription }}</p>
          </div>
          <el-button
            v-if="assistant.state.modelsLoaded && !assistant.state.models.length"
            type="primary"
            plain
            @click="openModelSettings"
          >
            前往模型管理
          </el-button>
          <div v-else class="assistant-empty__prompts">
            <button v-for="prompt in quickPrompts" :key="prompt" type="button" @click="useQuickPrompt(prompt)">
              {{ prompt }}
            </button>
          </div>
        </section>

        <article
          v-for="message in assistant.state.messages"
          :key="message.id"
          :class="['assistant-message', `is-${message.role}`, { 'is-error': message.status === 'error' }]"
        >
          <span v-if="message.role === 'assistant'" class="assistant-message__avatar"><el-icon><Cpu /></el-icon></span>
          <div class="assistant-message__bubble">
            <span v-if="message.status === 'pending' && !message.content" class="assistant-message__pending">
              <i /><i /><i />
            </span>
            <p v-else>{{ message.content }}</p>
          </div>
        </article>

        <div v-if="assistant.state.running && assistant.state.activity" class="assistant-activity">
          <span class="assistant-activity__dot" />
          {{ assistant.state.activity }}
        </div>
      </main>

      <footer class="assistant-composer">
        <el-alert
          v-if="assistant.state.runError"
          :title="assistant.state.runError"
          type="error"
          :closable="false"
          show-icon
        />
        <div class="assistant-composer__box">
          <el-input
            v-model="assistant.state.draft"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            resize="none"
            :disabled="assistant.state.running"
            placeholder="例如：分析当前关系图，找出可能遗漏的关联"
            aria-label="输入页面分析或表单填写要求"
            @keydown="handleComposerKeydown"
          />
          <el-button
            v-if="assistant.state.running"
            class="assistant-composer__send"
            circle
            :icon="CloseBold"
            aria-label="停止生成"
            title="停止生成"
            @click="assistant.stopRun"
          />
          <el-button
            v-else
            class="assistant-composer__send"
            type="primary"
            circle
            :icon="Promotion"
            :disabled="!canSend"
            aria-label="发送消息"
            title="发送"
            @click="assistant.sendMessage()"
          />
        </div>
        <span>Enter 发送，Shift + Enter 换行。AI 只回填草稿，不会主动点击保存或提交。</span>
      </footer>
          </section>
        </aside>
      </Transition>
    </div>
  </Teleport>
</template>

<style scoped>
.assistant-host {
  position: fixed;
  inset: 0;
  z-index: 1900;
  pointer-events: none;
}

.assistant-trigger {
  position: absolute;
  right: var(--app-space-6);
  bottom: var(--app-space-6);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  padding: 0;
  border: 1px solid var(--app-accent-border);
  border-radius: var(--app-radius-round);
  background: var(--app-accent);
  color: var(--system-primary-button-text);
  box-shadow: var(--app-accent-shadow);
  cursor: pointer;
  pointer-events: auto;
  transition: background-color 0.2s ease, box-shadow 0.2s ease;
}

.assistant-trigger:hover {
  background: var(--app-accent);
  box-shadow: var(--app-shadow-md);
}

.assistant-trigger:focus-visible {
  outline: 3px solid var(--app-accent-border);
  outline-offset: var(--app-space-hairline);
}

.assistant-trigger .el-icon {
  font-size: var(--app-font-size-title-lg);
}

.assistant-trigger__status {
  position: absolute;
  right: var(--app-space-tight);
  bottom: var(--app-space-tight);
  width: var(--app-space-2);
  height: var(--app-space-2);
  border: 2px solid var(--app-accent);
  border-radius: var(--app-radius-round);
  background: var(--app-success);
}

.assistant-flyout {
  position: absolute;
  top: var(--app-space-3);
  right: var(--app-space-3);
  bottom: var(--app-space-3);
  width: min(440px, calc(100% - var(--app-space-6)));
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface-solid);
  box-shadow: var(--app-shadow-sm);
  pointer-events: auto;
}

.assistant-panel {
  container-type: inline-size;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  background: var(--app-surface-solid);
  color: var(--app-text);
}

.assistant-panel__header,
.assistant-panel__model-row,
.assistant-composer {
  padding-inline: var(--app-space-4);
}

.assistant-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-3);
  padding-block: var(--app-space-4) var(--app-space-3);
  border-bottom: 1px solid var(--app-border-subtle);
}

.assistant-panel__identity,
.assistant-panel__header-actions,
.assistant-panel__identity > div,
.assistant-panel__identity span,
.assistant-panel__model-row,
.assistant-activity,
.assistant-message,
.assistant-empty__prompts,
.assistant-composer__box {
  display: flex;
}

.assistant-panel__identity {
  align-items: center;
  min-width: 0;
  gap: var(--app-space-3);
}

.assistant-panel__identity > div {
  flex-direction: column;
  min-width: 0;
  gap: var(--app-space-1);
}

.assistant-panel__identity strong {
  color: var(--app-title);
  font-size: var(--app-font-size-title-sm);
}

.assistant-panel__identity span:not(.assistant-panel__avatar) {
  align-items: center;
  gap: var(--app-space-1);
  overflow: hidden;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-panel__avatar,
.assistant-empty__icon,
.assistant-message__avatar {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: var(--app-radius-control);
  background: var(--app-accent-bg);
  color: var(--app-accent);
}

.assistant-panel__avatar {
  width: var(--app-control-height-lg);
  height: var(--app-control-height-lg);
  font-size: var(--app-font-size-title-md);
}

.assistant-panel__header-actions {
  align-items: center;
}

.assistant-panel__model-row {
  align-items: center;
  gap: var(--app-space-2);
  padding-block: var(--app-space-3);
  border-bottom: 1px solid var(--app-border-subtle);
  background: var(--app-surface-muted);
}

.assistant-panel__model-row .el-select {
  flex: 1;
  min-width: 0;
}

.assistant-model-option {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: var(--app-line-height-body);
}

.assistant-model-option strong,
.assistant-model-option span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assistant-model-option span {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.assistant-panel__messages {
  min-width: 0;
  min-height: 0;
  padding: var(--app-space-4);
  overflow-y: auto;
  overscroll-behavior: contain;
  background: var(--app-surface);
}

.assistant-empty {
  display: grid;
  justify-items: start;
  gap: var(--app-space-4);
  padding: var(--app-space-6) var(--app-space-2);
}

.assistant-empty__icon {
  width: 48px;
  height: 48px;
  font-size: var(--app-font-size-title-lg);
}

.assistant-empty strong {
  color: var(--app-title);
  font-size: var(--app-font-size-subtitle);
}

.assistant-empty p {
  margin: var(--app-space-2) 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-body);
  line-height: var(--app-line-height-loose);
}

.assistant-empty__prompts {
  flex-direction: column;
  align-items: stretch;
  width: 100%;
  gap: var(--app-space-2);
}

.assistant-empty__prompts button {
  padding: var(--app-space-3);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-solid);
  color: var(--app-text);
  text-align: left;
  line-height: var(--app-line-height-body);
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, color 0.2s ease;
}

.assistant-empty__prompts button:hover,
.assistant-empty__prompts button:focus-visible {
  border-color: var(--app-accent-border);
  background: var(--app-accent-bg);
  color: var(--app-accent);
  outline: none;
}

.assistant-message {
  align-items: flex-start;
  gap: var(--app-space-2);
  margin-bottom: var(--app-space-4);
}

.assistant-message.is-user {
  justify-content: flex-end;
}

.assistant-message__avatar {
  width: var(--app-control-height-sm);
  height: var(--app-control-height-sm);
}

.assistant-message__bubble {
  max-width: 84%;
  padding: var(--app-space-3) var(--app-space-4);
  border: 1px solid var(--app-border-subtle);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-solid);
  box-shadow: var(--app-shadow-sm);
}

.assistant-message.is-user .assistant-message__bubble {
  border-color: var(--app-accent-border);
  background: var(--app-accent);
  color: var(--system-primary-button-text);
  box-shadow: var(--app-accent-shadow);
}

.assistant-message.is-error .assistant-message__bubble {
  border-color: var(--app-danger);
  color: var(--app-danger);
}

.assistant-message__bubble p {
  margin: 0;
  font-size: var(--app-font-size-body-lg);
  line-height: var(--app-line-height-loose);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.assistant-message__pending {
  display: inline-flex;
  align-items: center;
  min-height: var(--app-space-4);
  gap: var(--app-space-1);
}

.assistant-message__pending i,
.assistant-activity__dot {
  width: var(--app-space-tight);
  height: var(--app-space-tight);
  border-radius: var(--app-radius-round);
  background: var(--app-accent);
  animation: assistant-pulse 1.2s ease-in-out infinite;
}

.assistant-message__pending i:nth-child(2) { animation-delay: 0.15s; }
.assistant-message__pending i:nth-child(3) { animation-delay: 0.3s; }

.assistant-activity {
  align-items: center;
  gap: var(--app-space-2);
  margin: 0 0 var(--app-space-3) var(--app-control-height-lg);
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.assistant-composer {
  display: grid;
  gap: var(--app-space-2);
  padding-block: var(--app-space-3) var(--app-space-4);
  border-top: 1px solid var(--app-border-subtle);
  background: var(--app-surface-solid);
}

.assistant-composer__box {
  align-items: flex-end;
  gap: var(--app-space-2);
  padding: var(--app-space-2);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-muted);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.assistant-composer__box:focus-within {
  border-color: var(--app-accent-border);
  box-shadow: 0 0 0 var(--app-space-micro) var(--app-accent-bg);
}

.assistant-composer__box :deep(.el-textarea__inner) {
  min-height: var(--app-control-height-lg) !important;
  padding: var(--app-space-2);
  border: 0;
  background: transparent;
  box-shadow: none;
}

.assistant-composer__send {
  flex: 0 0 auto;
}

.assistant-composer > span {
  color: var(--app-text-faint);
  font-size: var(--app-font-size-caption);
  line-height: var(--app-line-height-body);
}

@container (max-width: 380px) {
  .assistant-panel__header,
  .assistant-panel__model-row,
  .assistant-composer {
    padding-inline: var(--app-space-3);
  }

  .assistant-panel__messages {
    padding-inline: var(--app-space-3);
  }

  .assistant-message__bubble {
    max-width: 90%;
  }
}

@keyframes assistant-pulse {
  0%, 100% { opacity: 0.35; }
  50% { opacity: 1; }
}

.assistant-flyout-enter-active,
.assistant-flyout-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.assistant-flyout-enter-from,
.assistant-flyout-leave-to {
  opacity: 0;
  transform: translateX(calc(100% + var(--app-space-6)));
}

@media (prefers-reduced-motion: reduce) {
  .assistant-trigger,
  .assistant-flyout-enter-active,
  .assistant-flyout-leave-active,
  .assistant-empty__prompts button,
  .assistant-composer__box {
    transition: none;
  }

  .assistant-message__pending i,
  .assistant-activity__dot {
    animation: none;
  }
}
</style>
