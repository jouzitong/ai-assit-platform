<script setup lang="ts">
import { computed } from 'vue'
import { getColumnMinWidth, getFieldValue } from '../schema'
import type { ListRendererSchema, RendererAction } from '../types'

const props = defineProps<{
  schema: ListRendererSchema
  records: Record<string, unknown>[]
  loading?: boolean
}>()

const emit = defineEmits<{
  itemAction: [payload: { action: RendererAction; row: Record<string, unknown> }]
}>()

const fields = computed(() => props.schema.fields || [])
const rowActions = computed(() => props.schema.list_config?.actionColumns || [])
const itemType = computed(() => props.schema.list_config?.itemType || 'table')

const handleItemAction = (action: RendererAction, row: Record<string, unknown>) => {
  emit('itemAction', { action, row })
}

const getRowKey = (row: Record<string, unknown>, index: number) =>
  String(row.id ?? row.key ?? row.code ?? `row-${index}`)
</script>

<template>
    <div class="list-data-view">
      <div v-if="itemType === 'card'" class="list-data-view__cards" v-loading="loading">
      <el-card
        v-for="(row, index) in records"
        :key="getRowKey(row, index)"
        shadow="hover"
        class="list-data-view__card"
      >
        <div class="list-data-view__card-content">
          <div v-for="field in fields" :key="field.key" class="list-data-view__card-field">
            <span class="list-data-view__label">{{ field.label }}</span>
            <span class="list-data-view__value">{{ getFieldValue(row, field) }}</span>
          </div>
        </div>
        <div v-if="rowActions.length" class="list-data-view__card-actions">
          <el-button
            v-for="action in rowActions"
            :key="action.key"
            link
            :type="action.type || 'primary'"
            @click="handleItemAction(action, row)"
          >
            {{ action.name }}
          </el-button>
        </div>
      </el-card>
    </div>

    <el-table v-else :data="records" border class="list-data-view__table" v-loading="loading">
      <el-table-column
        v-for="field in fields"
        :key="field.key"
        :label="field.label"
        :prop="field.key"
        :min-width="getColumnMinWidth(field)"
        :class-name="field.options?.className"
        :align="String(field.options?.styles?.['text-align'] || 'left')"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span>{{ getFieldValue(row, field) }}</span>
        </template>
      </el-table-column>

      <el-table-column v-if="rowActions.length" label="操作" min-width="180" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            v-for="action in rowActions"
            :key="action.key"
            link
            :type="action.type || 'primary'"
            @click="handleItemAction(action, row)"
          >
            {{ action.name }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.list-data-view {
  min-width: 0;
}

.list-data-view__cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.list-data-view__card {
  border-radius: 24px;
}

.list-data-view__card-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.list-data-view__card-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.list-data-view__label {
  font-size: 12px;
  color: var(--app-text-muted);
  text-transform: uppercase;
}

.list-data-view__value {
  font-size: 14px;
  color: var(--app-title);
  word-break: break-word;
}

.list-data-view__card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}

.list-data-view__table {
  border-radius: 24px;
  overflow: hidden;
}
</style>
