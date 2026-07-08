<script setup lang="ts">
import {
  Calendar,
  ChatDotRound,
  Check,
  Clock,
  Collection,
  Crop,
  Document,
  EditPen,
  Files,
  FolderOpened,
  Loading,
  Microphone,
  MoreFilled,
  Notebook,
  Operation,
  Promotion,
  ArrowLeftBold,
  ArrowRightBold,
  Search,
  Setting,
  SwitchButton,
  UserFilled,
} from '@element-plus/icons-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import brandLogo from '../../../assets/icons/brand-logo.svg'
import brandMark from '../../../assets/icons/brand-mark.svg'
import { applyTheme, getSavedTheme } from '../../../stores/theme'
import { formatRelativeTime } from '../../../utils/date'
import { clearSession } from '../../../utils/session'
import {
  fetchConversationDetail,
  fetchConversationList,
  fetchEnabledModels,
  streamChatCompletion,
} from '../api'
import type {
  ChatConversationRound,
  ChatEnabledModel,
  ChatSessionItem,
  ChatStreamEvent,
  ChatUiMessage,
} from '../types'

const route = useRoute()
const router = useRouter()
const DEVELOPER_MODE_STORAGE_KEY = 'ai-conversation-ui-developer-mode'

const prompt = ref('')
const selectedModel = ref('')
const modelOptions = ref<ChatEnabledModel[]>([])
const activeNav = ref('chats')
const sidebarExpanded = ref(true)
const activeUserMenu = ref<'topbar' | 'sidebar' | null>(null)
const activeTheme = ref<'dark' | 'light'>('light')
const developerModeEnabled = ref(false)
const conversationList = ref<ChatSessionItem[]>([])
const chatMessages = ref<ChatUiMessage[]>([])
const currentRoundCode = ref('')
const currentSessionName = ref('')
const pendingSessionCode = ref('')
const isLoadingList = ref(false)
const isLoadingDetail = ref(false)
const isStreaming = ref(false)
const conversationError = ref('')
const welcomeTextarea = useTemplateRef<HTMLTextAreaElement>('welcomeTextarea')
const conversationTextarea = useTemplateRef<HTMLTextAreaElement>('conversationTextarea')

const welcomeCards = [
  '帮我分析本周核心业务波动，并给出三条解释假设',
  '把这个需求整理成执行计划，标出风险和依赖',
  '根据销售数据，生成一份管理层晨报摘要',
  '对接知识库后，我该如何设计问答链路和结果区？',
]

const welcomeSuggestions = [
  { title: 'Give me ideas', subtitle: "for what to do with my kids' art", prompt: welcomeCards[0] },
  { title: 'Show me a code snippet', subtitle: "of a website's sticky header", prompt: welcomeCards[1] },
  { title: 'Help me study', subtitle: 'vocabulary for a college entrance exam', prompt: welcomeCards[2] },
]

const quickNavItems = [
  { key: 'new-chat', label: '新对话', count: '', icon: EditPen },
  { key: 'search', label: '搜索', count: '', icon: Search },
  { key: 'notes', label: '笔记', count: '', icon: Document },
  { key: 'workspace', label: '工作空间', count: '', icon: Operation },
  { key: 'scheduler', label: '定时任务', count: '', icon: Clock },
]

const userMenuItems = [
  { key: 'system-settings', label: '系统设置', icon: Setting },
  { key: 'archived', label: '已归档对话', icon: Files },
  { key: 'workspace', label: '工作空间', icon: Collection },
  { key: 'notes', label: '笔记', icon: Notebook },
  { key: 'schedule', label: '日程', icon: Calendar },
  { key: 'automation', label: '自动化任务', icon: Loading },
  { key: 'explore', label: 'AI 对话探索区', icon: Crop },
]

const isConversationMode = computed(() => typeof route.params.sessionId === 'string' && route.params.sessionId.trim() !== '')
const currentSessionCode = computed(() =>
  typeof route.params.sessionId === 'string' ? route.params.sessionId.trim() : '',
)
const currentGroupId = computed(() =>
  typeof route.params.groupId === 'string' ? route.params.groupId.trim() : '',
)
const activeConversation = computed(() => currentSessionCode.value)
const selectedModelLabel = computed(() => {
  const matchedModel = modelOptions.value.find((item) => item.apiModel === selectedModel.value)
  return matchedModel?.apiModel || matchedModel?.modelName || matchedModel?.modelCode || selectedModel.value || '选择模型'
})
const pinnedConversations = computed(() =>
  conversationList.value.map((conversation) => ({
    id: conversation.sessionCode,
    title: conversation.sessionName || conversation.sessionCode,
    meta: formatRelativeTime(conversation.updateTime) || (conversation.pinned ? '置顶' : conversation.sessionCode.slice(-6)),
  })),
)

function resizeTextarea(element: HTMLTextAreaElement | null) {
  if (!element) {
    return
  }

  const computedStyle = window.getComputedStyle(element)
  const lineHeight = Number.parseFloat(computedStyle.lineHeight) || 24
  const maxHeight = lineHeight * 8
  element.style.height = '0px'
  element.style.height = `${Math.min(element.scrollHeight, maxHeight)}px`
  element.style.overflowY = element.scrollHeight > maxHeight ? 'auto' : 'hidden'
}

