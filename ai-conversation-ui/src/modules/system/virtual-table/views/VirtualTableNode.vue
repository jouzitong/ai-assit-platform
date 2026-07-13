<script setup lang="ts">
import { Connection, Key } from '@element-plus/icons-vue'
import { Handle, Position } from '@vue-flow/core'
import type { VirtualTableNodeData } from '../data/types'
import { logicalTypeLabel } from '../data/options'

defineProps<{
  data: VirtualTableNodeData
}>()
</script>

<template>
  <article class="virtual-table-node">
    <header class="virtual-table-node__header">
      <div class="virtual-table-node__icon">
        <el-icon><Connection /></el-icon>
      </div>
      <div class="virtual-table-node__title">
        <strong>{{ data.entity.entityName || data.entity.entityCode }}</strong>
        <code>{{ data.entity.entityCode }}</code>
      </div>
      <span :class="['virtual-table-node__status', { 'is-disabled': data.entity.status === 2 }]">
        {{ data.entity.status === 1 ? '已发布' : data.entity.status === 2 ? '已停用' : '草稿' }}
      </span>
    </header>

    <div class="virtual-table-node__sources">
      <span v-for="source in data.sourceLabels" :key="source">{{ source }}</span>
    </div>

    <div class="virtual-table-node__fields">
      <div v-for="field in data.fields" :key="field.id" class="virtual-table-node__field">
        <Handle
          :id="`in:${field.id}`"
          class="virtual-table-node__handle virtual-table-node__handle--target"
          type="target"
          :position="Position.Left"
        />
        <div class="virtual-table-node__field-name">
          <el-icon v-if="field.primaryKey"><Key /></el-icon>
          <span>{{ field.fieldName || field.fieldCode }}</span>
          <code>{{ field.fieldCode }}</code>
        </div>
        <span class="virtual-table-node__field-type">{{ logicalTypeLabel(field.logicalType) }}</span>
        <Handle
          :id="`out:${field.id}`"
          class="virtual-table-node__handle virtual-table-node__handle--source"
          type="source"
          :position="Position.Right"
        />
      </div>
      <div v-if="!data.fields.length" class="virtual-table-node__empty">暂无可关联字段</div>
    </div>
  </article>
</template>

<style scoped>
.virtual-table-node {
  width: 292px;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: var(--app-surface-solid);
  color: var(--app-text);
  box-shadow: var(--app-shadow-sm);
}

.virtual-table-node__header {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  gap: var(--app-space-3);
  align-items: center;
  padding: var(--app-space-3) var(--app-space-4);
  border-bottom: 1px solid var(--app-border-subtle);
  background: var(--app-surface-gradient);
}

.virtual-table-node__icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--app-accent-bg);
  color: var(--app-accent);
}

.virtual-table-node__title {
  min-width: 0;
}

.virtual-table-node__title strong,
.virtual-table-node__title code {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.virtual-table-node__title strong {
  color: var(--app-title);
  font-size: var(--app-font-size-body);
}

.virtual-table-node__title code {
  margin-top: 2px;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.virtual-table-node__status {
  padding: 3px 7px;
  border-radius: 999px;
  background: var(--app-accent-bg);
  color: var(--app-accent);
  font-size: 11px;
}

.virtual-table-node__status.is-disabled {
  color: var(--app-text-muted);
  background: var(--app-surface-muted);
}

.virtual-table-node__sources {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px var(--app-space-4);
  border-bottom: 1px solid var(--app-border-subtle);
}

.virtual-table-node__sources span {
  padding: 2px 7px;
  border: 1px solid var(--app-accent-border);
  border-radius: 999px;
  color: var(--app-accent);
  background: var(--app-accent-bg);
  font-size: 11px;
}

.virtual-table-node__field {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--app-space-2);
  align-items: center;
  min-height: 42px;
  padding: 7px var(--app-space-4);
  border-bottom: 1px solid var(--app-border-subtle);
}

.virtual-table-node__field:last-child {
  border-bottom: 0;
}

.virtual-table-node__field:hover {
  background: var(--app-accent-bg);
}

.virtual-table-node__field-name {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 1px 5px;
  min-width: 0;
}

.virtual-table-node__field-name .el-icon {
  grid-row: 1 / span 2;
  align-self: center;
  color: var(--app-accent);
}

.virtual-table-node__field-name span,
.virtual-table-node__field-name code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.virtual-table-node__field-name span {
  color: var(--app-title);
  font-size: 12px;
}

.virtual-table-node__field-name code,
.virtual-table-node__field-type {
  color: var(--app-text-muted);
  font-size: 10px;
}

.virtual-table-node__empty {
  padding: var(--app-space-4);
  color: var(--app-text-muted);
  text-align: center;
}

.virtual-table-node__handle {
  width: 10px;
  height: 10px;
  border: 2px solid var(--app-surface-solid);
  background: var(--app-accent);
  cursor: crosshair;
}

.virtual-table-node__handle--target {
  left: -5px;
}

.virtual-table-node__handle--source {
  right: -5px;
}
</style>
