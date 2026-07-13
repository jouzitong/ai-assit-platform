<script setup lang="ts">
import { Check, EditPen, Link, Plus, Reading, RefreshRight, Search, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, ref, watch } from 'vue'
import { AppPagination } from '../../../../components'
import type { CatalogStatus, VirtualDataId, VirtualKnowledgeStatusItem } from '../../api/virtualData'
import { catalogStatusLabel, catalogStatusOptions, catalogStatusType } from '../data/options'
import type { VirtualEntitySummary } from '../data/types'

const props = defineProps<{
  rows: VirtualEntitySummary[]
  knowledgeStatuses?: VirtualKnowledgeStatusItem[]
  knowledgeBases?: Array<{ code: string; label: string }>
  loading?: boolean
  operationLoading?: boolean
}>()

const emit = defineEmits<{
  initialize: []
  refresh: []
  openEntity: [id: VirtualDataId]
  validateEntity: [id: VirtualDataId]
  publishEntity: [id: VirtualDataId]
  batchPublish: [ids: VirtualDataId[]]
  batchUnpublish: [ids: VirtualDataId[]]
  syncKnowledge: [ids: VirtualDataId[]]
  previewKnowledge: [id: VirtualDataId]
  openCanvas: []
}>()

const keyword = defineModel<string>('keyword', { default: '' })
const sourceKey = defineModel<string>('sourceKey', { default: '' })
const status = defineModel<CatalogStatus | ''>('status', { default: '' })
const currentPage = defineModel<number>('currentPage', { default: 1 })
const pageSize = defineModel<number>('pageSize', { default: 20 })
const knowledgeBaseCode = defineModel<string>('knowledgeBaseCode', { default: '' })
const tableRef = ref<{ clearSelection: () => void; toggleRowSelection: (row: VirtualEntitySummary, selected: boolean) => void } | null>(null)
const selectedIdKeys = ref<Set<string>>(new Set())
let syncingPageSelection = false

const sourceOptions = computed(() => Array.from(new Set(props.rows.flatMap(row => row.sources))).sort())
const filteredRows = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  return props.rows.filter((row) => {
    const keywordMatched = !normalized || `${row.entityName || ''} ${row.entityCode || ''} ${row.physicalTables.join(' ')}`.toLowerCase().includes(normalized)
    const sourceMatched = !sourceKey.value || row.sources.includes(sourceKey.value)
    const statusMatched = status.value === '' || row.status === status.value
    return keywordMatched && sourceMatched && statusMatched
  })
})
const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredRows.value.slice(start, start + pageSize.value)
})
const selectedRows = computed(() => props.rows.filter(row => selectedIdKeys.value.has(String(row.id))))
const selectedIds = computed(() => selectedRows.value.map(row => row.id))
const statusByEntityId = computed(() => new Map((props.knowledgeStatuses || []).map(item => [String(item.entityId), item])))

function isKnowledgeSynced(row: VirtualEntitySummary) {
  if (!knowledgeBaseCode.value) return false
  return statusByEntityId.value.get(String(row.id))?.kbCodes?.includes(knowledgeBaseCode.value) === true
}

function knowledgeStatusLabel(row: VirtualEntitySummary) {
  if (row.status !== 1) return '需先发布'
  if (!knowledgeBaseCode.value) return '请选择知识库'
  return isKnowledgeSynced(row) ? '已同步' : '未同步'
}

function knowledgeStatusType(row: VirtualEntitySummary) {
  if (row.status !== 1 || !knowledgeBaseCode.value) return 'info'
  return isKnowledgeSynced(row) ? 'success' : 'warning'
}

function syncPageSelection() {
  void nextTick(() => {
    if (!tableRef.value) return
    syncingPageSelection = true
    tableRef.value.clearSelection()
    pagedRows.value.forEach((row) => {
      if (selectedIdKeys.value.has(String(row.id))) tableRef.value?.toggleRowSelection(row, true)
    })
    syncingPageSelection = false
  })
}

function handleSelectionChange(rows: VirtualEntitySummary[]) {
  if (syncingPageSelection) return
  const next = new Set(selectedIdKeys.value)
  pagedRows.value.forEach(row => next.delete(String(row.id)))
  rows.forEach(row => next.add(String(row.id)))
  selectedIdKeys.value = next
}

function selectAllUnsynced() {
  if (!knowledgeBaseCode.value) {
    ElMessage.warning('请先选择知识库，再选择全部未同步虚拟表')
    return
  }
  const rows = filteredRows.value.filter(row => row.status === 1 && !isKnowledgeSynced(row))
  selectedIdKeys.value = new Set(rows.map(row => String(row.id)))
  ElMessage.success(`已选择 ${rows.length} 张已发布且未同步的虚拟表`)
}

function handleBatchCommand(command: string) {
  if (!selectedIds.value.length) return
  if (command === 'publish') emit('batchPublish', selectedIds.value)
  if (command === 'unpublish') emit('batchUnpublish', selectedIds.value)
  if (command === 'sync') emit('syncKnowledge', selectedIds.value)
}

