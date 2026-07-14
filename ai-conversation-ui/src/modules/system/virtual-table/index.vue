<script setup lang="ts">
import { Grid, Share } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AppCodeEditor } from '../../../components'
import { searchAiKbStores, type AiKbStoreItem } from '../api/aiPlatform'
import { searchDbDataSources, type DbDataSourceItem, type DbTableMetaItem } from '../api/dataSources'
import {
  createVirtualBinding,
  createVirtualField,
  deleteFieldTransformPort,
  deleteFieldTransformRule,
  deleteVirtualBinding,
  deleteVirtualField,
  listFieldTransformers,
  checkVirtualUnpublish,
  getVirtualKnowledgeStatus,
  generateVirtualDescription,
  previewVirtualKnowledge,
  publishVirtualCatalogBatch,
  syncVirtualKnowledge,
  saveVirtualRelationsBatch,
  suggestVirtualRelations,
  unpublishVirtualCatalog,
  updateVirtualBinding,
  updateVirtualEntity,
  updateVirtualField,
  validateFieldTransformRule,
  validateVirtualCatalog,
  type CatalogStatus,
  type FieldTransformPortItem,
  type FieldTransformPortPayload,
  type FieldTransformRulePayload,
  type TransformerDescriptor,
  type VirtualBindingPayload,
  type VirtualDataId,
  type VirtualEntityItem,
  type VirtualEntityPayload,
  type VirtualFieldPayload,
  type VirtualKnowledgeStatusItem,
  type VirtualRelationBatchSavePayload,
  type VirtualRelationSuggestion,
} from '../api/virtualData'
import type { RelationLineStyle, VirtualEntitySummary } from './data/types'
import type { RelationLayoutMode } from './service/relationLayout'
import {
  initializeVirtualTables,
  loadPhysicalTables,
  loadVirtualCatalogOverview,
  loadVirtualTableWorkspace,
  saveTransformRuleWithPorts,
  syncPhysicalTableMetadata,
  type VirtualCatalogOverview,
  type VirtualTableWorkspace,
} from './service/virtualTable'
import VirtualRelationCanvas from './views/VirtualRelationCanvas.vue'
import VirtualTableCatalog from './views/VirtualTableCatalog.vue'
import VirtualTableInitializeDialog from './views/VirtualTableInitializeDialog.vue'
import VirtualTableModelDrawer from './views/VirtualTableModelDrawer.vue'

type ActiveView = 'catalog' | 'relations'

const emptyOverview = (): VirtualCatalogOverview => ({ entities: [], fields: [], bindings: [], relations: [] })
const emptyWorkspace = (): VirtualTableWorkspace => ({ fields: [], bindings: [], rules: [], ports: [], physicalFields: [] })

const route = useRoute()
const router = useRouter()
const routeQueryKeys = [
  'view',
  'catalogKeyword',
  'catalogSource',
  'catalogStatus',
  'catalogPage',
  'catalogPageSize',
  'catalogKb',
  'relationKeyword',
  'relationSources',
  'relationEntities',
  'layout',
  'lineStyle',
] as const

function queryText(value: unknown) {
  const normalized = Array.isArray(value) ? value[0] : value
  return typeof normalized === 'string' ? normalized : ''
}

function queryList(value: unknown) {
  const values = Array.isArray(value) ? value : [value]
  return values.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
}

function queryView(value: unknown): ActiveView {
  return queryText(value) === 'relations' ? 'relations' : 'catalog'
}

function queryCatalogStatus(value: unknown): CatalogStatus | '' {
  const status = queryText(value)
  return status === '0' || status === '1' || status === '2' ? Number(status) as CatalogStatus : ''
}

