<script setup lang="ts">
import {
  ChatDotRound,
  CloseBold,
  Cpu,
  Delete,
  FullScreen,
  MagicStick,
  Promotion,
  Rank,
  RefreshRight,
  ScaleToOriginal,
} from '@element-plus/icons-vue'
import { useZIndex, type InputInstance } from 'element-plus'
import { focusableStack, type FocusLayer } from 'element-plus/es/components/focus-trap/src/utils'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { renderMarkdown } from '../../ai-chat/utils/markdown'
import { useAssistantFloatingLayout } from '../composables/useAssistantFloatingLayout'
import { activeAgentPageCapability } from '../services/pageCapabilityRegistry'
import { useAiAssistantStore } from '../store/assistant'
import type { AiAssistantMessage } from '../types'
import AgentActivityTimeline from './AgentActivityTimeline.vue'

const ASSISTANT_PANEL_ID = 'ai-page-assistant-panel'

const route = useRoute()
const router = useRouter()
const assistant = useAiAssistantStore()
const { currentZIndex } = useZIndex()
const assistantHostRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLButtonElement | null>(null)
const composerRef = ref<InputInstance | null>(null)
const messagesRef = ref<HTMLElement | null>(null)
const lastExternalFocusRef = ref<HTMLElement | null>(null)
// Pause the active Element Plus dialog trap only while focus is inside the assistant.
const assistantFocusLayer: FocusLayer = {
  paused: false,
  pause() { this.paused = true },
  resume() { this.paused = false },
}
let assistantFocusLayerActive = false
const {
  isCompact,
  isLauncherDragging,
  isPanelInteracting,
  launcherStyle,
  panelStyle,
  panelSizeLabel,
  panelMaximized,
  panelAnchorSide,
  anchorPanelToLauncher,
  startLauncherDrag,
  startPanelDrag,
  startPanelResize,
  handlePointerMove,
  finishPointerInteraction,
  consumeLauncherClick,
  moveLauncherWithKeyboard,
  movePanelWithKeyboard,
  resizePanelWithKeyboard,
  togglePanelMaximize,
} = useAssistantFloatingLayout(assistantHostRef, triggerRef)

