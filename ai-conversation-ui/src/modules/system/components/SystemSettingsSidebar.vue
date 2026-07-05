<script setup lang="ts">
import { ElIcon } from 'element-plus'

type SettingsSection = {
  key: string
  label: string
  icon: unknown
}

defineProps<{
  sections: SettingsSection[]
  activeSection: string
}>()

const emit = defineEmits<{
  navigateHome: []
  selectSection: [sectionKey: string]
}>()
</script>

<template>
  <aside class="system-settings-sidebar">
    <button class="system-settings-back system-settings-back--top" type="button" @click="emit('navigateHome')">
      返回聊天首页
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
  border-right: 1px solid #e5e7eb;
  background: linear-gradient(180deg, #ffffff 0%, #f7f8fa 100%);
  overflow-y: auto;
}

.system-settings-brand h1 {
  margin: 6px 0 4px;
  color: #111827;
}

.system-settings-brand p {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.55;
}

.system-settings-nav {
  display: grid;
  gap: 6px;
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
  color: #374151;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.system-settings-nav__item.is-active {
  border-color: #dbe4ff;
  background: #eef4ff;
  color: #1d4ed8;
}

.system-settings-back {
  justify-content: center;
  background: #111827;
  color: #fff;
}

.system-settings-back--top {
  margin-bottom: 2px;
}

@media (max-width: 960px) {
  .system-settings-sidebar {
    height: auto;
    border-right: none;
    border-bottom: 1px solid #e5e7eb;
    overflow-y: visible;
  }
}
</style>
