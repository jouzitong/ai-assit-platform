<script setup lang="ts">
import { Aim, Delete, Filter, Link, MagicStick, RefreshLeft, RefreshRight, Search } from '@element-plus/icons-vue'
import { ConnectionLineType, MarkerType } from '@vue-flow/core'
import type { Connection, Edge, EdgeMouseEvent, Node } from '@vue-flow/core'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, markRaw, nextTick, onMounted, reactive, ref, watch } from 'vue'
import AppFlowCanvas from '../../../../components/canvas/AppFlowCanvas/index.vue'
import type {
  VirtualBindingItem,
  VirtualDataId,
  VirtualEntityItem,
  VirtualFieldItem,
  VirtualRelationItem,
  VirtualRelationPayload,
} from '../../api/virtualData'
import {
  calculateRelationLayout,
  calculateResponsiveGridLayout,
  type RelationLayoutMode,
} from '../service/relationLayout'
import { relationLineStyleOptions } from '../data/options'
import type { RelationLineStyle, VirtualTableNodeData } from '../data/types'
import VirtualTableNode from './VirtualTableNode.vue'

const props = defineProps<{
  entities: VirtualEntityItem[]
  fields: VirtualFieldItem[]
  bindings: VirtualBindingItem[]
  relations: VirtualRelationItem[]
  loading?: boolean
}>()

const emit = defineEmits<{
  refresh: []
  saveRelation: [id: VirtualDataId | null, payload: VirtualRelationPayload]
  deleteRelation: [id: VirtualDataId]
}>()

const keyword = defineModel<string>('keyword', { default: '' })
const selectedSources = defineModel<string[]>('selectedSources', { default: () => [] })
const selectedEntityIds = defineModel<string[]>('selectedEntityIds', { default: () => [] })
const layoutMode = defineModel<RelationLayoutMode>('layoutMode', { default: 'manual' })
const lineStyle = defineModel<RelationLineStyle>('lineStyle', { default: 'curve' })
const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])
const flowCanvas = ref<{
  getNodeDimensions: () => Map<string, { width: number; height: number }>
  getCanvasSize: () => { width: number; height: number }
} | null>(null)
const fitViewTrigger = ref(0)
const layoutLoading = ref(false)
const canUndoLayout = ref(false)
const relationDialogVisible = ref(false)
const editingRelationId = ref<VirtualDataId | null>(null)
const nodeTypes = { virtualTable: markRaw(VirtualTableNode) }
let undoLayoutPositions: Map<string, { x: number, y: number }> | null = null
let layoutRunId = 0

const edgeMarker = {
  type: MarkerType.ArrowClosed,
  color: 'var(--app-accent)',
  width: 18,
  height: 18,
}

const connectionLineType = computed(() => {
  if (lineStyle.value === 'straight') return ConnectionLineType.Straight
  if (lineStyle.value === 'polyline') return ConnectionLineType.SmoothStep
  return ConnectionLineType.Bezier
})

const connectionLineOptions = computed(() => ({
  markerEnd: edgeMarker,
  style: { stroke: 'var(--app-accent)', strokeWidth: 2 },
}))

const relationForm = reactive<VirtualRelationPayload>({
  relationCode: '',
  relationName: '',
  sourceEntityId: '',
  sourceFieldId: '',
  targetEntityId: '',
  targetFieldId: '',
  enabled: true,
  remark: '',
})

const sourceOptions = computed(() => Array.from(new Set(props.bindings
  .map(binding => binding.sourceKey?.trim() || '')
  .filter(Boolean))).sort())

const bindingByEntity = computed(() => {
  const index = new Map<string, VirtualBindingItem[]>()
  props.bindings.forEach((binding) => {
    const key = String(binding.entityId)
    index.set(key, [...(index.get(key) || []), binding])
  })
  return index
})

const fieldsByEntity = computed(() => {
  const index = new Map<string, VirtualFieldItem[]>()
  props.fields
    .filter(field => field.enabled !== false)
    .sort((left, right) => Number(left.ordinalPosition || 0) - Number(right.ordinalPosition || 0))
    .forEach((field) => {
      const key = String(field.entityId)
      index.set(key, [...(index.get(key) || []), field])
    })
  return index
})

const entityOptions = computed(() => props.entities.map(entity => ({
  id: String(entity.id),
  label: keyNameLabel(entity.entityCode, entity.entityName, entity.id),
})))