function queryPositiveInteger(value: unknown, fallback: number) {
  const parsed = Number(queryText(value))
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

function queryCatalogPageSize(value: unknown) {
  const parsed = queryPositiveInteger(value, 20)
  return [10, 20, 50, 100].includes(parsed) ? parsed : 20
}

function queryRelationLayoutMode(value: unknown): RelationLayoutMode {
  return queryText(value) === 'relation' ? 'relation' : 'manual'
}

function queryRelationLineStyle(value: unknown): RelationLineStyle {
  const style = queryText(value)
  return style === 'polyline' || style === 'straight' ? style : 'curve'
}

const activeView = ref<ActiveView>(queryView(route.query.view))
const catalogKeyword = ref(queryText(route.query.catalogKeyword))
const catalogSource = ref(queryText(route.query.catalogSource))
const catalogStatus = ref<CatalogStatus | ''>(queryCatalogStatus(route.query.catalogStatus))
const catalogPage = ref(queryPositiveInteger(route.query.catalogPage, 1))
const catalogPageSize = ref(queryCatalogPageSize(route.query.catalogPageSize))
const knowledgeBaseCode = ref(queryText(route.query.catalogKb))
const relationKeyword = ref(queryText(route.query.relationKeyword))
const relationSources = ref<string[]>(queryList(route.query.relationSources))
const relationEntities = ref<string[]>(queryList(route.query.relationEntities))
const relationLayoutMode = ref<RelationLayoutMode>(queryRelationLayoutMode(route.query.layout))
const relationLineStyle = ref<RelationLineStyle>(queryRelationLineStyle(route.query.lineStyle))
const overview = ref<VirtualCatalogOverview>(emptyOverview())
const dataSources = ref<DbDataSourceItem[]>([])
const physicalTables = ref<DbTableMetaItem[]>([])
const transformers = ref<TransformerDescriptor[]>([])
const knowledgeBases = ref<AiKbStoreItem[]>([])
const knowledgeStatuses = ref<VirtualKnowledgeStatusItem[]>([])
const selectedEntity = ref<VirtualEntityItem | null>(null)
const workspace = ref<VirtualTableWorkspace>(emptyWorkspace())
const loading = ref(false)
const workspaceLoading = ref(false)
const initializeVisible = ref(false)
const initializeLoading = ref(false)
const physicalTableLoading = ref(false)
const physicalSyncing = ref(false)
const modelDrawerVisible = ref(false)
const operationLoading = ref(false)
const knowledgePreviewVisible = ref(false)
const knowledgePreviewLoading = ref(false)
const knowledgePreviewTitle = ref('知识库预览')
const knowledgePreviewType = ref('markdown')
const knowledgePreviewContent = ref('')
const knowledgeSyncVisible = ref(false)
const knowledgeSyncSubmitting = ref(false)
const knowledgeSyncEntityIds = ref<VirtualDataId[]>([])
const descriptionGenerating = ref(false)
const relationBatchActive = ref(false)
const relationBatchDirty = ref(false)

const knowledgeBaseOptions = computed(() => knowledgeBases.value
  .filter(item => item.enabled !== false && item.kbCode)
  .map(item => ({ code: item.kbCode || '', label: `${item.kbName || item.kbCode} · ${item.kbCode}` })))
const knowledgePreviewFormat = computed(() => knowledgePreviewType.value === 'markdown' ? 'markdown' : 'text')

const summaries = computed<VirtualEntitySummary[]>(() => overview.value.entities.map((entity) => {
  const entityBindings = overview.value.bindings.filter(binding => String(binding.entityId) === String(entity.id))
  const relationCount = overview.value.relations.filter(relation => String(relation.sourceEntityId) === String(entity.id) || String(relation.targetEntityId) === String(entity.id)).length
  return {
    ...entity,
    sources: entityBindings.map(binding => binding.sourceKey || '-'),
    physicalTables: entityBindings.map(binding => binding.physicalTableName || '-'),
    fieldCount: overview.value.fields.filter(field => String(field.entityId) === String(entity.id)).length,
    relationCount,
  }
}))

const existingCodes = computed(() => overview.value.entities.map(entity => entity.entityCode || '').filter(Boolean))

function applyRouteState() {
  activeView.value = queryView(route.query.view)
  catalogKeyword.value = queryText(route.query.catalogKeyword)
  catalogSource.value = queryText(route.query.catalogSource)
  catalogStatus.value = queryCatalogStatus(route.query.catalogStatus)
  catalogPage.value = queryPositiveInteger(route.query.catalogPage, 1)
  catalogPageSize.value = queryCatalogPageSize(route.query.catalogPageSize)
  knowledgeBaseCode.value = queryText(route.query.catalogKb)
  relationKeyword.value = queryText(route.query.relationKeyword)
  relationSources.value = queryList(route.query.relationSources)
  relationEntities.value = queryList(route.query.relationEntities)
  relationLayoutMode.value = queryRelationLayoutMode(route.query.layout)
  relationLineStyle.value = queryRelationLineStyle(route.query.lineStyle)
}

function buildRouteQuery() {
  const query = { ...route.query }
  routeQueryKeys.forEach(key => delete query[key])
  query.view = activeView.value
  if (catalogKeyword.value.trim()) query.catalogKeyword = catalogKeyword.value.trim()
  if (catalogSource.value) query.catalogSource = catalogSource.value
  if (catalogStatus.value !== '') query.catalogStatus = String(catalogStatus.value)
  query.catalogPage = String(catalogPage.value)
  query.catalogPageSize = String(catalogPageSize.value)
  if (knowledgeBaseCode.value) query.catalogKb = knowledgeBaseCode.value
  if (relationKeyword.value.trim()) query.relationKeyword = relationKeyword.value.trim()
  if (relationSources.value.length) query.relationSources = [...relationSources.value]
  if (relationEntities.value.length) query.relationEntities = [...relationEntities.value]
  if (relationLayoutMode.value === 'relation') query.layout = 'relation'
  if (relationLineStyle.value !== 'curve') query.lineStyle = relationLineStyle.value
  return query
}

function syncRouteState(mode: 'push' | 'replace' = 'replace') {
  const target = { path: route.path, query: buildRouteQuery() }
  if (router.resolve(target).fullPath === route.fullPath) return
  void router[mode](target)
}

async function switchView(view: ActiveView) {
  if (activeView.value === view) return
  if (activeView.value === 'relations' && relationBatchActive.value && relationBatchDirty.value) {
    try {
      await ElMessageBox.confirm('关系画布还有未保存的批量变更，切换页面将放弃这些变更。', '离开关系画布', {
        type: 'warning',
        confirmButtonText: '放弃并离开',
        cancelButtonText: '继续编辑',
      })
    }
    catch {
      return
    }
  }
  activeView.value = view
  syncRouteState('push')
}

async function loadPage() {
  loading.value = true
  try {
    const [catalog, sourceResult, transformerResult, knowledgeBaseResult] = await Promise.all([
      loadVirtualCatalogOverview(),
      searchDbDataSources({ page: 1, size: 1000, enabled: true }),
      listFieldTransformers(),
      searchAiKbStores({ page: 1, size: 1000, enabled: true }).catch((error) => {
        ElMessage.warning(error instanceof Error ? error.message : '可用知识库加载失败')
        return { list: [] }
      }),
    ])
    overview.value = catalog
    dataSources.value = sourceResult?.list || []
    transformers.value = transformerResult || []
    knowledgeBases.value = knowledgeBaseResult?.list || []
    await loadKnowledgeStatuses(catalog.entities)
    if (selectedEntity.value) {
      selectedEntity.value = catalog.entities.find(item => String(item.id) === String(selectedEntity.value?.id)) || null
    }
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '虚拟表目录加载失败')
  }
  finally {
    loading.value = false
  }
}

