<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Document, Files, FolderOpened, Plus, Search } from '@element-plus/icons-vue'
import { searchAiChatSessions } from '../api/aiChat'
import { clearSession, getToken } from '../utils/session'
import { USER_STORAGE_KEY } from '../utils/session'
import { logoutAuth } from '../api/auth'
import { showPopup } from '../utils/popup'

const route = useRoute()
const router = useRouter()
const navRoot = ref(null)
const workspaceOpen = ref(false)
const sidebarCollapsed = ref(false)
const chatGroupCollapsed = ref(false)
const developerModeEnabled = ref(false)
const chatItems = ref([])
const chatPagination = ref({ page: 1, size: 10, total: 0 })
const chatListLoading = ref(false)
const chatListLoadingMore = ref(false)
const chatListError = ref('')
const DEVELOPER_MODE_KEY = 'emp-console:developer-mode'

const primaryMenus = [
  { key: 'knowledge', label: '知识库', icon: FolderOpened, type: 'route', path: '/knowledge' },
  { key: 'search', label: '搜索', icon: Search, type: 'plan' },
  { key: 'apps', label: '我的应用', icon: Files, type: 'plan' }
]

const workspaceMenus = [
  { key: 'profile', label: '个人设置', path: '/settings/profile', type: 'route' },
  { key: 'query', label: '智能问数（临时）', path: '/', type: 'route' }
]

const workspaceActive = computed(() =>
  workspaceMenus.some((item) => isActivePath(item.path)) || workspaceOpen.value
)
const activeChatSessionCode = computed(() => {
  const routeValue = typeof route.params?.sessionCode === 'string' ? route.params.sessionCode.trim() : ''
  return routeValue || ''
})
const hasMoreChatItems = computed(() => chatItems.value.length < chatPagination.value.total)

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

function isActivePath(targetPath) {
  if (targetPath === '/') {
    return route.path === '/' || route.path.startsWith('/c/')
  }
  return route.path === targetPath || route.path.startsWith(`${targetPath}/`)
}

function isPrimaryActive(item) {
  return item.type === 'route' && isActivePath(item.path)
}

function closeWorkspace() {
  workspaceOpen.value = false
}

function handleDocumentClick(event) {
  if (!workspaceOpen.value) {
    return
  }

  if (!navRoot.value?.contains(event.target)) {
    closeWorkspace()
  }
}

function handleChatSessionUpdated() {
  loadChatSessions()
}

function syncDeveloperMode() {
  developerModeEnabled.value = window.localStorage.getItem(DEVELOPER_MODE_KEY) === 'true'
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  closeWorkspace()
}

function toggleChatGroup() {
  chatGroupCollapsed.value = !chatGroupCollapsed.value
}

async function handleNewChat() {
  closeWorkspace()
  await router.push({
    path: '/',
    query: {}
  })
  window.dispatchEvent(new CustomEvent('ai-chat-reset-session'))
}

function handlePrimaryMenu(item) {
  if (item.type === 'route') {
    router.push(item.path)
    return
  }

  showPopup.info('规划中')
}

function handleChatItemClick(item) {
  if (!item?.sessionCode) {
    return
  }
  router.push(`/c/${item.sessionCode}`)
}

function handleWorkspaceMenu(item) {
  workspaceOpen.value = false
  router.push(item.path)
}

function toggleDeveloperMode() {
  developerModeEnabled.value = !developerModeEnabled.value
  window.localStorage.setItem(DEVELOPER_MODE_KEY, String(developerModeEnabled.value))
  showPopup.success(`开发者模式已${developerModeEnabled.value ? '开启' : '关闭'}`)
  workspaceOpen.value = false
}

async function handleLogout() {
  workspaceOpen.value = false
  const token = getToken()

  try {
    if (token) {
      await logoutAuth()
    }
  } catch {
    // 退出时优先保证前端状态清理，后端失效失败不阻塞登出。
  } finally {
    clearSession()
    await router.push('/login')
  }
}

function normalizeChatSessionList(payload) {
  if (Array.isArray(payload)) {
    return {
      list: payload,
      total: payload.length
    }
  }
  if (Array.isArray(payload?.list)) {
    return {
      list: payload.list,
      total: resolvePageTotal(payload?.pageInfo?.total, payload.list.length)
    }
  }
  if (Array.isArray(payload?.data)) {
    return {
      list: payload.data,
      total: payload.data.length
    }
  }
  return {
    list: [],
    total: 0
  }
}

function resolvePageTotal(total, fallback) {
  const numericTotal = Number(total)
  return Number.isFinite(numericTotal) ? numericTotal : fallback
}

function formatChatTime(value) {
  if (!value) {
    return ''
  }
  return String(value).replace('T', ' ').slice(0, 16)
}

function mapChatSessionItem(item) {
  const sessionCode = item?.sessionCode || ''
  return {
    key: sessionCode || `${item?.id || ''}-${item?.sessionName || ''}`,
    sessionCode,
    title: item?.sessionName || '未命名会话',
    pinned: Boolean(item?.pinned),
    businessType: item?.businessType || '',
    time: formatChatTime(item?.updateTime || item?.createTime)
  }
}