async function syncTextareaHeights() {
  await nextTick()
  resizeTextarea(welcomeTextarea.value)
  resizeTextarea(conversationTextarea.value)
}

function applySuggestion(text: string) {
  prompt.value = text
  void syncTextareaHeights()
}

function normalizeRole(role?: string) {
  if (!role) {
    return 'assistant' as const
  }
  return role.toLowerCase() === 'user' ? 'user' : 'assistant'
}

function flattenRoundsToMessages(rounds: ChatConversationRound[]) {
  return rounds.flatMap((round) =>
    (round.messages || [])
      .filter((message) => typeof message.content === 'string' && message.content.trim())
      .map((message) => ({
        id: message.messageCode || `${round.round?.roundCode || 'round'}-${message.sortNo || 0}`,
        role: normalizeRole(message.role),
        content: message.content,
        roundCode: message.roundCode,
        status: message.status || round.round?.status || undefined,
      })),
  )
}

function upsertAssistantMessage(messageId: string, payload: Partial<ChatUiMessage>) {
  const target = chatMessages.value.find((item) => item.id === messageId)
  if (!target) {
    return
  }

  if (payload.content !== undefined) {
    target.content = payload.content
  }
  if (payload.roundCode !== undefined) {
    target.roundCode = payload.roundCode
  }
  if (payload.status !== undefined) {
    target.status = payload.status
  }
}

async function loadConversationList() {
  isLoadingList.value = true
  try {
    const sessions = await fetchConversationList({})
    conversationList.value = Array.isArray(sessions) ? sessions : []
  } finally {
    isLoadingList.value = false
  }
}

async function loadEnabledModelList() {
  const models = await fetchEnabledModels().catch(() => [])
  modelOptions.value = Array.isArray(models) ? models : []
  if (!selectedModel.value) {
    const firstModel = modelOptions.value[0]
    selectedModel.value = firstModel?.apiModel || firstModel?.modelName || firstModel?.modelCode || 'gpt-4.1'
  }
}

async function loadConversationDetail(sessionCode: string) {
  if (!sessionCode) {
    chatMessages.value = []
    currentRoundCode.value = ''
    currentSessionName.value = ''
    return
  }

  isLoadingDetail.value = true
  conversationError.value = ''
  try {
    const detail = await fetchConversationDetail({ sessionCode })
    currentSessionName.value = detail.session?.sessionName || ''
    chatMessages.value = flattenRoundsToMessages(detail.rounds || [])
    const lastRound = [...(detail.rounds || [])].reverse().find((item) => item.round?.roundCode)
    currentRoundCode.value = lastRound?.round?.roundCode || ''
  } catch (error) {
    chatMessages.value = []
    currentRoundCode.value = ''
    currentSessionName.value = ''
    conversationError.value = error instanceof Error ? error.message : '会话详情加载失败'
  } finally {
    isLoadingDetail.value = false
  }
}

function handleStreamEvent(
  event: { event: string; data: ChatStreamEvent },
  assistantMessageId: string,
) {
  const { event: eventName, data } = event

  if (data.sessionCode && !currentSessionCode.value) {
    pendingSessionCode.value = data.sessionCode
    void router.replace(`/c/${data.sessionCode}`)
  }
  if (data.sessionCode) {
    pendingSessionCode.value = data.sessionCode
  }
  if (data.sessionName) {
    currentSessionName.value = data.sessionName
  }
  if (data.roundCode) {
    currentRoundCode.value = data.roundCode
  }

  if (eventName === 'answer-ready' || eventName === 'complete') {
    upsertAssistantMessage(assistantMessageId, {
      content: data.answer || '已收到，正在整理回复。',
      roundCode: data.roundCode,
      status: data.status,
    })
    return
  }

  if (eventName === 'error') {
    upsertAssistantMessage(assistantMessageId, {
      content: data.message || '对话执行失败，请稍后重试。',
      roundCode: data.roundCode,
      status: data.status || 'FAILED',
    })
    conversationError.value = data.message || '对话执行失败，请稍后重试。'
  }
}

async function handlePrimaryAction() {
  const message = prompt.value.trim()
  if (!message || isStreaming.value) {
    return
  }

  const userMessageId = `user-${Date.now()}`
  const assistantMessageId = `assistant-${Date.now()}`
  chatMessages.value.push({
    id: userMessageId,
    role: 'user',
    content: message,
  })
  chatMessages.value.push({
    id: assistantMessageId,
    role: 'assistant',
    content: '正在生成回复...',
    status: 'RUNNING',
  })

  prompt.value = ''
  conversationError.value = ''
  isStreaming.value = true
  await syncTextareaHeights()

  try {
    await streamChatCompletion(
      {
        sessionCode: currentSessionCode.value || undefined,
        apiModel: selectedModel.value || undefined,
        message,
        attachments: [],
        tools: [],
        ext: {},
      },
      (event) => handleStreamEvent(event, assistantMessageId),
    )
    const finalSessionCode = currentSessionCode.value || pendingSessionCode.value
    await loadConversationList()
    if (finalSessionCode) {
      await loadConversationDetail(finalSessionCode)
    }
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : '对话发送失败'
    upsertAssistantMessage(assistantMessageId, {
      content: errorMessage,
      status: 'FAILED',
    })
    conversationError.value = errorMessage
  } finally {
    isStreaming.value = false
  }
}

function handlePromptKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey) {
    return
  }

  event.preventDefault()
  void handlePrimaryAction()
}

function toggleUserMenu(target: 'topbar' | 'sidebar') {
  activeUserMenu.value = activeUserMenu.value === target ? null : target
}

function selectTheme(theme: 'dark' | 'light') {
  activeTheme.value = applyTheme(theme)
}

function syncDeveloperModeStorage() {
  window.localStorage.setItem(DEVELOPER_MODE_STORAGE_KEY, developerModeEnabled.value ? '1' : '0')
}

function toggleDeveloperMode() {
  developerModeEnabled.value = !developerModeEnabled.value
  syncDeveloperModeStorage()
}

async function handleUserMenuAction(key: string) {
  if (key === 'system-settings') {
    closeUserMenu()
    await router.push('/settings/system')
    return
  }
}

function closeUserMenu() {
  activeUserMenu.value = null
}

async function handleLogout() {
  closeUserMenu()
  clearSession()
  await router.push('/auth/login')
}

function handleDocumentClick(event: MouseEvent) {
  if (!activeUserMenu.value) {
    return
  }

  const target = event.target
  if (!(target instanceof Node)) {
    return
  }

  if (target instanceof Element && target.closest('.chat-home-user-menu-anchor')) {
    return
  }

  closeUserMenu()
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  developerModeEnabled.value = window.localStorage.getItem(DEVELOPER_MODE_STORAGE_KEY) === '1'
  activeTheme.value = getSavedTheme()
  void loadEnabledModelList()
  void loadConversationList()
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})

watch(prompt, () => {
  void syncTextareaHeights()
})

watch(isConversationMode, () => {
  void syncTextareaHeights()
}, { immediate: true })

watch(currentSessionCode, (sessionCode) => {
  if (!sessionCode) {
    chatMessages.value = []
    currentRoundCode.value = ''
    currentSessionName.value = ''
    pendingSessionCode.value = ''
    conversationError.value = ''
    return
  }
  void loadConversationDetail(sessionCode)
}, { immediate: true })

watch(route, () => {
  closeUserMenu()
})
</script>