async function refreshOverview() {
  loading.value = true
  try {
    overview.value = await loadVirtualCatalogOverview()
    await loadKnowledgeStatuses(overview.value.entities)
    if (selectedEntity.value) {
      selectedEntity.value = overview.value.entities.find(item => String(item.id) === String(selectedEntity.value?.id)) || null
    }
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '虚拟表目录刷新失败')
  }
  finally {
    loading.value = false
  }
}

async function loadKnowledgeStatuses(entities: VirtualEntityItem[]) {
  if (!entities.length) {
    knowledgeStatuses.value = []
    return
  }
  try {
    knowledgeStatuses.value = await getVirtualKnowledgeStatus(entities.map(entity => entity.id)) || []
  }
  catch (error) {
    knowledgeStatuses.value = []
    ElMessage.warning(error instanceof Error ? error.message : '知识库同步状态加载失败')
  }
}

async function loadWorkspace(entityId: VirtualDataId) {
  workspaceLoading.value = true
  try {
    workspace.value = await loadVirtualTableWorkspace(entityId)
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '虚拟模型加载失败')
  }
  finally {
    workspaceLoading.value = false
  }
}

async function openEntity(entityId: VirtualDataId) {
  selectedEntity.value = overview.value.entities.find(entity => String(entity.id) === String(entityId)) || null
  if (!selectedEntity.value) return
  modelDrawerVisible.value = true
  await loadWorkspace(entityId)
}