async function loadChatSessions(options = {}) {
  const { append = false } = options
  const nextPage = append ? chatPagination.value.page + 1 : 1
  const userId = parseStoredUserId()

  if (append) {
    chatListLoadingMore.value = true
  } else {
    chatListLoading.value = true
    chatListError.value = ''
  }

  try {
    const payload = await searchAiChatSessions({
      page: nextPage,
      size: chatPagination.value.size,
      userId: userId > 0 ? userId : undefined,
      businessType: 2,
      sorts: [
        {
          column: 'updateTime',
          sort: 'desc'
        }
      ]
    })
    const normalized = normalizeChatSessionList(payload)
    const nextItems = normalized.list.map(mapChatSessionItem).filter((item) => item.sessionCode)
    chatItems.value = append ? [...chatItems.value, ...nextItems] : nextItems
    chatPagination.value = {
      ...chatPagination.value,
      page: nextPage,
      total: normalized.total
    }
  } catch (error) {
    chatListError.value = error instanceof Error ? error.message : '聊天列表加载失败'
    if (!append) {
      chatItems.value = []
      chatPagination.value = {
        ...chatPagination.value,
        page: 1,
        total: 0
      }
    }
  } finally {
    chatListLoading.value = false
    chatListLoadingMore.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  window.addEventListener('ai-chat-session-updated', handleChatSessionUpdated)
  syncDeveloperMode()
  loadChatSessions()
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  window.removeEventListener('ai-chat-session-updated', handleChatSessionUpdated)
})

watch(
  () => route.path,
  () => {
    closeWorkspace()
  }
)
</script>

<template>
  <div ref="navRoot" class="shell-sidebar" :class="{ collapsed: sidebarCollapsed }">
    <div class="sidebar-top">
      <div class="brand" title="AI Assist Platform">
        <RouterLink to="/" class="brand-link">
          <span class="brand-logo">AI</span>
          <div class="brand-copy">
            <strong>AI 助手</strong>
            <span>聊天页面完整示例</span>
          </div>
        </RouterLink>

        <button class="icon-btn collapse-btn" type="button" :title="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'" @click="toggleSidebar">
          <component :is="sidebarCollapsed ? ArrowRight : ArrowLeft" class="shell-icon shell-icon-sm" />
        </button>
      </div>

      <button class="new-chat-btn" type="button" title="新建聊天" @click="handleNewChat">
        <span class="btn-icon">
          <Plus class="shell-icon" />
        </span>
        <span v-if="!sidebarCollapsed" class="btn-text">新建聊天</span>
      </button>
    </div>

    <nav class="primary-nav" aria-label="主导航">
      <button
        v-for="item in primaryMenus"
        :key="item.key"
        class="nav-item"
        :class="{ active: isPrimaryActive(item) }"
        type="button"
        :title="item.label"
        @click="handlePrimaryMenu(item)"
      >
        <span class="nav-icon">
          <component :is="item.icon" class="shell-icon" />
        </span>
        <span v-if="!sidebarCollapsed" class="nav-text">{{ item.label }}</span>
      </button>
    </nav>

    <section v-if="!sidebarCollapsed" class="chat-group" :class="{ collapsed: chatGroupCollapsed }">
      <button
        class="chat-group-toggle"
        type="button"
        :title="chatGroupCollapsed ? '展开聊天列表' : '收起聊天列表'"
        @click="toggleChatGroup"
      >
        <span>聊天</span>
        <span class="chat-group-arrow">⌄</span>
      </button>

      <div v-show="!chatGroupCollapsed" class="chat-list">
        <button
          v-for="item in chatItems"
          :key="item.key"
          class="chat-item"
          :class="{ active: item.sessionCode === activeChatSessionCode }"
          type="button"
          :title="item.title"
          @click="handleChatItemClick(item)"
        >
          <span class="chat-item-icon">
            <Document class="shell-icon shell-icon-sm" />
          </span>
          <span class="chat-item-title">
            <span class="chat-item-name">{{ item.title }}</span>
            <span v-if="item.time" class="chat-item-time">{{ item.time }}</span>
          </span>
        </button>

        <div v-if="chatListLoading" class="chat-list-state">加载中...</div>
        <div v-else-if="chatListError" class="chat-list-state">{{ chatListError }}</div>
        <div v-else-if="!chatItems.length" class="chat-list-state">暂无聊天记录</div>

        <button
          v-if="hasMoreChatItems"
          class="chat-load-more"
          type="button"
          :disabled="chatListLoadingMore"
          @click="loadChatSessions({ append: true })"
        >
          {{ chatListLoadingMore ? '加载中...' : '加载更多' }}
        </button>
      </div>
    </section>

    <div class="sidebar-footer">
      <button
        class="workspace-trigger"
        :class="{ active: workspaceActive }"
        type="button"
        title="个人工作区"
        @click.stop="workspaceOpen = !workspaceOpen"
      >
        <span class="workspace-avatar">周</span>
        <div v-if="!sidebarCollapsed" class="workspace-meta">
          <strong>周志通</strong>
          <span>个人工作区</span>
        </div>
      </button>

      <div v-if="workspaceOpen" class="workspace-dropdown" :class="{ compact: sidebarCollapsed }">
        <button
          v-for="item in workspaceMenus"
          :key="item.key"
          class="workspace-action"
          :class="{ active: isActivePath(item.path) }"
          type="button"
          @click="handleWorkspaceMenu(item)"
        >
          {{ item.label }}
        </button>

        <button class="workspace-action" :class="{ active: developerModeEnabled }" type="button" @click="toggleDeveloperMode">
          {{ developerModeEnabled ? '退出开发者模式' : '开发者模式' }}
        </button>

        <button class="workspace-action danger" type="button" @click="handleLogout">
          退出
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.shell-sidebar {
  width: 288px;
  height: 100%;
  padding: 12px;
  border-right: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--theme-bg-surface) 96%, white 4%);
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: relative;
  transition: width 0.22s ease;
}

