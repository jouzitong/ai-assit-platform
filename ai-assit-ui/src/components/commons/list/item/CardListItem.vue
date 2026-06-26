<script setup>
const props = defineProps({
  row: { type: Object, required: true },
  columns: { type: Array, default: () => [] },
  rowIndex: { type: Number, default: -1 },
  enumMap: { type: [Map, Object], default: () => new Map() }
})

const emit = defineEmits(['row-click', 'cell-click', 'action-click'])

function isActionColumn(column) {
  return column?.__isActionColumn === true
}

function getActionItems(column) {
  return (Array.isArray(column?.__actionColumns) ? column.__actionColumns : []).filter((item) => item?.visible !== false)
}

function getByPath(target, path) {
  if (!target || !path) return undefined
  return String(path).replace(/#/g, '.').split('.').filter(Boolean).reduce((current, key) => current?.[key], target)
}

function resolveValue(column) {
  const fields = column.fields
  if (Array.isArray(fields) && fields.length > 0) {
    const value = getByPath(props.row, fields.join('.'))
    if (value !== undefined) return value
  } else if (typeof fields === 'string' && fields) {
    const value = getByPath(props.row, fields)
    if (value !== undefined) return value
  }
  return props.row[column.key]
}

function findLabel(value, list) {
  if (!list) return null
  if (Array.isArray(list)) {
    const match = list.find((item) => {
      if (typeof item !== 'object') {
        return String(item) === String(value)
      }
      const candidateValue = item.value ?? item.code ?? item.key ?? item.name
      return String(candidateValue) === String(value)
    })
    if (!match) return null
    return typeof match === 'object'
      ? (match.label ?? match.desc ?? match.description ?? match.text ?? match.name ?? String(match.value ?? match.code ?? match.key ?? value))
      : String(match)
  }
  if (typeof list === 'object') return list[value] ?? null
  return null
}

function getDisplayValue(column) {
  const rawValue = resolveValue(column)
  if (column.selectType === 'static') return findLabel(rawValue, column.select_list) ?? rawValue ?? ''
  if (column.selectType === 'enums') {
    const enumKey = column.select_key || column.key
    const enumList = props.enumMap instanceof Map ? props.enumMap.get(enumKey) : props.enumMap?.[enumKey]
    return findLabel(rawValue, enumList) ?? rawValue ?? ''
  }
  return rawValue == null ? '' : typeof rawValue === 'object' ? JSON.stringify(rawValue) : rawValue
}

function slotName(column) {
  return column.slot ? `cell-${column.slot}` : `cell-${column.key}`
}
</script>

<template>
  <div class="card-item" @click="emit('row-click', { row, rowIndex })">
    <div
      v-for="(column, index) in columns"
      :key="index"
      class="card-cell"
      @click.stop="emit('cell-click', { row, rowIndex, column })"
    >
      <div class="card-cell__label">
        {{ column.label }}
      </div>
      <div class="card-cell__value">
        <slot :name="slotName(column)" :row="row" :row-index="rowIndex" :value="getDisplayValue(column)" :column="column">
          <span v-if="isActionColumn(column)" class="cell-actions">
            <span
              v-for="actionItem in getActionItems(column)"
              :key="actionItem.key"
              class="cell-action"
              :class="{ 'is-disabled': !actionItem.enabled }"
              @click.stop="actionItem.enabled !== false && emit('action-click', { row, rowIndex, column, actionItem })"
            >
              {{ actionItem.icon || actionItem.label }}
            </span>
          </span>
          <span v-else>{{ getDisplayValue(column) }}</span>
        </slot>
      </div>
    </div>
  </div>
</template>

<style scoped>
.card-item {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--stroke);
  border-radius: 12px;
  background: var(--bg-elev-2);
}

.card-cell {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 8px;
  align-items: start;
}

.card-cell__label {
  color: var(--text-dim);
  font-size: 12px;
}

.card-cell__value {
  min-width: 0;
}

.cell-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.cell-action {
  color: var(--accent);
  cursor: pointer;
}

.cell-action.is-disabled {
  color: var(--text-dim);
  cursor: not-allowed;
}
</style>