<template>
  <div
    :class="['chat-home-shell', { 'is-sidebar-collapsed': !sidebarExpanded }]"
    :style="{ '--chat-sidebar-width': sidebarExpanded ? '210px' : '88px' }"
  >
    <aside class="chat-home-sidebar">
      <div class="chat-home-brand">
        <button
          class="chat-home-brand__logo"
          type="button"
          aria-label="返回聊天首页"
          title="返回聊天首页"
          @click="router.push('/')"
        >
          <img
            class="chat-home-brand__logo-image"
            :src="sidebarExpanded ? brandLogo : brandMark"
            :alt="sidebarExpanded ? '智能问数 ZG' : '智能问数'"
          />
        </button>
      </div>

      <nav class="chat-home-nav">
        <button
          v-for="item in quickNavItems"
          :key="item.key"
          :class="['chat-home-nav__item', { 'is-active': activeNav === item.key }]"
          type="button"
          @click="
            item.key === 'new-chat'
              ? router.push('/')
              : (activeNav = item.key)
          "
        >
          <span class="chat-home-nav__leading">
            <el-icon><component :is="item.icon" /></el-icon>
            <span v-if="sidebarExpanded">{{ item.label }}</span>
          </span>
        </button>
      </nav>

      <div v-if="sidebarExpanded" class="chat-home-sidebar__section chat-home-sidebar__section--models">
        <div v-if="sidebarExpanded" class="chat-home-sidebar__header">
          <span>模型</span>
        </div>
        <div class="chat-home-model-inline">
          <span class="chat-home-model-inline__dot"></span>
          <span>{{ selectedModelLabel }}</span>
        </div>
      </div>

      <div v-if="sidebarExpanded" class="chat-home-sidebar__section">
        <div class="chat-home-sidebar__header"><span>分组</span></div>
        <div class="chat-home-group-label">对话</div>
        <div class="chat-home-group-label chat-home-group-label--muted">
          {{ isLoadingList ? '加载中...' : `共 ${pinnedConversations.length} 个会话` }}
        </div>

        <button
          v-for="conversation in pinnedConversations"
          :key="conversation.id"
          :class="['chat-home-thread', { 'is-current': activeConversation === conversation.id }]"
          type="button"
          @click="router.push(`/c/${conversation.id}`)"
        >
          <div class="chat-home-thread__leading">
            <div class="chat-home-thread__copy">
              <strong>{{ conversation.title }}</strong>
            </div>
          </div>
          <span class="chat-home-thread__meta">{{ conversation.meta }}</span>
          <button
            v-if="activeConversation === conversation.id"
            class="chat-home-thread__more"
            type="button"
            aria-label="More"
          >
            <el-icon><MoreFilled /></el-icon>
          </button>
        </button>

        <div v-if="!isLoadingList && pinnedConversations.length === 0" class="chat-home-thread-empty">
          暂无会话
        </div>
      </div>

      <div class="chat-home-sidebar__footer">
        <div class="chat-home-user-menu-anchor chat-home-user-menu-anchor--sidebar">
            <button class="chat-home-user" type="button" @click.stop="toggleUserMenu('sidebar')">
              <div class="chat-home-user__avatar">周</div>
              <div v-if="sidebarExpanded" class="chat-home-user__copy">
                <strong>周志通</strong>
              </div>
            </button>
          <div
            v-if="activeUserMenu === 'sidebar'"
            class="chat-home-user-menu chat-home-user-menu--sidebar"
            @click.stop
          >
            <div class="chat-home-user-menu__profile">
              <div class="chat-home-user-menu__avatar">周</div>
              <div class="chat-home-user-menu__identity">
                <div class="chat-home-user-menu__name">周志通</div>
                <div class="chat-home-user-menu__status">
                  <span class="chat-home-user-menu__status-dot"></span>
                  <span>在线</span>
                </div>
              </div>
            </div>

            <button class="chat-home-user-menu__status-action" type="button">
              <el-icon><Check /></el-icon>
              <span>更新您的状态</span>
            </button>

            <div class="chat-home-user-menu__section">
              <div class="chat-home-user-menu__section-title">
                <el-icon><Setting /></el-icon>
                <span>设置</span>
              </div>
              <div class="chat-home-user-menu__theme-label">主题</div>
              <div class="chat-home-user-menu__theme-switch">
                <button
                  :class="['chat-home-user-menu__theme-option', { 'is-active': activeTheme === 'dark' }]"
                  type="button"
                  @click="selectTheme('dark')"
                >
                  暗色
                </button>
                <button
                  :class="['chat-home-user-menu__theme-option', { 'is-active': activeTheme === 'light' }]"
                  type="button"
                  @click="selectTheme('light')"
                >
                  浅色
                </button>
              </div>
            </div>

            <div class="chat-home-user-menu__list">
              <button class="chat-home-user-menu__item" type="button" @click="toggleDeveloperMode">
                <el-icon><Setting /></el-icon>
                <span>{{ developerModeEnabled ? '退出开发者模式' : '进入开发者模式' }}</span>
              </button>
              <button
                v-for="item in userMenuItems"
                :key="item.key"
                class="chat-home-user-menu__item"
                type="button"
                @click="handleUserMenuAction(item.key)"
              >
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </button>
            </div>

            <button class="chat-home-user-menu__item chat-home-user-menu__item--logout" type="button" @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>
              <span>登出</span>
            </button>
          </div>
        </div>
      </div>
    </aside>

    <button
      class="chat-home-shell__toggle"
      type="button"
      :aria-label="sidebarExpanded ? '收起侧边栏' : '展开侧边栏'"
      :title="sidebarExpanded ? '收起侧边栏' : '展开侧边栏'"
      @click="sidebarExpanded = !sidebarExpanded"
    >
      <el-icon>
        <ArrowLeftBold v-if="sidebarExpanded" />
        <ArrowRightBold v-else />
      </el-icon>
    </button>

    <main class="chat-home-main">
      <header class="chat-home-topbar">
        <div class="chat-home-topbar__left">
          <div class="chat-home-model-switcher">
            <span>{{ selectedModelLabel }}</span>
            <span class="chat-home-model-switcher__caret">⌄</span>
            <button class="chat-home-model-switcher__plus" type="button">+</button>
          </div>
        </div>

        <div class="chat-home-topbar__right">
          <button class="ghost-icon-button" type="button"><el-icon><MoreFilled /></el-icon></button>
          <button class="ghost-icon-button" type="button"><el-icon><Operation /></el-icon></button>
          <div class="chat-home-user-menu-anchor">
            <button class="avatar-chip" type="button" @click.stop="toggleUserMenu('topbar')">
              <el-icon><UserFilled /></el-icon>
            </button>
            <div v-if="activeUserMenu === 'topbar'" class="chat-home-user-menu" @click.stop>
              <div class="chat-home-user-menu__profile">
                <div class="chat-home-user-menu__avatar">周</div>
                <div class="chat-home-user-menu__identity">
                  <div class="chat-home-user-menu__name">周志通</div>
                  <div class="chat-home-user-menu__status">
                    <span class="chat-home-user-menu__status-dot"></span>
                    <span>在线</span>
                  </div>
                </div>
              </div>

              <button class="chat-home-user-menu__status-action" type="button">
                <el-icon><Check /></el-icon>
                <span>更新您的状态</span>
              </button>

              <div class="chat-home-user-menu__section">
                <div class="chat-home-user-menu__section-title">
                  <el-icon><Setting /></el-icon>
                  <span>设置</span>
                </div>
                <div class="chat-home-user-menu__theme-label">主题</div>
                <div class="chat-home-user-menu__theme-switch">
                  <button
                    :class="['chat-home-user-menu__theme-option', { 'is-active': activeTheme === 'dark' }]"
                    type="button"
                    @click="selectTheme('dark')"
                  >
                    暗色
                  </button>
                  <button
                    :class="['chat-home-user-menu__theme-option', { 'is-active': activeTheme === 'light' }]"
                    type="button"
                    @click="selectTheme('light')"
                  >
                    浅色
                  </button>
                </div>
              </div>

              <div class="chat-home-user-menu__list">
                <button class="chat-home-user-menu__item" type="button" @click="toggleDeveloperMode">
                  <el-icon><Setting /></el-icon>
                  <span>{{ developerModeEnabled ? '退出开发者模式' : '进入开发者模式' }}</span>
                </button>
                <button
                  v-for="item in userMenuItems"
                  :key="item.key"
                  class="chat-home-user-menu__item"
                  type="button"
                  @click="handleUserMenuAction(item.key)"
                >
                  <el-icon><component :is="item.icon" /></el-icon>
                  <span>{{ item.label }}</span>
                </button>
              </div>

              <button class="chat-home-user-menu__item chat-home-user-menu__item--logout" type="button" @click="handleLogout">
                <el-icon><SwitchButton /></el-icon>
                <span>登出</span>
              </button>
            </div>
          </div>
        </div>
      </header>

      <section v-if="!isConversationMode" class="chat-home-welcome">
        <div class="chat-home-welcome-stage">
          <div class="chat-home-welcome-model">
            <div class="chat-home-welcome-model__avatar">oi</div>
            <div class="chat-home-welcome-model__name">{{ selectedModelLabel }}</div>
          </div>
          <div class="chat-home-composer chat-home-composer--floating chat-home-composer--welcome">
            <textarea
              ref="welcomeTextarea"
              v-model="prompt"
              placeholder="有什么我能帮您的么?"
              rows="1"
              @keydown="handlePromptKeydown"
            ></textarea>
            <div class="chat-home-composer__footer">
              <div class="chat-home-composer__tools">
                <button class="composer-tool-button" type="button"><span>+</span></button>
                <button class="composer-tool-button" type="button">
                  <el-icon><Operation /></el-icon>
                </button>
              </div>
              <div class="chat-home-composer__actions">
                <button class="composer-icon-button" type="button"><el-icon><Microphone /></el-icon></button>
                <button class="composer-send-button" type="button" :disabled="isStreaming" @click="handlePrimaryAction">
                  <el-icon><Promotion /></el-icon>
                </button>
              </div>
            </div>
          </div>

          <div class="chat-home-welcome-suggestions">
            <div class="chat-home-welcome-suggestions__header">建议</div>
            <button
              v-for="item in welcomeSuggestions"
              :key="item.title"
              class="chat-home-welcome-suggestion"
              type="button"
              @click="applySuggestion(item.prompt)"
            >
              <strong>{{ item.title }}</strong>
              <span>{{ item.subtitle }}</span>
            </button>
          </div>
        </div>
      </section>

      <section v-else class="chat-home-conversation">
        <div class="chat-home-center-column chat-home-center-column--conversation">
          <div v-if="chatMessages.length === 0 && !isLoadingDetail" class="chat-home-assistant">
            <div class="chat-home-assistant__avatar">pr</div>
            <div class="chat-home-assistant__body">
              <div class="chat-home-assistant__title">{{ selectedModelLabel }}</div>
              <div class="chat-home-assistant__meta">{{ currentSessionName || '新会话' }}</div>
              <div class="chat-home-assistant__text">你好！有什么我可以帮你的吗？</div>
              <div class="chat-home-assistant__actions">
                <button
                  v-for="index in 6"
                  :key="index"
                  class="ghost-inline-icon"
                  type="button"
                >
                  <span></span>
                </button>
              </div>
            </div>
          </div>

          <div v-if="currentGroupId" class="chat-home-group-pill">Group · {{ currentGroupId }}</div>

          <div v-if="chatMessages.length === 0 && !isLoadingDetail" class="chat-home-followups">
            <button
              v-for="card in welcomeCards.slice(0, 3)"
              :key="card"
              class="chat-home-followup-line"
              type="button"
              @click="applySuggestion(card)"
            >
              {{ card }}
            </button>
          </div>

          <div v-if="conversationError" class="chat-home-feedback">{{ conversationError }}</div>
          <div v-else-if="isLoadingDetail" class="chat-home-feedback">会话加载中...</div>

          <div class="chat-home-message-list">
            <article
              v-for="message in chatMessages"
              :key="message.id"
              :class="['chat-home-message', `is-${message.role}`]"
            >
              <template v-if="message.role === 'assistant'">
                <div class="chat-home-message__assistant-row">
                  <div class="chat-home-assistant__avatar chat-home-assistant__avatar--small">pr</div>
                  <div class="chat-home-message__assistant-copy">
                    <div class="chat-home-message__assistant-name">{{ selectedModelLabel }}</div>
                    <div class="chat-home-message__assistant-text">{{ message.content }}</div>
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="chat-home-message__user-row">
                  <div class="chat-home-user-bubble">{{ message.content }}</div>
                </div>
              </template>
            </article>
          </div>
        </div>

        <div class="chat-home-composer chat-home-composer--floating chat-home-composer--conversation">
          <textarea
            ref="conversationTextarea"
            v-model="prompt"
            placeholder="输入消息"
            rows="1"
            @keydown="handlePromptKeydown"
          ></textarea>
          <div class="chat-home-composer__footer">
            <div class="chat-home-composer__tools">
              <button class="composer-tool-button" type="button"><span>+</span></button>
              <button class="composer-tool-button" type="button">
                <el-icon><Operation /></el-icon>
              </button>
            </div>
            <div class="chat-home-composer__actions">
              <button class="composer-icon-button" type="button"><el-icon><Microphone /></el-icon></button>
              <button class="composer-send-button" type="button" :disabled="isStreaming" @click="handlePrimaryAction">
                <el-icon><Promotion /></el-icon>
              </button>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.chat-home-shell {
  position: relative;
  display: grid;
  grid-template-columns: var(--chat-sidebar-width) minmax(0, 1fr);
  min-height: 100vh;
  background: var(--chat-shell-bg);
  transition: grid-template-columns 0.2s ease;
}

