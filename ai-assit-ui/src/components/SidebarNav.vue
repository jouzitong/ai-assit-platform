<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearSession, getToken } from '../utils/session'
import { logoutAuth } from '../api/auth'
import { applyTheme as setTheme, getSavedTheme } from '../assets/style/themes/theme-manager'
import { showPopup } from '../utils/popup'

const route = useRoute()
const router = useRouter()
const settingsOpen = ref(false)
const isDarkTheme = ref(false)
const developerModeEnabled = ref(false)
const DEVELOPER_MODE_KEY = 'emp-console:developer-mode'

const menus = [
  { path: '/home', label: 'AI 首页', short: '首页' },
  { path: '/query', label: '智能问数', short: '问数' },
  { path: '/knowledge', label: '知识库', short: '知识库' },
  { path: '/emp/attendance', label: '考勤看板', short: '考勤' },
  { path: '/emp/performance', label: '绩效洞察', short: '绩效' },
  { path: '/emp/cost', label: '人力成本分析', short: '成本' }
]

const settingsMenus = [
  { path: '/settings/profile', label: '个人管理', short: '个人' },
  { path: '/settings/system', label: '系统管理', short: '系统' }
]

const themeLabel = computed(() => (isDarkTheme.value ? '切换浅色主题' : '切换深色主题'))

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
})
</script>

<template>
  <header class="topbar">
    <div class="brand-row">
      <h2>EMP Console</h2>
      <span class="brand-badge">AI 助手平台</span>
    </div>

    <nav class="menu-group">
      <RouterLink
        v-for="item in menus"
        :key="item.path"
        :to="item.path"
        class="menu-link"
        :class="{ active: isActivePath(item.path) }"
        :title="item.label"
      >
        {{ item.label }}
      </RouterLink>
    </nav>

    <div class="settings-group">
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
