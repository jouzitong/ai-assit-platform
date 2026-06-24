<script setup>
import { computed, ref } from 'vue'
import ActionBar from '../../../components/commons/list/ActionBar.vue'
import DataListFooter from '../../../components/commons/list/DataListFooter.vue'
import DataTable from '../../../components/commons/list/DataTable.vue'
import FilterBar from '../../../components/commons/list/FilterBar.vue'
import HeaderBar from '../../../components/commons/list/HeaderBar.vue'
import ListCommonLayout from '../../../components/commons/list/ListCommonLayout.vue'
import {
  KNOWLEDGE_ACTION_ITEMS,
  KNOWLEDGE_FILTER_SCHEMA,
  KNOWLEDGE_HEADER_TABS,
  KNOWLEDGE_LIST_CONFIG,
  KNOWLEDGE_PAGE_TITLE,
  KNOWLEDGE_TABLE_COLUMNS
} from './data'
import { SERVICE_NAMES } from '../../../config/services'
import { useEnumStore } from '../../../store/enums'
import { useKnowledgePage } from './service/knowledge'

const sorts = ref([{ key: 'documentName', type: 'asc' }])
const { getServiceEnums, getEnumOptions } = useEnumStore()

const {
  activeTab,
  page,
  pageSize,
  totalItems,
  keyword,
  bizTypeCode,
  loading,
  errorMessage,
  createDialogVisible,
  createSubmitting,
  createError,
  createForm,
  batchMode,
  selectedDocumentCodes,
  filteredSources,
  openSource,
  triggerKnowledgeSync,
  deleteKnowledgeDocuments,
  loadDataSources,
  openCreateDialog,
  closeCreateDialog,
  submitCreateDialog,
  showPendingAction,
  enterBatchMode,
  exitBatchMode,
  toggleDocumentSelection,
  toggleSelectAll
} = useKnowledgePage()

const filterSchema = computed(() => {
  const bizTypeOptions = [
    { code: '', name: '全部业务类型' },
    ...getEnumOptions('aiKbBizType', SERVICE_NAMES.AI_ENGINE).map(item => ({
      code: String(item.value ?? ''),
      name: item.label
    }))
  ]

  return KNOWLEDGE_FILTER_SCHEMA.map(item => {
    if (item.key === 'keyword') {
      return {
        ...item,
        value: keyword.value
      }
    }
    if (item.key === 'bizTypeCode') {
      return {
        ...item,
        value: bizTypeCode.value,
        type_config: {
          ...(item.type_config || {}),
          options: bizTypeOptions
        }
      }
    }
    return { ...item }
  })
})

const tableColumns = computed(() => {
  if (!batchMode.value) {
    return KNOWLEDGE_TABLE_COLUMNS
  }
  return [
    { key: '__select__', label: '选择', width: 8, alignCenter: true, slot: 'select' },
    ...KNOWLEDGE_TABLE_COLUMNS
  ]
})

const listConfig = KNOWLEDGE_LIST_CONFIG
const enumMap = computed(() => getServiceEnums(SERVICE_NAMES.AI_ENGINE))

const actionItems = computed(() => {
  if (batchMode.value) {
    return [
      { key: 'batch-sync', label: '同步', type: 'primary', action: 'batch-sync' },
      { key: 'batch-delete', label: '删除', variant: 'ghost', action: 'batch-delete' },
      { key: 'batch-cancel', label: '取消批量', variant: 'ghost', action: 'batch-cancel' }
    ]
  }
  if (activeTab.value === 'draft') {
    return [{ key: 'publish', label: '发布', type: 'primary', action: 'publish' }]
  }
  if (activeTab.value === 'history') {
    return [
      { key: 'select', label: '选择', variant: 'ghost', action: 'select' },
      { key: 'rollback', label: '版本回退', type: 'primary', action: 'rollback' }
    ]
  }
  return [...KNOWLEDGE_ACTION_ITEMS, { key: 'batch', label: '批量操作', variant: 'ghost', action: 'batch' }]
})

const headerTab = computed(() => ({
  activeTab: activeTab.value,
  list: KNOWLEDGE_HEADER_TABS
}))

function handleFilterAction(payload) {
  if (payload?.action === 'keyword-change') {
    keyword.value = payload.value ?? ''
    return
  }
  if (payload?.action === 'biz-type-change') {
    bizTypeCode.value = payload.value ?? ''
  }
}

function handleHeaderTabChange(nextTab) {
  activeTab.value = nextTab?.activeTab || 'current'
}