.chat-home-shell.is-sidebar-collapsed {
  grid-template-columns: var(--chat-sidebar-width) minmax(0, 1fr);
}

.chat-home-sidebar {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 12px 10px;
  border-right: 1px solid var(--chat-sidebar-border);
  background: var(--chat-sidebar-bg);
  overflow: visible;
}

.chat-home-brand {
  display: block;
  padding: 0 4px;
}

.chat-home-brand__logo {
  display: block;
  width: 168px;
  padding: 0;
  border: 0;
  background: transparent;
  line-height: 0;
  cursor: pointer;
  overflow: hidden;
}

.chat-home-brand__logo-image {
  display: block;
  width: 100%;
  height: auto;
  max-width: none;
}

.chat-home-shell.is-sidebar-collapsed .chat-home-brand {
  display: flex;
  justify-content: center;
  padding: 0;
}

.chat-home-shell.is-sidebar-collapsed .chat-home-brand__logo {
  width: 40px;
}

.chat-home-shell.is-sidebar-collapsed .chat-home-brand__logo-image {
  width: 40px;
}

.chat-home-shell__toggle {
  position: absolute;
  top: 28px;
  left: calc(var(--chat-sidebar-width) - 16px);
  z-index: 15;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--chat-sidebar-border);
  border-radius: 50%;
  background: var(--chat-panel-bg);
  color: var(--chat-icon-muted);
  box-shadow: var(--chat-panel-shadow);
  cursor: pointer;
  transition: left 0.2s ease, color 0.2s ease, background-color 0.2s ease;
}