const filteredEntities = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()
  return props.entities.filter((entity) => {
    const entityId = String(entity.id)
    const bindings = bindingByEntity.value.get(entityId) || []
    const sourceMatched = !selectedSources.value.length
      || bindings.some(binding => selectedSources.value.includes(binding.sourceKey || ''))
    const entityMatched = !selectedEntityIds.value.length
      || selectedEntityIds.value.some(id => String(id) === entityId)
    const keywordMatched = !normalizedKeyword
      || `${entity.entityCode || ''} ${entity.entityName || ''} ${bindings.map(item => item.physicalTableName || '').join(' ')}`
        .toLowerCase()
        .includes(normalizedKeyword)
    return sourceMatched && entityMatched && keywordMatched
  }).slice(0, 30)
})

const visibleEntityIds = computed(() => new Set(filteredEntities.value.map(entity => String(entity.id))))
const hiddenCount = computed(() => Math.max(0, props.entities.length - filteredEntities.value.length))

function entityById(id: VirtualDataId) {
  return props.entities.find(entity => String(entity.id) === String(id))
}

function fieldById(id: VirtualDataId) {
  return props.fields.find(field => String(field.id) === String(id))
}

function fieldsForEntity(entityId: VirtualDataId) {
  return fieldsByEntity.value.get(String(entityId)) || []
}

function keyNameLabel(key: string | undefined, name: string | undefined, fallback: VirtualDataId) {
  const normalizedKey = key?.trim() || ''
  const normalizedName = name?.trim() || ''
  if (normalizedKey && normalizedName && normalizedKey !== normalizedName) {
    return `${normalizedKey} · ${normalizedName}`
  }
  return normalizedKey || normalizedName || String(fallback)
}

function fieldOptionLabel(field: VirtualFieldItem) {
  return keyNameLabel(field.fieldCode, field.fieldName, field.id)
}

function normalizeRelationCode(value: string) {
  let code = value.replace(/[^A-Za-z0-9_]/g, '_').replace(/_+/g, '_')
  if (!code || !/^[A-Za-z]/.test(code)) code = `r_${code || 'relation'}`
  return code.slice(0, 64).replace(/_+$/g, '')
}

function defaultRelationCode(sourceEntityId: VirtualDataId, sourceFieldId: VirtualDataId, targetEntityId: VirtualDataId, targetFieldId: VirtualDataId) {
  const sourceEntity = entityById(sourceEntityId)?.entityCode || String(sourceEntityId)
  const sourceField = fieldById(sourceFieldId)?.fieldCode || String(sourceFieldId)
  const targetEntity = entityById(targetEntityId)?.entityCode || String(targetEntityId)
  const targetField = fieldById(targetFieldId)?.fieldCode || String(targetFieldId)
  return normalizeRelationCode(`${sourceEntity}_${sourceField}_to_${targetEntity}_${targetField}`)
}

function estimatedNodeHeight(node: Node) {
  const data = node.data as VirtualTableNodeData | undefined
  const fieldHeight = data?.fields.length ? data.fields.length * 42 : 52
  const sourceCount = data?.sourceLabels.length || 0
  const sourceHeight = sourceCount
    ? 17 + sourceCount * 21 + Math.max(0, sourceCount - 1) * 6
    : 17
  return 57 + sourceHeight + fieldHeight + 2
}

function canvasSize() {
  return flowCanvas.value?.getCanvasSize() || { width: 0, height: 0 }
}

function layoutNodes(measured = new Map<string, { width: number; height: number }>()) {
  return nodes.value.map((node) => {
    const dimensions = measured.get(String(node.id))
    return {
      id: String(node.id),
      width: dimensions?.width || 292,
      height: dimensions?.height || estimatedNodeHeight(node),
    }
  })
}

function nextAnimationFrame() {
  return new Promise<void>(resolve => window.requestAnimationFrame(() => resolve()))
}

async function measuredNodeDimensions() {
  await nextTick()
  let dimensions = new Map<string, { width: number; height: number }>()
  for (let attempt = 0; attempt < 3; attempt += 1) {
    dimensions = flowCanvas.value?.getNodeDimensions() || dimensions
    const complete = nodes.value.every((node) => {
      const size = dimensions.get(String(node.id))
      return Boolean(size?.width && size?.height)
    })
    if (complete) break
    await nextAnimationFrame()
  }
  return dimensions
}

