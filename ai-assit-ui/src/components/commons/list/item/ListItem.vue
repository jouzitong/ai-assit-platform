<script setup>
const props = defineProps({
  row: { type: Object, required: true },
  columns: { type: Array, default: () => [] },
  rowIndex: { type: Number, default: -1 },
  isRowSelected: { type: Boolean, default: false },
  enumMap: { type: [Map, Object], default: () => new Map() }
})

const emit = defineEmits(['row-click', 'cell-click', 'action-click'])

function isActionColumn(column) {
  return column?.__isActionColumn === true
}

function getActionItems(column) {
  return (Array.isArray(column?.__actionColumns) ? column.__actionColumns : []).filter((item) => item?.visible !== false)
}

function getActionText(actionItem) {
  return actionItem?.icon || actionItem?.label || ''
}

function cellSlotName(column) {
  return column.slot ? `cell-${column.slot}` : `cell-${column.key}`
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
    const match = list.find((item) => (typeof item === 'object' ? item.value === value : item === value))
    if (!match) return null
    return typeof match === 'object' ? match.label : String(match)
  }
  if (typeof list === 'object') {
    return list[value] ?? null
  }
  return null
}

function resolveSelectLabel(value, column) {
  if (value === null || value === undefined) return null
  if (column.selectType === 'static') return findLabel(value, column.select_list)
  if (column.selectType === 'enums') {
    const enumKey = column.select_key || column.key
    const enumList = props.enumMap instanceof Map ? props.enumMap.get(enumKey) : props.enumMap?.[enumKey]
    return findLabel(value, enumList)
  }
  return findLabel(value, column.enum)
}

function getDisplayValue(column) {
  const rawValue = resolveValue(column)
  const mapped = resolveSelectLabel(rawValue, column)
  const value = mapped ?? rawValue
  if (value == null) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return value
}

function getCellStyle(row, column) {
  const part = column.__widthPart || 10
  const baseStyle = { width: `${(part / 120) * 100}%`, minWidth: `${(part / 120) * 100}%` }
  const style = typeof column.cellStyle === 'function' ? column.cellStyle(row, column) || {} : column.cellStyle || {}
  if (column.alignCenter === true) {
    style.textAlign = 'center'
  }
  return { ...baseStyle, ...style }
}

function getCellClass(row, column) {
  const classes = []
  if (column.cellClass) {
    classes.push(typeof column.cellClass === 'function' ? column.cellClass(row, column) : column.cellClass)
  }
  if (column.alignCenter === true) classes.push('is-center')
  if (typeof column.onClick === 'function') classes.push('is-clickable')
  return classes
}

function getContentStyle(row, column) {
  return typeof column.style === 'function' ? column.style(row, column) || {} : column.style || {}
}

function getContentClass(row, column) {
  const classes = []
  if (typeof column.class === 'function') classes.push(column.class(row, column))
  else if (column.class) classes.push(column.class)
  if (typeof column.classFunction === 'function') classes.push(column.classFunction(row, column, resolveValue(column)))
  if (column.className) classes.push(column.className)
  return classes
}
</script>

<template>
  <tr :class="{ 'is-selected': isRowSelected }" @click="emit('row-click', { row, rowIndex })">
    <td
      v-for="(column, index) in columns"
      :key="index"
      :style="getCellStyle(row, column)"
      :class="getCellClass(row, column)"
      @click="emit('cell-click', { row, rowIndex, column, value: getDisplayValue(column) })"
    >
      <span class="cell-content-wrap" :title="String(getDisplayValue(column) || '')">
        <span class="cell-content" :class="getContentClass(row, column)" :style="getContentStyle(row, column)">
          <slot
            :name="cellSlotName(column)"
            :row="row"
            :row-index="rowIndex"
            :value="getDisplayValue(column)"
            :column="column"
          >
            <span v-if="isActionColumn(column)" class="cell-actions">
              <span
                v-for="actionItem in getActionItems(column)"
                :key="actionItem.key"
                class="cell-action"
                :class="{ 'is-disabled': !actionItem.enabled }"
                @click.stop="actionItem.enabled !== false && emit('action-click', { row, rowIndex, column, actionItem })"
              >
                {{ getActionText(actionItem) }}
              </span>
            </span>
            <span v-else>{{ getDisplayValue(column) }}</span>
          </slot>
        </span>
      </span>
    </td>
  </tr>
</template>

<style scoped>
tr {
  height: 20px;
  transition: background-color 0.2s ease;
}

td {
  height: 20px;
  padding: 10px 12px;
  vertical-align: middle;
  border-right: 1px solid var(--stroke);
  border-bottom: 1px solid var(--stroke);
}

td:last-child {
  border-right: none;
}

.cell-content-wrap {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: inherit;
  line-height: 20px;
}

.cell-content {
  display: inline-block;
  max-width: 100%;
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

tr.is-selected td {
  background: rgba(120, 230, 255, 0.22) !important;
}
</style>
