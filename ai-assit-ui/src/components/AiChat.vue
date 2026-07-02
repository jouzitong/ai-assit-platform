<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { FolderOpened, Paperclip, Promotion, Setting } from '@element-plus/icons-vue'
import { createAiChatConversation } from '../api/aiChat'
import { clearPendingAiChatDraft, savePendingAiChatDraft } from '../utils/aiChatDraft'
import { showPopup } from '../utils/popup'
import { USER_STORAGE_KEY } from '../utils/session'

const props = defineProps({
  layout: {
    type: String,
    default: 'entry'
  },
  heroSummary: {
    type: Object,
    default: null
  },
  title: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: ''
  },
  promptSuggestions: {
    type: Array,
    default: () => []
  },
  composerTools: {
    type: Array,
    default: () => []
  },
  modelValue: {
    type: String,
    default: undefined
  },
  showWelcome: {
    type: Boolean,
    default: true
  },
  sending: {
    type: Boolean,
    default: false
  },
  sendDisabled: {
    type: Boolean,
    default: false
  },
  submitMode: {
    type: String,
    default: 'create-conversation'
  },
  businessType: {
    type: Number,
    default: 2
  },
  buildSessionRoute: {
    type: Function,
    default: (sessionCode) => `/c/${sessionCode}`
  }
})

const emit = defineEmits(['update:modelValue', 'submit', 'suggestion-click'])
const router = useRouter()
const chatMessage = ref('')
const chatInputRef = ref(null)
const streamError = ref('')

const resolvedTitle = computed(() => props.title || props.heroSummary?.title || '')
const resolvedPlaceholder = computed(() => props.placeholder || props.heroSummary?.placeholder || '')
const resolvedComposerTools = computed(() => {
  return Array.isArray(props.composerTools) && props.composerTools.length > 0
    ? props.composerTools
    : (props.heroSummary?.composerTools || [])
})
const inputValue = computed({
  get() {
    return props.modelValue !== undefined ? props.modelValue : chatMessage.value
  },
  set(value) {
    if (props.modelValue !== undefined) {
      emit('update:modelValue', value)
      return
    }
    chatMessage.value = value
  }
})
const canSend = computed(() => Boolean(inputValue.value.trim()) && !props.sending && !props.sendDisabled)

function syncTextareaHeight() {
  const textarea = chatInputRef.value
  if (!textarea) return

  const computedStyle = window.getComputedStyle(textarea)
  const lineHeight = Number.parseFloat(computedStyle.lineHeight) || 27.2
  const maxHeight = lineHeight * 5

  textarea.style.height = 'auto'
  textarea.style.height = `${Math.min(textarea.scrollHeight, maxHeight)}px`
  textarea.style.overflowY = textarea.scrollHeight > maxHeight ? 'auto' : 'hidden'
}

function handleChatInput() {
  syncTextareaHeight()
}

function handleSuggestionClick(text) {
  inputValue.value = text
  emit('suggestion-click', text)
  nextTick(() => {
    syncTextareaHeight()
    chatInputRef.value?.focus()
  })
}

function handleComposerKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    submitChat()
  }
}

function resetChatState() {
  inputValue.value = ''
  streamError.value = ''
  clearPendingAiChatDraft()
  nextTick(syncTextareaHeight)
}

function parseStoredUserId() {
  try {
    const raw = window.localStorage.getItem(USER_STORAGE_KEY)
    if (!raw) {
      return 0
    }
    const user = JSON.parse(raw)
    const value = Number(user?.id ?? user?.userId ?? 0)
    return Number.isFinite(value) ? value : 0
  } catch {
    return 0
  }
}

async function submitChat() {
  const text = inputValue.value.trim()
  if (!text || props.sending) {
    return
  }

  if (props.submitMode === 'emit') {
    emit('submit', text)
    return
  }

  streamError.value = ''
  inputValue.value = ''
  nextTick(syncTextareaHeight)

  try {
    const response = await createAiChatConversation({
      userId: parseStoredUserId() || undefined,
      sessionName: text.length > 20 ? text.slice(0, 20) : text,
      businessType: props.businessType
    })
    const session = response?.session || response?.data?.session || null
    const sessionCode = typeof session?.sessionCode === 'string' ? session.sessionCode.trim() : ''
    if (!sessionCode) {
      throw new Error('创建会话失败')
    }

    savePendingAiChatDraft({
      sessionCode,
      message: text
    })
    window.dispatchEvent(new CustomEvent('ai-chat-session-updated'))
    await router.push(props.buildSessionRoute(sessionCode))
  } catch (error) {
    streamError.value = error instanceof Error ? error.message : '请求 ai-chat 失败'
    showPopup.error(streamError.value)
  }
}

function focusInput() {
  chatInputRef.value?.focus()
}

watch(inputValue, () => {
  nextTick(syncTextareaHeight)
})

