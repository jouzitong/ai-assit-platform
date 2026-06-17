<script setup>
import { computed, ref, watch } from 'vue'
import ActionBar from '../../../components/commons/list/ActionBar.vue'
import DataListFooter from '../../../components/commons/list/DataListFooter.vue'
import DataTable from '../../../components/commons/list/DataTable.vue'
import FilterBar from '../../../components/commons/list/FilterBar.vue'
import HeaderBar from '../../../components/commons/list/HeaderBar.vue'
import ListCommonLayout from '../../../components/commons/list/ListCommonLayout.vue'
import Sidebar from '../../../components/commons/list/Sidebar.vue'
import { useKnowledgePage } from './service/knowledge'

const page = ref(1)
const pageSize = ref(10)
const sidebarCollapsed = ref(false)
const sorts = ref([{ key: 'name', type: 'asc' }])

const {
  activeTab,
  activeTabLabel,
  tabOptions,
  keyword,
  sourceList,
  loading,
  errorMessage,
  filteredSources,
  openSource,
  triggerKnowledgeSync,
  loadDataSources,
  openCreateDialog,
  openEditDialog
} = useKnowledgePage()

const filterSchema = ref([
  {
    key: 'keyword',
    label: '搜索',
    type: 'input',
    value: keyword.value,
    action: 'keyword-change',
    type_config: {
      placeholder: '搜索知识库名称 / 类型 / 负责人 / 主机',
      width: 280,
      clearable: true
    }
  },
  {
    key: 'tab',
    label: '视图',
    type: 'select',
    value: activeTab.value,
    action: 'tab-change',
    type_config: {
      width: 140,
      options: tabOptions.map(item => ({
        code: item.key,
        name: item.label
      }))
    }
  }
])

const tableColumns = [
  { key: 'name', label: '知识库名称', width: 24 },
  { key: 'type', label: '类型', width: 14 },
  { key: 'owner', label: '负责人', width: 16 },
  { key: 'host', label: '主机', width: 18 },
  { key: 'database', label: '库 / Schema', width: 16 },
  { key: 'syncMode', label: '同步模式', width: 12 },
  {
    key: 'statusLabel',
    label: '状态',
    width: 10,
    class: row => `knowledge-status ${row.status || ''}`
  }
]

const listConfig = {
  striped: true,
  actionColumns: [
    { key: 'preview', label: '查看' },
    { key: 'edit', label: '编辑' }
  ],
  sorts_config: {
    header_enable: true,
    sorts: ['name', 'type', 'owner', 'syncMode', 'statusLabel']
  }
}

const actionItems = computed(() => ([
  { key: 'create', label: '新建知识库', type: 'primary', action: 'create' },
  { key: 'refresh', label: '刷新', variant: 'ghost', action: 'refresh', loading: loading.value },
  { key: 'sync', label: '同步', variant: 'ghost', action: 'sync' }
]))

const keywordScopedSources = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  if (!normalized) {
    return sourceList.value
  }
  return sourceList.value.filter(item =>
    [item.name, item.type, item.owner, item.host, item.database].some(value =>
      String(value ?? '').toLowerCase().includes(normalized)
    )
  )
})

const sidebarItems = computed(() => tabOptions.map(item => ({
  key: item.key,
  label: item.label,
  count: keywordScopedSources.value.filter(row => matchesTab(row, item.key)).length
})))

const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredSources.value.slice(start, start + pageSize.value)
})

watch(keyword, (value) => {
  updateFilterValue('keyword', value)
  page.value = 1
})

watch(activeTab, (value) => {
  updateFilterValue('tab', value)
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

function matchesTab(item, tabKey) {
  if (tabKey === 'draft') {
    return item.status === 'warning'
  }
  if (tabKey === 'history') {
    return item.status === 'offline'
  }
  return item.status === 'online'
}

function handleFilterAction(payload) {
  if (payload?.action === 'keyword-change') {
    keyword.value = payload.value ?? ''
    return
  }
  if (payload?.action === 'tab-change') {
    activeTab.value = payload.value || 'current'
  }
}

function handleSidebarSelect(tabKey) {
  activeTab.value = tabKey
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
  if (payload?.row?.key) {
    openSource(payload.row.key)
  }
}

function handleTableAction(payload) {
  if (!payload?.row) {
    return
  }
  if (payload.actionItem?.key === 'edit') {
    openEditDialog(payload.row)
    return
  }
  openSource(payload.row.key)
}
</script>

<template>
  <div class="knowledge-page">
    <ListCommonLayout :sidebar-collapsed="sidebarCollapsed">
      <template #sidebar>
        <Sidebar title="知识库分组" collapsible :collapsed="sidebarCollapsed" @toggle="sidebarCollapsed = $event">
          <template #main>
            <button
              v-for="item in sidebarItems"
              :key="item.key"
              type="button"
              class="sidebar-entry"
              :class="{ active: activeTab === item.key }"
              @click="handleSidebarSelect(item.key)"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
            </button>
          </template>
        </Sidebar>
      </template>

      <template #header>
        <HeaderBar title="知识库" :meta="`${activeTabLabel} · ${filteredSources.length} 条`" :show-title="true">
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
          <div v-if="errorMessage" class="knowledge-message is-error">
            {{ errorMessage }}
          </div>
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

.knowledge-message {
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--stroke);
  background: var(--surface-bg-1);
  color: var(--text-dim);
}

.knowledge-message.is-error {
  border-color: rgba(220, 38, 38, 0.22);
  background: rgba(254, 242, 242, 0.9);
  color: #b91c1c;
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