function applyResponsiveGridLayout(measured = new Map<string, { width: number; height: number }>()) {
  const positions = calculateResponsiveGridLayout(layoutNodes(measured), canvasSize())
  nodes.value = nodes.value.map(node => ({
    ...node,
    position: positions.get(String(node.id)) || node.position,
  }))
}

function snapshotNodePositions() {
  return new Map(nodes.value.map(node => [String(node.id), { ...node.position }]))
}

async function applyRelationLayout() {
  if (!nodes.value.length) {
    layoutLoading.value = false
    return
  }
  const currentRunId = ++layoutRunId
  layoutLoading.value = true
  try {
    const measured = await measuredNodeDimensions()
    if (currentRunId !== layoutRunId) return
    const positions = await calculateRelationLayout(
      layoutNodes(measured),
      edges.value.map(edge => ({
        id: String(edge.id),
        source: String(edge.source),
        target: String(edge.target),
      })),
      canvasSize(),
    )
    if (currentRunId !== layoutRunId) return
    nodes.value = nodes.value.map((node) => ({
      ...node,
      position: positions.get(String(node.id)) || node.position,
    }))
    fitViewTrigger.value += 1
  }
  catch (error) {
    if (currentRunId !== layoutRunId) return
    layoutMode.value = 'manual'
    ElMessage.error(error instanceof Error ? error.message : '关系布局计算失败')
  }
  finally {
    if (currentRunId === layoutRunId) layoutLoading.value = false
  }
}

function beautifyCanvas() {
  if (!nodes.value.length || layoutLoading.value) return
  undoLayoutPositions = snapshotNodePositions()
  canUndoLayout.value = true
  if (layoutMode.value === 'relation') {
    void applyRelationLayout()
    return
  }
  layoutMode.value = 'relation'
}

function undoCanvasLayout() {
  if (!undoLayoutPositions) return
  layoutRunId += 1
  nodes.value = nodes.value.map(node => ({
    ...node,
    position: undoLayoutPositions?.get(String(node.id)) || node.position,
  }))
  undoLayoutPositions = null
  canUndoLayout.value = false
  layoutLoading.value = false
  layoutMode.value = 'manual'
  fitViewTrigger.value += 1
}

function rebuildCanvas() {
  layoutRunId += 1
  layoutLoading.value = false
  undoLayoutPositions = null
  canUndoLayout.value = false
  const previousPositions = new Map(nodes.value.map(node => [String(node.id), node.position]))
  const nextNodes = filteredEntities.value.map((entity) => {
    const entityId = String(entity.id)
    return {
      id: entityId,
      type: 'virtualTable',
      position: previousPositions.get(entityId) || { x: 0, y: 0 },
      data: {
        entity,
        fields: fieldsByEntity.value.get(entityId) || [],
        bindings: bindingByEntity.value.get(entityId) || [],
        sourceLabels: Array.from(new Set((bindingByEntity.value.get(entityId) || [])
          .map(binding => binding.sourceKey || '')
          .filter(Boolean))),
      },
    }
  })
  nodes.value = nextNodes
  const initialPositions = calculateResponsiveGridLayout(layoutNodes(), canvasSize())
  nodes.value = nextNodes.map(node => ({
    ...node,
    position: previousPositions.get(String(node.id)) || initialPositions.get(String(node.id)) || node.position,
  }))
  edges.value = props.relations
    .filter(relation => relation.enabled !== false)
    .filter(relation => visibleEntityIds.value.has(String(relation.sourceEntityId)) && visibleEntityIds.value.has(String(relation.targetEntityId)))
    .map(relation => ({
      id: String(relation.id),
      source: String(relation.sourceEntityId),
      target: String(relation.targetEntityId),
      sourceHandle: `out:${relation.sourceFieldId}`,
      targetHandle: `in:${relation.targetFieldId}`,
      label: relation.relationName || relation.relationCode,
      ...relationLinePresentation(lineStyle.value),
      markerEnd: edgeMarker,
      animated: false,
      selectable: true,
      focusable: true,
      style: { stroke: 'var(--app-accent)', strokeWidth: 2 },
      labelStyle: { fill: 'var(--app-text)', fontSize: 11, fontWeight: 500 },
      labelShowBg: true,
      labelBgStyle: {
        fill: 'var(--app-surface-solid)',
        stroke: 'var(--app-accent-border)',
        strokeWidth: 1,
      },
      labelBgPadding: [8, 5] as [number, number],
      labelBgBorderRadius: 6,
      ariaLabel: `${relation.relationName || relation.relationCode || '字段关联'}，从来源字段指向目标字段`,
      data: { relation },
    }))
  if (layoutMode.value === 'relation') {
    void applyRelationLayout()
  }
  else {
    fitViewTrigger.value += 1
  }
}

