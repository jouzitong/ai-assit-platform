<script setup lang="ts">
import { ArrowLeftBold, Setting, SwitchButton } from '@element-plus/icons-vue'
import { ElIcon } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { applyTheme, getSavedTheme, type ThemeName } from '../../../stores/theme'
import { clearSession, getStoredUser } from '../../../utils/session'

type SettingsSection = {
  key: string
  label: string
  icon: unknown
}

defineProps<{
  sections: SettingsSection[]
  activeSection: string
}>()

const router = useRouter()
const activeTheme = ref<ThemeName>('light')
const userMenuVisible = ref(false)

const emit = defineEmits<{
  navigateHome: []
  selectSection: [sectionKey: string]
}>()

const storedUser = computed<Record<string, unknown> | null>(() => getStoredUser<Record<string, unknown>>())
const displayName = computed(() => {
  const user = storedUser.value
  if (!user) {
    return '当前用户'
  }
  return String(user.nickname || user.realName || user.username || user.name || '当前用户')
})
const avatarText = computed(() => displayName.value.trim().slice(0, 1) || 'U')

function toggleUserMenu() {
  userMenuVisible.value = !userMenuVisible.value
}

function closeUserMenu() {
  userMenuVisible.value = false
}

function selectTheme(theme: ThemeName) {
  activeTheme.value = applyTheme(theme)
}

async function handleLogout() {
  closeUserMenu()
  clearSession()
  await router.push('/auth/login')
}

function handleDocumentClick(event: MouseEvent) {
  if (!userMenuVisible.value) {
    return
  }

  const target = event.target
  if (!(target instanceof Element)) {
    return
  }

  if (target.closest('.system-settings-user-menu-anchor')) {
    return
  }

  closeUserMenu()
}

onMounted(() => {
  activeTheme.value = getSavedTheme()
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
})
</script>

<template>
  <aside class="system-settings-sidebar">
    <button
      class="system-settings-back system-settings-back--top"
      type="button"
      aria-label="返回聊天首页"
      title="返回聊天首页"
      @click="emit('navigateHome')"
    >
      <el-icon><ArrowLeftBold /></el-icon>
    </button>

    <div class="system-settings-brand">
      <h1>系统设置</h1>
      <p>独立的业务配置工作台，不复用聊天页导航。</p>
    </div>

    <nav class="system-settings-nav">
      <button
        v-for="section in sections"
        :key="section.key"
        :class="['system-settings-nav__item', { 'is-active': activeSection === section.key }]"
        type="button"
        @click="emit('selectSection', section.key)"
      >
        <el-icon><component :is="section.icon" /></el-icon>
        <span>{{ section.label }}</span>
      </button>
    </nav>

    <div class="system-settings-sidebar__footer">
      <div class="system-settings-user-menu-anchor">
        <button class="system-settings-user" type="button" @click.stop="toggleUserMenu">
          <div class="system-settings-user__avatar">{{ avatarText }}</div>
          <div class="system-settings-user__copy">
            <strong>{{ displayName }}</strong>
            <span>用户设置</span>
          </div>
        </button>

        <div v-if="userMenuVisible" class="system-settings-user-menu" @click.stop>
          <div class="system-settings-user-menu__profile">
            <div class="system-settings-user-menu__avatar">{{ avatarText }}</div>
            <div class="system-settings-user-menu__identity">
              <div class="system-settings-user-menu__name">{{ displayName }}</div>
              <div class="system-settings-user-menu__status">在线</div>
            </div>
          </div>

          <div class="system-settings-user-menu__section">
            <div class="system-settings-user-menu__section-title">
              <el-icon><Setting /></el-icon>
              <span>主题</span>
            </div>
            <div class="system-settings-user-menu__theme-switch">
              <button
                :class="['system-settings-user-menu__theme-option', { 'is-active': activeTheme === 'dark' }]"
                type="button"
                @click="selectTheme('dark')"
              >
                暗色
              </button>
              <button
                :class="['system-settings-user-menu__theme-option', { 'is-active': activeTheme === 'light' }]"
                type="button"
                @click="selectTheme('light')"
              >
                浅色
              </button>
            </div>
          </div>

          <button class="system-settings-user-menu__item system-settings-user-menu__item--logout" type="button" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
            <span>登出</span>
          </button>
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.system-settings-sidebar {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 0;
  height: 100vh;
  padding: 20px 14px;
  border-right: 1px solid var(--system-sidebar-border);
  background: var(--system-sidebar-bg);
  overflow-y: auto;
}