const assistantZIndex = computed(() => currentZIndex.value + 1)
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
  const messages = messagesRef.value
  const shouldStickToBottom = !messages
    || messages.scrollHeight - messages.scrollTop - messages.clientHeight < 64
  void nextTick(() => {
    if (!messagesRef.value || !shouldStickToBottom) return
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
  void nextTick(() => composerRef.value?.focus())
}

function rememberExternalFocus() {
  const target = document.activeElement
  if (target instanceof HTMLElement && !assistantHostRef.value?.contains(target)) {
    lastExternalFocusRef.value = target
  }
}

function activateAssistantFocusLayer() {
  if (assistantFocusLayerActive) return
  focusableStack.push(assistantFocusLayer)
  assistantFocusLayerActive = true
}

function deactivateAssistantFocusLayer() {
  if (!assistantFocusLayerActive) return
  focusableStack.remove(assistantFocusLayer)
  assistantFocusLayerActive = false
}

function handleTriggerPointerDown(event: PointerEvent) {
  rememberExternalFocus()
  startLauncherDrag(event)
}

function handleTriggerClick(event: MouseEvent) {
  if (consumeLauncherClick(event)) {
    if (!assistant.state.open) {
      deactivateAssistantFocusLayer()
      lastExternalFocusRef.value?.focus({ preventScroll: true })
    }
    return
  }
  if (assistant.state.open) {
    void closeAssistant(true)
    return
  }
  void openAssistant()
}

function assistantResultLabel(status: AiAssistantMessage['status']) {
  if (status === 'complete') return '最终总结'
  if (status === 'pending') return '正在生成总结'
  return '处理结果'
}

async function openAssistant() {
  const shouldAnchor = !assistant.state.open
  activateAssistantFocusLayer()
  if (shouldAnchor) anchorPanelToLauncher()
  assistant.openAssistant()
  await nextTick()
  if (shouldAnchor) anchorPanelToLauncher()
  composerRef.value?.focus()
}

async function closeAssistant(focusTrigger = false) {
  assistant.closeAssistant()
  deactivateAssistantFocusLayer()
  await nextTick()
  const focusTarget = focusTrigger
    ? triggerRef.value
    : lastExternalFocusRef.value?.isConnected
    ? lastExternalFocusRef.value
    : triggerRef.value
  focusTarget?.focus({ preventScroll: true })
}

async function openModelSettings() {
  await closeAssistant()
  await router.push('/settings/system/ai-platform/model')
}

function handleGlobalAssistantShortcut(event: KeyboardEvent) {
  if (
    event.code !== 'KeyA'
    || !event.altKey
    || !event.shiftKey
    || event.ctrlKey
    || event.metaKey
  ) return

  event.preventDefault()
  event.stopPropagation()
  if (assistantHostRef.value?.contains(document.activeElement)) {
    deactivateAssistantFocusLayer()
    lastExternalFocusRef.value?.focus({ preventScroll: true })
    return
  }
  rememberExternalFocus()
  void openAssistant()
}

watch(
  () => {
    const lastMessage = assistant.state.messages.at(-1)
    const lastActivity = lastMessage?.activities?.at(-1)
    return [
      assistant.state.messages.length,
      lastMessage?.content,
      lastMessage?.activities?.length,
      lastActivity?.status,
      lastActivity?.title,
    ]
  },
  scrollToLatest,
)
function handleDocumentFocusIn(event: FocusEvent) {
  const target = event.target
  if (!(target instanceof HTMLElement)) return
  if (assistantHostRef.value?.contains(target)) {
    activateAssistantFocusLayer()
    return
  }
  deactivateAssistantFocusLayer()
  lastExternalFocusRef.value = target
}

onMounted(() => {
  document.addEventListener('focusin', handleDocumentFocusIn)
  document.addEventListener('keydown', handleGlobalAssistantShortcut, true)
})
onBeforeUnmount(() => {
  document.removeEventListener('focusin', handleDocumentFocusIn)
  document.removeEventListener('keydown', handleGlobalAssistantShortcut, true)
  deactivateAssistantFocusLayer()
  assistant.stopRun()
  assistant.closeAssistant()
})
</script>

<template>
  <Teleport to="body">
    <div
      ref="assistantHostRef"
      class="assistant-host"
      data-ai-assistant-root
      :style="{ zIndex: assistantZIndex }"
    >
      <button
        ref="triggerRef"
        class="assistant-trigger"
        :class="{
          'is-dragging': isLauncherDragging,
          'is-open': assistant.state.open,
        }"
        :style="launcherStyle"
        type="button"
        :aria-label="assistant.state.open ? '关闭或拖动 AI 页面助手' : '打开或拖动 AI 页面助手'"
        aria-keyshortcuts="Alt+Shift+A"
        :aria-controls="ASSISTANT_PANEL_ID"
        :aria-expanded="assistant.state.open"
        :title="assistant.state.open
          ? '关闭 AI 页面助手（按钮可拖动）'
          : '打开 AI 页面助手（按钮可拖动；Alt + Shift + A 快速切换）'"
        @pointerdown="handleTriggerPointerDown"
        @pointermove="handlePointerMove"
        @pointerup="finishPointerInteraction"
        @pointercancel="finishPointerInteraction"
        @lostpointercapture="finishPointerInteraction"
        @keydown="moveLauncherWithKeyboard"
        @click="handleTriggerClick"
      >
        <el-icon><Cpu /></el-icon>
        <span class="assistant-trigger__status" aria-hidden="true" />
      </button>

      <Transition name="assistant-flyout">
        <aside
          :id="ASSISTANT_PANEL_ID"
          v-if="assistant.state.open"
          class="assistant-flyout"
          :class="{
            'is-compact': isCompact,
            'is-interacting': isPanelInteracting,
            'is-anchored-above': panelAnchorSide === 'above',
            'is-anchored-below': panelAnchorSide === 'below',
          }"
          :style="panelStyle"
          role="complementary"
          aria-label="AI 页面助手"
          aria-keyshortcuts="Alt+Shift+A"
          data-ai-assistant-panel
          @keydown.esc.stop="closeAssistant()"
        >
          <section class="assistant-panel">
      <header class="assistant-panel__header">
        <div class="assistant-panel__identity">
          <button
            class="assistant-panel__drag-handle"
            type="button"
            :disabled="panelMaximized"
            aria-label="移动 AI 助手窗口"
            title="拖动窗口；方向键可微调位置"
            @pointerdown="startPanelDrag"
            @pointermove="handlePointerMove"
            @pointerup="finishPointerInteraction"
            @pointercancel="finishPointerInteraction"
            @lostpointercapture="finishPointerInteraction"
            @keydown="movePanelWithKeyboard"
          >
            <el-icon><Rank /></el-icon>
          </button>
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
            :icon="panelMaximized ? ScaleToOriginal : FullScreen"
            :aria-label="panelMaximized ? '还原 AI 助手窗口' : '最大化 AI 助手窗口'"
            :title="panelMaximized ? '还原窗口' : '最大化窗口'"
            @click="togglePanelMaximize"
          />
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
            @click="closeAssistant()"
          />
        </div>
      </header>

      <div class="assistant-panel__model-row">
        <el-select
          :model-value="assistant.state.selectedModelCode"
          :loading="assistant.state.modelsLoading"
          :disabled="assistant.state.running"
          filterable
          :append-to="assistantHostRef || 'body'"
          :popper-style="{ pointerEvents: 'auto' }"
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

      <main
        ref="messagesRef"
        class="assistant-panel__messages"
        role="log"
        aria-live="polite"
        aria-relevant="additions"
        aria-label="AI 助手对话记录"
      >
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
          <div class="assistant-message__content">
            <AgentActivityTimeline
              v-if="message.role === 'assistant' && message.activities?.length"
              :activities="message.activities"
              :message-status="message.status"
            />
            <div
              v-if="message.content || message.role === 'user' || !message.activities?.length"
              class="assistant-message__bubble"
            >
              <span
                v-if="message.role === 'assistant'"
                class="assistant-message__result-label"
              >{{ assistantResultLabel(message.status) }}</span>
              <span v-if="message.status === 'pending' && !message.content" class="assistant-message__pending">
                <i /><i /><i />
              </span>
              <div
                v-else-if="message.role === 'assistant'"
                class="assistant-message__markdown"
                v-html="renderMarkdown(message.content)"
              />
              <p v-else>{{ message.content }}</p>
            </div>
          </div>
        </article>
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
            ref="composerRef"
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
        <span>Enter 发送；Alt + Shift + A 可在弹窗与助手间切换。AI 只回填草稿，不会主动提交。</span>
      </footer>
          </section>
          <button
            v-if="!panelMaximized"
            class="assistant-panel__resize-handle"
            type="button"
            :aria-label="`调整 AI 助手窗口大小，当前 ${panelSizeLabel}`"
            :title="`拖动调整窗口大小（${panelSizeLabel}）`"
            @pointerdown="startPanelResize"
            @pointermove="handlePointerMove"
            @pointerup="finishPointerInteraction"
            @pointercancel="finishPointerInteraction"
            @lostpointercapture="finishPointerInteraction"
            @keydown="resizePanelWithKeyboard"
          />
        </aside>
      </Transition>
    </div>
  </Teleport>