function relationLinePresentation(style: RelationLineStyle) {
  if (style === 'straight') {
    return { type: 'straight' as const }
  }
  if (style === 'polyline') {
    return { type: 'smoothstep' as const, pathOptions: { borderRadius: 0, offset: 28 } }
  }
  return { type: 'default' as const, pathOptions: { curvature: 0.25 } }
}

function updateRelationLineStyle() {
  edges.value = edges.value.map(edge => ({
    ...edge,
    ...relationLinePresentation(lineStyle.value),
  }))
}

function parseHandle(handle: string | null | undefined) {
  const [, id] = String(handle || '').split(':')
  return id || ''
}

function openCreateRelation(connection: Connection) {
  const sourceFieldId = parseHandle(connection.sourceHandle)
  const targetFieldId = parseHandle(connection.targetHandle)
  if (!connection.source || !connection.target || !sourceFieldId || !targetFieldId) {
    ElMessage.warning('请从一个字段的右侧连接点拖到另一个字段的左侧连接点')
    return
  }
  if (connection.source === connection.target && sourceFieldId === targetFieldId) {
    ElMessage.warning('不能将字段关联到自身')
    return
  }
  const sourceField = fieldById(sourceFieldId)
  const targetField = fieldById(targetFieldId)
  if (sourceField?.logicalType !== targetField?.logicalType) {
    ElMessage.warning('只有逻辑类型一致的字段才能建立关联')
    return
  }
  const duplicated = props.relations.some(relation => String(relation.sourceFieldId) === sourceFieldId && String(relation.targetFieldId) === targetFieldId)
  if (duplicated) {
    ElMessage.info('这两个字段之间已经存在关联')
    return
  }
  editingRelationId.value = null
  Object.assign(relationForm, {
    relationCode: defaultRelationCode(connection.source, sourceFieldId, connection.target, targetFieldId),
    relationName: `${fieldById(sourceFieldId)?.fieldName || sourceFieldId} → ${fieldById(targetFieldId)?.fieldName || targetFieldId}`,
    sourceEntityId: connection.source,
    sourceFieldId,
    targetEntityId: connection.target,
    targetFieldId,
    enabled: true,
    remark: '',
  })
  relationDialogVisible.value = true
}

function openEditRelation(event: EdgeMouseEvent) {
  const relation = (event.edge.data as { relation?: VirtualRelationItem } | undefined)?.relation
    || props.relations.find(item => String(item.id) === String(event.edge.id))
  if (!relation?.id || relation.sourceEntityId === undefined || relation.sourceFieldId === undefined
    || relation.targetEntityId === undefined || relation.targetFieldId === undefined) {
    return
  }
  editingRelationId.value = relation.id
  Object.assign(relationForm, {
    relationCode: relation.relationCode || '',
    relationName: relation.relationName || '',
    sourceEntityId: String(relation.sourceEntityId),
    sourceFieldId: String(relation.sourceFieldId),
    targetEntityId: String(relation.targetEntityId),
    targetFieldId: String(relation.targetFieldId),
    enabled: relation.enabled !== false,
    remark: relation.remark || '',
  })
  relationDialogVisible.value = true
}

function submitRelation() {
  relationForm.relationCode = normalizeRelationCode(relationForm.relationCode)
  if (!relationForm.relationCode || !relationForm.relationName.trim() || !relationForm.sourceFieldId || !relationForm.targetFieldId) {
    ElMessage.warning('请填写关联编码、名称并选择完整的来源和目标字段')
    return
  }
  if (fieldById(relationForm.sourceFieldId)?.logicalType !== fieldById(relationForm.targetFieldId)?.logicalType) {
    ElMessage.warning('来源字段与目标字段的逻辑类型必须一致')
    return
  }
  const duplicated = props.relations.some(relation => String(relation.id) !== String(editingRelationId.value)
    && String(relation.sourceFieldId) === String(relationForm.sourceFieldId)
    && String(relation.targetFieldId) === String(relationForm.targetFieldId))
  if (duplicated) {
    ElMessage.warning('这两个字段之间已经存在关联')
    return
  }
  emit('saveRelation', editingRelationId.value, { ...relationForm })
  relationDialogVisible.value = false
}