.shell-sidebar.collapsed {
  width: 76px;
  align-items: stretch;
}

.sidebar-top {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
}

.brand-link {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: inherit;
}

.brand-logo,
.workspace-avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--color-accent);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.brand-copy,
.workspace-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-copy strong,
.workspace-meta strong {
  font-size: 14px;
  font-weight: 650;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.brand-copy span,
.workspace-meta span {
  font-size: 12px;
  color: var(--color-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.icon-btn,
.new-chat-btn,
.nav-item,
.chat-group-toggle,
.chat-item,
.workspace-trigger,
.workspace-action {
  font: inherit;
}

.icon-btn {
  width: 38px;
  height: 38px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  flex-shrink: 0;
}

.new-chat-btn,
.nav-item,
.chat-item,
.workspace-trigger {
  width: 100%;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 0 12px;
  text-align: left;
}

.new-chat-btn {
  border: 1px solid var(--color-border);
}

.btn-icon,
.nav-icon,
.chat-item-icon {
  width: 22px;
  text-align: center;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.shell-icon {
  width: 18px;
  height: 18px;
}

.shell-icon-sm {
  width: 16px;
  height: 16px;
}

.primary-nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nav-item.active,
.chat-item:hover,
.workspace-trigger:hover,
.chat-group-toggle:hover,
.new-chat-btn:hover,
.nav-item:hover {
  background: color-mix(in srgb, var(--theme-bg-surface-muted) 78%, var(--theme-brand-primary) 22%);
}

.chat-group {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-group-toggle {
  width: 100%;
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 8px 6px;
}

.chat-group.collapsed .chat-group-arrow {
  transform: rotate(-90deg);
}

.chat-group-arrow {
  transition: transform 0.2s ease;
}

.chat-list {
  min-height: 0;
  overflow: auto;
}

.chat-item {
  margin-bottom: 4px;
}

.chat-item.active {
  background: color-mix(in srgb, var(--theme-bg-surface-muted) 78%, var(--theme-brand-primary) 22%);
}

.chat-item-title {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.chat-item-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-item-time {
  font-size: 11px;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.chat-list-state {
  padding: 10px 12px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.chat-load-more {
  width: 100%;
  min-height: 36px;
  margin-top: 6px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: transparent;
  color: var(--color-text-secondary);
  font: inherit;
  cursor: pointer;
}

.chat-load-more:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.chat-load-more:hover:not(:disabled) {
  background: color-mix(in srgb, var(--theme-bg-surface-muted) 78%, white 22%);
}

.sidebar-footer {
  position: relative;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

.workspace-trigger {
  padding-left: 0;
  padding-right: 0;
}

.workspace-trigger.active {
  background: transparent;
}

.workspace-dropdown {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(100% + 12px);
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: 18px;
  background: var(--popup-bg);
  box-shadow: var(--popup-shadow);
  display: grid;
  gap: 6px;
}

.workspace-dropdown.compact {
  left: 0;
  width: 200px;
}

.workspace-action {
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
  padding: 10px 12px;
}

.workspace-action:hover,
.workspace-action.active {
  background: color-mix(in srgb, var(--theme-bg-surface-muted) 78%, white 22%);
}

.workspace-action.danger {
  color: var(--color-danger);
}

.shell-sidebar.collapsed .brand {
  flex-direction: column;
}

.shell-sidebar.collapsed .brand-copy {
  display: none;
}

.shell-sidebar.collapsed .brand-link,
.shell-sidebar.collapsed .new-chat-btn,
.shell-sidebar.collapsed .nav-item,
.shell-sidebar.collapsed .workspace-trigger {
  justify-content: center;
  padding-left: 0;
  padding-right: 0;
}

.shell-sidebar.collapsed .workspace-trigger {
  margin-top: auto;
}
</style>