.system-settings-brand h1 {
  margin: 6px 0 4px;
  color: var(--system-title);
}

.system-settings-brand p {
  margin: 0;
  color: var(--system-text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.system-settings-nav {
  display: grid;
  gap: 6px;
}

.system-settings-sidebar__footer {
  margin-top: auto;
  position: relative;
}

.system-settings-nav__item,
.system-settings-back {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--system-text);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.system-settings-nav__item.is-active {
  border-color: var(--system-accent-border);
  background: var(--system-accent-bg);
  color: var(--system-accent-text);
}

.system-settings-back {
  justify-content: flex-start;
  width: 40px;
  min-width: 40px;
  padding: 0;
  background: transparent;
  color: var(--system-text-soft);
}

.system-settings-back--top {
  margin-bottom: 2px;
}

.system-settings-back:hover {
  background: var(--system-accent-bg);
  color: var(--system-accent-text);
}

.system-settings-user-menu-anchor {
  position: relative;
  width: 100%;
}

.system-settings-user {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--system-border);
  border-radius: 14px;
  background: var(--system-surface-muted);
  color: var(--system-text);
  text-align: left;
  cursor: pointer;
}

.system-settings-user__avatar,
.system-settings-user-menu__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--system-accent-text);
  color: #08111f;
  font-weight: 700;
}

.system-settings-user__avatar {
  width: 28px;
  height: 28px;
  font-size: 12px;
}

.system-settings-user__copy strong {
  display: block;
  color: var(--system-title);
  font-size: 13px;
}

.system-settings-user__copy span {
  display: block;
  margin-top: 2px;
  color: var(--system-text-faint);
  font-size: 12px;
}

.system-settings-user-menu {
  position: absolute;
  bottom: calc(100% + 10px);
  left: 0;
  z-index: 20;
  width: 248px;
  padding: 12px;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
  box-shadow: var(--system-shadow);
}

.system-settings-user-menu__profile {
  display: flex;
  align-items: center;
  gap: 12px;
}

.system-settings-user-menu__avatar {
  width: 42px;
  height: 42px;
  font-size: 16px;
}

.system-settings-user-menu__name {
  color: var(--system-title);
  font-size: 14px;
  font-weight: 700;
}

.system-settings-user-menu__status {
  margin-top: 4px;
  color: var(--system-text-soft);
  font-size: 12px;
}

.system-settings-user-menu__section {
  margin-top: 14px;
}

.system-settings-user-menu__section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--system-title);
  font-size: 13px;
  font-weight: 600;
}

.system-settings-user-menu__theme-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
}

.system-settings-user-menu__theme-option,
.system-settings-user-menu__item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  min-height: 36px;
  padding: 0 10px;
  border: none;
  border-radius: 12px;
  background: var(--system-surface-muted);
  color: var(--system-text);
  font-size: 13px;
  cursor: pointer;
}

.system-settings-user-menu__theme-option.is-active {
  background: var(--system-accent-text);
  color: #08111f;
  font-weight: 700;
}

.system-settings-user-menu__item {
  justify-content: flex-start;
  margin-top: 12px;
}

.system-settings-user-menu__item--logout:hover,
.system-settings-user-menu__theme-option:hover {
  filter: brightness(1.04);
}

@media (max-width: 960px) {
  .system-settings-sidebar {
    height: auto;
    border-right: none;
    border-bottom: 1px solid var(--system-sidebar-border);
    overflow-y: visible;
  }
}
</style>
