<script setup lang="ts">
import { resolveRendererActionIcon } from '../../../application/renderers/action'
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

function isReloadAction(action: RendererAction) {
  return action.action === 'RELOAD'
}
</script>

<template>
  <el-button
    v-for="action in props.actions"
    :key="action.key"
    :type="action.options?.type"
    :icon="resolveRendererActionIcon(action)"
    :style="action.options?.style"
    :class="action.options?.class"
    :loading="isReloadAction(action) && loading"
    :disabled="isReloadAction(action) && !refreshable"
    :title="action.name"
    :aria-label="action.name"
    @click="emit('action', action)"
  >
    {{ action.name }}
  </el-button>
</template>