async function refreshWorkspace() {
  if (!selectedEntity.value) return
  await Promise.all([loadWorkspace(selectedEntity.value.id), refreshOverview()])
}

async function loadSourceTables(sourceKey: string) {
  if (!sourceKey) {
    physicalTables.value = []
    return
  }
  physicalTableLoading.value = true
  try {
    physicalTables.value = await loadPhysicalTables(sourceKey)
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '物理表加载失败')
  }
  finally {
    physicalTableLoading.value = false
  }
}

async function syncSourceTables(sourceKey: string) {
  physicalSyncing.value = true
  try {
    const result = await syncPhysicalTableMetadata(sourceKey)
    ElMessage.success(`物理元数据同步完成，共发现 ${result.tableCount} 张表`)
    await loadSourceTables(sourceKey)
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '物理元数据同步失败')
  }
  finally {
    physicalSyncing.value = false
  }
}

async function initializeSelectedTables(sourceKey: string, tables: DbTableMetaItem[]) {
  initializeLoading.value = true
  try {
    const result = await initializeVirtualTables(sourceKey, tables)
    if (result.created.length) {
      ElMessage.success(`已初始化 ${result.created.length} 张虚拟表`)
      initializeVisible.value = false
      await refreshOverview()
    }
    if (result.failed.length) {
      const detail = result.failed.slice(0, 3).map(item => `${item.tableName}: ${item.message}`).join('；')
      ElMessage.warning(`${result.failed.length} 张表初始化失败。${detail}`)
    }
  }
  finally {
    initializeLoading.value = false
  }
}

async function runMutation(action: () => Promise<unknown>, successMessage: string, refreshModel = true) {
  try {
    await action()
    ElMessage.success(successMessage)
    if (refreshModel && selectedEntity.value) {
      await refreshWorkspace()
    }
    else {
      await refreshOverview()
    }
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `${successMessage}失败`)
  }
}

function saveEntity(id: VirtualDataId, payload: VirtualEntityPayload) {
  return runMutation(() => updateVirtualEntity(id, payload), '虚拟表信息已保存')
}

async function generateDescription(
  id: VirtualDataId,
  currentDescription: string,
  apply: (description: string) => void,
) {
  descriptionGenerating.value = true
  try {
    const result = await generateVirtualDescription({ entityId: id, currentDescription })
    if (!result.description?.trim()) throw new Error('AI 未返回有效说明')
    apply(result.description.trim())
    ElMessage.success('AI 说明已生成，请确认后保存')
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 说明生成失败')
  }
  finally {
    descriptionGenerating.value = false
  }
}

function saveField(id: VirtualDataId | null, payload: VirtualFieldPayload) {
  return runMutation(
    () => id === null ? createVirtualField(payload) : updateVirtualField(id, payload),
    id === null ? '虚拟字段已创建' : '虚拟字段已保存',
  )
}

function removeField(id: VirtualDataId) {
  const referencedByRule = workspace.value.ports.some(port => String(port.virtualFieldId) === String(id))
  const referencedByRelation = overview.value.relations.some(relation => String(relation.sourceFieldId) === String(id) || String(relation.targetFieldId) === String(id))
  if (referencedByRule || referencedByRelation) {
    ElMessage.warning('该字段仍被转换规则或表关联引用，请先解除引用')
    return Promise.resolve()
  }
  return runMutation(() => deleteVirtualField(id), '虚拟字段已删除')
}

function saveBinding(id: VirtualDataId | null, payload: VirtualBindingPayload) {
  return runMutation(
    () => id === null ? createVirtualBinding(payload) : updateVirtualBinding(id, payload),
    id === null ? '物理绑定已创建' : '物理绑定已保存',
  )
}

