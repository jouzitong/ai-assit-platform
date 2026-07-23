<script setup lang="ts">
import { computed } from 'vue'
import type { RenderAppMode, RenderModeHostProps } from '../model/render-app'
import { findRenderMode } from '../model/render-mode-registry'

const props = defineProps<RenderModeHostProps & {
  mode: RenderAppMode
}>()

const emit = defineEmits<{
  refresh: []
}>()

const modeComponent = computed(() => findRenderMode(props.mode)?.component)
const hostProps = computed(() => ({
  title: props.title,
  description: props.description,
  loading: props.loading,
  refreshable: props.refreshable,
  lastRefreshedAt: props.lastRefreshedAt,
  responsivePreset: props.responsivePreset,
  compact: props.compact,
}))
</script>

<template>
  <component
    :is="modeComponent"
    v-bind="hostProps"
    @refresh="emit('refresh')"
  >
    <slot />
  </component>
</template>