</template>

<style scoped>
.assistant-host {
  position: fixed;
  inset: 0;
  pointer-events: none;
}

.assistant-trigger {
  position: absolute;
  z-index: 2;
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
  touch-action: none;
  user-select: none;
  transition: background-color 0.2s ease, box-shadow 0.2s ease;
}

.assistant-trigger.is-dragging {
  cursor: grabbing;
}

.assistant-trigger.is-open {
  box-shadow: var(--app-shadow-md);
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
  z-index: 1;
  max-width: calc(100% - var(--app-space-6));
  max-height: calc(100% - var(--app-space-6));
  overflow: visible;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface-solid);
  box-shadow: var(--app-shadow-sm);
  pointer-events: auto;
  transform-origin: center;
  transition: box-shadow 0.2s ease;
}

.assistant-flyout::after {
  position: absolute;
  left: var(--assistant-anchor-x);
  z-index: 0;
  width: var(--app-space-3);
  height: var(--app-space-3);
  border-color: var(--app-border);
  background: var(--app-surface-solid);
  content: '';
  pointer-events: none;
  transform: translateX(-50%) rotate(45deg);
}

.assistant-flyout.is-anchored-above {
  transform-origin: var(--assistant-anchor-x) 100%;
}

.assistant-flyout.is-anchored-above::after {
  bottom: calc(var(--app-space-tight) * -1);
  border-right: 1px solid var(--app-border);
  border-bottom: 1px solid var(--app-border);
}