function removeBinding(id: VirtualDataId) {
  if (workspace.value.rules.some(rule => String(rule.bindingId) === String(id))) {
    ElMessage.warning('该绑定仍有字段转换规则，请先删除关联规则')
    return Promise.resolve()
  }
  return runMutation(() => deleteVirtualBinding(id), '物理绑定已删除')
}

function saveRule(
  id: VirtualDataId | null,
  payload: FieldTransformRulePayload,
  ports: Array<FieldTransformPortPayload & { id?: VirtualDataId }>,
  existingPorts: FieldTransformPortItem[],
) {
  return runMutation(() => saveTransformRuleWithPorts(id, payload, ports, existingPorts), id === null ? '转换规则已创建' : '转换规则已保存')
}

function removeRule(id: VirtualDataId) {
  return runMutation(async () => {
    const ports = workspace.value.ports.filter(port => String(port.ruleId) === String(id))
    await Promise.all(ports.map(port => deleteFieldTransformPort(port.id)))
    await deleteFieldTransformRule(id)
  }, '转换规则已删除')
}

function validateRule(id: VirtualDataId) {
  return runMutation(() => validateFieldTransformRule(id), '转换规则校验通过', false)
}

function validateEntity(id: VirtualDataId) {
  return runMutation(() => validateVirtualCatalog(id), '虚拟表目录校验通过', false)
}

function publishEntity(id: VirtualDataId) {
  return publishEntities([id])
}

async function publishEntities(ids: VirtualDataId[], skipConfirm = false) {
  const uniqueIds = [...new Set(ids.map(String))]
    .map(id => overview.value.entities.find(entity => String(entity.id) === id)?.id)
    .filter((id): id is VirtualDataId => id !== undefined)
  const unpublishedIds = uniqueIds.filter(id => overview.value.entities.find(entity => String(entity.id) === String(id))?.status !== 1)
  if (!unpublishedIds.length) {
    ElMessage.info('所选虚拟表均已发布')
    return true
  }
  if (!skipConfirm) {
    try {
      await ElMessageBox.confirm(`确认发布所选 ${unpublishedIds.length} 张虚拟表吗？`, '批量发布', {
        type: 'warning',
        confirmButtonText: '确认发布',
        cancelButtonText: '取消',
      })
    }
    catch {
      return false
    }
  }
  operationLoading.value = true
  try {
    await publishVirtualCatalogBatch(unpublishedIds)
    ElMessage.success(`已发布 ${unpublishedIds.length} 张虚拟表`)
    await refreshOverview()
    return true
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '虚拟表发布失败')
    return false
  }
  finally {
    operationLoading.value = false
  }
}

async function unpublishEntities(ids: VirtualDataId[]) {
  const publishedIds = ids.filter(id => overview.value.entities.find(entity => String(entity.id) === String(id))?.status === 1)
  if (!publishedIds.length) {
    ElMessage.info('所选虚拟表均未发布')
    return
  }
  operationLoading.value = true
  try {
    const statuses = await checkVirtualUnpublish(publishedIds)
    const syncedStatuses = (statuses || []).filter(item => item.kbCodes?.length)
    const syncedKbCodes = [...new Set(syncedStatuses.flatMap(item => item.kbCodes || []))]
    const message = syncedStatuses.length
      ? `其中 ${syncedStatuses.length} 张虚拟表已同步到知识库「${syncedKbCodes.join('、')}」。取消发布会同步删除这些知识文档，是否确认？`
      : `确认取消发布所选 ${publishedIds.length} 张虚拟表吗？`
    await ElMessageBox.confirm(message, '取消发布确认', {
      type: 'warning',
      confirmButtonText: syncedStatuses.length ? '取消发布并删除知识文档' : '确认取消发布',
      cancelButtonText: '取消',
    })
    const result = await unpublishVirtualCatalog(publishedIds)
    ElMessage.success(`已取消发布 ${Number(result?.unpublishedCount || 0)} 张虚拟表，删除 ${Number(result?.deletedDocumentCount || 0)} 篇知识文档`)
    await refreshOverview()
  }
  catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '取消发布失败')
    }
  }
  finally {
    operationLoading.value = false
  }
}

