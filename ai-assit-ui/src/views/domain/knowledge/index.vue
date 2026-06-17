<script setup>
import { computed, ref, watch } from 'vue'
import ActionBar from '../../../components/commons/list/ActionBar.vue'
import DataListFooter from '../../../components/commons/list/DataListFooter.vue'
import DataTable from '../../../components/commons/list/DataTable.vue'
import FilterBar from '../../../components/commons/list/FilterBar.vue'
import HeaderBar from '../../../components/commons/list/HeaderBar.vue'
import ListCommonLayout from '../../../components/commons/list/ListCommonLayout.vue'
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
  openCreateDialog
} = useKnowledgePage()

const filterSchema = ref([
  {
    key: 'keyword',
    label: '搜索',
    type: 'input',
    value: keyword.value,
    action: 'keyword-change',
    type_config: {
      placeholder: '搜索 kbCode / documentCode / documentName / bizKey',
      width: 280,
      clearable: true
    }
  }
])

const tableColumns = [
  { key: 'kbCode', label: '知识库编码', width: 12 },
  { key: 'documentCode', label: '文档编码', width: 16 },
  { key: 'documentName', label: '文档名称', width: 16 },
  { key: 'documentType', label: '文档类型', width: 12 },
  { key: 'bizType', label: '业务类型', width: 12 },
  { key: 'bizKey', label: '业务唯一键', width: 14 },
  { key: 'sourceSystem', label: '来源系统', width: 12 },
  {
    key: 'status',
    label: '状态',
    width: 10,
    class: row => `knowledge-status ${row.statusClass || ''}`
  },
  { key: 'draftVersionNo', label: '草稿版本号', width: 10 },
  { key: 'contentFormat', label: '内容格式', width: 12 },
  { key: 'contentSize', label: '内容大小', width: 10 },
  { key: 'reviewStatus', label: '审核状态', width: 12 },
  { key: 'lastGeneratedAt', label: '最近生成时间', width: 14 }
]

const listConfig = {
  striped: true,
  actionColumns: [
    { key: 'preview', label: '查看正文' }
  ],
  sorts_config: {
    header_enable: true,
    sorts: ['kbCode', 'documentCode', 'documentName', 'documentType', 'bizType', 'status', 'draftVersionNo', 'reviewStatus', 'lastGeneratedAt']
  }
}

const actionItems = computed(() => ([
  { key: 'create', label: '新建知识库', type: 'primary', action: 'create' },
  { key: 'refresh', label: '刷新', variant: 'ghost', action: 'refresh', loading: loading.value },
  { key: 'sync', label: '同步', variant: 'ghost', action: 'sync' }
]))

const headerTab = computed(() => ({
  activeTab: activeTab.value,
  list: [
    { key: 'current', label: '生效' },
    { key: 'draft', label: '草稿' },
    { key: 'history', label: '历史' }
  ]
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
  if (payload?.action === 'sync') {
    triggerKnowledgeSync()
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
        <HeaderBar :showTitle="true"  title="知识库 · V1.0.0" :tab="headerTab" @update:tab="handleHeaderTabChange">
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
