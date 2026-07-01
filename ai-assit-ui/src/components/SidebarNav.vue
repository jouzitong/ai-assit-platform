<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Document, Files, FolderOpened, Plus, Search } from '@element-plus/icons-vue'
import { clearSession, getToken } from '../utils/session'
import { logoutAuth } from '../api/auth'
import { showPopup } from '../utils/popup'

const route = useRoute()
const router = useRouter()
const navRoot = ref(null)
const workspaceOpen = ref(false)
const sidebarCollapsed = ref(false)
const chatGroupCollapsed = ref(false)

const primaryMenus = [
  { key: 'knowledge', label: '知识库', icon: FolderOpened, type: 'route', path: '/knowledge' },
  { key: 'search', label: '搜索', icon: Search, type: 'plan' },
  { key: 'apps', label: '我的应用', icon: Files, type: 'plan' }
]

const chatItems = [
  { key: 'chat-1', title: '智能问数平台设计' },
  { key: 'chat-2', title: '低代码 JSON 渲染方案' },
  { key: 'chat-3', title: '数据权限设计' },
  { key: 'chat-4', title: 'AI Agent 节点编排' }
]

const workspaceMenus = [
  { key: 'profile', label: '个人设置', path: '/settings/profile', type: 'route' },
  { key: 'query', label: '智能问数（临时）', path: '/query', type: 'route' }
]

const workspaceActive = computed(() =>
  workspaceMenus.some((item) => isActivePath(item.path)) || workspaceOpen.value
)

function isActivePath(targetPath) {
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

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  closeWorkspace()
}

function toggleChatGroup() {
  chatGroupCollapsed.value = !chatGroupCollapsed.value
}

function handleNewChat() {
  showPopup.info('规划中')
}

function handlePrimaryMenu(item) {
  if (item.type === 'route') {
    router.push(item.path)
    return
  }

  showPopup.info('规划中')
}

function handleChatItemClick() {
  showPopup.info('规划中')
}

function handleWorkspaceMenu(item) {
  workspaceOpen.value = false
  router.push(item.path)
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

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})

watch(
  () => route.path,
  () => {
    closeWorkspace()
  }
)
</script>

<template>
  <aside ref="navRoot" class="shell-sidebar" :class="{ collapsed: sidebarCollapsed }">
    <div class="sidebar-top">
      <div class="brand" title="AI Assist Platform">
        <RouterLink to="/home" class="brand-link">
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
          type="button"
          :title="item.title"
          @click="handleChatItemClick"
        >
          <span class="chat-item-icon">
            <Document class="shell-icon shell-icon-sm" />
          </span>
          <span class="chat-item-title">{{ item.title }}</span>
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

        <button class="workspace-action danger" type="button" @click="handleLogout">
          退出
        </button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.shell-sidebar {
  width: 288px;
  height: 100vh;
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

.chat-item-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
