<script setup lang="ts">
import { FullScreen } from '@element-plus/icons-vue'
import { computed, ref } from 'vue'
import { ResponsiveViewport } from '../../../application/layout'
import type { RenderModeHostProps } from '../model/render-app'
import RenderActionBar from './RenderActionBar.vue'
import RenderGlobalFilterBar from './RenderGlobalFilterBar.vue'

const props = withDefaults(defineProps<RenderModeHostProps>(), {
  responsivePreset: 'dashboard',
})

const emit = defineEmits<{
  refresh: []
  action: [action: NonNullable<RenderModeHostProps['actions']>[number]]
  'filters-change': [filters: Record<string, unknown>]
  'filters-submit': []
  'filters-reset': []
}>()

const hostRef = ref<HTMLElement | null>(null)
const resolvedActions = computed(() => props.actions?.length
  ? props.actions
  : [{ key: 'refresh', name: '刷新', action: 'RELOAD', options: { icon: 'refresh' } }])

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
  <main ref="hostRef" :class="['dashboard-mode-host', { 'is-compact': compact }]">
    <header v-if="!compact" class="dashboard-mode-host__header">
      <div class="dashboard-mode-host__heading">
        <h1>{{ title }}</h1>
        <p v-if="description">{{ description }}</p>
      </div>
      <div class="dashboard-mode-host__actions">
        <span v-if="lastRefreshedAt" class="dashboard-mode-host__updated-at">
          更新于 {{ lastRefreshedAt }}
        </span>
        <RenderActionBar
          :actions="resolvedActions"
          :loading="loading"
          :refreshable="refreshable"
          @action="emit('action', $event)"
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
      <RenderGlobalFilterBar
        v-if="filters?.length"
        class="dashboard-mode-host__filters"
        :filters="filters"
        :model-value="filterValues || {}"
        @update:model-value="emit('filters-change', $event)"
        @submit="emit('filters-submit')"
        @reset="emit('filters-reset')"
      />
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

.dashboard-mode-host.is-compact {
  grid-template-rows: minmax(0, 1fr);
}

.dashboard-mode-host:fullscreen {
  background: var(--app-body-bg);
}

.dashboard-mode-host__header {
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    'heading actions'
    'filters filters';
  align-items: center;
  gap: var(--app-space-4);
  min-height: var(--app-layout-header-height);
  padding: var(--app-space-2) var(--app-space-4);
  border-bottom: 1px solid var(--app-border-subtle);
  background: var(--app-surface-raised);
  box-shadow: var(--app-shadow-md);
}

.dashboard-mode-host__heading {
  grid-area: heading;
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
  grid-area: actions;
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--app-space-2);
}

.dashboard-mode-host__filters {
  grid-area: filters;
  min-width: 0;
}

.dashboard-mode-host__filters :deep(.list-filter-bar) {
  border: 0;
  background: transparent;
}

.dashboard-mode-host__content {
  min-width: 0;
  min-height: 0;
  padding: var(--app-space-3);
}

.dashboard-mode-host.is-compact .dashboard-mode-host__content {
  padding: 0;
}

@media (max-width: 768px) {
  .dashboard-mode-host__updated-at,
  .dashboard-mode-host__heading p {
    display: none;
  }
}
</style>
