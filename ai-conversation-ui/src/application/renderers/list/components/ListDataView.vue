<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { resolveRendererActionIcon } from '../../action'
import { getColumnMinWidth, getFieldDisplayValue, getFieldName } from '../schema'
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
const tableMotionClass = ref('')
let hasRenderedRecords = false
let motionTimer: ReturnType<typeof setTimeout> | undefined

watch(
  () => props.records,
  () => {
    if (!hasRenderedRecords) {
      hasRenderedRecords = true
      return
    }

    window.clearTimeout(motionTimer)
    tableMotionClass.value = tableMotionClass.value === 'list-data-view__table-row--motion-a'
      ? 'list-data-view__table-row--motion-b'
      : 'list-data-view__table-row--motion-a'
    motionTimer = window.setTimeout(() => {
      tableMotionClass.value = ''
    }, 420)
  },
)

onBeforeUnmount(() => window.clearTimeout(motionTimer))

const handleItemAction = (action: RendererAction, row: Record<string, unknown>) => {
  emit('itemAction', { action, row })
}

const getRowKey = (row: Record<string, unknown>, index: number) =>
  String(row.id ?? row.key ?? row.code ?? `row-${index}`)

const getRowClassName = ({ rowIndex }: { rowIndex: number }) =>
  tableMotionClass.value

const getRowStyle = ({ rowIndex }: { rowIndex: number }) => ({
  '--list-row-motion-delay': `${Math.min(rowIndex * 32, 288)}ms`,
})
</script>

<template>
  <div class="list-data-view" :aria-busy="loading || undefined">
    <transition-group v-if="itemType === 'card'" tag="div" name="list-card" class="list-data-view__cards">
      <el-card
        v-for="(row, index) in records"
        :key="getRowKey(row, index)"
        shadow="hover"
        class="list-data-view__card"
      >
        <div class="list-data-view__card-content">
          <div v-for="field in fields" :key="field.key" class="list-data-view__card-field">
            <span class="list-data-view__label">{{ getFieldName(field) }}</span>
            <span class="list-data-view__value">{{ getFieldDisplayValue(row, field) }}</span>
          </div>
        </div>
        <div v-if="rowActions.length" class="list-data-view__card-actions">
          <el-button
            v-for="action in rowActions"
            :key="action.key"
            link
            :type="action.options?.type || 'primary'"
            :icon="resolveRendererActionIcon(action)"
            :style="action.options?.style"
            :class="action.options?.class"
            @click="handleItemAction(action, row)"
          >
            {{ action.name }}
          </el-button>
        </div>
      </el-card>
    </transition-group>

    <el-table
      v-else
      :data="records"
      :row-key="getRowKey"
      :row-class-name="getRowClassName"
      :row-style="getRowStyle"
      border
      class="list-data-view__table"
    >
      <el-table-column
        v-for="field in fields"
        :key="field.key"
        :label="getFieldName(field)"
        :prop="field.key"
        :min-width="getColumnMinWidth(field)"
        :class-name="field.options?.className"
        :align="String(field.options?.styles?.['text-align'] || 'left')"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span>{{ getFieldDisplayValue(row, field) }}</span>
        </template>
      </el-table-column>

      <el-table-column v-if="rowActions.length" label="操作" min-width="180" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            v-for="action in rowActions"
            :key="action.key"
            link
            :type="action.options?.type || 'primary'"
            :icon="resolveRendererActionIcon(action)"
            :style="action.options?.style"
            :class="action.options?.class"
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
  display: flex;
  flex: 1;
  width: 100%;
  min-width: 0;
  min-height: 0;
}

.list-data-view__cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--app-space-4);
}

.list-data-view__card {
  border-radius: var(--app-radius-panel);
}

.list-data-view__card-content {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-comfortable);
}

.list-data-view__card-field {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-tight);
}

.list-data-view__label {
  font-size: var(--app-font-size-caption);
  color: var(--app-text-muted);
  text-transform: uppercase;
}

.list-data-view__value {
  font-size: var(--app-font-size-body-lg);
  color: var(--app-title);
  word-break: break-word;
}

.list-data-view__card-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--app-space-3);
  margin-top: 16px;
}

.list-card-enter-active,
.list-card-leave-active,
.list-card-move {
  transition: opacity 180ms ease, transform 180ms ease;
}

.list-card-enter-from,
.list-card-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

.list-card-leave-active {
  position: absolute;
}

.list-data-view__table {
  flex: 1;
  width: 100%;
  height: 100%;
  min-height: 0;
  border-radius: var(--app-radius-panel);
  overflow: hidden;
}

:deep(.el-table__body tr.list-data-view__table-row--motion-a),
:deep(.el-table__body tr.list-data-view__table-row--motion-b) {
  animation-duration: 180ms;
  animation-delay: var(--list-row-motion-delay, 0ms);
  animation-fill-mode: both;
  animation-timing-function: ease-out;
}

:deep(.el-table__body tr.list-data-view__table-row--motion-a) {
  animation-name: list-data-view-row-arrive-a;
}

:deep(.el-table__body tr.list-data-view__table-row--motion-b) {
  animation-name: list-data-view-row-arrive-b;
}

@keyframes list-data-view-row-arrive-a {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
}

@keyframes list-data-view-row-arrive-b {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .list-card-enter-active,
  .list-card-leave-active,
  .list-card-move,
  :deep(.el-table__body tr.list-data-view__table-row--motion-a),
  :deep(.el-table__body tr.list-data-view__table-row--motion-b) {
    animation: none;
    transition: none;
  }
}

@container application-list-layout (max-width: 560px) {
  .list-data-view__cards {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
