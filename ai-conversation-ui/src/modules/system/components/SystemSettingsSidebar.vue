<script setup lang="ts">
import { CaretRight, Setting, SwitchButton } from '@element-plus/icons-vue'
import { ElIcon } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import brandLogoDark from '../../../assets/icons/brand-logo-dark.svg'
import brandLogo from '../../../assets/icons/brand-logo.svg'
import brandMark from '../../../assets/icons/brand-mark.svg'
import { applyTheme, getSavedTheme, type ThemeName } from '../../../stores/theme'
import { clearSession, getStoredUser } from '../../../utils/session'

type SettingsSection = {
  key: string
  label: string
  icon: unknown
  disabled?: boolean
  children?: SettingsSection[]
}

const props = defineProps<{
  sections: SettingsSection[]
  activeSection: string
  collapsed?: boolean
}>()

const router = useRouter()
const activeTheme = ref<ThemeName>('light')
const userMenuVisible = ref(false)
const userMenuAnchorRef = ref<HTMLElement | null>(null)
const userMenuRef = ref<HTMLElement | null>(null)
const userMenuStyle = ref<Record<string, string>>({})
const expandedSectionKeys = ref(new Set<string>())

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

function isGroupActive(section: SettingsSection) {
  return props.activeSection === section.key || section.children?.some(item => item.key === props.activeSection) === true
}

function isExpanded(section: SettingsSection) {
  return expandedSectionKeys.value.has(section.key)
}

function expandActiveGroup() {
  const activeGroup = props.sections.find(section => section.children?.some(item => item.key === props.activeSection))
  if (!activeGroup || expandedSectionKeys.value.has(activeGroup.key)) {
    return
  }
  expandedSectionKeys.value = new Set([...expandedSectionKeys.value, activeGroup.key])
}

function handleGroupClick(section: SettingsSection) {
  if (!section.children?.length) {
    return
  }
  if (props.collapsed) {
    emit('selectSection', section.children[0].key)
    return
  }

  const nextExpanded = new Set(expandedSectionKeys.value)
  if (nextExpanded.has(section.key)) {
    nextExpanded.delete(section.key)
  } else {
    nextExpanded.add(section.key)
  }
  expandedSectionKeys.value = nextExpanded
}

function updateUserMenuPosition() {
  const anchor = userMenuAnchorRef.value
  if (!anchor) {
    return
  }

  const rect = anchor.getBoundingClientRect()
  const menuWidth = userMenuRef.value?.offsetWidth ?? 248
  const menuHeight = userMenuRef.value?.offsetHeight ?? 220
  const viewportPadding = 12

  let left = props.collapsed ? rect.right + 12 : rect.left
  left = Math.min(left, window.innerWidth - menuWidth - viewportPadding)
  left = Math.max(viewportPadding, left)

  let top = rect.top - menuHeight - 12
  if (top < viewportPadding) {
    top = rect.bottom + 12
  }
  top = Math.min(top, window.innerHeight - menuHeight - viewportPadding)
  top = Math.max(viewportPadding, top)

  userMenuStyle.value = {
    position: 'fixed',
    left: `${left}px`,
    top: `${top}px`,
  }
}

async function toggleUserMenu() {
  userMenuVisible.value = !userMenuVisible.value
  if (userMenuVisible.value) {
    await nextTick()
    updateUserMenuPosition()
  }
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

  if (target.closest('.system-settings-user-menu')) {
    return
  }

  if (target.closest('.system-settings-user-menu-anchor')) {
    return
  }

  closeUserMenu()
}

function handleViewportChange() {
  if (!userMenuVisible.value) {
    return
  }
  updateUserMenuPosition()
}

onMounted(() => {
  activeTheme.value = getSavedTheme()
  expandActiveGroup()
  document.addEventListener('click', handleDocumentClick)
  window.addEventListener('resize', handleViewportChange)
  window.addEventListener('scroll', handleViewportChange, true)
})

watch(() => props.activeSection, expandActiveGroup)

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  window.removeEventListener('resize', handleViewportChange)
  window.removeEventListener('scroll', handleViewportChange, true)
})
</script>