async function prepareKnowledgeSync(ids: VirtualDataId[]) {
  const uniqueIds = [...new Set(ids.map(String))]
    .map(id => overview.value.entities.find(entity => String(entity.id) === id)?.id)
    .filter((id): id is VirtualDataId => id !== undefined)
  if (!uniqueIds.length) return
  if (!knowledgeBaseOptions.value.length) {
    ElMessage.warning('暂无可用知识库，请先在知识库管理中创建并启用知识库')
    return
  }
  const unpublishedIds = uniqueIds.filter(id => overview.value.entities.find(entity => String(entity.id) === String(id))?.status !== 1)
  if (unpublishedIds.length) {
    try {
      await ElMessageBox.confirm(
        `所选虚拟表中有 ${unpublishedIds.length} 张尚未发布。知识库同步仅支持已发布虚拟表，是否先发布再继续？`,
        '发布后同步',
        { type: 'warning', confirmButtonText: '确认发布', cancelButtonText: '取消' },
      )
    }
    catch {
      return
    }
    if (!await publishEntities(unpublishedIds, true)) return
  }
  knowledgeSyncEntityIds.value = uniqueIds
  if (!knowledgeBaseOptions.value.some(item => item.code === knowledgeBaseCode.value)) {
    knowledgeBaseCode.value = knowledgeBaseOptions.value[0]?.code || ''
  }
  knowledgeSyncVisible.value = true
}

async function submitKnowledgeSync() {
  if (!knowledgeBaseCode.value) {
    ElMessage.warning('请选择目标知识库')
    return
  }
  knowledgeSyncSubmitting.value = true
  try {
    const result = await syncVirtualKnowledge({
      kbCode: knowledgeBaseCode.value,
      entityIds: knowledgeSyncEntityIds.value,
    })
    ElMessage.success(`同步完成：新增 ${Number(result?.createdCount || 0)}，更新 ${Number(result?.updatedCount || 0)}，未变更 ${Number(result?.unchangedCount || 0)}`)
    knowledgeSyncVisible.value = false
    await loadKnowledgeStatuses(overview.value.entities)
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识库同步失败')
  }
  finally {
    knowledgeSyncSubmitting.value = false
  }
}

async function openKnowledgePreview(id: VirtualDataId) {
  const entity = overview.value.entities.find(item => String(item.id) === String(id))
  knowledgePreviewTitle.value = `知识库预览 · ${entity?.entityName || entity?.entityCode || id}`
  knowledgePreviewVisible.value = true
  knowledgePreviewLoading.value = true
  knowledgePreviewContent.value = ''
  try {
    const result = await previewVirtualKnowledge(id)
    knowledgePreviewType.value = String(result?.type || 'markdown').toLowerCase()
    knowledgePreviewContent.value = result?.content || ''
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识库预览加载失败')
  }
  finally {
    knowledgePreviewLoading.value = false
  }
}

async function saveRelationBatch(payload: VirtualRelationBatchSavePayload, done: (success: boolean) => void) {
  try {
    const result = await saveVirtualRelationsBatch(payload)
    ElMessage.success(`关系变更已保存：新增 ${Number(result?.createdCount || 0)}，更新 ${Number(result?.updatedCount || 0)}，删除 ${Number(result?.deletedCount || 0)}`)
    await refreshOverview()
    done(true)
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '关系批量保存失败')
    done(false)
  }
}

async function generateRelationSuggestions(entityIds: VirtualDataId[], done: (suggestions: VirtualRelationSuggestion[] | null) => void) {
  try {
    done(await suggestVirtualRelations(entityIds))
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 关系分析失败')
    done(null)
  }
}

function updateRelationBatchState(active: boolean, dirty: boolean) {
  relationBatchActive.value = active
  relationBatchDirty.value = dirty
}

watch(() => route.query, applyRouteState, { deep: true })
watch(
  [catalogKeyword, catalogSource, catalogStatus, catalogPage, catalogPageSize, knowledgeBaseCode, relationKeyword, relationSources, relationEntities, relationLayoutMode, relationLineStyle],
  () => syncRouteState(),
  { deep: true },
)