onMounted(() => {
  nextTick(syncTextareaHeight)
  window.addEventListener('ai-chat-reset-session', resetChatState)
})

onBeforeUnmount(() => {
  window.removeEventListener('ai-chat-reset-session', resetChatState)
})

defineExpose({
  focusInput,
  syncTextareaHeight
})
</script>

<template>
  <section v-if="layout === 'workspace'" class="ai-chat-shell ai-chat-workspace" :class="{ 'is-empty': showWelcome }">
    <div class="chat-stage">
      <div class="chat-scroll">
        <template v-if="showWelcome">
          <slot name="welcome">
            <section class="welcome">
              <h1>{{ resolvedTitle }}</h1>
              <p>{{ resolvedPlaceholder }}</p>
            </section>

            <section v-if="promptSuggestions.length > 0" class="suggestions">
              <button
                v-for="item in promptSuggestions"
                :key="item"
                class="suggestion-card"
                type="button"
                @click="handleSuggestionClick(item)"
              >
                {{ item }}
              </button>
            </section>
          </slot>
        </template>
        <slot v-else name="conversation" />
      </div>
    </div>

    <footer class="composer-area">
      <div class="composer">
        <slot name="composer-before" />
        <textarea
          ref="chatInputRef"
          v-model="inputValue"
          :placeholder="resolvedPlaceholder"
          rows="1"
          @input="handleChatInput"
          @keydown="handleComposerKeydown"
        />
        <div class="composer-bottom">
          <div class="composer-tools">
            <slot name="composer-tools" />
          </div>
          <slot name="composer-hint" />
          <button class="send-btn" type="button" :disabled="!canSend" aria-label="发送" @click="submitChat">
            <Promotion class="submit-icon" />
          </button>
        </div>
      </div>
    </footer>
  </section>

  <section v-else class="chat-entry-panel">
    <div class="chat-entry-copy">
      <h1>{{ resolvedTitle }}</h1>
    </div>

    <div class="chat-composer-stage">
      <div class="chat-composer">
        <textarea
          ref="chatInputRef"
          v-model="inputValue"
          class="chat-input"
          :placeholder="resolvedPlaceholder"
          rows="1"
          @input="handleChatInput"
          @keydown="handleComposerKeydown"
        />
        <div class="chat-composer-footer">
          <div class="composer-left-tools">
            <button
              v-for="item in resolvedComposerTools"
              :key="item"
              type="button"
              class="composer-tool-chip"
            >
              <Paperclip v-if="item === '附件'" class="composer-icon" />
              <Setting v-else-if="item === '工具'" class="composer-icon" />
              <FolderOpened v-else class="composer-icon" />
              <span>{{ item }}</span>
            </button>
          </div>
          <button type="button" class="chat-submit" :disabled="!canSend" aria-label="发送" @click="submitChat">
            <Promotion v-if="!props.sending" class="submit-icon" />
            <span v-else class="submit-text">...</span>
          </button>
        </div>
      </div>

      <div class="prompt-suggestion-list">
        <button
          v-for="item in promptSuggestions"
          :key="item"
          type="button"
          class="prompt-chip"
          @click="handleSuggestionClick(item)"
        >
          {{ item }}
        </button>
      </div>

      <section v-if="props.sending || streamError" class="stream-panel">
        <div class="stream-panel-head">
          <strong>{{ props.sending ? '正在进入会话' : '发送失败' }}</strong>
        </div>
        <p v-if="streamError" class="stream-error">{{ streamError }}</p>
        <div v-else class="stream-answer">正在创建会话并跳转到完整对话页...</div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.ai-chat-shell {
  width: 100%;
  min-height: 0;
}

.ai-chat-workspace {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-stage {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  position: relative;
}

.chat-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 32px 18px 28px;
}

.welcome,
.suggestions,
.composer {
  max-width: 1160px;
  margin-left: auto;
  margin-right: auto;
}

.welcome {
  margin-top: 70px;
  margin-bottom: 28px;
  text-align: center;
}

.welcome h1 {
  margin: 0 0 12px;
  font-size: 32px;
  line-height: 1.25;
  font-weight: 700;
}