.chat-home-shell__toggle:hover {
  background: var(--chat-hover-bg);
  color: var(--chat-text-strong);
}

.chat-home-nav {
  display: grid;
  gap: 2px;
}

.chat-home-shell.is-sidebar-collapsed .chat-home-nav__item {
  justify-content: center;
  padding-inline: 0;
}

.chat-home-shell.is-sidebar-collapsed .chat-home-nav__leading {
  justify-content: center;
}

.chat-home-nav__item,
.chat-home-thread {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px 8px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--chat-text-secondary);
  text-align: left;
  cursor: pointer;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.chat-home-nav__leading {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.chat-home-nav__leading :deep(.el-icon) {
  flex: none;
  color: var(--chat-icon-secondary);
  font-size: 14px;
}

.chat-home-nav__item:hover,
.chat-home-thread:hover {
  background: var(--chat-hover-bg);
}

.chat-home-nav__item.is-active,
.chat-home-thread.is-current {
  background: var(--chat-hover-bg);
  color: var(--chat-text-strong);
  box-shadow: none;
}

.chat-home-nav__item.is-active .chat-home-nav__leading :deep(.el-icon) {
  color: var(--chat-text-strong);
}

.chat-home-sidebar__section {
  display: grid;
  gap: 4px;
}

.chat-home-sidebar__header {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 6px 6px 2px;
  color: var(--chat-text-muted);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.chat-home-sidebar__section--models {
  margin-top: 2px;
}

.chat-home-model-inline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 2px 8px 6px;
  color: var(--chat-text-secondary);
  font-size: 13px;
}

.chat-home-model-inline__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--chat-accent-strong);
}

.chat-home-group-label {
  padding: 4px 8px 2px;
  color: var(--chat-text-tertiary);
  font-size: 12px;
  font-weight: 600;
}

.chat-home-group-label--muted {
  color: var(--chat-text-muted);
  font-weight: 500;
}

.chat-home-thread {
  position: relative;
  justify-content: flex-start;
  gap: 6px;
  padding-right: 28px;
}

.chat-home-thread__leading {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.chat-home-thread__icon {
  flex: none;
  color: var(--chat-icon);
  font-size: 13px;
}

.chat-home-thread__copy {
  display: flex;
  flex: 1;
  min-width: 0;
}

.chat-home-thread strong {
  display: block;
  overflow: hidden;
  color: var(--chat-text-primary);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-home-thread__meta {
  position: absolute;
  right: 22px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--chat-text-subtle);
  font-size: 10px;
}

.chat-home-thread.is-current .chat-home-thread__icon {
  color: var(--chat-text-strong);
}

.chat-home-thread__more {
  position: absolute;
  right: 4px;
  top: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  transform: translateY(-50%);
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--chat-text-muted);
  cursor: pointer;
}

.chat-home-thread-empty {
  padding: 6px 8px 2px;
  color: var(--chat-text-faint);
  font-size: 12px;
}

.chat-home-sidebar__footer {
  margin-top: auto;
  position: relative;
  z-index: 25;
}

.chat-home-shell.is-sidebar-collapsed .chat-home-sidebar__footer {
  width: 100%;
}

.chat-home-user {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 4px;
  border: none;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.chat-home-shell.is-sidebar-collapsed .chat-home-user {
  justify-content: center;
  padding-inline: 0;
}

.chat-home-user__avatar,
.avatar-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--chat-accent-warm);
  color: var(--chat-shell-bg);
  font-size: 11px;
  font-weight: 700;
}

.chat-home-user__copy strong {
  display: block;
  color: var(--chat-text-primary);
  font-size: 12px;
  font-weight: 600;
}

.chat-home-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 0;
  background: var(--chat-main-bg);
}

