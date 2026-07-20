<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { AppPagination } from '../../../components'
import { SingleListLayout } from '../../layout'
import ListDataView from './components/ListDataView.vue'
import ListFilterBar from './components/ListFilterBar.vue'
import ListHeaderBar from './components/ListHeaderBar.vue'
import ListSummaryBar from './components/ListSummaryBar.vue'
import ListTabsBar from './components/ListTabsBar.vue'
import ListTreePanel from './components/ListTreePanel.vue'
import { createDefaultQueryState, normalizeSchema, shouldShowTree } from './schema'
import type {
  ApplicationRendererState,
  ListRendererData,
  ListRendererSchema,
  RendererAction,
  RendererQueryState,
  RendererTreeNode,
} from './types'

const props = withDefaults(
  defineProps<{
    schema: ListRendererSchema
    data?: Partial<ListRendererData>
    state?: ApplicationRendererState
    records?: Record<string, unknown>[]
    treeData?: RendererTreeNode[]
    loading?: boolean
    total?: number
    developerMode?: boolean
    developerActions?: RendererAction[]
  }>(),
  {
    records: () => [],
    treeData: () => [],
    loading: false,
    total: 0,
    developerMode: false,
    developerActions: () => [],
  },
)

const emit = defineEmits<{
  action: [action: RendererAction]
  itemAction: [payload: { action: RendererAction; row: Record<string, unknown> }]
  queryChange: [query: RendererQueryState]
  reload: [query: RendererQueryState]
}>()

const normalizedSchema = computed(() => normalizeSchema(props.schema))
const schemaQuerySignature = computed(() =>
  JSON.stringify({
    component: normalizedSchema.value.component,
    activeTab: normalizedSchema.value.tab?.activeTab,
    filterKeys: (normalizedSchema.value.filters || []).map((filter) => filter.key),
    pageSize: normalizedSchema.value.list_config?.pagination?.pageSize,
    itemType: normalizedSchema.value.list_config?.itemType,
  }),
)
const queryState = reactive<RendererQueryState>(createDefaultQueryState(normalizedSchema.value))

watch(
  schemaQuerySignature,
  () => {
    Object.assign(queryState, createDefaultQueryState(normalizedSchema.value))
  },
)

watch(
  queryState,
  () => {
    emit('queryChange', { ...queryState, filters: { ...queryState.filters } })
  },
  { deep: true },
)

const showTree = computed(() => shouldShowTree(normalizedSchema.value))
const paginationEnabled = computed(() => normalizedSchema.value.list_config?.pagination?.enabled)
const variant = computed(() => normalizedSchema.value.list_config?.variant || 'default')
const summaryCards = computed(() => normalizedSchema.value.summary?.cards || [])
const resolvedRecords = computed(() => props.data?.records || props.records)
const resolvedTreeData = computed(() => props.data?.treeData || props.treeData)
const resolvedLoading = computed(() => props.state?.loading ?? props.loading)
const resolvedTotal = computed(() => props.data?.total ?? props.total)

const handleAction = (action: RendererAction) => {
  emit('action', action)
}

const handleItemAction = (payload: { action: RendererAction; row: Record<string, unknown> }) => {
  emit('itemAction', payload)
}

const handleSearch = () => {
  queryState.page = 1
  emit('reload', { ...queryState, filters: { ...queryState.filters } })
}

const handleReset = () => {
  Object.assign(queryState, createDefaultQueryState(normalizedSchema.value))
  emit('reload', { ...queryState, filters: { ...queryState.filters } })
}

const handleTabChange = (value: string) => {
  queryState.activeTab = value
  queryState.page = 1
  emit('reload', { ...queryState, filters: { ...queryState.filters } })
}

const handleTreeSelect = (node: RendererTreeNode) => {
  queryState.selectedTreeKey = node.key
  queryState.page = 1
  emit('reload', { ...queryState, filters: { ...queryState.filters } })
}

const handleFilterChange = (filters: Record<string, unknown>) => {
  queryState.filters = filters
}

const handlePageChange = (page: number) => {
  queryState.page = page
  emit('reload', { ...queryState, filters: { ...queryState.filters } })
}

const handlePageSizeChange = (pageSize: number) => {
  queryState.pageSize = pageSize
  queryState.page = 1
  emit('reload', { ...queryState, filters: { ...queryState.filters } })
}
</script>

<template>
  <SingleListLayout
    :variant="variant"
    :show-tree="showTree"
    :pagination-enabled="paginationEnabled"
  >
    <template #header>
      <ListHeaderBar
        :schema="normalizedSchema"
        :developer-mode="developerMode"
        :developer-actions="developerActions"
        @action="handleAction"
      />
    </template>

    <template #tabs>
      <ListTabsBar
        :model-value="queryState.activeTab"
        :tab="normalizedSchema.tab"
        @update:model-value="handleTabChange"
      />
    </template>

    <template #tree>
      <ListTreePanel
        :config="normalizedSchema.tree"
        :data="resolvedTreeData"
        :selected-key="queryState.selectedTreeKey"
        @select="handleTreeSelect"
      />
    </template>

    <template #filters>
      <ListFilterBar
        :filters="normalizedSchema.filters || []"
        :model-value="queryState.filters"
        @update:model-value="handleFilterChange"
        @submit="handleSearch"
        @reset="handleReset"
      />
    </template>

    <template #summary>
      <ListSummaryBar :cards="summaryCards" />
    </template>

    <ListDataView
      :schema="normalizedSchema"
      :records="resolvedRecords"
      :loading="resolvedLoading"
      @item-action="handleItemAction"
    />

    <template #pagination>
      <AppPagination
        v-model:current-page="queryState.page"
        v-model:page-size="queryState.pageSize"
        :page-sizes="normalizedSchema.list_config?.pagination?.pageSizeOptions || [5, 10, 20, 30, 50]"
        :total="resolvedTotal"
        :pager-count="5"
        @current-change="handlePageChange"
        @size-change="handlePageSizeChange"
      />
    </template>
  </SingleListLayout>
</template>
