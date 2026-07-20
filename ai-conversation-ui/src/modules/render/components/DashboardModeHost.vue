<script setup lang="ts">
import { FullScreen, RefreshRight } from '@element-plus/icons-vue'
import { ref } from 'vue'
import { ResponsiveViewport } from '../../../application/layout'
import type { RenderModeHostProps } from '../model/render-app'

withDefaults(defineProps<RenderModeHostProps>(), {
  responsivePreset: 'dashboard',
})

const emit = defineEmits<{
  refresh: []
}>()

const hostRef = ref<HTMLElement | null>(null)

async function openFullscreen() {
  if (document.fullscreenElement) {
    await document.exitFullscreen()
    return
  }
  if (!hostRef.value) {
    return
  }
  await hostRef.value.requestFullscreen()
}
</script>

<template>
  <main ref="hostRef" class="dashboard-mode-host">
    <header class="dashboard-mode-host__header">
      <div class="dashboard-mode-host__heading">
        <h1>{{ title }}</h1>
        <p v-if="description">{{ description }}</p>
      </div>
      <div class="dashboard-mode-host__actions">
        <span v-if="lastRefreshedAt" class="dashboard-mode-host__updated-at">
          更新于 {{ lastRefreshedAt }}
        </span>
        <el-button
          circle
          plain
          :icon="RefreshRight"
          :loading="loading"
          :disabled="!refreshable"
          aria-label="刷新看板"
          title="刷新看板"
          @click="emit('refresh')"
        />
        <el-button
          circle
          plain
          :icon="FullScreen"
          aria-label="全屏显示"
          title="全屏显示"
          @click="openFullscreen"
        />
      </div>
    </header>
    <section class="dashboard-mode-host__content">
      <ResponsiveViewport :preset="responsivePreset">
        <slot />
      </ResponsiveViewport>
    </section>
  </main>
</template>

<style scoped>
.dashboard-mode-host {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  width: 100%;
  height: 100dvh;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--app-body-bg);
  color: var(--app-text);
}

.dashboard-mode-host:fullscreen {
  background: var(--app-body-bg);
}

.dashboard-mode-host__header {
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-4);
  min-height: var(--app-layout-header-height);
  padding: var(--app-space-2) var(--app-space-4);
  border-bottom: 1px solid var(--app-border-subtle);
  background: var(--app-surface-raised);
  box-shadow: var(--app-shadow-md);
}

.dashboard-mode-host__heading {
  min-width: 0;
}

.dashboard-mode-host__heading h1,
.dashboard-mode-host__heading p {
  overflow: hidden;
  margin: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-mode-host__heading h1 {
  color: var(--app-title);
  font-size: var(--app-font-size-title-md);
}

.dashboard-mode-host__heading p,
.dashboard-mode-host__updated-at {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.dashboard-mode-host__actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--app-space-2);
}

.dashboard-mode-host__content {
  min-width: 0;
  min-height: 0;
  padding: var(--app-space-3);
}

@media (max-width: 768px) {
  .dashboard-mode-host__updated-at,
  .dashboard-mode-host__heading p {
    display: none;
  }
}
</style>