async function removeCurrentRelation() {
  if (editingRelationId.value === null) return
  try {
    await ElMessageBox.confirm('删除后执行计划将不再使用这条关联，确认继续吗？', '删除关联', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    emit('deleteRelation', editingRelationId.value)
    relationDialogVisible.value = false
  }
  catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除关联失败')
    }
  }
}

function resetFilters() {
  keyword.value = ''
  selectedSources.value = []
  selectedEntityIds.value = []
}

watch(
  () => [props.entities, props.fields, props.bindings, props.relations, keyword.value, selectedSources.value, selectedEntityIds.value],
  rebuildCanvas,
  { deep: true, immediate: true },
)

watch(layoutMode, (mode) => {
  if (mode === 'relation') void applyRelationLayout()
})

watch(lineStyle, updateRelationLineStyle)

onMounted(async () => {
  if (layoutMode.value !== 'manual' || !nodes.value.length) return
  const measured = await measuredNodeDimensions()
  applyResponsiveGridLayout(measured)
  fitViewTrigger.value += 1
})
</script>

<template>
  <section class="relation-canvas-panel">
    <header class="relation-canvas-panel__toolbar">
      <div class="relation-canvas-panel__filters">
        <el-input v-model="keyword" clearable placeholder="筛选虚拟表或物理表" aria-label="筛选虚拟表或物理表">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="selectedSources" multiple collapse-tags clearable placeholder="显示数据源" aria-label="显示数据源">
          <el-option v-for="source in sourceOptions" :key="source" :label="source" :value="source" />
        </el-select>
        <el-select v-model="selectedEntityIds" multiple collapse-tags clearable filterable placeholder="显示虚拟表" aria-label="显示虚拟表">
          <el-option v-for="entity in entityOptions" :key="entity.id" :label="entity.label" :value="entity.id" />
        </el-select>
        <el-button :icon="Filter" @click="resetFilters">重置</el-button>
      </div>
      <div class="relation-canvas-panel__actions">
        <span>显示 {{ filteredEntities.length }} 张表<span v-if="hiddenCount">，隐藏 {{ hiddenCount }} 张</span></span>
        <label class="relation-canvas-panel__line-style">
          <span>连线</span>
          <el-select v-model="lineStyle" aria-label="连线样式">
            <el-option v-for="option in relationLineStyleOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </label>
        <el-button type="primary" plain :icon="MagicStick" :loading="layoutLoading" @click="beautifyCanvas">智能美化</el-button>
        <el-button :icon="RefreshLeft" :disabled="!canUndoLayout || layoutLoading" @click="undoCanvasLayout">撤销布局</el-button>
        <el-button :icon="Aim" @click="fitViewTrigger += 1">适应画布</el-button>
        <el-button :icon="RefreshRight" :loading="loading" @click="emit('refresh')">刷新</el-button>
      </div>
    </header>

    <div class="relation-canvas-panel__hint">
      <el-icon><Link /></el-icon>
      从来源字段右侧蓝色圆点拖到目标字段左侧蓝色圆点即可建立关联；点击已有连线可以编辑或删除。
    </div>

    <div v-loading="loading" class="relation-canvas-panel__canvas">
      <AppFlowCanvas
        ref="flowCanvas"
        v-model:nodes="nodes"
        v-model:edges="edges"
        :node-types="nodeTypes"
        :nodes-connectable="true"
        :show-controls="true"
        :zoom-on-scroll="true"
        :zoom-on-pinch="true"
        :connection-line-type="connectionLineType"
        :connection-line-options="connectionLineOptions"
        :fit-view-trigger="fitViewTrigger"
        :fit-view-padding="0.06"
        :canvas-extent="[[-2000, -1200], [16000, 16000]]"
        :node-extent="[[-1600, -1000], [15000, 15000]]"
        @connect="openCreateRelation"
        @edge-click="openEditRelation"
      />
      <div v-if="!loading && !filteredEntities.length" class="relation-canvas-panel__empty">
        当前筛选条件下没有可显示的虚拟表
      </div>
    </div>

    <el-dialog v-model="relationDialogVisible" width="620px" :title="editingRelationId === null ? '建立字段关联' : '编辑字段关联'" append-to-body destroy-on-close>
      <el-form label-position="top" class="relation-editor">
        <div class="relation-editor__endpoint">
          <div>
            <span>来源</span>
            <el-select v-model="relationForm.sourceEntityId" filterable aria-label="来源虚拟表" @change="relationForm.sourceFieldId = ''">
              <el-option v-for="entity in entityOptions" :key="entity.id" :label="entity.label" :value="entity.id" />
            </el-select>
            <el-select v-model="relationForm.sourceFieldId" filterable aria-label="来源字段">
              <el-option v-for="field in fieldsForEntity(relationForm.sourceEntityId)" :key="field.id" :label="fieldOptionLabel(field)" :value="String(field.id)" />
            </el-select>
          </div>
          <el-icon><Link /></el-icon>
          <div>
            <span>目标</span>
            <el-select v-model="relationForm.targetEntityId" filterable aria-label="目标虚拟表" @change="relationForm.targetFieldId = ''">
              <el-option v-for="entity in entityOptions" :key="entity.id" :label="entity.label" :value="entity.id" />
            </el-select>
            <el-select v-model="relationForm.targetFieldId" filterable aria-label="目标字段">
              <el-option v-for="field in fieldsForEntity(relationForm.targetEntityId)" :key="field.id" :label="fieldOptionLabel(field)" :value="String(field.id)" />
            </el-select>
          </div>
        </div>
        <div class="relation-editor__grid">
          <el-form-item label="关联编码">
            <el-input v-model="relationForm.relationCode" maxlength="64" />
          </el-form-item>
          <el-form-item label="关联名称">
            <el-input v-model="relationForm.relationName" maxlength="128" />
          </el-form-item>
        </div>
        <el-form-item label="说明">
          <el-input v-model="relationForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="relationForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="relation-editor__footer">
          <el-button v-if="editingRelationId !== null" type="danger" plain :icon="Delete" @click="removeCurrentRelation">删除关联</el-button>
          <span />
          <el-button @click="relationDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitRelation">保存关联</el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.relation-canvas-panel {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  min-height: 0;
  height: 100%;
}

