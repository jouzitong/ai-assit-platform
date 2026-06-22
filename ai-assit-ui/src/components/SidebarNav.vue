<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearSession, getToken } from '../utils/session'
import { logoutAuth } from '../api/auth'
import { applyTheme as setTheme, getSavedTheme } from '../assets/style/themes/theme-manager'
import { showPopup } from '../utils/popup'

const route = useRoute()
const router = useRouter()
const navRoot = ref(null)
const settingsOpen = ref(false)
const isDarkTheme = ref(false)
const developerModeEnabled = ref(false)
const DEVELOPER_MODE_KEY = 'emp-console:developer-mode'

const menus = [
  { path: '/home', label: 'AI 首页', short: '首页', hint: '平台入口' },
  { path: '/query', label: '智能问数', short: '问数', hint: '分析链路' },
  { path: '/knowledge', label: '知识库', short: '知识', hint: '知识资产' },
  { path: '/emp/attendance', label: '考勤看板', short: '考勤', hint: '出勤趋势' },
  { path: '/emp/performance', label: '绩效洞察', short: '绩效', hint: '组织表现' },
  { path: '/emp/cost', label: '人力成本分析', short: '成本', hint: '预算偏差' }
]

const settingsMenus = [
  { path: '/settings/profile', label: '个人管理', short: '个人' },
  { path: '/settings/system', label: '系统管理', short: '系统' }
]

const themeLabel = computed(() => (isDarkTheme.value ? '切换浅色主题' : '切换深色主题'))
const currentMenu = computed(() => menus.find((item) => isActivePath(item.path)) || menus[0])
const utilityBadges = computed(() => ([
  { key: 'theme', label: isDarkTheme.value ? 'Dark' : 'Light' },
  { key: 'mode', label: developerModeEnabled.value ? 'Dev On' : 'Dev Off' }
]))

function isActivePath(targetPath) {
  return route.path === targetPath || route.path.startsWith(`${targetPath}/`)
}

function applyTheme(nextIsDark) {
  const nextTheme = setTheme(nextIsDark ? 'dark' : 'light')
  isDarkTheme.value = nextTheme === 'dark'
}

function toggleTheme() {
  applyTheme(!isDarkTheme.value)
  settingsOpen.value = false
}

function syncDeveloperMode() {
  developerModeEnabled.value = window.localStorage.getItem(DEVELOPER_MODE_KEY) === 'true'
}

function toggleDeveloperMode() {
  developerModeEnabled.value = !developerModeEnabled.value
  window.localStorage.setItem(DEVELOPER_MODE_KEY, String(developerModeEnabled.value))
  showPopup.success(`开发者模式已${developerModeEnabled.value ? '开启' : '关闭'}`)
  settingsOpen.value = false
}

function closeSettings() {
  settingsOpen.value = false
}

function handleDocumentClick(event) {
  if (!settingsOpen.value) {
    return
  }

  if (!navRoot.value?.contains(event.target)) {
    closeSettings()
  }
}

async function handleLogout() {
  settingsOpen.value = false
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
  applyTheme(getSavedTheme() === 'dark')
  syncDeveloperMode()
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})

watch(
  () => route.path,
  () => {
    closeSettings()
  }
)
</script>

<template>
  <header ref="navRoot" class="topbar">
    <div class="brand-panel">
      <RouterLink to="/home" class="brand-row">
        <span class="brand-mark">AI</span>
        <div class="brand-copy">
          <h2>AI Assist Platform</h2>
          <span class="brand-badge">Workspace Console</span>
        </div>
      </RouterLink>
      <div class="brand-context">
        <strong>{{ currentMenu.label }}</strong>
        <span>{{ currentMenu.hint }}</span>
      </div>
    </div>

    <nav class="menu-group" aria-label="主导航">
      <RouterLink
        v-for="item in menus"
        :key="item.path"
        :to="item.path"
        class="menu-link"
        :class="{ active: isActivePath(item.path) }"
        :title="item.label"
      >
        <strong>{{ item.label }}</strong>
      </RouterLink>
    </nav>

    <div class="settings-group">
      <div class="utility-badges">
        <span v-for="badge in utilityBadges" :key="badge.key" class="utility-badge">
          {{ badge.label }}
        </span>
      </div>
      <button
        class="avatar-trigger"
        :class="{ active: settingsMenus.some((item) => isActivePath(item.path)) || settingsOpen }"
        @click="settingsOpen = !settingsOpen"
        aria-label="打开个人设置菜单"
        type="button"
      >
        <span class="avatar-circle">管</span>
        <span class="avatar-meta">
          <strong>管理员</strong>
          <small>个人设置</small>
        </span>
      </button>

      <div v-if="settingsOpen" class="settings-dropdown">
        <RouterLink
          v-for="item in settingsMenus"
          :key="item.path"
          :to="item.path"
          class="dropdown-link"
          :class="{ active: isActivePath(item.path) }"
          :title="item.label"
          @click="settingsOpen = false"
        >
          {{ item.label }}
        </RouterLink>

        <button
          class="dropdown-action"
          :class="{ active: developerModeEnabled }"
          type="button"
          title="切换开发者模式"
          @click="toggleDeveloperMode"
        >
          开发者模式
        </button>

        <button
          class="dropdown-action"
          type="button"
          :title="themeLabel"
          @click="toggleTheme"
        >
          {{ themeLabel }}
        </button>

        <button
          class="dropdown-action danger"
          type="button"
          title="退出当前账号"
          @click="handleLogout"
        >
          退出
        </button>
      </div>
    </div>
  </header>
</template>
