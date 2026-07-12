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
} from '@element-plus/icons-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import brandLogo from '../../../assets/icons/brand-logo.svg'
import brandMark from '../../../assets/icons/brand-mark.svg'
import { applyTheme, getSavedTheme } from '../../../stores/theme'
import { formatRelativeTime } from '../../../utils/date'
import { clearSession, getStoredUser } from '../../../utils/session'
import {
  fetchConversationDetail,
  fetchConversationList,
  fetchEnabledModels,
  createChatTransportRequest,
  streamChatTransport,
} from '../api'
import type {
  ChatConversationRound,
  ChatEnabledModel,
  ChatSessionItem,
  ChatTransportEvent,
  ChatUiMessage,
} from '../types'

const route = useRoute()
const router = useRouter()
const DEVELOPER_MODE_STORAGE_KEY = 'ai-conversation-ui-developer-mode'

type CurrentUserProfile = {
  displayName?: string
  name?: string
  username?: string
  avatarUrl?: string
  profileImageUrl?: string
}

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
const currentRunId = ref('')
const lastEventId = ref('')
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
const currentUserProfile = getStoredUser<CurrentUserProfile>() || {}
const currentUserName = computed(() =>
  [currentUserProfile.displayName, currentUserProfile.name, currentUserProfile.username]
    .find((value) => typeof value === 'string' && value.trim())?.trim() || '当前用户',
)
const currentUserAvatarUrl = computed(() =>
  [currentUserProfile.avatarUrl, currentUserProfile.profileImageUrl]
    .find((value) => typeof value === 'string' && value.trim())?.trim() || '',
)
const currentUserAvatarText = computed(() => Array.from(currentUserName.value)[0]?.toLocaleUpperCase() || '?')
const selectedModelLabel = computed(() => {
  const matchedModel = modelOptions.value.find((item) => item.modelCode === selectedModel.value)
  return matchedModel?.modelName || matchedModel?.modelCode || matchedModel?.apiModel || selectedModel.value || '选择模型'
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
    selectedModel.value = firstModel?.modelCode || ''
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
  event: { id: string; event: string; data: ChatTransportEvent },
  assistantMessageId: string,
) {
  const { event: eventName, data } = event
  const payload = data.payload || {}
  lastEventId.value = data.eventId || event.id || lastEventId.value
  if (data.runId) {
    currentRunId.value = data.runId
  }

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

  if (eventName === 'assistant.started') {
    upsertAssistantMessage(assistantMessageId, { status: 'RUNNING' })
    return
  }

  if (eventName === 'assistant.message.delta') {
    const message = payload.message as { content?: Array<{ text?: string; markdown?: string }>; append?: boolean } | undefined
    const content = (message?.content || []).map((item) => item.markdown || item.text || '').join('\n')
    if (content) {
      const previous = chatMessages.value.find((item) => item.id === assistantMessageId)?.content || ''
      upsertAssistantMessage(assistantMessageId, {
        content: message?.append ? `${previous}${content}` : content,
        roundCode: data.roundCode,
        status: 'RUNNING',
      })
    }
    return
  }

  if (eventName === 'round.completed') {
    const assistant = (payload.round as { assistant?: { messages?: Array<{ content?: Array<{ text?: string; markdown?: string }> }> } } | undefined)?.assistant
    const content = (assistant?.messages || [])
      .flatMap((message) => message.content || [])
      .map((item) => item.markdown || item.text || '')
      .join('\n')
    upsertAssistantMessage(assistantMessageId, {
      content: content || chatMessages.value.find((item) => item.id === assistantMessageId)?.content || '已收到，正在整理回复。',
      roundCode: data.roundCode,
      status: 'COMPLETED',
    })
    return
  }

  if (eventName === 'round.failed' || eventName === 'round.cancelled' || eventName === 'assistant.input_required') {
    const message = eventName === 'assistant.input_required'
      ? (payload.input as { message?: string } | undefined)?.message
      : (payload.round as { message?: string } | undefined)?.message
    upsertAssistantMessage(assistantMessageId, {
      content: message || (eventName === 'round.cancelled' ? '对话已取消。' : '对话执行失败，请稍后重试。'),
      roundCode: data.roundCode,
      status: eventName === 'round.cancelled' ? 'CANCELLED' : 'FAILED',
    })
    conversationError.value = message || '对话执行失败，请稍后重试。'
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
    await streamChatTransport(
      createChatTransportRequest({
        sessionCode: currentSessionCode.value || undefined,
        modelCode: selectedModel.value || undefined,
        message,
      }, route.path),
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
              <div class="chat-home-user__avatar" :aria-label="`${currentUserName} 的头像`" role="img">
                <img v-if="currentUserAvatarUrl" class="chat-home-user-avatar-image" :src="currentUserAvatarUrl" :alt="currentUserName" />
                <span v-else>{{ currentUserAvatarText }}</span>
              </div>
              <div v-if="sidebarExpanded" class="chat-home-user__copy">
                <strong>{{ currentUserName }}</strong>
              </div>
            </button>
          <div
            v-if="activeUserMenu === 'sidebar'"
            class="chat-home-user-menu chat-home-user-menu--sidebar"
            @click.stop
          >
            <div class="chat-home-user-menu__profile">
              <div class="chat-home-user-menu__avatar" :aria-label="`${currentUserName} 的头像`" role="img">
                <img v-if="currentUserAvatarUrl" class="chat-home-user-avatar-image" :src="currentUserAvatarUrl" :alt="currentUserName" />
                <span v-else>{{ currentUserAvatarText }}</span>
              </div>
              <div class="chat-home-user-menu__identity">
                <div class="chat-home-user-menu__name">{{ currentUserName }}</div>
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
            <button class="avatar-chip" type="button" :aria-label="`${currentUserName} 的头像菜单`" @click.stop="toggleUserMenu('topbar')">
              <img v-if="currentUserAvatarUrl" class="chat-home-user-avatar-image" :src="currentUserAvatarUrl" :alt="currentUserName" />
              <span v-else>{{ currentUserAvatarText }}</span>
            </button>
            <div v-if="activeUserMenu === 'topbar'" class="chat-home-user-menu" @click.stop>
              <div class="chat-home-user-menu__profile">
                <div class="chat-home-user-menu__avatar" :aria-label="`${currentUserName} 的头像`" role="img">
                  <img v-if="currentUserAvatarUrl" class="chat-home-user-avatar-image" :src="currentUserAvatarUrl" :alt="currentUserName" />
                  <span v-else>{{ currentUserAvatarText }}</span>
                </div>
                <div class="chat-home-user-menu__identity">
                  <div class="chat-home-user-menu__name">{{ currentUserName }}</div>
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
            <div class="chat-home-assistant__avatar">AI</div>
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
                  <div class="chat-home-assistant__avatar chat-home-assistant__avatar--small">AI</div>
                  <div class="chat-home-message__assistant-copy">
                    <div class="chat-home-message__assistant-name">{{ selectedModelLabel }}</div>
                    <div class="chat-home-message__assistant-text">{{ message.content }}</div>
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="chat-home-message__user-row">
                  <div class="chat-home-user-bubble">{{ message.content }}</div>
                  <div class="chat-home-message__user-avatar" :aria-label="`${currentUserName} 的头像`" role="img">
                    <img v-if="currentUserAvatarUrl" class="chat-home-user-avatar-image" :src="currentUserAvatarUrl" :alt="currentUserName" />
                    <span v-else>{{ currentUserAvatarText }}</span>
                  </div>
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

<style scoped lang="scss">
@use '../../../styles/chat-home';
</style>