.chat-home-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 40px;
  gap: 10px;
  padding: 6px 12px 4px 14px;
}

.chat-home-topbar__left,
.chat-home-topbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-home-model-switcher {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--chat-text-secondary);
  font-size: 14px;
  font-weight: 600;
}

.chat-home-model-switcher__caret {
  color: var(--chat-text-subtle);
  font-size: 12px;
}

.chat-home-model-switcher__plus {
  border: none;
  background: transparent;
  color: var(--chat-icon);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.ghost-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  background: transparent;
  color: var(--chat-text-muted);
  cursor: pointer;
}

.ghost-icon-button :deep(.el-icon),
.avatar-chip :deep(.el-icon) {
  font-size: 14px;
}

.chat-home-user-menu-anchor {
  position: relative;
}

.chat-home-user-menu-anchor--sidebar {
  width: 100%;
}

.chat-home-user-menu {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: 20;
  width: 264px;
  padding: 12px;
  border: 1px solid var(--chat-panel-border);
  border-radius: 18px;
  background: var(--chat-panel-bg);
  box-shadow: var(--chat-panel-shadow);
}

.chat-home-user-menu--sidebar {
  top: auto;
  right: auto;
  bottom: calc(100% + 10px);
  left: 0;
}

.chat-home-user-menu__profile {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-home-user-menu__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--chat-accent-warm);
  color: var(--chat-shell-bg);
  font-size: 18px;
  font-weight: 700;
}

.chat-home-user-menu__identity {
  min-width: 0;
}

.chat-home-user-menu__name {
  color: var(--chat-text-strong);
  font-size: 14px;
  font-weight: 700;
}

.chat-home-user-menu__status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
  color: var(--chat-text-strong);
  font-size: 12px;
}

.chat-home-user-menu__status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--chat-status-online);
}

.chat-home-user-menu__status-action,
.chat-home-user-menu__item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 36px;
  padding: 0 10px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: var(--chat-text-strong);
  font-size: 14px;
  text-align: left;
  cursor: pointer;
}

.chat-home-user-menu__status-action {
  justify-content: center;
  margin-top: 12px;
  background: var(--chat-soft-bg);
  font-size: 13px;
}

.chat-home-user-menu__status-action :deep(.el-icon),
.chat-home-user-menu__item :deep(.el-icon),
.chat-home-user-menu__section-title :deep(.el-icon) {
  color: var(--chat-text-strong);
  font-size: 16px;
}

.chat-home-user-menu__section {
  margin-top: 12px;
}

.chat-home-user-menu__section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 6px;
  color: var(--chat-text-strong);
  font-size: 14px;
  font-weight: 500;
}

.chat-home-user-menu__theme-label {
  margin-top: 8px;
  padding: 0 6px;
  color: var(--chat-text-faint);
  font-size: 12px;
}

.chat-home-user-menu__theme-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 8px;
}

.chat-home-user-menu__theme-option {
  height: 38px;
  border: none;
  border-radius: 14px;
  background: var(--chat-soft-bg);
  color: var(--chat-text-secondary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.chat-home-user-menu__theme-option.is-active {
  background: var(--chat-accent-strong);
  color: var(--chat-shell-bg);
}

.chat-home-user-menu__list {
  display: grid;
  gap: 4px;
  margin-top: 10px;
}

.chat-home-user-menu__item:hover,
.chat-home-user-menu__item--logout:hover {
  background: var(--chat-soft-bg);
}

.chat-home-user-menu__item--logout {
  margin-top: 4px;
}

.chat-home-welcome,
.chat-home-conversation {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  position: relative;
  background: var(--chat-main-bg);
}

.chat-home-welcome {
  align-items: center;
  justify-content: center;
  padding: 24px 24px 18px;
}

.chat-home-center-column {
  width: min(760px, calc(100% - 48px));
  margin: 56px auto 0;
}

.chat-home-welcome-stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: min(620px, calc(100% - 64px));
  margin: 0 auto;
}

.chat-home-welcome-model {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 22px;
}

.chat-home-welcome-model__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--chat-panel-border);
  border-radius: 50%;
  background: var(--chat-panel-bg);
  color: var(--chat-text-strong);
  font-size: 13px;
  font-weight: 600;
}

.chat-home-welcome-model__name {
  color: var(--chat-text-secondary);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.chat-home-center-column--conversation {
  margin-top: 38px;
}

.chat-home-assistant {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.chat-home-assistant__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--chat-assistant-avatar-bg);
  color: var(--chat-text-soft);
  font-size: 11px;
  font-weight: 700;
}

.chat-home-assistant__avatar--small {
  width: 24px;
  height: 24px;
}

.chat-home-assistant__body {
  min-width: 0;
}

.chat-home-assistant__title {
  color: var(--chat-text-title);
  font-size: 14px;
  font-weight: 700;
}

.chat-home-assistant__meta {
  margin-top: 2px;
  color: var(--chat-text-subtle);
  font-size: 12px;
}

.chat-home-assistant__text {
  margin-top: 6px;
  color: var(--chat-text-body);
  font-size: 14px;
  line-height: 1.7;
}

.chat-home-assistant__actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.ghost-inline-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  background: transparent;
  cursor: pointer;
}

.ghost-inline-icon span {
  width: 12px;
  height: 12px;
  border: 1px solid var(--chat-text-subtle);
  border-radius: 50%;
}