.relation-canvas-panel__toolbar {
  display: flex;
  gap: var(--app-space-4);
  align-items: center;
  justify-content: space-between;
  padding: var(--app-space-3) var(--app-space-4);
  border-bottom: 1px solid var(--app-border);
  background: var(--app-surface-solid);
}

.relation-canvas-panel__filters,
.relation-canvas-panel__actions {
  display: flex;
  gap: var(--app-space-2);
  align-items: center;
}

.relation-canvas-panel__actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.relation-canvas-panel__filters :deep(.el-input) {
  width: 220px;
}

.relation-canvas-panel__filters :deep(.el-select) {
  width: 190px;
}

.relation-canvas-panel__actions span {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.relation-canvas-panel__line-style {
  display: flex;
  gap: var(--app-space-2);
  align-items: center;
  white-space: nowrap;
}

.relation-canvas-panel__line-style :deep(.el-select) {
  width: 92px;
}

.relation-canvas-panel__hint {
  display: flex;
  gap: var(--app-space-2);
  align-items: center;
  padding: 8px var(--app-space-4);
  border-bottom: 1px solid var(--app-accent-border);
  background: var(--app-accent-bg);
  color: var(--app-accent);
  font-size: var(--app-font-size-caption);
}

.relation-canvas-panel__canvas {
  position: relative;
  min-height: 0;
  overflow: hidden;
}

.relation-canvas-panel__empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--app-text-muted);
  pointer-events: none;
}

.relation-editor__endpoint {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: var(--app-space-3);
  align-items: center;
  margin-bottom: var(--app-space-4);
  padding: var(--app-space-4);
  border: 1px solid var(--app-accent-border);
  border-radius: 10px;
  background: var(--app-accent-bg);
}

.relation-editor__endpoint > div {
  min-width: 0;
}

.relation-editor__endpoint span,
.relation-editor__endpoint strong,
.relation-editor__endpoint code {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.relation-editor__endpoint span,
.relation-editor__endpoint code {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.relation-editor__endpoint strong {
  margin: 4px 0;
  color: var(--app-title);
}

.relation-editor__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--app-space-4);
}

.relation-editor__footer {
  display: flex;
  gap: var(--app-space-2);
  align-items: center;
  justify-content: flex-end;
}

.relation-editor__footer > span {
  flex: 1;
}

@media (max-width: 1100px) {
  .relation-canvas-panel__toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .relation-canvas-panel__filters {
    flex-wrap: wrap;
  }
}

@media (max-width: 720px) {
  .relation-canvas-panel__filters > :deep(*) {
    width: 100% !important;
  }

  .relation-editor__grid,
  .relation-editor__endpoint {
    grid-template-columns: 1fr;
  }
}
</style>