function handleToolbarAction(payload) {
  if (payload?.action === 'batch') {
    enterBatchMode()
    return
  }
  if (payload?.action === 'batch-sync') {
    triggerKnowledgeSync()
    return
  }
  if (payload?.action === 'batch-delete') {
    deleteKnowledgeDocuments()
    return
  }
  if (payload?.action === 'batch-cancel') {
    exitBatchMode()
    return
  }
  if (payload?.action === 'create') {
    openCreateDialog()
    return
  }
  if (payload?.action === 'refresh') {
    loadDataSources({ showLoadingPopup: true, showSuccessPopup: true })
    return
  }
  if (payload?.action === 'publish') {
    triggerKnowledgeSync()
    return
  }
  if (payload?.action === 'sync') {
    triggerKnowledgeSync()
    return
  }
  if (payload?.action === 'select') {
    showPendingAction('历史版本选择')
    return
  }
  if (payload?.action === 'rollback') {
    showPendingAction('版本回退')
    return
  }
}

function handleRowClick(payload) {
  if (payload?.row) {
    if (batchMode.value) {
      const documentCode = payload.row.documentCode
      const checked = !selectedDocumentCodes.value.includes(documentCode)
      toggleDocumentSelection(documentCode, checked)
      return
    }
    openSource(payload.row)
  }
}

function handleTableAction(payload) {
  if (!payload?.row) {
    return
  }
  if (batchMode.value) {
    return
  }
  openSource(payload.row)
}

const pageRowsSelected = computed(() => {
  return filteredSources.value.length > 0 && filteredSources.value.every(row => selectedDocumentCodes.value.includes(row.documentCode))
})

const pageRowsIndeterminate = computed(() => {
  const selectedCount = filteredSources.value.filter(row => selectedDocumentCodes.value.includes(row.documentCode)).length
  return selectedCount > 0 && selectedCount < filteredSources.value.length
})
</script>

<template>
  <div class="knowledge-page">
    <ListCommonLayout>
      <template #header>
        <HeaderBar :showTitle="true" :title="KNOWLEDGE_PAGE_TITLE" :tab="headerTab" @update:tab="handleHeaderTabChange">
          <template #left>
            <FilterBar :schema="filterSchema" @action="handleFilterAction" />
          </template>
          <template #right>
            <ActionBar :actions="actionItems" @action="handleToolbarAction" />
          </template>
        </HeaderBar>
      </template>

      <template #table>
        <div class="knowledge-table-panel">
          <div v-if="batchMode" class="knowledge-batch-bar">
            <label class="knowledge-batch-check">
              <input
                type="checkbox"
                :checked="pageRowsSelected"
                :indeterminate.prop="pageRowsIndeterminate"
                @change="toggleSelectAll($event.target.checked, filteredSources)"
              />
              <span>本页全选</span>
            </label>
            <span class="knowledge-batch-summary">已选择 {{ selectedDocumentCodes.length }} 个文档</span>
          </div>
          <DataTable
            :rows="filteredSources"
            :columns="tableColumns"
            :list-config="listConfig"
            :sorts="sorts"
            :enum-map="enumMap"
            row-key="key"
            @update:sorts="sorts = $event"
            @row-click="handleRowClick"
            @action-click="handleTableAction"
          >
            <template #cell-select="{ row }">
              <input
                type="checkbox"
                :checked="selectedDocumentCodes.includes(row.documentCode)"
                @click.stop
                @change="toggleDocumentSelection(row.documentCode, $event.target.checked)"
              />
            </template>
          </DataTable>
        </div>
      </template>

      <template #footer>
        <DataListFooter
          :total-items="totalItems"
          :page="page"
          :page-size="pageSize"
          show-page-size
          @update:page="page = $event"
          @update:pageSize="pageSize = $event"
        />
      </template>
    </ListCommonLayout>

    <div v-if="createDialogVisible" class="kb-create-mask" @click.self="closeCreateDialog">
      <section class="kb-create-dialog" role="dialog" aria-modal="true" aria-label="新建知识库">
        <header class="kb-create-head">
          <div>
            <h3>新建知识库</h3>
            <p>创建知识库主记录，文档写入后会显示在当前列表。</p>
          </div>
          <button class="kb-create-close" type="button" :disabled="createSubmitting" @click="closeCreateDialog">×</button>
        </header>

        <p v-if="createError" class="kb-create-error">{{ createError }}</p>

        <div class="kb-create-grid">
          <label class="kb-create-field">
            <span>知识库编码</span>
            <input v-model="createForm.kbCode" type="text" placeholder="例如 w05enpcxa4" />
          </label>
          <label class="kb-create-field">
            <span>知识库名称</span>
            <input v-model="createForm.kbName" type="text" placeholder="例如 员工数据知识库" />
          </label>
          <label class="kb-create-field">
            <span>来源类型</span>
            <select v-model="createForm.sourceType">
              <option value="DB_DATA_SOURCE">数据库数据源</option>
            </select>
          </label>
          <label class="kb-create-field">
            <span>业务唯一键</span>
            <input v-model="createForm.sourceKey" type="text" placeholder="例如 ods_trade_mysql" />
          </label>
          <label class="kb-create-field">
            <span>远端 KB ID</span>
            <input v-model="createForm.providerKbId" type="text" placeholder="可选" />
          </label>
          <label class="kb-create-field">
            <span>状态</span>
            <select v-model="createForm.status">
              <option value="INIT">初始化</option>
              <option value="ACTIVE">可用</option>
              <option value="DISABLED">停用</option>
            </select>
          </label>
          <label class="kb-create-field">
            <span>Workspace ID</span>
            <input v-model="createForm.workspaceId" type="text" placeholder="同步需要，可选" />
          </label>
          <label class="kb-create-field">
            <span>KB Endpoint</span>
            <input v-model="createForm.kbEndpoint" type="text" placeholder="同步需要，可选" />
          </label>
          <label class="kb-create-field full">
            <span>扩展信息 JSON</span>
            <textarea v-model="createForm.extJson" rows="4" placeholder='{"owner":"ai-engine"}' />
          </label>
        </div>

        <footer class="kb-create-actions">
          <button type="button" class="kb-create-btn" :disabled="createSubmitting" @click="closeCreateDialog">取消</button>
          <button type="button" class="kb-create-btn primary" :disabled="createSubmitting" @click="submitCreateDialog">
            {{ createSubmitting ? '保存中...' : '保存' }}
          </button>
        </footer>
      </section>
    </div>
  </div>
