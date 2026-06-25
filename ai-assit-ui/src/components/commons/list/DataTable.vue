<script setup>
import { computed, ref, watch } from 'vue'
import Pagination from './Pagination.vue'
import ListItem from './item/ListItem.vue'
import CardListItem from './item/CardListItem.vue'

const props = defineProps({
  fields: { type: Array, default: () => [] },
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] },
  rowKey: { type: String, default: 'id' },
  selectedRowValue: { type: [String, Number], default: null },
  showHeader: { type: Boolean, default: true },
  layoutType: { type: String, default: 'table' },
  itemComponent: { type: [String, Object], default: null },
  minWidth: { type: Number, default: 860 },
  listConfig: { type: Object, default: () => ({}) },
  list_config: { type: Object, default: null },
  sorts: { type: Array, default: () => [] },
  pagination: { type: Boolean, default: false },
  totalItems: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  showPageSize: { type: Boolean, default: false },
  enumMap: { type: [Map, Object], default: () => new Map() }
})

const emit = defineEmits(['row-click', 'cell-click', 'action-click', 'sort-change', 'update:sorts', 'change', 'update:page', 'update:pageSize'])

const currentSort = ref({ key: '', type: '' })

watch(() => props.sorts, syncSortFromProps, { immediate: true, deep: true })

const runtimeListConfig = computed(() => props.list_config || props.listConfig || {})
const normalizedListConfig = computed(() => {
  const cfg = runtimeListConfig.value
  const sortsConfig = cfg.sorts_config && typeof cfg.sorts_config === 'object' ? cfg.sorts_config : {}
  return {
    align: cfg.align || 'left',
    density: cfg.density || 'normal',
    hoverable: cfg.hoverable !== false,
    striped: cfg.striped === true,
    bordered: cfg.bordered !== false,
    actionColumns: Array.isArray(cfg.actionColumns) ? cfg.actionColumns : [],
    sorts_config: {
      header_enable: sortsConfig.header_enable === true,
      sorts: Array.isArray(sortsConfig.sorts) ? sortsConfig.sorts.map((key) => String(key).trim()).filter(Boolean) : []
    }
  }
})

const resolvedColumns = computed(() => {
  const actionColumn = buildActionColumn()
  const appendAction = (columns) => {
    const base = Array.isArray(columns) ? [...columns] : []
    if (actionColumn && !base.some((column) => String(column?.key) === String(actionColumn.key))) {
      base.push(actionColumn)
    }
    return base
  }
  if (props.columns.length) return appendAction(props.columns.map(normalizeColumn))
  if (props.fields.length) return appendAction(props.fields.map(resolveFieldColumn))
  return appendAction(defaultColumns.value.map(normalizeColumn))
})

const visibleColumns = computed(() => resolvedColumns.value.filter(isColumnVisible))
const totalWidthParts = computed(() => visibleColumns.value.reduce((sum, column) => sum + (column.__widthPart || 0), 0) || 120)
const tableWidthPercent = computed(() => Math.max(100, (totalWidthParts.value / 120) * 100))
const tableStyle = computed(() => ({ minWidth: `${props.minWidth}px`, width: `${tableWidthPercent.value}%` }))
const tableWrapClass = computed(() => [`density-${normalizedListConfig.value.density}`, normalizedListConfig.value.hoverable ? 'is-hoverable' : 'no-hoverable'])
const tableClass = computed(() => [`align-${normalizedListConfig.value.align}`, normalizedListConfig.value.striped ? 'is-striped' : '', normalizedListConfig.value.bordered ? 'is-bordered' : 'no-border'])
const resolvedItemComponent = computed(() => props.itemComponent || (props.layoutType === 'card' ? CardListItem : ListItem))
const defaultColumns = computed(() => ([
  { key: 'id', label: 'ID' },
  { key: 'title', label: '任务' },
  { key: 'owner', label: '负责人' },
  { key: 'deadline', label: '截止时间' },
  { key: 'status', label: '状态' },
  { key: 'priority', label: '优先级' }
]))
const resolvedTotalItems = computed(() => typeof props.totalItems === 'number' && props.totalItems >= 0 ? props.totalItems : props.rows.length)

const sortedRows = computed(() => {
  const { key, type } = currentSort.value
  if (!key || !['asc', 'desc'].includes(type)) return props.rows
  const factor = type === 'desc' ? -1 : 1
  return props.rows
    .map((row, index) => ({ row, index }))
    .sort((a, b) => {
      const compared = compareSortValue(resolveSortValue(a.row, key), resolveSortValue(b.row, key))
      if (compared !== 0) return compared * factor
      return a.index - b.index
    })
    .map((item) => item.row)
})