.chat-home-followup-line {
  display: block;
  width: 100%;
  padding: 10px 0;
  border: none;
  border-top: 1px solid var(--chat-followup-border);
  background: transparent;
  color: var(--chat-text-subtle);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.chat-home-followups {
  margin-top: 10px;
}

.chat-home-group-pill {
  display: inline-flex;
  align-items: center;
  margin-top: 12px;
  padding: 0 10px;
  height: 22px;
  border-radius: 999px;
  background: var(--chat-soft-bg-alt);
  color: var(--chat-text-muted);
  font-size: 11px;
}

.chat-home-composer {
  width: min(880px, calc(100% - 96px));
  padding: 10px 14px 10px;
  border: 1px solid var(--chat-composer-border);
  border-radius: 24px;
  background: var(--chat-panel-bg);
  box-shadow: var(--chat-composer-shadow);
}

.chat-home-composer textarea {
  width: 100%;
  min-height: 24px;
  max-height: 208px;
  padding: 0;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--chat-text-strong);
  font-size: 14px;
  line-height: 24px;
}

.chat-home-composer textarea::placeholder {
  color: var(--chat-text-faint);
}

.chat-home-composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-top: 6px;
}

.chat-home-composer__tools,
.chat-home-composer__actions {
  display: flex;
  gap: 8px;
}

.composer-tool-button,
.composer-icon-button,
.composer-send-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  cursor: pointer;
}

.composer-tool-button {
  width: 20px;
  height: 20px;
  color: var(--chat-text-muted);
  font-size: 18px;
}

.composer-icon-button {
  width: 26px;
  height: 26px;
  color: var(--chat-text-muted);
}

.composer-send-button {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--chat-accent-strong);
  color: var(--chat-shell-bg);
}

.composer-send-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.chat-home-composer--floating {
  align-self: center;
  margin-top: auto;
  margin-bottom: 10px;
}

.chat-home-composer--welcome {
  width: 100%;
  max-width: 600px;
  margin-top: 0;
  margin-bottom: 0;
  border-radius: 22px;
}

.chat-home-conversation {
  padding: 0 0 12px;
}

.chat-home-user-bubble {
  display: inline-flex;
  height: 26px;
  align-items: center;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--chat-bubble-bg);
  color: var(--chat-text-tertiary);
  font-size: 13px;
}

.chat-home-message-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin-top: 22px;
}

.chat-home-message {
  width: 100%;
}

.chat-home-message__assistant-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.chat-home-message__assistant-name {
  color: var(--chat-text-title);
  font-size: 14px;
  font-weight: 700;
}

.chat-home-message__assistant-text {
  margin-top: 8px;
  color: var(--chat-text-body);
  font-size: 14px;
  line-height: 1.8;
}

.chat-home-message__user-row {
  display: flex;
  justify-content: flex-end;
}

.chat-home-message.is-user .chat-home-user-bubble {
  background: var(--chat-bubble-bg);
  color: var(--chat-text-tertiary);
  height: auto;
  min-height: 38px;
  padding: 9px 14px;
  line-height: 1.5;
}

.chat-home-message__assistant-copy {
  max-width: 560px;
}

.chat-home-feedback {
  margin-top: 18px;
  color: var(--chat-text-muted);
  font-size: 13px;
}

.chat-home-composer--conversation {
  width: min(860px, calc(100% - 140px));
}

.chat-home-welcome-suggestions {
  display: grid;
  gap: 12px;
  width: min(300px, 100%);
  margin-top: 20px;
  padding-left: 6px;
}

.chat-home-welcome-suggestions__header {
  color: var(--chat-text-muted);
  font-size: 12px;
  font-weight: 600;
}

.chat-home-welcome-suggestion {
  display: grid;
  gap: 2px;
  width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.chat-home-welcome-suggestion strong {
  color: var(--chat-text-secondary);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.3;
}

.chat-home-welcome-suggestion span {
  color: var(--chat-text-faint);
  font-size: 12px;
  line-height: 1.4;
}

@media (max-width: 1200px) {
  .chat-home-conversation__ghost-user {
    padding-right: 48px;
  }
}

@media (max-width: 960px) {
  .chat-home-shell {
    grid-template-columns: 1fr;
  }

  .chat-home-shell__toggle {
    display: none;
  }

  .chat-home-sidebar {
    display: none;
  }

  .chat-home-topbar {
    padding-inline: 10px;
  }

  .chat-home-center-column,
  .chat-home-composer,
  .chat-home-composer--conversation {
    width: calc(100% - 28px);
  }

  .chat-home-conversation__ghost-user {
    padding-right: 14px;
  }

  .chat-home-center-column {
    margin-top: 30px;
  }
}

@media (max-width: 640px) {
  .chat-home-topbar__right {
    gap: 4px;
  }

  .chat-home-model-switcher {
    font-size: 13px;
  }

  .chat-home-center-column,
  .chat-home-composer,
  .chat-home-composer--conversation {
    width: calc(100% - 20px);
  }

  .chat-home-assistant__text,
  .chat-home-message__assistant-text {
    font-size: 13px;
  }

  .chat-home-conversation__ghost-user {
    padding-right: 10px;
  }

  .chat-home-user-bubble {
    font-size: 12px;
  }
}
</style>