</template>

<style scoped>
.knowledge-page {
  min-height: calc(100vh - 140px);
}

.knowledge-table-panel {
  display: grid;
  gap: 12px;
  min-height: 0;
}

.knowledge-batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 14px;
  border: 1px solid rgba(191, 219, 254, 0.95);
  border-radius: 14px;
  background: rgba(239, 246, 255, 0.72);
}

.knowledge-batch-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #1e3a8a;
  font-size: 13px;
  font-weight: 700;
}

.knowledge-batch-check input {
  width: 16px;
  height: 16px;
}

.knowledge-batch-summary {
  color: #475569;
  font-size: 12px;
  font-weight: 600;
}

.sidebar-entry {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding: 10px 12px;
  border: 1px solid var(--stroke);
  border-radius: 12px;
  background: var(--control-bg);
  color: var(--text);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.sidebar-entry.active {
  border-color: rgba(37, 99, 235, 0.35);
  background: rgba(37, 99, 235, 0.08);
  box-shadow: 0 10px 18px rgba(37, 99, 235, 0.08);
}

:deep(.knowledge-status.online) {
  color: #15803d;
  font-weight: 600;
}

:deep(.knowledge-status.warning) {
  color: #b45309;
  font-weight: 600;
}

:deep(.knowledge-status.offline) {
  color: #b91c1c;
  font-weight: 600;
}

.kb-create-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.28);
}

.kb-create-dialog {
  width: min(760px, 100%);
  max-height: min(760px, calc(100vh - 48px));
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  gap: 14px;
  padding: 18px;
  border: 1px solid rgba(191, 219, 254, 0.92);
  border-radius: 8px;
  background: #f8fbff;
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.18);
}

.kb-create-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.kb-create-head h3 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
}

.kb-create-head p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.kb-create-close {
  width: 32px;
  height: 32px;
  border: 1px solid rgba(148, 163, 184, 0.45);
  border-radius: 8px;
  background: #fff;
  color: #475569;
  cursor: pointer;
  font-size: 20px;
  line-height: 1;
}

.kb-create-error {
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
  color: #991b1b;
  background: #fef2f2;
  font-size: 13px;
  font-weight: 600;
}

.kb-create-grid {
  min-height: 0;
  overflow: auto;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.kb-create-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.kb-create-field.full {
  grid-column: 1 / -1;
}

.kb-create-field span {
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.kb-create-field input,
.kb-create-field select,
.kb-create-field textarea {
  width: 100%;
  min-width: 0;
  border: 1px solid rgba(148, 163, 184, 0.45);
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  font: inherit;
  font-size: 13px;
  outline: none;
}

.kb-create-field input,
.kb-create-field select {
  height: 36px;
  padding: 0 10px;
}

.kb-create-field textarea {
  resize: vertical;
  min-height: 92px;
  padding: 10px;
}

.kb-create-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.kb-create-btn {
  height: 36px;
  padding: 0 16px;
  border: 1px solid rgba(148, 163, 184, 0.5);
  border-radius: 8px;
  background: #fff;
  color: #334155;
  cursor: pointer;
  font-weight: 700;
}

.kb-create-btn.primary {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}

.kb-create-btn:disabled,
.kb-create-close:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

@media (max-width: 720px) {
  .knowledge-batch-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .kb-create-grid {
    grid-template-columns: 1fr;
  }
}
</style>
