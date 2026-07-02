<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { FolderOpened, Paperclip, Promotion, Setting } from '@element-plus/icons-vue'
import { queryAiChatStream } from '../../../../../api/aiChat'
import { showPopup } from '../../../../../utils/popup'

const chatMessage = ref('')
const chatInputRef = ref(null)
const sending = ref(false)
const streamedAnswer = ref('')
const streamError = ref('')
const activeSessionCode = ref('')
const router = useRouter()

const canSend = computed(() => chatMessage.value.trim() && !sending.value)

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
  chatMessage.value = text
  nextTick(() => {
    syncTextareaHeight()
    chatInputRef.value?.focus()
  })
}

function handleComposerKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    submitHomeChat()
  }
}

function resetHomeChatState() {
  activeSessionCode.value = ''
  chatMessage.value = ''
  streamedAnswer.value = ''
  streamError.value = ''
  sending.value = false
  nextTick(syncTextareaHeight)
}

function parseSseEventBlock(block) {
  const lines = String(block || '')
    .split('\n')
    .map((line) => line.trimEnd())
    .filter(Boolean)

  const event = { eventType: '', data: '' }
  for (const line of lines) {
    if (line.startsWith('event:')) {
      event.eventType = line.slice('event:'.length).trim()
      continue
    }
    if (line.startsWith('data:')) {
      const dataLine = line.slice('data:'.length).trim()
      event.data = event.data ? `${event.data}\n${dataLine}` : dataLine
    }
  }
  return event
}

async function consumeSseResponse(response, handlers) {
  if (!response?.body) {
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        if (buffer.trim()) {
          handlers.onEvent?.(parseSseEventBlock(buffer))
        }
        break
      }

      buffer += decoder.decode(value, { stream: true })

      let splitIndex = buffer.indexOf('\n\n')
      while (splitIndex !== -1) {
        const block = buffer.slice(0, splitIndex)
        buffer = buffer.slice(splitIndex + 2)
        if (block.trim()) {
          handlers.onEvent?.(parseSseEventBlock(block))
        }
        splitIndex = buffer.indexOf('\n\n')
      }
    }
  } finally {
    reader.releaseLock?.()
  }
}

async function submitHomeChat() {
  const text = chatMessage.value.trim()
  if (!text || sending.value) {
    return
  }

  sending.value = true
  streamError.value = ''
  streamedAnswer.value = ''

  const request = {
    sessionCode: activeSessionCode.value || undefined,
    message: text
  }

  chatMessage.value = ''
  nextTick(syncTextareaHeight)

  try {
    const response = await queryAiChatStream(request)
    let finalAnswer = ''

    await consumeSseResponse(response, {
      onEvent: ({ eventType, data }) => {
        if (!data) {
          return
        }

        let payload = null
        try {
          payload = JSON.parse(data)
        } catch {
          payload = { message: data }
        }

        if (payload?.sessionCode) {
          activeSessionCode.value = payload.sessionCode
        }

        if (eventType === 'init' && payload?.sessionCode) {
          window.dispatchEvent(new CustomEvent('ai-chat-session-updated'))
          return
        }

        if (eventType === 'chunk') {
          const delta = payload?.delta || payload?.message || ''
          if (delta) {
            streamedAnswer.value += delta
          }
          return
        }

        if (eventType === 'complete') {
          finalAnswer = payload?.answer || streamedAnswer.value
          streamedAnswer.value = finalAnswer || '任务已完成。'
          window.dispatchEvent(new CustomEvent('ai-chat-session-updated'))
          return
        }

        if (eventType === 'error') {
          throw new Error(payload?.message || '请求 ai-chat 失败')
        }
      }
    })
  } catch (error) {
    streamError.value = error instanceof Error ? error.message : '请求 ai-chat 失败'
    showPopup.error(streamError.value)
  } finally {
    if (activeSessionCode.value) {
      router.replace(`/c/${activeSessionCode.value}`)
    }
    sending.value = false
  }
}

function openFullConversation() {
  if (!activeSessionCode.value) {
    return
  }
  router.push({
    path: `/c/${activeSessionCode.value}`
  })
}

onMounted(() => {
  nextTick(() => {
    syncTextareaHeight()
  })
  window.addEventListener('ai-chat-reset-session', resetHomeChatState)
})

onBeforeUnmount(() => {
  window.removeEventListener('ai-chat-reset-session', resetHomeChatState)
})

defineProps({
  heroSummary: {
    type: Object,
    required: true
  },
  promptSuggestions: {
    type: Array,
    required: true
  }
})
</script>

<template>
  <main class="home-overview page">
    <section class="chat-entry-panel">
      <div class="chat-entry-copy">
        <h1>{{ heroSummary.title }}</h1>
      </div>

      <div class="chat-composer-stage">
        <div class="chat-composer">
          <textarea
            ref="chatInputRef"
            v-model="chatMessage"
            class="chat-input"
            :placeholder="heroSummary.placeholder"
            @input="handleChatInput"
            @keydown="handleComposerKeydown"
            rows="1"
          />
          <div class="chat-composer-footer">
            <div class="composer-left-tools">
              <button
                v-for="item in heroSummary.composerTools"
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
            <button type="button" class="chat-submit" :disabled="!canSend" aria-label="发送" @click="submitHomeChat">
              <Promotion v-if="!sending" class="submit-icon" />
              <span v-else class="submit-text">...</span>
            </button>
          </div>
        </div>

        <div class="prompt-suggestion-list">
          <button v-for="item in promptSuggestions" :key="item" type="button" class="prompt-chip" @click="handleSuggestionClick(item)">
            {{ item }}
          </button>
        </div>

        <section v-if="sending || streamedAnswer || streamError" class="stream-panel">
          <div class="stream-panel-head">
            <strong>{{ sending ? 'AI 正在回复' : '最新回复' }}</strong>
            <button v-if="activeSessionCode" type="button" class="stream-link-btn" @click="openFullConversation">
              打开完整会话
            </button>
          </div>
          <p v-if="streamError" class="stream-error">{{ streamError }}</p>
          <div v-else class="stream-answer">{{ streamedAnswer || '正在建立上下文...' }}</div>
        </section>
      </div>
    </section>
  </main>
</template>