onMounted(() => {
  syncRouteState()
  void loadPage()
})
</script>

<template>
  <section class="virtual-table-page">
    <header class="virtual-table-page__header">
      <div class="virtual-table-page__header-primary">
        <div class="virtual-table-page__title">
          <h1>虚拟表管理</h1>
        </div>
        <div id="virtual-table-header-actions" class="virtual-table-page__header-actions" />
      </div>
      <div class="virtual-table-page__header-secondary">
        <div id="virtual-table-header-filters" class="virtual-table-page__header-filters" />
        <nav class="virtual-table-page__switch" aria-label="虚拟表工作区">
          <button
            :class="{ 'is-active': activeView === 'catalog' }"
            type="button"
            title="虚拟表目录"
            aria-label="虚拟表目录"
            :aria-pressed="activeView === 'catalog'"
            @click="switchView('catalog')"
          >
            <el-icon><Grid /></el-icon>
          </button>
          <button
            :class="{ 'is-active': activeView === 'relations' }"
            type="button"
            title="关系画布"
            aria-label="关系画布"
            :aria-pressed="activeView === 'relations'"
            @click="switchView('relations')"
          >
            <el-icon><Share /></el-icon>
          </button>
        </nav>
      </div>
    </header>

    <main class="virtual-table-page__content">
      <VirtualTableCatalog
        v-if="activeView === 'catalog'"
        v-model:keyword="catalogKeyword"
        v-model:source-key="catalogSource"
        v-model:status="catalogStatus"
        v-model:current-page="catalogPage"
        v-model:page-size="catalogPageSize"
        v-model:knowledge-base-code="knowledgeBaseCode"
        :rows="summaries"
        :knowledge-statuses="knowledgeStatuses"
        :knowledge-bases="knowledgeBaseOptions"
        :loading="loading"
        :operation-loading="operationLoading"
        @initialize="initializeVisible = true"
        @refresh="refreshOverview"
        @open-entity="openEntity"
        @validate-entity="validateEntity"
        @publish-entity="publishEntity"
        @batch-publish="publishEntities"
        @batch-unpublish="unpublishEntities"
        @sync-knowledge="prepareKnowledgeSync"
        @preview-knowledge="openKnowledgePreview"
      />
      <VirtualRelationCanvas
        v-else
        v-model:keyword="relationKeyword"
        v-model:selected-sources="relationSources"
        v-model:selected-entity-ids="relationEntities"
        v-model:layout-mode="relationLayoutMode"
        v-model:line-style="relationLineStyle"
        :entities="overview.entities"
        :fields="overview.fields"
        :bindings="overview.bindings"
        :relations="overview.relations"
        :loading="loading"
        @refresh="refreshOverview"
        @save-batch="saveRelationBatch"
        @suggest-relations="generateRelationSuggestions"
        @batch-state-change="updateRelationBatchState"
      />
    </main>

    <VirtualTableInitializeDialog
      v-model="initializeVisible"
      :data-sources="dataSources"
      :physical-tables="physicalTables"
      :existing-codes="existingCodes"
      :loading="physicalTableLoading"
      :syncing="physicalSyncing"
      :submitting="initializeLoading"
      @source-change="loadSourceTables"
      @sync="syncSourceTables"
      @submit="initializeSelectedTables"
    />

    <VirtualTableModelDrawer
      v-model="modelDrawerVisible"
      :entity="selectedEntity"
      :workspace="workspace"
      :transformers="transformers"
      :loading="workspaceLoading"
      :description-generating="descriptionGenerating"
      @refresh="refreshWorkspace"
      @save-entity="saveEntity"
      @save-field="saveField"
      @delete-field="removeField"
      @save-binding="saveBinding"
      @delete-binding="removeBinding"
      @save-rule="saveRule"
      @delete-rule="removeRule"
      @validate-rule="validateRule"
      @generate-description="generateDescription"
    />

    <el-dialog v-model="knowledgeSyncVisible" title="同步虚拟表知识库" width="560px" append-to-body destroy-on-close>
      <section class="virtual-knowledge-sync-dialog">
        <p>将以 Upsert 方式同步 {{ knowledgeSyncEntityIds.length }} 张已发布虚拟表：文档存在则更新，不存在则新增。</p>
        <el-form label-position="top">
          <el-form-item label="目标知识库" required>
            <el-select v-model="knowledgeBaseCode" filterable placeholder="请选择知识库">
              <el-option v-for="item in knowledgeBaseOptions" :key="item.code" :label="item.label" :value="item.code" />
            </el-select>
          </el-form-item>
        </el-form>
      </section>
      <template #footer>
        <el-button @click="knowledgeSyncVisible = false">取消</el-button>
        <el-button type="primary" :loading="knowledgeSyncSubmitting" @click="submitKnowledgeSync">确认同步</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="knowledgePreviewVisible" :title="knowledgePreviewTitle" width="860px" append-to-body destroy-on-close>
      <div v-loading="knowledgePreviewLoading" class="virtual-knowledge-preview">
        <AppCodeEditor
          v-if="knowledgePreviewContent"
          :model-value="knowledgePreviewContent"
          :format="knowledgePreviewFormat"
          readonly
          :show-format-switcher="false"
          :toolbar-label="knowledgePreviewFormat === 'markdown' ? 'Markdown' : 'Text'"
          min-height="460px"
        />
        <el-empty v-else-if="!knowledgePreviewLoading" description="暂无知识库预览内容" />
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.virtual-table-page {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-width: 0;
  min-height: calc(100vh - 20px);
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: 14px;
  background: var(--app-surface);
  box-shadow: var(--app-shadow-md);
}

