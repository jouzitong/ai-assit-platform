<script setup lang="ts">
import { computed } from 'vue'
import type { RendererAction } from '../../../application/schema'
import type { RenderAppMode, RenderModeHostProps } from '../model/render-app'
import { findRenderMode } from '../model/render-mode-registry'

const props = defineProps<RenderModeHostProps & {
  mode: RenderAppMode
}>()

const emit = defineEmits<{
  refresh: []
  action: [action: RendererAction]
  'filters-change': [filters: Record<string, unknown>]
  'filters-submit': []
  'filters-reset': []
}>()

const modeComponent = computed(() => findRenderMode(props.mode)?.component)
const hostProps = computed(() => ({
  title: props.title,
  description: props.description,
  formMode: props.formMode,
  loading: props.loading,
  refreshable: props.refreshable,
  lastRefreshedAt: props.lastRefreshedAt,
  responsivePreset: props.responsivePreset,
  compact: props.compact,
  actions: props.actions,
  filters: props.filters,
  filterValues: props.filterValues,
}))
</script>

<template>
  <component
    :is="modeComponent"
    v-bind="hostProps"
    @refresh="emit('refresh')"
    @action="emit('action', $event)"
    @filters-change="emit('filters-change', $event)"
    @filters-submit="emit('filters-submit')"
    @filters-reset="emit('filters-reset')"
  >
    <slot />
  </component>
</template>