function resolveFieldColumn(field) {
  if (field && typeof field === 'object') return normalizeColumn(field)
  const key = String(field)
  return normalizeColumn({ key, field: key, fields: key })
}

function normalizeColumn(column) {
  const key = column.key || column.field || ''
  const options = column?.options && typeof column.options === 'object' ? column.options : {}
  const merged = { ...column, ...options, key }
  if (!merged.label) merged.label = key
  if (merged.fields == null) {
    merged.fields = typeof merged.field === 'string' && merged.field ? merged.field : key
  }
  merged.__widthPart = resolveColumnWidthPart(merged)
  return merged
}

function normalizeActionColumns() {
  return normalizedListConfig.value.actionColumns
    .filter((item) => item && item.key && item.visible !== false)
    .map((item) => ({
      key: String(item.key),
      label: item.label == null ? '' : String(item.label),
      icon: item.icon == null ? '' : String(item.icon),
      enabled: item.enabled !== false,
      visible: item.visible !== false
    }))
}

function buildActionColumn() {
  const actions = normalizeActionColumns()
  if (!actions.length) return null
  return normalizeColumn({
    key: '__actions__',
    label: '操作',
    alignCenter: true,
    width: 12,
    __isActionColumn: true,
    __actionColumns: actions
  })
}

function resolveColumnWidthPart(column) {
  const parsed = Number(column.width)
  return !Number.isNaN(parsed) && parsed > 0 ? parsed : 10
}

function isColumnVisible(column) {
  if (column.visible === undefined) return true
  return typeof column.visible === 'function' ? column.visible(column, props.rows) !== false : column.visible !== false
}

function resolveSortType(type) {
  return type === 'asc' || type === 'desc' ? type : ''
}

function syncSortFromProps(sorts) {
  const first = (Array.isArray(sorts) ? sorts : []).find((item) => item?.key)
  currentSort.value = first ? { key: String(first.key), type: resolveSortType(first.type || 'asc') || 'asc' } : { key: '', type: '' }
}

function canSortColumn(column) {
  if (!column?.key || column.__isActionColumn === true || normalizedListConfig.value.sorts_config.header_enable !== true) return false
  const whiteList = normalizedListConfig.value.sorts_config.sorts
  return !whiteList.length || whiteList.includes(String(column.key))
}

function getSortType(column) {
  if (String(currentSort.value.key) !== String(column?.key)) return ''
  return resolveSortType(currentSort.value.type)
}

function getNextSortType(column) {
  const current = getSortType(column)
  if (!current) return 'asc'
  if (current === 'asc') return 'desc'
  return ''
}

function handleHeaderClick(column) {
  if (!canSortColumn(column)) return
  const nextType = getNextSortType(column)
  currentSort.value = nextType ? { key: String(column.key), type: nextType } : { key: '', type: '' }
  const nextSorts = currentSort.value.key && currentSort.value.type ? [{ key: currentSort.value.key, type: currentSort.value.type }] : []
  emit('sort-change', { key: currentSort.value.key, type: currentSort.value.type, sorts: nextSorts })
  emit('update:sorts', nextSorts)
}