.welcome p {
  margin: 0;
  color: var(--theme-text-muted, #6b7280);
  font-size: 15px;
}

.suggestions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.suggestion-card {
  min-height: 80px;
  border: 1px solid var(--theme-border-default, #e5e7eb);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.66);
  color: var(--theme-text-primary, #202123);
  text-align: left;
  cursor: pointer;
  padding: 14px 16px;
  font: inherit;
  transition: background-color 0.2s ease;
}

.suggestion-card:hover {
  background: rgba(255, 255, 255, 0.94);
}

.composer-area {
  flex-shrink: 0;
  padding: 8px 18px 12px;
  background: transparent;
}

.composer {
  border: 1px solid var(--theme-border-default, #e5e7eb);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
  padding: 8px;
  position: relative;
}

.composer textarea {
  width: 100%;
  min-height: 40px;
  max-height: 160px;
  resize: none;
  outline: none;
  border: none;
  background: transparent;
  color: var(--theme-text-primary, #202123);
  font: inherit;
  font-size: 15px;
  line-height: 1.5;
  padding: 6px 8px;
  display: block;
}

.composer-bottom {
  display: grid;
  grid-template-columns: minmax(0, auto) 1fr auto;
  align-items: center;
  gap: 10px;
  padding-top: 2px;
}

.composer-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.send-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: var(--primary, #10a37f);
  color: #fff;
  cursor: pointer;
  font-size: 17px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.send-btn:disabled {
  background: #9ca3af;
  cursor: not-allowed;
}

.ai-chat-workspace.is-empty .chat-stage {
  justify-content: center;
  padding: 24px 0 40px;
}

.ai-chat-workspace.is-empty .chat-scroll {
  flex: 0 0 auto;
  min-height: auto;
  overflow: visible;
  padding-bottom: 24px;
}

.ai-chat-workspace.is-empty .composer-area {
  padding-top: 0;
  padding-bottom: 0;
}

.chat-entry-panel {
  width: min(100%, 860px);
  min-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.chat-entry-copy {
  text-align: center;
  padding-top: 34px;
  flex-shrink: 0;
}

.chat-entry-copy h1 {
  margin: 0;
  font-size: clamp(34px, 4vw, 52px);
  line-height: 1.1;
  letter-spacing: -0.05em;
}

.chat-composer-stage {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 24px;
  padding: 12px 0 18px;
}

.chat-composer {
  border: 1px solid color-mix(in srgb, var(--theme-border-default) 68%, white 32%);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 24px 54px rgba(15, 23, 42, 0.08);
  padding: 18px 18px 14px;
}

.chat-input {
  display: block;
  width: 100%;
  min-height: 72px;
  max-height: calc(1.7em * 5 + 12px);
  resize: none;
  overflow-y: hidden;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--theme-text-primary);
  font: inherit;
  font-size: 16px;
  line-height: 1.7;
}

.chat-input::placeholder {
  color: var(--theme-text-muted);
}

.chat-composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 12px;
}

.composer-left-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.composer-tool-chip,
.prompt-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--theme-border-default) 68%, white 32%);
  background: rgba(255, 255, 255, 0.84);
  color: var(--theme-text-secondary);
  font: inherit;
  font-size: 12px;
  font-weight: 700;
}

.composer-tool-chip {
  gap: 6px;
  cursor: pointer;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.composer-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.composer-tool-chip:hover,
.prompt-chip:hover {
  color: #1d4ed8;
  border-color: rgba(59, 130, 246, 0.28);
}

.chat-submit {
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 50%;
  background: var(--theme-text-muted);
  color: #fff;
  font: inherit;
  font-size: 17px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.chat-submit:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.submit-icon {
  width: 16px;
  height: 16px;
}

.submit-text {
  font-size: 14px;
  line-height: 1;
}

.prompt-suggestion-list {
  width: min(100%, 780px);
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.prompt-chip {
  width: 100%;
  min-height: 76px;
  justify-content: flex-start;
  padding: 14px 16px;
  text-align: left;
  cursor: pointer;
  white-space: normal;
  line-height: 1.55;
  transition: transform 0.2s ease, border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease;
}

.prompt-chip:hover {
  transform: translateY(-1px);
  background: rgba(255, 255, 255, 0.94);
}

.stream-panel {
  width: min(100%, 780px);
  margin: 0 auto;
  padding: 18px 20px;
  border-radius: 22px;
  border: 1px solid color-mix(in srgb, var(--theme-border-default) 68%, white 32%);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.06);
}

.stream-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.stream-panel-head strong {
  font-size: 14px;
}

.stream-answer,
.stream-error {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
  color: var(--theme-text-primary);
}

.stream-error {
  color: var(--color-danger);
}

@media (max-width: 720px) {
  .chat-scroll {
    padding: 24px 14px 20px;
  }

  .welcome {
    margin-top: 40px;
  }

  .welcome h1 {
    font-size: 26px;
  }

  .suggestions {
    grid-template-columns: 1fr;
  }

  .composer-area {
    padding: 8px 12px 12px;
  }

  .composer-bottom {
    grid-template-columns: 1fr auto;
    align-items: flex-end;
  }

  .chat-entry-copy {
    padding-top: 0;
  }

  .chat-composer-stage {
    justify-content: flex-start;
    gap: 16px;
    padding: 18px 0 0;
  }

  .chat-composer-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .chat-submit {
    width: 100%;
    height: 42px;
    border-radius: 999px;
  }

  .prompt-suggestion-list {
    grid-template-columns: 1fr;
  }
}
</style>
