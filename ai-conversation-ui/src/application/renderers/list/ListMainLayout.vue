<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import ListDataView from './components/ListDataView.vue'
import ListFilterBar from './components/ListFilterBar.vue'
import ListHeaderBar from './components/ListHeaderBar.vue'
import ListTabsBar from './components/ListTabsBar.vue'
import ListTreePanel from './components/ListTreePanel.vue'
import { createDefaultQueryState, normalizeSchema, shouldShowTree } from './schema'
import type {
  ListRendererSchema,
  RendererAction,
  RendererQueryState,
  RendererTreeNode,
} from './types'

const props = withDefaults(
  defineProps<{
    schema: ListRendererSchema
    records?: Record<string, unknown>[]
    treeData?: RendererTreeNode[]
    loading?: boolean
    total?: number
  }>(),
  {
    records: () => [],
    treeData: () => [],
    loading: false,
    total: 0,
  },
)

const emit = defineEmits<{
  action: [action: RendererAction]
  itemAction: [payload: { action: RendererAction; row: Record<string, unknown> }]
  queryChange: [query: RendererQueryState]
  reload: [query: RendererQueryState]
}>()

const normalizedSchema = computed(() => normalizeSchema(props.schema))
const queryState = reactive<RendererQueryState>(createDefaultQueryState(normalizedSchema.value))

watch(
  normalizedSchema,
  (schema) => {
    Object.assign(queryState, createDefaultQueryState(schema))
  },
  { deep: true },
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
  <section class="list-main-layout">
    <ListHeaderBar :schema="normalizedSchema" @action="handleAction" />

    <ListTabsBar
      :model-value="queryState.activeTab"
      :tab="normalizedSchema.tab"
      @update:model-value="handleTabChange"
    />

    <ListFilterBar
      :filters="normalizedSchema.filters || []"
      :model-value="queryState.filters"
      @update:model-value="handleFilterChange"
      @submit="handleSearch"
      @reset="handleReset"
    />

    <div class="list-main-layout__body" :class="{ 'list-main-layout__body--tree': showTree }">
      <aside v-if="showTree" class="list-main-layout__tree">
        <ListTreePanel
          :data="treeData"
          :selected-key="queryState.selectedTreeKey"
          @select="handleTreeSelect"
        />
      </aside>

      <div class="list-main-layout__content">
        <el-card shadow="never" class="list-main-layout__content-card">
          <ListDataView
            :schema="normalizedSchema"
            :records="records"
            :loading="loading"
            @item-action="handleItemAction"
          />

          <div v-if="paginationEnabled" class="list-main-layout__pagination">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :current-page="queryState.page"
              :page-size="queryState.pageSize"
              :page-sizes="normalizedSchema.list_config?.pagination?.pageSizeOptions || [10, 20, 30, 50]"
              :total="total"
              @current-change="handlePageChange"
              @size-change="handlePageSizeChange"
            />
          </div>
        </el-card>
      </div>
    </div>
  </section>
</template>

<style scoped>
.list-main-layout {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(35, 108, 255, 0.08), transparent 32%),
    linear-gradient(180deg, #f6f9fc 0%, #eef3f8 100%);
  border-radius: 28px;
}

.list-main-layout__body {
  min-width: 0;
}

.list-main-layout__body--tree {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 20px;
}

.list-main-layout__tree,
.list-main-layout__content {
  min-width: 0;
}

.list-main-layout__content-card {
  border-radius: 28px;
}

.list-main-layout__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

@media (max-width: 960px) {
  .list-main-layout {
    padding: 16px;
  }

  .list-main-layout__body--tree {
    grid-template-columns: 1fr;
  }
}
</style>
