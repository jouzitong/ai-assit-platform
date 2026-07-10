<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { findApplicationRenderer } from '../../../application/registry'
import { RenderJsonRuntimeHost, resolveRendererRuntimeData } from '../../../application/runtime'
import {
  createDefaultQueryState,
  normalizeSchema,
} from '../../../application/renderers/list/schema'
import type {
  ApplicationRendererState,
  ListRendererData,
  ListRendererSchema,
  RendererAction,
  RendererQueryState,
} from '../../../application/renderers/list/types'

const schema: ListRendererSchema = {
  id: 'render-runtime-default-config-list',
  version: '1.0.0',
  title: 'Render Runtime · default_config',
  component: 'zg-common-list',
  tab: {
    activeTab: 'all',
    tabs: [
      { key: 'all', label: '全部配置' },
      { key: 'string', label: '字符串' },
      { key: 'json', label: 'JSON' },
    ],
  },
  datasource: {
    key: 'runtime-default-config',
    type: 'db-query-list',
    model: 'default_config',
    filter_dict: {
      deleted: 0,
    },
    filterExpr: 'deleted',
    ext: {
      fields: [
        'id',
        'version',
        'create_time',
        'created_by',
        'update_time',
        'updated_by',
        'config_key',
        'config_value',
        'desc',
        'tags',
        'value_type',
        'deleted',
      ],
      sorts: [
        {
          field: 'update_time',
          order: 'desc',
        },
      ],
    },
  },
  filters: [
    {
      key: 'config_key',
      label: '配置键',
      component: 'zg-input',
    },
    {
      key: 'value_type',
      label: '值类型',
      component: 'zg-selector',
      list: [
        { key: 'string', value: 'string' },
        { key: 'json', value: 'json' },
        { key: 'text', value: 'text' },
        { key: 'number', value: 'number' },
      ],
      options: {
        clearable: true,
        filterable: true,
        styles: {
          width: '180px',
        },
      },
    },
    {
      key: 'deleted',
      label: '删除标记',
      component: 'zg-selector',
      list: [
        { key: '未删除', value: 0 },
        { key: '已删除', value: 1 },
      ],
      options: {
        clearable: true,
        styles: {
          width: '160px',
        },
      },
    },
  ],
  fields: [
    {
      key: 'id',
      name: 'id',
      label: 'ID',
      field: ['id'],
      options: {
        styles: {
          'text-align': 'center',
          width: 8,
        },
        className: 'text-center',
      },
    },
    {
      key: 'config_key',
      name: 'config_key',
      label: '配置键',
      field: ['config_key'],
      options: {
        styles: {
          'text-align': 'left',
          width: 18,
        },
      },
    },
    {
      key: 'value_type',
      name: 'value_type',
      label: '值类型',
      field: ['value_type'],
      options: {
        styles: {
          'text-align': 'left',
          width: 12,
        },
      },
    },
    {
      key: 'config_value',
      name: 'config_value',
      label: '配置值',
      field: ['config_value'],
      options: {
        styles: {
          'text-align': 'left',
          width: 28,
        },
      },
    },
    {
      key: 'desc',
      name: 'desc',
      label: '备注',
      field: ['desc'],
      options: {
        styles: {
          'text-align': 'left',
          width: 16,
        },
      },
    },
    {
      key: 'tags',
      name: 'tags',
      label: '标签',
      field: ['tags'],
      options: {
        styles: {
          'text-align': 'left',
          width: 14,
        },
      },
    },
    {
      key: 'deleted',
      name: 'deleted',
      label: '删除',
      field: ['deleted'],
      options: {
        styles: {
          'text-align': 'center',
          width: 8,
        },
      },
    },
    {
      key: 'update_time',
      name: 'update_time',
      label: '更新时间',
      field: ['update_time'],
      options: {
        styles: {
          'text-align': 'left',
          width: 14,
        },
      },
    },
  ],
  actions: [
    {
      key: 'reload',
      name: '刷新',
      action: 'RELOAD',
      type: 'primary',
    },
  ],
  list_config: {
    variant: 'workbench',
    itemType: 'table',
    actionColumns: [
      {
        key: 'view',
        name: '查看',
        action: 'VIEW',
        type: 'primary',
      },
    ],
    pagination: {
      enabled: true,
      pageSize: 10,
      pageSizeOptions: [10, 20, 50],
    },
    className: 'runtime-list-layout',
    events: [],
  },
}

const runtimeDocument = {
  protocol: 'render-json',
  protocolVersion: '1.0.0',
  pageId: 'runtime-default-config-list',
  revision: 'db-query-default-config',
  root: {
    id: 'default-config-list',
    component: schema.component,
  },
} as const

