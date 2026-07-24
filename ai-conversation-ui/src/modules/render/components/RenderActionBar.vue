<script setup lang="ts">
import {
  Download,
  FullScreen,
  Operation,
  Printer,
  RefreshRight,
} from '@element-plus/icons-vue'
import type { RendererAction } from '../../../application/schema'

const props = withDefaults(defineProps<{
  actions?: RendererAction[]
  loading?: boolean
  refreshable?: boolean
}>(), {
  actions: () => [],
  loading: false,
  refreshable: true,
})

const emit = defineEmits<{
  action: [action: RendererAction]
}>()

const ACTION_ICONS = {
  download: Download,
  fullscreen: FullScreen,
  operation: Operation,
  print: Printer,
  refresh: RefreshRight,
} as const

function resolveActionIcon(action: RendererAction) {
  return action.icon && action.icon in ACTION_ICONS
    ? ACTION_ICONS[action.icon as keyof typeof ACTION_ICONS]
    : undefined
}

function isReloadAction(action: RendererAction) {
  return action.action === 'RELOAD'
}
</script>

<template>
  <el-button
    v-for="action in props.actions"
    :key="action.key"
    :type="action.type || undefined"
    :icon="resolveActionIcon(action)"
    :loading="isReloadAction(action) && loading"
    :disabled="action.disabled || (isReloadAction(action) && !refreshable)"
    :circle="Boolean(resolveActionIcon(action) && !action.name)"
    :title="action.title || action.name"
    :aria-label="action.title || action.name || action.key"
    @click="emit('action', action)"
  >
    <span v-if="action.name">{{ action.name }}</span>
  </el-button>
</template>