<template>
  <aside class="system-settings-sidebar" :class="{ 'system-settings-sidebar--collapsed': collapsed }">
    <button
      class="system-settings-logo"
      type="button"
      aria-label="返回聊天首页"
      title="返回聊天首页"
      @click="emit('navigateHome')"
    >
      <img
        class="system-settings-logo__image"
        :src="collapsed ? brandMark : (activeTheme === 'dark' ? brandLogoDark : brandLogo)"
        :alt="collapsed ? '智能问数' : '智能问数 ZG'"
      />
    </button>

    <div class="system-settings-brand">
      <h1>系统设置</h1>
    </div>

    <nav class="system-settings-nav">
      <div v-for="section in sections" :key="section.key" class="system-settings-nav__group">
        <button
          :class="['system-settings-nav__item', { 'is-active': isGroupActive(section), 'is-group': section.children?.length, 'is-disabled': section.disabled }]"
          type="button"
          :disabled="section.disabled"
          :aria-expanded="section.children?.length ? isExpanded(section) : undefined"
          @click="section.children?.length ? handleGroupClick(section) : emit('selectSection', section.key)"
        >
          <el-icon><component :is="section.icon" /></el-icon>
          <span v-if="!collapsed" class="system-settings-nav__label">{{ section.label }}</span>
          <el-icon v-if="section.children?.length && !collapsed" :class="['system-settings-nav__caret', { 'is-expanded': isExpanded(section) }]">
            <CaretRight />
          </el-icon>
        </button>

        <div v-if="section.children?.length && isExpanded(section) && !collapsed" class="system-settings-nav__children">
          <button
            v-for="child in section.children"
            :key="child.key"
            :class="['system-settings-nav__child', { 'is-active': activeSection === child.key, 'is-disabled': child.disabled }]"
            type="button"
            :disabled="child.disabled"
            @click="emit('selectSection', child.key)"
          >
            {{ child.label }}
          </button>
        </div>
      </div>
    </nav>

    <div class="system-settings-sidebar__footer">
      <div ref="userMenuAnchorRef" class="system-settings-user-menu-anchor">
        <button class="system-settings-user" type="button" @click.stop="toggleUserMenu">
          <div class="system-settings-user__avatar">{{ avatarText }}</div>
          <div v-if="!collapsed" class="system-settings-user__copy">
            <strong>{{ displayName }}</strong>
            <span>用户设置</span>
          </div>
        </button>

        <Teleport to="body">
          <div
            v-if="userMenuVisible"
            ref="userMenuRef"
            class="system-settings-user-menu"
            :style="userMenuStyle"
            @click.stop
          >
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
        </Teleport>
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
  overflow-x: visible;
}

.system-settings-brand h1 {
  margin: 6px 0 4px;
  color: var(--system-title);
}

.system-settings-nav {
  display: grid;
  gap: 6px;
}

.system-settings-nav__group {
  display: grid;
  gap: 4px;
}

.system-settings-sidebar__footer {
  margin-top: auto;
  position: relative;
}

.system-settings-nav__item {
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
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.system-settings-nav__label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.system-settings-nav__caret {
  margin-left: auto;
  transition: transform 0.2s ease;
}

.system-settings-nav__caret.is-expanded {
  transform: rotate(90deg);
}

.system-settings-nav__children {
  display: grid;
  gap: 2px;
  margin-left: 20px;
  padding-left: 14px;
  border-left: 1px solid var(--system-border-subtle);
}

.system-settings-nav__child {
  min-height: 32px;
  padding: 0 10px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--system-text-soft);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.system-settings-nav__child:hover,
.system-settings-nav__child:focus-visible {
  background: var(--system-surface-muted);
  color: var(--system-title);
  outline: none;
}

.system-settings-nav__child.is-active {
  background: var(--system-accent-bg);
  color: var(--system-accent-text);
  font-weight: 600;
}

.system-settings-nav__item:disabled,
.system-settings-nav__child:disabled {
  color: var(--system-text-faint);
  cursor: not-allowed;
  opacity: 0.65;
}

.system-settings-logo {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  line-height: 0;
  cursor: pointer;
  overflow: hidden;
  transition: box-shadow 0.2s ease;
}

.system-settings-logo:focus-visible {
  outline: 2px solid var(--system-accent-text);
  outline-offset: 2px;
}

.system-settings-logo:hover {
  box-shadow: var(--system-shadow);
}

.system-settings-logo__image {
  display: block;
  width: 100%;
  height: auto;
  max-width: none;
}

.system-settings-nav__item.is-active {
  border-color: var(--system-accent-border);
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

.system-settings-sidebar--collapsed {
  align-items: center;
  padding: 20px 12px;
}

.system-settings-sidebar--collapsed .system-settings-logo {
  width: 40px;
}

.system-settings-sidebar--collapsed .system-settings-logo__image {
  width: 40px;
}

.system-settings-sidebar--collapsed .system-settings-brand {
  display: none;
}

.system-settings-sidebar--collapsed .system-settings-nav {
  width: 100%;
}

.system-settings-sidebar--collapsed .system-settings-nav__item {
  justify-content: center;
  padding: 0;
}

.system-settings-sidebar--collapsed .system-settings-sidebar__footer {
  width: 100%;
}

.system-settings-sidebar--collapsed .system-settings-user {
  justify-content: center;
  padding: 10px 0;
}

.system-settings-user__avatar,
.system-settings-user-menu__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--system-accent-bg-strong);
  color: var(--system-accent-text);
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
  z-index: 40;
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