const normalizedSchema = computed(() => normalizeSchema(schema))
const rendererDefinition = computed(() => findApplicationRenderer(normalizedSchema.value.component))
const rendererComponent = computed(() => rendererDefinition.value?.component)
const queryState = reactive<RendererQueryState>(createDefaultQueryState(normalizedSchema.value))
const resolvedData = ref<Partial<ListRendererData>>({
  records: [],
  total: 0,
})
const rendererState = reactive<ApplicationRendererState>({
  loading: false,
  error: undefined,
  empty: false,
})
const runtimeErrorMessage = ref('')
const runtimeNotes = ref<string[]>([])
const lastResolvedQuery = ref<Partial<RendererQueryState>>({})

watch(
  normalizedSchema,
  (value) => {
    Object.assign(queryState, createDefaultQueryState(value))
  },
  { deep: true },
)

const summaryCards = computed(() => [
  { key: 'total', label: '总记录', value: resolvedData.value.total || 0, accent: '#60a5fa' },
  { key: 'page', label: '当前页', value: queryState.page, accent: '#34d399' },
  { key: 'size', label: '分页大小', value: queryState.pageSize, accent: '#f59e0b' },
  { key: 'rows', label: '当前行数', value: resolvedData.value.records?.length || 0, accent: '#a78bfa' },
])

const runtimeRendererSchema = computed<ListRendererSchema>(() => ({
  ...normalizedSchema.value,
  summary: {
    cards: summaryCards.value,
  },
}))

const runtimeSummary = computed(() =>
  JSON.stringify(
    {
      protocol: runtimeDocument.protocol,
      protocolVersion: runtimeDocument.protocolVersion,
      pageId: runtimeDocument.pageId,
      root: runtimeDocument.root,
      datasource: normalizedSchema.value.datasource,
    },
    null,
    2,
  ),
)

function buildRuntimeQuery(query: Partial<RendererQueryState>) {
  const nextFilters = {
    ...(query.filters || {}),
  }

  if (query.activeTab === 'string' || query.activeTab === 'json') {
    nextFilters.value_type = query.activeTab
  }

  if (query.activeTab === 'all' && (nextFilters.value_type === 'string' || nextFilters.value_type === 'json')) {
    delete nextFilters.value_type
  }

  return {
    ...query,
    filters: nextFilters,
  }
}

async function loadRendererData(query: Partial<RendererQueryState>) {
  rendererState.loading = true
  rendererState.error = undefined
  runtimeErrorMessage.value = ''

  const runtimeQuery = buildRuntimeQuery(query)
  lastResolvedQuery.value = runtimeQuery

  try {
    const payload = await resolveRendererRuntimeData(normalizedSchema.value.component, {
      schema: normalizedSchema.value,
      query: runtimeQuery,
    })

    const resolved = payload.resolved as { data?: Partial<ListRendererData> } | null
    resolvedData.value = {
      records: resolved?.data?.records || [],
      total: resolved?.data?.total || 0,
      treeData: resolved?.data?.treeData || [],
    }
    rendererState.empty = (resolvedData.value.records?.length || 0) === 0
  } catch (error) {
    const message = error instanceof Error ? error.message : '列表数据加载失败'
    rendererState.error = error
    rendererState.empty = false
    runtimeErrorMessage.value = message
    ElMessage.error(message)
  } finally {
    rendererState.loading = false
  }
}

onMounted(() => {
  if (!rendererDefinition.value) {
    runtimeErrorMessage.value = `未找到 renderer: ${normalizedSchema.value.component}`
    return
  }

  runtimeNotes.value = [
    '当前测试页由 runtime 入口驱动 resolver 查询 `default_config`。',
    '列表布局由 list renderer 内部复用 SingleListLayout 承载。',
    '数据源使用 `db-query-list`，没有直接在 renderer 内发请求。',
  ]

  void loadRendererData(queryState)
})

function handleAction(action: RendererAction) {
  if (action.action === 'RELOAD') {
    void loadRendererData(queryState)
    return
  }

  ElMessage.success(`runtime action: ${action.action}`)
}

function handleItemAction(payload: { action: RendererAction; row: Record<string, unknown> }) {
  ElMessage.warning(`row action: ${payload.action.action} / ${String(payload.row.id ?? '')}`)
}

function handleQueryChange(nextQuery: RendererQueryState) {
  Object.assign(queryState, nextQuery)
}

function handleReload(nextQuery: RendererQueryState) {
  Object.assign(queryState, nextQuery)
  void loadRendererData(nextQuery)
}
</script>