.assistant-flyout.is-anchored-below {
  transform-origin: var(--assistant-anchor-x) 0;
}

.assistant-flyout.is-anchored-below::after {
  top: calc(var(--app-space-tight) * -1);
  border-top: 1px solid var(--app-border);
  border-left: 1px solid var(--app-border);
}

.assistant-flyout.is-interacting {
  box-shadow: var(--app-shadow-md);
  user-select: none;
}

.assistant-flyout.is-compact .assistant-panel__identity > div > span {
  display: none;
}

.assistant-panel {
  position: relative;
  z-index: 1;
  container-type: inline-size;
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border-radius: inherit;
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
.assistant-message,
.assistant-empty__prompts,
.assistant-composer__box {
  display: flex;
}

.assistant-panel__identity {
  align-items: center;
  min-width: 0;
  gap: var(--app-space-2);
}

.assistant-panel__drag-handle {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: var(--app-control-height-sm);
  height: var(--app-control-height-sm);
  padding: 0;
  border: 0;
  border-radius: var(--app-radius-md);
  background: transparent;
  color: var(--app-text-muted);
  cursor: grab;
  touch-action: none;
}

.assistant-panel__drag-handle:hover:not(:disabled),
.assistant-panel__drag-handle:focus-visible {
  background: var(--app-accent-bg);
  color: var(--app-accent);
  outline: none;
}

.assistant-panel__drag-handle:active:not(:disabled) {
  cursor: grabbing;
}

.assistant-panel__drag-handle:disabled {
  color: var(--app-text-faint);
  cursor: default;
}

.assistant-panel__identity > div {
  flex-direction: column;
  min-width: 0;
  gap: var(--app-space-1);
}

.assistant-panel__identity strong {
  display: block;
  overflow: hidden;
  color: var(--app-title);
  font-size: var(--app-font-size-title-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
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
  gap: var(--app-space-1);
}

.assistant-panel__header-actions :deep(.el-button + .el-button) {
  margin-left: 0;
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

.assistant-message__content {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  max-width: 84%;
  gap: var(--app-space-2);
}

.assistant-message.is-user .assistant-message__content {
  align-items: flex-end;
}

.assistant-message__avatar {
  width: var(--app-control-height-sm);
  height: var(--app-control-height-sm);
}

.assistant-message__bubble {
  display: grid;
  max-width: 100%;
  gap: var(--app-space-1);
  padding: var(--app-space-3) var(--app-space-4);
  border: 1px solid var(--app-border-subtle);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-solid);
  box-shadow: var(--app-shadow-sm);
}

.assistant-message__result-label {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
  font-weight: 600;
  line-height: var(--app-line-height-body);
}

.assistant-message.is-error .assistant-message__result-label {
  color: var(--app-danger);
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

.assistant-message__bubble > p,
.assistant-message__markdown {
  min-width: 0;
  font-size: var(--app-font-size-body-lg);
  line-height: var(--app-line-height-loose);
  overflow-wrap: anywhere;
}

.assistant-message__bubble > p {
  margin: 0;
  white-space: pre-wrap;
}

.assistant-message__markdown :deep(h3) {
  margin: 0 0 var(--app-space-2);
  color: var(--app-title);
  font-size: var(--app-font-size-subtitle);
  line-height: var(--app-line-height-body);
}

.assistant-message__markdown :deep(p) {
  margin: 0 0 var(--app-space-2);
}

.assistant-message__markdown :deep(ul),
.assistant-message__markdown :deep(ol) {
  display: grid;
  gap: var(--app-space-tight);
  margin: 0 0 var(--app-space-2);
  padding-inline-start: var(--app-space-5);
}

.assistant-message__markdown :deep(li) {
  padding-inline-start: var(--app-space-hairline);
}

.assistant-message__markdown :deep(strong) {
  color: var(--app-title);
  font-weight: 700;
}

.assistant-message__markdown :deep(code) {
  padding: var(--app-space-hairline) var(--app-space-tight);
  border: 1px solid var(--app-border-subtle);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface-muted);
  color: var(--app-accent);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: var(--app-font-size-caption);
  overflow-wrap: anywhere;
}

.assistant-message__markdown :deep(blockquote) {
  margin: var(--app-space-2) 0;
  padding: var(--app-space-2) var(--app-space-3);
  border-inline-start: var(--app-space-micro) solid var(--app-accent-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-muted);
  color: var(--app-text-muted);
}

.assistant-message__markdown :deep(> :last-child) {
  margin-bottom: 0;
}

.assistant-message__pending {
  display: inline-flex;
  align-items: center;
  min-height: var(--app-space-4);
  gap: var(--app-space-1);
}

.assistant-message__pending i {
  width: var(--app-space-tight);
  height: var(--app-space-tight);
  border-radius: var(--app-radius-round);
  background: var(--app-accent);
  animation: assistant-pulse 1.2s ease-in-out infinite;
}

.assistant-message__pending i:nth-child(2) { animation-delay: 0.15s; }
.assistant-message__pending i:nth-child(3) { animation-delay: 0.3s; }

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
  padding-inline-start: calc(var(--app-control-height-sm) + var(--app-space-1));
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
  line-height: var(--app-line-height-body);
}

.assistant-panel__resize-handle {
  position: absolute;
  bottom: 0;
  left: 0;
  z-index: 1;
  width: var(--app-control-height-sm);
  height: var(--app-control-height-sm);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--app-text-faint);
  cursor: nesw-resize;
  touch-action: none;
}

.assistant-panel__resize-handle::before,
.assistant-panel__resize-handle::after {
  position: absolute;
  bottom: var(--app-space-tight);
  left: var(--app-space-tight);
  border-bottom: 2px solid currentColor;
  border-left: 2px solid currentColor;
  content: '';
}

.assistant-panel__resize-handle::before {
  width: var(--app-space-3);
  height: var(--app-space-3);
}

.assistant-panel__resize-handle::after {
  width: var(--app-space-tight);
  height: var(--app-space-tight);
}

.assistant-panel__resize-handle:hover,
.assistant-panel__resize-handle:focus-visible {
  color: var(--app-accent);
  outline: 2px solid var(--app-accent-border);
  outline-offset: -2px;
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

  .assistant-message__content {
    max-width: 90%;
  }

  .assistant-panel__avatar {
    display: none;
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
  transform: scale(0.98);
}

@media (prefers-reduced-motion: reduce) {
  .assistant-trigger,
  .assistant-flyout-enter-active,
  .assistant-flyout-leave-active,
  .assistant-empty__prompts button,
  .assistant-composer__box {
    transition: none;
  }

  .assistant-message__pending i {
    animation: none;
  }
}
</style>
