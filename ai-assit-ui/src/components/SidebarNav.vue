<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearSession, THEME_STORAGE_KEY } from '../utils/session'

const route = useRoute()
const router = useRouter()
const settingsOpen = ref(false)
const isDarkTheme = ref(false)

const menus = [
  { path: '/home', label: 'AI 首页', short: '首页' },
  { path: '/query', label: '智能问数', short: '问数' },
  { path: '/emp/attendance', label: '考勤看板', short: '考勤' },
  { path: '/emp/performance', label: '绩效洞察', short: '绩效' },
  { path: '/emp/cost', label: '人力成本分析', short: '成本' }
]

const settingsMenus = [
  { path: '/settings/profile', label: '个人管理', short: '个人' },
  { path: '/settings/system', label: '系统管理', short: '系统' }
]

const themeLabel = computed(() => (isDarkTheme.value ? '切换浅色主题' : '切换深色主题'))

function applyTheme(nextIsDark) {
  isDarkTheme.value = nextIsDark
  document.documentElement.dataset.theme = nextIsDark ? 'dark' : 'light'
  window.localStorage.setItem(THEME_STORAGE_KEY, nextIsDark ? 'dark' : 'light')
}

function toggleTheme() {
  applyTheme(!isDarkTheme.value)
  settingsOpen.value = false
}

async function handleLogout() {
  settingsOpen.value = false
  clearSession()
  await router.push('/login')
}

onMounted(() => {
  const savedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
  applyTheme(savedTheme === 'dark')
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
        :class="{ active: route.path === item.path }"
        :title="item.label"
      >
        {{ item.label }}
      </RouterLink>
    </nav>

    <div class="settings-group">
      <button
        class="avatar-trigger"
        :class="{ active: settingsMenus.some((item) => item.path === route.path) || settingsOpen }"
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
          :class="{ active: route.path === item.path }"
          :title="item.label"
          @click="settingsOpen = false"
        >
          {{ item.label }}
        </RouterLink>

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