function handleKeywordChange(value: string) {
  keyword.value = value
  currentPage.value = 1
}

function handleSourceChange(value: string) {
  sourceKey.value = value
  currentPage.value = 1
}

function handleStatusChange(value: CatalogStatus | '') {
  status.value = value
  currentPage.value = 1
}

function handlePageSizeChange() {
  currentPage.value = 1
}

watch([() => filteredRows.value.length, pageSize], ([total]) => {
  const maxPage = Math.max(1, Math.ceil(total / pageSize.value))
  if (currentPage.value > maxPage) currentPage.value = maxPage
})
watch([pagedRows, selectedIdKeys], syncPageSelection, { deep: true })
watch(() => props.rows, (rows) => {
  const validIds = new Set(rows.map(row => String(row.id)))
  selectedIdKeys.value = new Set([...selectedIdKeys.value].filter(id => validIds.has(id)))
}, { deep: true })

const stats = computed(() => ({
  total: props.rows.length,
  published: props.rows.filter(row => row.status === 1).length,
  sources: new Set(props.rows.flatMap(row => row.sources)).size,
  relations: Math.floor(props.rows.reduce((sum, row) => sum + row.relationCount, 0) / 2),
}))
</script>

<template>
  <section class="virtual-catalog">
    <div class="virtual-catalog__stats" aria-label="虚拟表统计">
      <article><span>虚拟表</span><strong>{{ stats.total }}</strong><small>统一业务实体</small></article>
      <article><span>已发布</span><strong>{{ stats.published }}</strong><small>可参与执行计划</small></article>
      <article><span>接入数据源</span><strong>{{ stats.sources }}</strong><small>物理映射来源</small></article>
      <article><span>字段关联</span><strong>{{ stats.relations }}</strong><small>跨表逻辑关系</small></article>
    </div>

    <section class="virtual-catalog__panel">
      <header class="virtual-catalog__toolbar">
        <div class="virtual-catalog__filters">
          <el-input :model-value="keyword" clearable placeholder="搜索名称、编码或物理表" aria-label="搜索虚拟表" @update:model-value="handleKeywordChange">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select :model-value="sourceKey" clearable placeholder="全部数据源" aria-label="按数据源筛选" @update:model-value="handleSourceChange">
            <el-option v-for="source in sourceOptions" :key="source" :label="source" :value="source" />
          </el-select>
          <el-select :model-value="status" clearable placeholder="全部状态" aria-label="按发布状态筛选" @update:model-value="handleStatusChange">
            <el-option v-for="option in catalogStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </div>
        <div class="virtual-catalog__actions">
          <el-button :icon="Link" @click="emit('openCanvas')">关系画布</el-button>
          <el-button :icon="RefreshRight" :loading="loading" @click="emit('refresh')">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="emit('initialize')">从数据源初始化</el-button>
        </div>
      </header>

      <div class="virtual-catalog__bulkbar">
        <div class="virtual-catalog__bulk-actions">
          <span>已选择 <strong>{{ selectedIds.length }}</strong> 张虚拟表</span>
          <el-button :disabled="operationLoading || !knowledgeBaseCode" @click="selectAllUnsynced">选择全部未同步</el-button>
          <el-dropdown :disabled="operationLoading || !selectedIds.length" @command="handleBatchCommand">
            <el-button type="primary" plain :loading="operationLoading">
              批量操作<el-icon class="el-icon--right"><UploadFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="publish">发布</el-dropdown-item>
                <el-dropdown-item command="unpublish" divided>取消发布</el-dropdown-item>
                <el-dropdown-item command="sync">同步知识库</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <el-select v-model="knowledgeBaseCode" clearable filterable placeholder="选择知识库查看同步状态" aria-label="目标知识库">
          <el-option v-for="item in knowledgeBases" :key="item.code" :label="item.label" :value="item.code" />
        </el-select>
      </div>

      <el-table ref="tableRef" v-loading="loading" :data="pagedRows" row-key="id" class="virtual-catalog__table" height="100%" scrollbar-always-on @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="48" fixed :reserve-selection="true" />
        <el-table-column label="虚拟表" min-width="280" fixed>
          <template #default="{ row }">
            <div class="virtual-catalog__identity">
              <strong>{{ row.entityName || row.entityCode }}</strong>
              <code>{{ row.entityCode }}</code>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="物理映射" min-width="300">
          <template #default="{ row }">
            <div class="virtual-catalog__bindings">
              <span v-for="(table, index) in row.physicalTables" :key="`${table}-${index}`">
                <b>{{ row.sources[index] || row.sources[0] || '-' }}</b> / {{ table }}
              </span>
              <em v-if="!row.physicalTables.length">暂未绑定物理表</em>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="112">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="catalogStatusType(row.status)">{{ catalogStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="catalogVersion" label="版本" width="82" align="center" />
        <el-table-column label="模型规模" width="160">
          <template #default="{ row }">
            <span class="virtual-catalog__scale">{{ row.fieldCount }} 字段 · {{ row.relationCount }} 关联</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="320" show-overflow-tooltip />
        <el-table-column label="知识库" width="118">
          <template #default="{ row }">
            <el-tag size="small" effect="light" :type="knowledgeStatusType(row)">{{ knowledgeStatusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="390" fixed="right">
          <template #default="{ row }">
            <div class="virtual-catalog__row-actions">
              <el-button text type="primary" :icon="EditPen" @click="emit('openEntity', row.id)">模型配置</el-button>
              <el-button text :icon="Reading" @click="emit('previewKnowledge', row.id)">知识预览</el-button>
              <el-button text :icon="Check" @click="emit('validateEntity', row.id)">校验</el-button>
              <el-button v-if="row.status !== 1" text type="success" :icon="UploadFilled" @click="emit('publishEntity', row.id)">发布</el-button>
              <el-button v-else text type="danger" @click="emit('batchUnpublish', [row.id])">取消发布</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <div class="virtual-catalog__empty">
            <strong>还没有虚拟表</strong>
            <span>从已有数据源和物理表初始化，字段与直连规则会自动生成。</span>
            <el-button type="primary" :icon="Plus" @click="emit('initialize')">开始初始化</el-button>
          </div>
        </template>
      </el-table>

      <footer class="virtual-catalog__pagination">
        <AppPagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="filteredRows.length"
          :pager-count="5"
          @size-change="handlePageSizeChange"
        />
      </footer>
    </section>
  </section>
</template>

<style scoped>
.virtual-catalog {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--app-space-4);
  min-height: 0;
  height: 100%;
  padding: var(--app-space-4);
}

.virtual-catalog__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--app-space-3);
}

.virtual-catalog__stats article {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 2px var(--app-space-3);
  padding: var(--app-space-4);
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: var(--app-surface-gradient);
  box-shadow: var(--app-shadow-sm);
}

.virtual-catalog__stats span,
.virtual-catalog__stats small {
  color: var(--app-text-muted);
}

.virtual-catalog__stats strong {
  grid-row: 1 / span 2;
  grid-column: 2;
  align-self: center;
  color: var(--app-title);
  font-size: 26px;
}

.virtual-catalog__stats small {
  font-size: var(--app-font-size-caption);
}

.virtual-catalog__panel {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: var(--app-surface-solid);
  box-shadow: var(--app-shadow-sm);
}

.virtual-catalog__toolbar {
  display: flex;
  gap: var(--app-space-4);
  align-items: center;
  justify-content: space-between;
  padding: var(--app-space-3) var(--app-space-4);
  border-bottom: 1px solid var(--app-border);
}

.virtual-catalog__filters,
.virtual-catalog__actions,
.virtual-catalog__bulk-actions,
.virtual-catalog__row-actions {
  display: flex;
  gap: var(--app-space-2);
  align-items: center;
}

.virtual-catalog__bulkbar {
  display: flex;
  gap: var(--app-space-4);
  align-items: center;
  justify-content: space-between;
  padding: var(--app-space-2) var(--app-space-4);
  border-bottom: 1px solid var(--app-border);
  background: var(--app-surface-muted);
}

.virtual-catalog__bulkbar > :deep(.el-select) {
  width: 260px;
}

.virtual-catalog__bulk-actions > span {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.virtual-catalog__bulk-actions strong {
  color: var(--app-accent);
}

.virtual-catalog__filters :deep(.el-input) {
  width: 260px;
}

.virtual-catalog__filters :deep(.el-select) {
  width: 150px;
}

.virtual-catalog__table {
  min-height: 0;
}

.virtual-catalog__identity strong,
.virtual-catalog__identity code {
  display: block;
}

.virtual-catalog__identity strong {
  color: var(--app-title);
}

.virtual-catalog__identity code,
.virtual-catalog__scale {
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.virtual-catalog__bindings {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.virtual-catalog__bindings span {
  padding: 3px 7px;
  border: 1px solid var(--app-border-subtle);
  border-radius: 6px;
  background: var(--app-surface-muted);
  color: var(--app-text-soft);
  font-size: var(--app-font-size-caption);
}

.virtual-catalog__bindings b {
  color: var(--app-accent);
  font-weight: 600;
}

.virtual-catalog__bindings em {
  color: var(--app-text-muted);
  font-style: normal;
}

.virtual-catalog__row-actions {
  flex-wrap: nowrap;
  gap: var(--app-space-1);
  white-space: nowrap;
}

.virtual-catalog__row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.virtual-catalog__pagination {
  padding: var(--app-space-3) var(--app-space-4);
  border-top: 1px solid var(--app-border);
}

.virtual-catalog__empty {
  display: grid;
  justify-items: center;
  gap: var(--app-space-2);
  padding: 48px;
  color: var(--app-text-muted);
}

.virtual-catalog__empty strong {
  color: var(--app-title);
  font-size: var(--app-font-size-title-sm);
}

@media (max-width: 1120px) {
  .virtual-catalog__toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 760px) {
  .virtual-catalog__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .virtual-catalog__filters,
  .virtual-catalog__actions,
  .virtual-catalog__bulkbar {
    flex-wrap: wrap;
  }
}
</style>
