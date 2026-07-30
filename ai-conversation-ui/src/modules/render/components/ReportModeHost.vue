<script setup lang="ts">
import { computed } from 'vue'
import type { RenderModeHostProps } from '../model/render-app'
import RenderActionBar from './RenderActionBar.vue'
import RenderGlobalFilterBar from './RenderGlobalFilterBar.vue'

const props = defineProps<RenderModeHostProps>()

const emit = defineEmits<{
  refresh: []
  action: [action: NonNullable<RenderModeHostProps['actions']>[number]]
  'filters-change': [filters: Record<string, unknown>]
  'filters-submit': []
  'filters-reset': []
}>()

const resolvedActions = computed(() => props.actions?.length
  ? props.actions
  : [
      { key: 'refresh', name: '刷新', action: 'RELOAD', options: { icon: 'refresh' } },
      { key: 'print', name: '打印', action: 'PRINT', options: { type: 'primary', icon: 'print' } },
    ])

</script>

<template>
  <main class="report-mode-host">
    <header class="report-mode-host__header">
      <div>
        <h1>{{ title }}</h1>
      </div>
      <div class="report-mode-host__actions">
        <RenderActionBar
          :actions="resolvedActions"
          :loading="loading"
          :refreshable="refreshable"
          @action="emit('action', $event)"
        />
      </div>
      <RenderGlobalFilterBar
        v-if="filters?.length"
        class="report-mode-host__filters"
        :filters="filters"
        :model-value="filterValues || {}"
        @update:model-value="emit('filters-change', $event)"
        @submit="emit('filters-submit')"
        @reset="emit('filters-reset')"
      />
    </header>
    <section class="report-mode-host__content">
      <slot />
    </section>
  </main>
</template>

<style scoped>
.report-mode-host {
  width: 100%;
  min-height: 100dvh;
  padding: var(--app-space-5);
  background: var(--app-body-bg);
  color: var(--app-text);
}

.report-mode-host__header,
.report-mode-host__content {
  width: min(100%, 1600px);
  margin: 0 auto;
}

.report-mode-host__header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    'heading actions'
    'filters filters';
  align-items: center;
  gap: var(--app-space-4);
  margin-bottom: var(--app-space-5);
  padding: var(--app-space-4);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface-raised);
  box-shadow: var(--app-shadow-md);
}

.report-mode-host__header > div:first-child {
  grid-area: heading;
}

.report-mode-host__header h1 {
  margin: 0;
  color: var(--app-title);
  font-size: var(--app-font-size-title-lg);
}

.report-mode-host__actions {
  grid-area: actions;
  display: flex;
  flex: none;
  gap: var(--app-space-2);
}

.report-mode-host__filters {
  grid-area: filters;
  min-width: 0;
  container: application-list-layout / inline-size;
}

.report-mode-host__filters :deep(.list-filter-bar) {
  border: 0;
  background: transparent;
}

.report-mode-host__content {
  min-width: 0;
  min-height: 0;
  padding: var(--app-space-5);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface-solid);
  box-shadow: var(--app-shadow-sm);
}

@media (max-width: 768px) {
  .report-mode-host {
    padding: var(--app-space-3);
  }

  .report-mode-host__header {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      'heading'
      'filters'
      'actions';
    align-items: flex-start;
  }

  .report-mode-host__actions {
    width: 100%;
  }
}

@media print {
  .report-mode-host {
    min-height: auto;
    padding: 0;
    background: var(--app-surface-solid);
  }

  .report-mode-host__header {
    position: static;
    margin-bottom: 12mm;
    padding: 0;
    border: 0;
    box-shadow: none;
  }

  .report-mode-host__actions {
    display: none;
  }

  .report-mode-host__content {
    width: 100%;
    padding: 0;
    border: 0;
    box-shadow: none;
  }
}
</style>
