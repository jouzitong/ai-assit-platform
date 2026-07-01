<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SidebarNav from './SidebarNav.vue'
import { applyTheme as setTheme, getSavedTheme } from '../assets/style/themes/theme-manager'
import { showPopup } from '../utils/popup'

const route = useRoute()
const router = useRouter()
const showShell = computed(() => !route.meta?.plainLayout)
const isDarkTheme = ref(false)

const pageTitle = computed(() => route.meta?.title || 'AI 助手')
const pageSubtitle = computed(() => {
  if (route.path.startsWith('/knowledge')) return '知识资产工作台'
  if (route.path.startsWith('/settings/system')) return '系统设置'
  if (route.path.startsWith('/settings/profile')) return '个人设置'
  if (route.path.startsWith('/query')) return '智能问数'
  return '聊天页面完整示例'
})

function setShellScrollLock(locked) {
  document.documentElement.classList.toggle('layout-lock-scroll', locked)
  document.body.classList.toggle('layout-lock-scroll', locked)
}

function applyTheme(nextIsDark) {
  const nextTheme = setTheme(nextIsDark ? 'dark' : 'light')
  isDarkTheme.value = nextTheme === 'dark'
}

function toggleTheme() {
  applyTheme(!isDarkTheme.value)
}

function openShareTip() {
  showPopup.info('规划中')
}

function openSystemSettings() {
  router.push('/settings/system/overview')
}

onMounted(() => {
  applyTheme(getSavedTheme() === 'dark')
})

watch(
  showShell,
  (locked) => {
    setShellScrollLock(locked)
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  setShellScrollLock(false)
})
</script>

<template>
  <div v-if="showShell" class="app-shell">
    <SidebarNav />

    <section class="shell-main">
      <header class="shell-topbar">
        <div class="shell-title">
          <h1>{{ pageTitle }}</h1>
          <p>{{ pageSubtitle }}</p>
        </div>

        <div class="shell-actions">
          <button class="shell-action-btn" type="button" @click="toggleTheme">
            {{ isDarkTheme ? '☀ 主题' : '☾ 主题' }}
          </button>
          <button class="shell-action-btn" type="button" @click="openShareTip">
            分享
          </button>
          <button class="shell-action-btn" type="button" @click="openSystemSettings">
            设置
          </button>
        </div>
      </header>

      <section class="content-shell">
        <section class="content">
          <RouterView />
        </section>
      </section>
    </section>
  </div>

  <RouterView v-else />
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  background: var(--theme-bg-canvas);
}

.shell-main {
  flex: 1;
  min-width: 0;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.shell-topbar {
  height: 58px;
  padding: 0 18px;
  border-bottom: 1px solid var(--color-border);
  background: var(--theme-bg-canvas);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-shrink: 0;
}

.shell-title {
  min-width: 0;
}

.shell-title h1 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
}

.shell-title p {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

.shell-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.shell-action-btn {
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

.shell-action-btn:hover {
  background: color-mix(in srgb, var(--theme-bg-surface-muted) 78%, white 22%);
}

.content-shell {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.content {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: auto;
}

.content > :deep(*) {
  min-height: 0;
}
</style>
