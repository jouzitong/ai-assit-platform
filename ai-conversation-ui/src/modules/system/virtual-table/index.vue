<script setup lang="ts">
import { Grid, Share } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { searchDbDataSources, type DbDataSourceItem, type DbTableMetaItem } from '../api/dataSources'
import {
  createVirtualBinding,
  createVirtualField,
  createVirtualRelation,
  deleteFieldTransformPort,
  deleteFieldTransformRule,
  deleteVirtualBinding,
  deleteVirtualField,
  deleteVirtualRelation,
  listFieldTransformers,
  publishVirtualCatalog,
  updateVirtualBinding,
  updateVirtualEntity,
  updateVirtualField,
  updateVirtualRelation,
  validateFieldTransformRule,
  validateVirtualCatalog,
  type FieldTransformPortItem,
  type FieldTransformPortPayload,
  type FieldTransformRulePayload,
  type TransformerDescriptor,
  type VirtualBindingPayload,
  type VirtualDataId,
  type VirtualEntityItem,
  type VirtualEntityPayload,
  type VirtualFieldPayload,
  type VirtualRelationPayload,
} from '../api/virtualData'
import type { VirtualEntitySummary } from './data/types'
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

const emptyOverview = (): VirtualCatalogOverview => ({ entities: [], fields: [], bindings: [], relations: [] })
const emptyWorkspace = (): VirtualTableWorkspace => ({ fields: [], bindings: [], rules: [], ports: [], physicalFields: [] })

const activeView = ref<'catalog' | 'relations'>('catalog')
const overview = ref<VirtualCatalogOverview>(emptyOverview())
const dataSources = ref<DbDataSourceItem[]>([])
const physicalTables = ref<DbTableMetaItem[]>([])
const transformers = ref<TransformerDescriptor[]>([])
const selectedEntity = ref<VirtualEntityItem | null>(null)
const workspace = ref<VirtualTableWorkspace>(emptyWorkspace())
const loading = ref(false)
const workspaceLoading = ref(false)
const initializeVisible = ref(false)
const initializeLoading = ref(false)
const physicalTableLoading = ref(false)
const physicalSyncing = ref(false)
const modelDrawerVisible = ref(false)

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

async function loadPage() {
  loading.value = true
  try {
    const [catalog, sourceResult, transformerResult] = await Promise.all([
      loadVirtualCatalogOverview(),
      searchDbDataSources({ page: 1, size: 1000, enabled: true }),
      listFieldTransformers(),
    ])
    overview.value = catalog
    dataSources.value = sourceResult?.list || []
    transformers.value = transformerResult || []
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
  return runMutation(() => publishVirtualCatalog(id), '虚拟表已发布', false)
}

function saveRelation(id: VirtualDataId | null, payload: VirtualRelationPayload) {
  return runMutation(
    () => id === null ? createVirtualRelation(payload) : updateVirtualRelation(id, payload),
    id === null ? '字段关联已创建' : '字段关联已保存',
    false,
  )
}

function removeRelation(id: VirtualDataId) {
  return runMutation(() => deleteVirtualRelation(id), '字段关联已删除', false)
}

onMounted(loadPage)
</script>

<template>
  <section class="virtual-table-page">
    <header class="virtual-table-page__header">
      <div class="virtual-table-page__title">
        <span>DATA VIRTUALIZATION</span>
        <h1>虚拟表管理</h1>
        <p>在物理数据库之上维护统一实体、字段语义、跨源映射与动态关联。</p>
      </div>
      <nav class="virtual-table-page__switch" aria-label="虚拟表工作区">
        <button :class="{ 'is-active': activeView === 'catalog' }" type="button" @click="activeView = 'catalog'">
          <el-icon><Grid /></el-icon><span>虚拟表目录</span>
        </button>
        <button :class="{ 'is-active': activeView === 'relations' }" type="button" @click="activeView = 'relations'">
          <el-icon><Share /></el-icon><span>关系画布</span>
        </button>
      </nav>
    </header>

    <main class="virtual-table-page__content">
      <VirtualTableCatalog
        v-if="activeView === 'catalog'"
        :rows="summaries"
        :loading="loading"
        @initialize="initializeVisible = true"
        @refresh="refreshOverview"
        @open-entity="openEntity"
        @validate-entity="validateEntity"
        @publish-entity="publishEntity"
        @open-canvas="activeView = 'relations'"
      />
      <VirtualRelationCanvas
        v-else
        :entities="overview.entities"
        :fields="overview.fields"
        :bindings="overview.bindings"
        :relations="overview.relations"
        :loading="loading"
        @refresh="refreshOverview"
        @save-relation="saveRelation"
        @delete-relation="removeRelation"
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
      @refresh="refreshWorkspace"
      @save-entity="saveEntity"
      @save-field="saveField"
      @delete-field="removeField"
      @save-binding="saveBinding"
      @delete-binding="removeBinding"
      @save-rule="saveRule"
      @delete-rule="removeRule"
      @validate-rule="validateRule"
    />
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
  display: flex;
  gap: var(--app-space-5);
  align-items: center;
  justify-content: space-between;
  padding: var(--app-space-4) var(--app-space-5);
  border-bottom: 1px solid var(--app-border);
  background: var(--app-surface-gradient);
}

.virtual-table-page__title span {
  color: var(--app-accent);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

.virtual-table-page__title h1,
.virtual-table-page__title p {
  margin: 0;
}

.virtual-table-page__title h1 {
  margin-top: 2px;
  color: var(--app-title);
  font-size: 21px;
}

.virtual-table-page__title p {
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
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
  gap: 7px;
  align-items: center;
  min-height: 34px;
  padding: 0 var(--app-space-4);
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

@media (max-width: 960px) {
  .virtual-table-page {
    min-height: 100vh;
    overflow: visible;
  }

  .virtual-table-page__header {
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