<template>
  <RenderJsonRuntimeHost :document="runtimeDocument">
    <template #default="{ document, protocolLabel, hasRootNode }">
      <section class="test-render-runtime-view">
        <header class="test-render-runtime-view__hero">
          <div>
            <p class="test-render-runtime-view__eyebrow">Render Runtime Test</p>
            <h1>default_config</h1>
            <p class="test-render-runtime-view__description">
              当前页面通过 runtime 入口查找 list renderer，并调用 resolver 对 `default_config`
              执行 `db-query-list` 查询。layout 仍由 list renderer 内部的
              `SingleListLayout` 承载。
            </p>
          </div>

          <aside class="test-render-runtime-view__meta">
            <el-tag size="large" effect="plain">{{ protocolLabel }}</el-tag>
            <el-tag size="large" type="success" effect="plain">
              {{ hasRootNode ? 'root ready' : 'root missing' }}
            </el-tag>
            <el-tag size="large" type="info" effect="plain">
              {{ document?.pageId || 'unknown-page' }}
            </el-tag>
          </aside>
        </header>

        <div class="test-render-runtime-view__grid">
          <article class="test-render-runtime-view__preview">
            <div v-if="runtimeErrorMessage" class="test-render-runtime-view__runtime-error">
              {{ runtimeErrorMessage }}
            </div>

            <component
              :is="rendererComponent"
              v-else-if="rendererComponent"
              :schema="runtimeRendererSchema"
              :data="resolvedData"
              :state="rendererState"
              :records="resolvedData.records || []"
              :total="resolvedData.total || 0"
              @query-change="handleQueryChange"
              @reload="handleReload"
              @action="handleAction"
              @item-action="handleItemAction"
            />
          </article>

          <aside class="test-render-runtime-view__panel">
            <div v-if="runtimeNotes.length" class="test-render-runtime-view__panel-card">
              <h2>Runtime Notes</h2>
              <ul class="test-render-runtime-view__notes">
                <li v-for="note in runtimeNotes" :key="note">{{ note }}</li>
              </ul>
            </div>

            <div class="test-render-runtime-view__panel-card">
              <h2>Runtime Document</h2>
              <pre>{{ runtimeSummary }}</pre>
            </div>

            <div class="test-render-runtime-view__panel-card">
              <h2>Current Query State</h2>
              <pre>{{ JSON.stringify(queryState, null, 2) }}</pre>
            </div>

            <div class="test-render-runtime-view__panel-card">
              <h2>Last Resolved Query</h2>
              <pre>{{ JSON.stringify(lastResolvedQuery, null, 2) }}</pre>
            </div>

            <div class="test-render-runtime-view__panel-card">
              <h2>Resolved Records</h2>
              <pre>{{ JSON.stringify(resolvedData.records?.slice(0, 3) || [], null, 2) }}</pre>
            </div>
          </aside>
        </div>
      </section>
    </template>
  </RenderJsonRuntimeHost>
</template>

<style scoped>
.test-render-runtime-view {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.test-render-runtime-view__hero {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
}

.test-render-runtime-view__eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: var(--app-primary);
}

.test-render-runtime-view__hero h1 {
  margin: 0;
  font-size: 30px;
  line-height: 1.1;
  color: var(--app-title);
}

.test-render-runtime-view__description {
  max-width: 720px;
  margin: 12px 0 0;
  color: var(--app-text-muted);
  line-height: 1.7;
}

.test-render-runtime-view__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.test-render-runtime-view__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 24px;
  align-items: start;
}

.test-render-runtime-view__preview,
.test-render-runtime-view__panel-card {
  min-width: 0;
}

.test-render-runtime-view__panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.test-render-runtime-view__panel-card {
  padding: 18px 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 20px;
  background: var(--el-bg-color);
  box-shadow: var(--app-soft-shadow);
}

.test-render-runtime-view__panel-card h2 {
  margin: 0 0 12px;
  font-size: 16px;
  color: var(--app-title);
}

.test-render-runtime-view__panel-card pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
}

.test-render-runtime-view__runtime-error {
  padding: 18px 20px;
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: 20px;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.test-render-runtime-view__notes {
  margin: 0;
  padding-left: 18px;
  color: var(--app-text-muted);
  line-height: 1.7;
}

@media (max-width: 1280px) {
  .test-render-runtime-view__grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .test-render-runtime-view__hero {
    flex-direction: column;
  }

  .test-render-runtime-view__meta {
    justify-content: flex-start;
  }
}
</style>
