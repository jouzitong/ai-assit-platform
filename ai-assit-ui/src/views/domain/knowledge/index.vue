<script setup>
import { computed, ref, watch } from 'vue'
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
import { useKnowledgePage } from './service/knowledge'

const page = ref(1)
const pageSize = ref(10)
const sorts = ref([{ key: 'documentName', type: 'asc' }])

const {
  activeTab,
  keyword,
  loading,
  errorMessage,
  filteredSources,
  openSource,
  triggerKnowledgeSync,
  loadDataSources,
  openCreateDialog,
  showPendingAction
} = useKnowledgePage()

const filterSchema = ref(KNOWLEDGE_FILTER_SCHEMA.map(item => ({ ...item })))

const tableColumns = KNOWLEDGE_TABLE_COLUMNS

const listConfig = KNOWLEDGE_LIST_CONFIG

const actionItems = computed(() => {
  if (activeTab.value === 'draft') {
    return [{ key: 'publish', label: '发布', type: 'primary', action: 'publish' }]
  }
  if (activeTab.value === 'history') {
    return [
      { key: 'select', label: '选择', variant: 'ghost', action: 'select' },
      { key: 'rollback', label: '版本回退', type: 'primary', action: 'rollback' }
    ]
  }
  return KNOWLEDGE_ACTION_ITEMS
})

const headerTab = computed(() => ({
  activeTab: activeTab.value,
  list: KNOWLEDGE_HEADER_TABS
}))

const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredSources.value.slice(start, start + pageSize.value)
})

watch(keyword, (value) => {
  updateFilterValue('keyword', value)
  page.value = 1
})

watch(activeTab, (value) => {
  page.value = 1
})

watch(filteredSources, (rows) => {
  const maxPage = Math.max(1, Math.ceil(rows.length / pageSize.value))
  if (page.value > maxPage) {
    page.value = maxPage
  }
})

watch(pageSize, () => {
  page.value = 1
})

function updateFilterValue(key, value) {
  const field = filterSchema.value.find(item => item.key === key)
  if (field) {
    field.value = value
  }
}

function handleFilterAction(payload) {
  if (payload?.action === 'keyword-change') {
    keyword.value = payload.value ?? ''
  }
}

function handleHeaderTabChange(nextTab) {
  activeTab.value = nextTab?.activeTab || 'current'
}

function handleToolbarAction(payload) {
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
    openSource(payload.row)
  }
}

function handleTableAction(payload) {
  if (!payload?.row) {
    return
  }
  openSource(payload.row)
}
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
          <DataTable
            :rows="pagedRows"
            :columns="tableColumns"
            :list-config="listConfig"
            :sorts="sorts"
            row-key="key"
            @update:sorts="sorts = $event"
            @row-click="handleRowClick"
            @action-click="handleTableAction"
          />
        </div>
      </template>

      <template #footer>
        <DataListFooter
          :total-items="filteredSources.length"
          :page="page"
          :page-size="pageSize"
          show-page-size
          @update:page="page = $event"
          @update:pageSize="pageSize = $event"
        />
      </template>
    </ListCommonLayout>
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
</style>
