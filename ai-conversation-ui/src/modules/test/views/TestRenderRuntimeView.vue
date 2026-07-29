<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { AppCodeEditor } from '../../../components'
import { AppJsonTree } from '../../../components/basic'
import { findApplicationRenderer } from '../../../application/registry'
import { ResponsiveViewport } from '../../../application/layout'
import {
  buildDbQueryListRequest,
  resolveListRendererStructure,
} from '../../../application/resolver/db-query-list-resolver'
import {
  createRenderRuntimeScope,
  createRuntimeEventDispatcher,
  loadRenderMetaContent,
  patchRenderRuntimeScope,
  RenderJsonRuntimeHost,
  recordRuntimeEvent,
  resolveRendererRuntimeData,
  upsertRenderMetaContent,
} from '../../../application/runtime'
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
import type { RuntimeRendererEvent } from '../../../application/runtime'
import { getDeveloperModeEnabled } from '../../../utils/developerMode'

const RENDER_META_CODE = 'default_config.meta.list.test'

const baseSchema: ListRendererSchema = {
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
      options: {
        list: [
          { key: 'string', value: 'string' },
          { key: 'json', value: 'json' },
          { key: 'text', value: 'text' },
          { key: 'number', value: 'number' },
        ],
        clearable: true,
        filterable: true,
        submitOnChange: true,
        styles: {
          width: '180px',
        },
      },
    },
    {
      key: 'deleted',
      label: '删除标记',
      component: 'zg-selector',
      options: {
        list: [
          { key: '未删除', value: 0 },
          { key: '已删除', value: 1 },
        ],
        clearable: true,
        submitOnChange: true,
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
      options: { type: 'primary' },
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
        options: { type: 'primary' },
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

const schemaState = ref<ListRendererSchema>(cloneSchema(baseSchema))
const renderDocumentState = ref<Record<string, unknown> | null>(null)
const metadataDialogVisible = ref(false)
const metadataSaving = ref(false)
const contextPanelVisible = ref(false)
const developerModeEnabled = ref(false)
const metadataDraft = ref(stringifySchema(schemaState.value))
const normalizedSchema = computed(() => normalizeSchema(schemaState.value))
const runtimeDocument = computed(() => ({
  ...(renderDocumentState.value || {}),
  protocol: 'render-json',
  protocolVersion: String(renderDocumentState.value?.protocolVersion || '1.0.0'),
  pageId: String(renderDocumentState.value?.pageId || RENDER_META_CODE),
  revision: 'db-query-default-config',
  root: {
    ...((renderDocumentState.value?.root || {}) as Record<string, unknown>),
    id: 'default-config-list',
    component: normalizedSchema.value.component,
  },
}) as const)
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
const lastRuntimeQuery = ref<Partial<RendererQueryState>>(buildRuntimeQuery(queryState))
const lastRequestBody = ref(buildDbQueryListRequest(normalizedSchema.value, lastRuntimeQuery.value))
const lastLoadedRequestSignature = ref('')
const runtimeScope = reactive(createRenderRuntimeScope({
  code: RENDER_META_CODE,
  document: runtimeDocument.value,
  schema: normalizedSchema.value,
  query: queryState,
  data: resolvedData.value,
  state: rendererState,
}))

watch(
  normalizedSchema,
  (value) => {
    Object.assign(queryState, createDefaultQueryState(value))
  },
  { deep: true },
)

const developerActions = computed<NonNullable<ListRendererSchema['actions']>>(() => {
  if (!developerModeEnabled.value) {
    return []
  }

  return [
    {
      key: '__metadata__',
      name: '元数据配置',
      action: 'METADATA',
      options: { type: 'info' },
    },
    {
      key: '__runtime_context__',
      name: 'SCOPE',
      action: 'DEBUG_CONTEXT',
      options: { icon: 'operation', type: 'info' },
    },
  ]
})

const runtimeRendererSchema = computed<ListRendererSchema>(() => ({
  ...normalizedSchema.value,
  actions: [
    ...(normalizedSchema.value.actions || []).filter((action) =>
      action.action !== 'METADATA' && action.action !== 'DEBUG_CONTEXT',
    ),
    ...developerActions.value,
  ],
}))

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

const currentRuntimeQuery = computed(() => buildRuntimeQuery(queryState))
const currentRequestBody = computed(() => buildDbQueryListRequest(normalizedSchema.value, currentRuntimeQuery.value))
const currentRequestPlans = computed(() => resolveListRendererStructure({
  schema: normalizedSchema.value,
  query: currentRuntimeQuery.value,
}).requestPlans)
watch(
  [runtimeDocument, normalizedSchema, currentRuntimeQuery, currentRequestPlans, resolvedData],
  () => {
    patchRenderRuntimeScope(runtimeScope, {
      document: runtimeDocument.value,
      schema: normalizedSchema.value,
      query: currentRuntimeQuery.value,
      requestPlans: currentRequestPlans.value,
      data: resolvedData.value,
      state: rendererState,
    })
  },
  { deep: true },
)
const runtimeContext = computed(() => ({
  ...runtimeScope,
  request: currentRequestBody.value,
  lastQuery: lastRuntimeQuery.value,
  lastRequest: lastRequestBody.value,
}))

async function loadRuntimeDocument() {
  const content = await loadRenderMetaContent(RENDER_META_CODE)
  renderDocumentState.value = content
  const nextSchema = resolveSchemaFromRenderDocument(content)
  if (nextSchema) {
    schemaState.value = cloneSchema(nextSchema)
    metadataDraft.value = stringifySchema(schemaState.value)
    const nextQuery = createDefaultQueryState(normalizeSchema(schemaState.value))
    Object.assign(queryState, nextQuery)
  }
  patchRenderRuntimeScope(runtimeScope, {
    code: RENDER_META_CODE,
    document: runtimeDocument.value,
    schema: normalizedSchema.value,
    query: queryState,
  })
}

async function loadRendererData(
  query: Partial<RendererQueryState>,
  eventOrOptions?: RuntimeRendererEvent | { force?: boolean },
) {
  const runtimeQuery = buildRuntimeQuery(query)
  const requestBody = buildDbQueryListRequest(normalizedSchema.value, runtimeQuery)
  const requestSignature = JSON.stringify(requestBody)
  const forceReload = isForceReload(eventOrOptions)

  if (!forceReload && lastLoadedRequestSignature.value === requestSignature) {
    lastRuntimeQuery.value = runtimeQuery
    lastRequestBody.value = requestBody
    return
  }

  rendererState.loading = true
  rendererState.error = undefined
  runtimeErrorMessage.value = ''
  lastRuntimeQuery.value = runtimeQuery
  lastRequestBody.value = requestBody

  try {
    const payload = await resolveRendererRuntimeData(normalizedSchema.value.component, {
      schema: normalizedSchema.value,
      query: runtimeQuery,
    })

    const resolved = payload.resolved as { data?: Partial<ListRendererData>; requestPlans?: unknown[] } | null
    resolvedData.value = {
      records: resolved?.data?.records || [],
      total: resolved?.data?.total || 0,
      treeData: resolved?.data?.treeData || [],
    }
    patchRenderRuntimeScope(runtimeScope, {
      requestPlans: resolved?.requestPlans || [],
      data: resolvedData.value,
      state: rendererState,
    })
    lastLoadedRequestSignature.value = requestSignature
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

function isForceReload(eventOrOptions?: RuntimeRendererEvent | { force?: boolean }) {
  if (!eventOrOptions) {
    return false
  }

  if ('force' in eventOrOptions) {
    return Boolean(eventOrOptions.force)
  }

  if (eventOrOptions.type === 'action') {
    const payload = eventOrOptions.payload as { action?: RendererAction } | undefined
    return payload?.action?.action === 'RELOAD'
  }

  return false
}

onMounted(async () => {
  developerModeEnabled.value = getDeveloperModeEnabled()

  try {
    await loadRuntimeDocument()
  } catch (error) {
    ElMessage.warning(error instanceof Error ? `Render Meta 加载失败，已使用本地兜底配置: ${error.message}` : 'Render Meta 加载失败，已使用本地兜底配置')
  }

  if (!rendererDefinition.value) {
    runtimeErrorMessage.value = `未找到 renderer: ${normalizedSchema.value.component}`
    return
  }

  void loadRendererData(queryState)
})

function handleAction(action: RendererAction) {
  void runtimeEventDispatcher.dispatch({
    type: 'action',
    source: buildRuntimeEventSource('action'),
    payload: { action },
  })
}

function handleItemAction(payload: { action: RendererAction; row: Record<string, unknown> }) {
  void runtimeEventDispatcher.dispatch({
    type: 'itemAction',
    source: buildRuntimeEventSource('itemAction'),
    payload,
  })
}

function handleQueryChange(nextQuery: RendererQueryState) {
  void runtimeEventDispatcher.dispatch({
    type: 'queryChange',
    source: buildRuntimeEventSource('queryChange'),
    payload: nextQuery,
  })
}

function handleReload(nextQuery: RendererQueryState) {
  void runtimeEventDispatcher.dispatch({
    type: 'reload',
    source: buildRuntimeEventSource('reload'),
    payload: nextQuery,
  })
}

function executeRuntimeAction(payload: { action: RendererAction; row?: Record<string, unknown> }) {
  const { action, row } = payload
  if (action.action === 'METADATA') {
    if (!developerModeEnabled.value) {
      return
    }
    metadataDraft.value = stringifySchema(schemaState.value)
    metadataDialogVisible.value = true
    return
  }

  if (action.action === 'DEBUG_CONTEXT') {
    if (!developerModeEnabled.value) {
      return
    }
    contextPanelVisible.value = true
    return
  }

  const rowSuffix = row ? ` / ${String(row.id ?? '')}` : ''
  ElMessage.success(`runtime action: ${action.action}${rowSuffix}`)
}

function updateRuntimeQuery(nextQuery: Partial<RendererQueryState>) {
  Object.assign(queryState, nextQuery)
  patchRenderRuntimeScope(runtimeScope, {
    query: queryState,
  })
}

function buildRuntimeEventSource(event: string) {
  return {
    renderer: normalizedSchema.value.component,
    componentId: normalizedSchema.value.id,
    event,
  }
}

const runtimeEventDispatcher = createRuntimeEventDispatcher({
  getContext: () => runtimeContext.value,
  updateQuery: updateRuntimeQuery,
  reload: loadRendererData,
  executeAction: executeRuntimeAction,
  runHook: (_name, payload) => {
    recordRuntimeEvent(runtimeScope, payload.event)
  },
})

function resolveSchemaFromRenderDocument(content: Record<string, unknown>): ListRendererSchema | null {
  if (isListRendererSchema(content)) {
    return content
  }

  const schema = content.schema
  if (isListRendererSchema(schema)) {
    return schema
  }

  const root = content.root
  if (isRecord(root)) {
    if (isListRendererSchema(root.schema)) {
      return root.schema
    }
    if (isListRendererSchema(root)) {
      return root
    }
  }

  return null
}

function isListRendererSchema(value: unknown): value is ListRendererSchema {
  return isRecord(value) && typeof value.id === 'string' && typeof value.component === 'string'
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function handleFormatMetadata() {
  try {
    metadataDraft.value = stringifySchema(JSON.parse(metadataDraft.value) as ListRendererSchema)
  } catch {
    ElMessage.error('当前 JSON 格式不合法，无法格式化')
  }
}

async function handleResetMetadata() {
  schemaState.value = cloneSchema(baseSchema)
  metadataDraft.value = stringifySchema(schemaState.value)
  metadataDialogVisible.value = false
  const nextQuery = createDefaultQueryState(normalizeSchema(schemaState.value))
  Object.assign(queryState, nextQuery)
  await loadRendererData(nextQuery)
  ElMessage.success('已恢复默认元数据并重新请求')
}

async function handleApplyMetadata() {
  metadataSaving.value = true
  try {
    const nextSchema = JSON.parse(metadataDraft.value) as ListRendererSchema
    const savedContent = await upsertRenderMetaContent(RENDER_META_CODE, cloneSchema(nextSchema) as unknown as Record<string, unknown>)
    renderDocumentState.value = savedContent
    const savedSchema = resolveSchemaFromRenderDocument(savedContent) || nextSchema
    schemaState.value = cloneSchema(savedSchema)
    metadataDraft.value = stringifySchema(schemaState.value)
    metadataDialogVisible.value = false
    const nextQuery = createDefaultQueryState(normalizeSchema(schemaState.value))
    Object.assign(queryState, nextQuery)
    await loadRendererData(nextQuery)
    ElMessage.success('元数据已保存')
  } catch (error) {
    ElMessage.error(error instanceof Error ? `JSON 解析失败: ${error.message}` : 'JSON 解析失败')
  } finally {
    metadataSaving.value = false
  }
}

function cloneSchema(schema: ListRendererSchema) {
  return JSON.parse(JSON.stringify(schema)) as ListRendererSchema
}

function stringifySchema(schema: ListRendererSchema) {
  return JSON.stringify(schema, null, 2)
}
</script>

<template>
  <ResponsiveViewport
    preset="dashboard"
    :config="{ maxScale: 1.15 }"
  >
    <RenderJsonRuntimeHost :document="runtimeDocument">
      <section class="test-render-runtime-view">
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

        <teleport to="body">
          <div
            v-if="contextPanelVisible"
            class="test-render-runtime-view__context-layer"
            @click.self="contextPanelVisible = false"
          >
            <aside class="test-render-runtime-view__context-panel">
              <header class="test-render-runtime-view__context-header">
                <h2>Runtime Context</h2>
                <el-button text @click="contextPanelVisible = false">关闭</el-button>
              </header>
              <div class="test-render-runtime-view__context-body">
                <div class="test-render-runtime-view__context-meta">
                  <span class="test-render-runtime-view__context-meta-label">核心组件</span>
                  <span class="test-render-runtime-view__context-meta-value">{{ normalizedSchema.component }}</span>
                </div>
                <AppJsonTree :value="runtimeContext" label="SCOPE" />
              </div>
            </aside>
          </div>
        </teleport>

        <el-dialog
          v-model="metadataDialogVisible"
          title="Render JSON 元数据配置"
          width="960px"
          destroy-on-close
          class="test-render-runtime-view__metadata-dialog"
        >
          <div class="test-render-runtime-view__metadata-toolbar">
            <el-button @click="handleFormatMetadata">格式化 JSON</el-button>
            <el-button @click="handleResetMetadata">恢复默认</el-button>
          </div>

          <AppCodeEditor
            v-model="metadataDraft"
            format="json"
            height="520px"
            min-height="520px"
            toolbar-label="JSON"
            :show-format-switcher="false"
            class="test-render-runtime-view__metadata-input"
          />

          <template #footer>
            <div class="test-render-runtime-view__metadata-footer">
              <el-button @click="metadataDialogVisible = false">取消</el-button>
              <el-button type="primary" :loading="metadataSaving" @click="handleApplyMetadata">保存</el-button>
            </div>
          </template>
        </el-dialog>
      </section>
    </RenderJsonRuntimeHost>
  </ResponsiveViewport>
</template>

<style scoped>
.test-render-runtime-view {
  height: 100%;
  min-width: 0;
}

.test-render-runtime-view__runtime-error {
  padding: 18px 20px;
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: 20px;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.test-render-runtime-view__metadata-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.test-render-runtime-view__metadata-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.test-render-runtime-view__metadata-input {
  width: 100%;
}

.test-render-runtime-view__context-layer {
  position: fixed;
  inset: 0;
  z-index: 2100;
  pointer-events: auto;
}

.test-render-runtime-view__context-panel {
  position: fixed;
  top: 88px;
  right: 28px;
  width: min(372px, calc(100vw - 56px));
  max-height: calc(100vh - 116px);
  display: flex;
  flex-direction: column;
  min-width: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  box-shadow: var(--el-box-shadow-dark);
}

.test-render-runtime-view__context-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.test-render-runtime-view__context-header h2 {
  margin: 0;
  font-size: 15px;
  color: var(--app-title);
}

.test-render-runtime-view__context-body {
  min-height: 0;
  overflow: auto;
  max-height: calc(100vh - 172px);
  padding: 12px 16px 16px;
}

.test-render-runtime-view__context-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.test-render-runtime-view__context-meta-label {
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.test-render-runtime-view__context-meta-value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--el-text-color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
}

@media (max-width: 960px) {
  .test-render-runtime-view__metadata-toolbar {
    flex-direction: column;
  }

  .test-render-runtime-view__context-panel {
    top: 64px;
    right: 12px;
    max-height: calc(100vh - 76px);
    width: calc(100vw - 24px);
  }

  .test-render-runtime-view__context-body {
    max-height: calc(100vh - 132px);
  }
}
</style>
