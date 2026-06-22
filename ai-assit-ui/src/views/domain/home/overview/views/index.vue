<script setup>
import { nextTick, onMounted, ref } from 'vue'

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
  },
  quickEntries: {
    type: Array,
    required: true
  },
  focusPanels: {
    type: Array,
    required: true
  },
  activityFeed: {
    type: Array,
    required: true
  },
  calendarItems: {
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
              {{ item }}
            </button>
          </div>
          <div class="composer-right-actions">
            <button type="button" class="composer-select-chip">
              {{ heroSummary.modelLabel }}
            </button>
            <button type="button" class="composer-icon-chip" :aria-label="heroSummary.voiceLabel" :title="heroSummary.voiceLabel">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M12 3C10.3431 3 9 4.34315 9 6V12C9 13.6569 10.3431 15 12 15C13.6569 15 15 13.6569 15 12V6C15 4.34315 13.6569 3 12 3ZM7 11.5C7 10.9477 6.55228 10.5 6 10.5C5.44772 10.5 5 10.9477 5 11.5V12C5 15.5262 7.60879 18.4431 11 18.9291V21H9C8.44772 21 8 21.4477 8 22C8 22.5523 8.44772 23 9 23H15C15.5523 23 16 22.5523 16 22C16 21.4477 15.5523 21 15 21H13V18.9291C16.3912 18.4431 19 15.5262 19 12V11.5C19 10.9477 18.5523 10.5 18 10.5C17.4477 10.5 17 10.9477 17 11.5V12C17 14.7614 14.7614 17 12 17C9.23858 17 7 14.7614 7 12V11.5Z"
                  fill="currentColor"
                />
              </svg>
            </button>
            <button type="button" class="chat-submit">发送</button>
          </div>
        </div>
      </div>

      <div class="prompt-suggestion-list">
        <button v-for="item in promptSuggestions" :key="item" type="button" class="prompt-chip">
          {{ item }}
        </button>
      </div>
    </section>

    <section class="home-section-grid">
      <article class="quick-panel">
        <div class="section-head">
          <div>
            <p class="section-eyebrow">Applications</p>
            <h2>常用应用</h2>
          </div>
        </div>

        <div class="quick-entry-list">
          <RouterLink v-for="entry in quickEntries" :key="entry.title" :to="entry.to" class="quick-entry">
            <div>
              <strong>{{ entry.title }}</strong>
              <p>{{ entry.description }}</p>
            </div>
            <span>{{ entry.meta }}</span>
          </RouterLink>
        </div>
      </article>

      <article class="activity-panel">
        <div class="section-head">
          <div>
            <p class="section-eyebrow">Activity</p>
            <h2>最近动态</h2>
          </div>
        </div>

        <div class="activity-list">
          <div v-for="item in activityFeed" :key="`${item.time}-${item.title}`" class="activity-row" :data-type="item.type">
            <span class="activity-time">{{ item.time }}</span>
            <div class="activity-content">
              <strong>{{ item.title }}</strong>
              <p>{{ item.detail }}</p>
            </div>
          </div>
        </div>
      </article>
    </section>

    <section class="lower-grid">
      <article class="calendar-panel">
        <div class="section-head">
          <div>
            <p class="section-eyebrow">Calendar</p>
            <h2>日历模块</h2>
          </div>
        </div>

        <div class="calendar-list">
          <article v-for="item in calendarItems" :key="`${item.date}-${item.title}`" class="calendar-item">
            <div class="calendar-date">
              <span>{{ item.day }}</span>
              <strong>{{ item.date }}</strong>
            </div>
            <div class="calendar-content">
              <strong>{{ item.title }}</strong>
              <p>{{ item.time }}</p>
            </div>
          </article>
        </div>
      </article>

      <section class="focus-grid">
      <article v-for="panel in focusPanels" :key="panel.title" class="focus-panel">
        <div class="section-head">
          <div>
            <p class="section-eyebrow">{{ panel.eyebrow }}</p>
            <h2>{{ panel.title }}</h2>
          </div>
        </div>

        <ul class="focus-list">
          <li v-for="item in panel.items" :key="item">{{ item }}</li>
        </ul>
      </article>
      </section>
    </section>
  </main>
</template>
