<script setup lang="ts">
import type { ListRendererSchema, RendererAction } from '../types'

defineProps<{
  schema: ListRendererSchema
}>()

const emit = defineEmits<{
  action: [action: RendererAction]
}>()

const toButtonType = (type?: RendererAction['type']) => type || 'default'
</script>

<template>
  <div class="list-header-bar">
    <div class="list-header-bar__main">
      <div>
        <h2 class="list-header-bar__title">{{ schema.title }}</h2>
        <div class="list-header-bar__meta">
          <el-tag size="small" effect="plain">{{ schema.component }}</el-tag>
          <el-tag v-if="schema.datasource?.model" size="small" type="info" effect="plain">
            {{ schema.datasource.model }}
          </el-tag>
        </div>
      </div>
      <div v-if="schema.actions?.length" class="list-header-bar__actions">
        <el-button
          v-for="action in schema.actions"
          :key="action.key"
          :type="toButtonType(action.type)"
          :disabled="action.disabled"
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
  gap: 12px;
}

.list-header-bar__main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.list-header-bar__title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--app-title);
}

.list-header-bar__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.list-header-bar__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 768px) {
  .list-header-bar__main {
    flex-direction: column;
  }

  .list-header-bar__actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
