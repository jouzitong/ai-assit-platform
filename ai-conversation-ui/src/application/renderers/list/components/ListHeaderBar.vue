<script setup lang="ts">
import { Operation } from '@element-plus/icons-vue'
import type { ListRendererSchema, RendererAction } from '../types'

defineProps<{
  schema: ListRendererSchema
}>()

const emit = defineEmits<{
  action: [action: RendererAction]
}>()

const toButtonType = (type?: RendererAction['type']) => type || 'default'
const resolveActionIcon = (action: RendererAction) => {
  if (action.icon === 'operation') {
    return Operation
  }
  return undefined
}
</script>

<template>
  <div class="list-header-bar">
    <div class="list-header-bar__main">
      <div>
        <h2 class="list-header-bar__title">{{ schema.title }}</h2>
      </div>
      <div v-if="schema.actions?.length" class="list-header-bar__actions">
        <el-button
          v-for="action in schema.actions"
          :key="action.key"
          :type="toButtonType(action.type)"
          :disabled="action.disabled"
          :circle="Boolean(resolveActionIcon(action) && !action.name)"
          :title="action.name"
          @click="emit('action', action)"
        >
          <el-icon v-if="resolveActionIcon(action)">
            <component :is="resolveActionIcon(action)" />
          </el-icon>
          <span v-if="action.name">{{ action.name }}</span>
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