.virtual-table-page__header {
  display: grid;
  border-bottom: 1px solid var(--app-border);
  background: var(--app-surface-solid);
}

.virtual-table-page__header-primary,
.virtual-table-page__header-secondary {
  display: flex;
  gap: var(--app-space-4);
  align-items: center;
  justify-content: space-between;
}

.virtual-table-page__header-primary {
  min-height: 58px;
  padding: var(--app-space-3) var(--app-space-4);
}

.virtual-table-page__header-secondary {
  min-height: 52px;
  padding: 9px var(--app-space-4);
  border-top: 1px solid var(--app-border);
}

.virtual-table-page__header-actions {
  min-width: 0;
  margin-left: auto;
}

.virtual-table-page__header-filters {
  flex: 1;
  min-width: 0;
}

.virtual-table-page__title h1 {
  margin: 0;
  color: var(--app-title);
  font-size: 20px;
  line-height: 1.3;
}

.virtual-table-page__switch {
  display: flex;
  padding: 4px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: var(--app-surface-muted);
}

.virtual-table-page__switch button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  min-height: 34px;
  padding: 0;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--app-text-muted);
  cursor: pointer;
  transition: color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.virtual-table-page__switch button:hover,
.virtual-table-page__switch button:focus-visible {
  color: var(--app-accent);
  outline: none;
}

.virtual-table-page__switch button:focus-visible {
  box-shadow: 0 0 0 2px var(--app-accent-border);
}

.virtual-table-page__switch button.is-active {
  background: var(--app-surface-solid);
  color: var(--app-accent);
  box-shadow: var(--app-shadow-sm);
}

.virtual-table-page__content {
  min-height: 0;
  overflow: hidden;
}

.virtual-knowledge-sync-dialog {
  display: grid;
  gap: var(--app-space-3);
}

.virtual-knowledge-sync-dialog p {
  margin: 0;
  color: var(--app-text-muted);
  line-height: 1.7;
}

.virtual-knowledge-sync-dialog :deep(.el-select) {
  width: 100%;
}

.virtual-knowledge-preview {
  min-height: 460px;
}

@media (max-width: 960px) {
  .virtual-table-page {
    min-height: 100vh;
    overflow: visible;
  }

  .virtual-table-page__header {
    overflow: visible;
  }

  .virtual-table-page__header-primary {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .virtual-table-page__header-actions {
    width: 100%;
    margin-left: 0;
  }

  .virtual-table-page__header-secondary {
    align-items: stretch;
    flex-direction: column;
  }

  .virtual-table-page__switch {
    align-self: flex-start;
  }
}

@media (prefers-reduced-motion: reduce) {
  .virtual-table-page__switch button {
    transition: none;
  }
}
</style>
