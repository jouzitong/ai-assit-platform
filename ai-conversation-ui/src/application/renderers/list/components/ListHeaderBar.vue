<script setup lang="ts">
import { computed } from 'vue'
import { resolveRendererActionIcon } from '../../action'
import type { ListRendererSchema, RendererAction } from '../types'

const props = withDefaults(defineProps<{
  schema: ListRendererSchema
  developerMode?: boolean
  developerActions?: RendererAction[]
}>(), {
  developerMode: false,
  developerActions: () => [],
})

const emit = defineEmits<{
  action: [action: RendererAction]
}>()

const actions = computed(() => {
  const runtimeActionKeys = new Set(props.developerActions.map(action => action.key))
  return [
    ...(props.schema.actions || []).filter(action => !runtimeActionKeys.has(action.key)),
    ...props.developerActions,
  ]
})

</script>

<template>
  <div class="list-header-bar">
    <div class="list-header-bar__main">
      <div>
        <h2 class="list-header-bar__title">{{ schema.title }}</h2>
      </div>
      <div v-if="developerMode || actions.length" class="list-header-bar__actions">
        <el-tag v-if="developerMode" type="warning" effect="dark">开发模式</el-tag>
        <el-button
          v-for="action in actions"
          :key="action.key"
          :type="action.options?.type"
          :icon="resolveRendererActionIcon(action)"
          :style="action.options?.style"
          :class="action.options?.class"
          :title="action.name"
          :aria-label="action.name"
          @click="emit('action', action)"
        >
          {{ action.name }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.list-header-bar {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-3);
}

.list-header-bar__main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--app-space-4);
}

.list-header-bar__title {
  margin: 0;
  font-size: var(--app-font-size-title-lg);
  font-weight: 700;
  color: var(--app-title);
}

.list-header-bar__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--app-space-3);
}

@container application-list-layout (max-width: 768px) {
  .list-header-bar__main {
    flex-direction: column;
  }

  .list-header-bar__actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
