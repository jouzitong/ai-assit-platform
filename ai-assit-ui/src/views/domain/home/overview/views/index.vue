<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { FolderOpened, Paperclip, Promotion, Setting } from '@element-plus/icons-vue'

const chatMessage = ref('')
const chatInputRef = ref(null)

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

onMounted(() => {
  nextTick(() => {
    syncTextareaHeight()
  })
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
            <button type="button" class="chat-submit" aria-label="发送">
              <Promotion class="submit-icon" />
            </button>
          </div>
        </div>

        <div class="prompt-suggestion-list">
          <button v-for="item in promptSuggestions" :key="item" type="button" class="prompt-chip">
            {{ item }}
          </button>
        </div>
      </div>
    </section>
  </main>
</template>