function resolveSortValue(row, key) {
  return String(key).replace(/#/g, '.').split('.').filter(Boolean).reduce((current, item) => current?.[item], row)
}

function compareSortValue(left, right) {
  const leftEmpty = left === null || left === undefined || left === ''
  const rightEmpty = right === null || right === undefined || right === ''
  if (leftEmpty && rightEmpty) return 0
  if (leftEmpty) return 1
  if (rightEmpty) return -1
  if (typeof left === 'number' && typeof right === 'number') return left === right ? 0 : left > right ? 1 : -1
  if (typeof left === 'boolean' && typeof right === 'boolean') return left === right ? 0 : left ? 1 : -1
  const leftDate = Date.parse(left)
  const rightDate = Date.parse(right)
  if (!Number.isNaN(leftDate) && !Number.isNaN(rightDate)) return leftDate === rightDate ? 0 : leftDate > rightDate ? 1 : -1
  return String(left).localeCompare(String(right), undefined, { numeric: true, sensitivity: 'base' })
}

function getHeaderStyle(column) {
  const style = {}
  if (column.align) style.textAlign = column.align
  if (column.alignCenter === true || (!column.align && !column.alignCenter && normalizedListConfig.value.align === 'center')) {
    style.textAlign = 'center'
  }
  return style
}

function getColStyle(column) {
  return { width: `${((column.__widthPart || 10) / 120) * 100}%` }
}

function getHeaderClass(column) {
  const classes = []
  if (column.headerClass) classes.push(column.headerClass)
  if (column.className) classes.push(column.className)
  if (column.class) classes.push(column.class)
  if (column.alignCenter === true) classes.push('is-center')
  if (canSortColumn(column)) classes.push('is-sortable')
  if (getSortType(column)) classes.push('is-sorted')
  return classes
}

function isRowSelected(row) {
  return props.selectedRowValue != null && row?.[props.rowKey] === props.selectedRowValue
}
</script>

<template>
  <div class="table-wrap" :class="tableWrapClass">
    <div class="table-wrap__body">
      <table v-if="layoutType !== 'card'" class="table" :class="tableClass" :style="tableStyle">
        <colgroup>
          <col v-for="(column, index) in visibleColumns" :key="`col-${index}`" :style="getColStyle(column)">
        </colgroup>
        <thead>
          <tr v-if="showHeader">
            <th
              v-for="(column, index) in visibleColumns"
              :key="index"
              :style="getHeaderStyle(column)"
              :class="getHeaderClass(column)"
              @click="handleHeaderClick(column)"
            >
              <span class="table-header__inner" :class="{ 'is-sortable': canSortColumn(column), 'is-sorted': getSortType(column) !== '' }">
                <span>{{ column.label }}</span>
                <span v-if="canSortColumn(column)" class="table-header__sort">
                  <span v-if="getSortType(column) === 'asc'">↑</span>
                  <span v-else-if="getSortType(column) === 'desc'">↓</span>
                  <span v-else>↕</span>
                </span>
              </span>
            </th>
          </tr>
        </thead>
        <tbody>
          <component
            :is="resolvedItemComponent"
            v-for="(row, rowIndex) in sortedRows"
            :key="rowKey ? row[rowKey] : rowIndex"
            :row="row"
            :columns="visibleColumns"
            :row-index="rowIndex"
            :is-row-selected="isRowSelected(row)"
            :enum-map="enumMap"
            @row-click="emit('row-click', $event)"
            @cell-click="emit('cell-click', $event)"
            @action-click="emit('action-click', $event)"
          >
            <template v-for="(_, slotName) in $slots" #[slotName]="slotProps">
              <slot :name="slotName" v-bind="slotProps" />
            </template>
          </component>
        </tbody>
      </table>

      <div v-else class="card-list">
        <component
          :is="resolvedItemComponent"
          v-for="(row, rowIndex) in sortedRows"
          :key="rowKey ? row[rowKey] : rowIndex"
          :row="row"
          :columns="visibleColumns"
          :row-index="rowIndex"
          :is-row-selected="isRowSelected(row)"
          :enum-map="enumMap"
          @row-click="emit('row-click', $event)"
          @cell-click="emit('cell-click', $event)"
          @action-click="emit('action-click', $event)"
        >
          <template v-for="(_, slotName) in $slots" #[slotName]="slotProps">
            <slot :name="slotName" v-bind="slotProps" />
          </template>
        </component>
      </div>
    </div>

    <div v-if="pagination" class="table-footer">
      <Pagination
        :total-items="resolvedTotalItems"
        :page="page"
        :page-size="pageSize"
        :show-page-size="showPageSize"
        @change="emit('change', $event)"
        @update:page="emit('update:page', $event)"
        @update:pageSize="emit('update:pageSize', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.table-wrap {
  display: grid;
  min-height: 0;
  background: var(--bg-elev-2);
  border-radius: 14px;
  border: 1px solid var(--stroke);
  overflow: hidden;
}

.table-wrap__body {
  min-height: 0;
  overflow: auto;
}

.table {
  border-collapse: collapse;
  table-layout: fixed;
}

th {
  padding: 10px 12px;
  font-size: 12px;
  border-bottom: 1px solid var(--stroke);
  background: var(--surface-bg-1);
  color: var(--text-dim);
}

.table-header__inner {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.is-sortable {
  cursor: pointer;
}

.table-footer {
  padding: 10px 12px;
  border-top: 1px solid var(--stroke);
  display: flex;
  justify-content: flex-end;
  background: var(--surface-bg-2);
}

.card-list {
  display: grid;
  gap: 10px;
  padding: 12px;
}

.align-center th,
:deep(.align-center td) {
  text-align: center;
}

.is-striped :deep(tbody tr:nth-child(even) td) {
  background: rgba(148, 163, 184, 0.05);
}

.density-compact :deep(td),
.density-compact th {
  padding-top: 8px;
  padding-bottom: 8px;
}
</style>
