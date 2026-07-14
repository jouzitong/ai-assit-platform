<script setup lang="ts">
import {
  Aim,
  Check,
  Close,
  Delete,
  EditPen,
  Filter,
  MagicStick,
  Plus,
  RefreshLeft,
  RefreshRight,
  Search,
  Switch as SwitchIcon,
} from '@element-plus/icons-vue'
import { ConnectionLineType, MarkerType } from '@vue-flow/core'
import type { Connection, Edge, EdgeMouseEvent, Node } from '@vue-flow/core'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import AppFlowCanvas from '../../../../components/canvas/AppFlowCanvas/index.vue'
import type {
  RelationResultMode,
  VirtualBindingItem,
  VirtualDataId,
  VirtualEntityItem,
  VirtualFieldItem,
  VirtualRelationBatchSavePayload,
  VirtualRelationItem,
  VirtualRelationPayload,
  VirtualRelationSuggestion,
} from '../../api/virtualData'
import { logicalTypeLabel, relationLineStyleOptions, relationResultModeOptions } from '../data/options'
import type { RelationDraftStatus, RelationLineStyle, VirtualTableNodeData } from '../data/types'
import {
  calculateRelationLayout,
  calculateResponsiveGridLayout,
  type RelationLayoutMode,
} from '../service/relationLayout'
import VirtualTableNode from './VirtualTableNode.vue'

type DraftOrigin = 'database' | 'manual' | 'ai'

interface DraftRelation extends VirtualRelationPayload {
  id: VirtualDataId
  persistedId?: VirtualDataId
  draftStatus: RelationDraftStatus
  draftOrigin: DraftOrigin
  aiReason?: string
  aiConfidence?: number
}

const props = defineProps<{
  entities: VirtualEntityItem[]
  fields: VirtualFieldItem[]
  bindings: VirtualBindingItem[]
  relations: VirtualRelationItem[]
  loading?: boolean
}>()

const emit = defineEmits<{
  refresh: []
  saveBatch: [payload: VirtualRelationBatchSavePayload, done: (success: boolean) => void]
  suggestRelations: [entityIds: VirtualDataId[], done: (suggestions: VirtualRelationSuggestion[] | null) => void]
  batchStateChange: [active: boolean, dirty: boolean]
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
const batchMode = ref(false)
const batchSaving = ref(false)
const aiLoading = ref(false)
const draftRelations = ref<DraftRelation[]>([])
const relationDialogVisible = ref(false)
const editingRelationId = ref<VirtualDataId | null>(null)
const aiSelectionVisible = ref(false)
const aiSelectionKeyword = ref('')
const aiSelectedEntityIds = ref<string[]>([])
const nodeTypes = { virtualTable: markRaw(VirtualTableNode) }
let undoLayoutPositions: Map<string, { x: number, y: number }> | null = null
let layoutRunId = 0
let draftSequence = 0

const relationForm = reactive<VirtualRelationPayload>({
  relationCode: '',
  relationName: '',
  resultMode: 0,
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

const candidateEntities = computed(() => {
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
  })
})

const filteredEntities = computed(() => candidateEntities.value.slice(0, 30))
const visibleEntityIds = computed(() => new Set(filteredEntities.value.map(entity => String(entity.id))))
const hiddenCount = computed(() => Math.max(0, candidateEntities.value.length - filteredEntities.value.length))
const workingRelations = computed<DraftRelation[]>(() => batchMode.value
  ? draftRelations.value
  : props.relations.map(relationToDraft))
const changeSummary = computed(() => ({
  added: draftRelations.value.filter(item => item.draftStatus === 'added').length,
  updated: draftRelations.value.filter(item => item.draftStatus === 'updated').length,
  deleted: draftRelations.value.filter(item => item.draftStatus === 'deleted').length,
}))
const changeCount = computed(() => changeSummary.value.added + changeSummary.value.updated + changeSummary.value.deleted)
const hasChanges = computed(() => changeCount.value > 0)
const currentDraft = computed(() => editingRelationId.value === null
  ? null
  : workingRelations.value.find(item => String(item.id) === String(editingRelationId.value)) || null)
const formReadOnly = computed(() => !batchMode.value || currentDraft.value?.draftStatus === 'deleted')
const targetFieldOptions = computed(() => {
  const sourceType = fieldById(relationForm.sourceFieldId)?.logicalType
  return fieldsForEntity(relationForm.targetEntityId)
    .filter(field => sourceType === undefined || field.logicalType === sourceType)
})
const filteredAiEntities = computed(() => {
  const normalized = aiSelectionKeyword.value.trim().toLowerCase()
  if (!normalized) return props.entities
  return props.entities.filter(entity => `${entity.entityCode || ''} ${entity.entityName || ''}`.toLowerCase().includes(normalized))
})

const connectionLineType = computed(() => {
  if (lineStyle.value === 'straight') return ConnectionLineType.Straight
  if (lineStyle.value === 'polyline') return ConnectionLineType.SmoothStep
  return ConnectionLineType.Bezier
})

const connectionLineOptions = computed(() => ({
  markerEnd: { type: MarkerType.ArrowClosed, color: 'var(--app-accent)', width: 18, height: 18 },
  style: { stroke: 'var(--app-accent)', strokeWidth: 2 },
}))

function relationToPayload(relation: VirtualRelationItem): VirtualRelationPayload {
  return {
    relationCode: relation.relationCode || '',
    relationName: relation.relationName || '',
    resultMode: relation.resultMode === 1 ? 1 : 0,
    sourceEntityId: relation.sourceEntityId === undefined ? '' : String(relation.sourceEntityId),
    sourceFieldId: relation.sourceFieldId === undefined ? '' : String(relation.sourceFieldId),
    targetEntityId: relation.targetEntityId === undefined ? '' : String(relation.targetEntityId),
    targetFieldId: relation.targetFieldId === undefined ? '' : String(relation.targetFieldId),
    enabled: relation.enabled !== false,
    remark: relation.remark || '',
  }
}

function relationToDraft(relation: VirtualRelationItem): DraftRelation {
  return {
    id: relation.id,
    persistedId: relation.id,
    ...relationToPayload(relation),
    draftStatus: 'unchanged',
    draftOrigin: 'database',
  }
}

function newDraftId() {
  draftSequence += 1
  return `draft:${Date.now()}:${draftSequence}`
}

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
  if (normalizedKey && normalizedName && normalizedKey !== normalizedName) return `${normalizedKey} · ${normalizedName}`
  return normalizedKey || normalizedName || String(fallback)
}

function fieldOptionLabel(field: VirtualFieldItem) {
  return `${keyNameLabel(field.fieldCode, field.fieldName, field.id)} · ${logicalTypeLabel(field.logicalType)}`
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

function relationModeLabel(resultMode: RelationResultMode) {
  return resultMode === 1 ? '1:N / N:N' : '1:1 / N:1'
}

function estimatedNodeHeight(node: Node) {
  const data = node.data as VirtualTableNodeData | undefined
  const fieldHeight = data?.fields.length ? data.fields.length * 42 : 52
  const sourceCount = data?.sourceLabels.length || 0
  const sourceHeight = sourceCount ? 17 + sourceCount * 21 + Math.max(0, sourceCount - 1) * 6 : 17
  return 57 + sourceHeight + fieldHeight + 2
}

function canvasSize() {
  return flowCanvas.value?.getCanvasSize() || { width: 0, height: 0 }
}

function layoutNodes(measured = new Map<string, { width: number; height: number }>()) {
  return nodes.value.map((node) => {
    const dimensions = measured.get(String(node.id))
    return { id: String(node.id), width: dimensions?.width || 292, height: dimensions?.height || estimatedNodeHeight(node) }
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
    if (nodes.value.every(node => Boolean(dimensions.get(String(node.id))?.width))) break
    await nextAnimationFrame()
  }
  return dimensions
}

function applyResponsiveGridLayout(measured = new Map<string, { width: number; height: number }>()) {
  const positions = calculateResponsiveGridLayout(layoutNodes(measured), canvasSize())
  nodes.value = nodes.value.map(node => ({ ...node, position: positions.get(String(node.id)) || node.position }))
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
      edges.value.filter(edge => (edge.data as { status?: RelationDraftStatus } | undefined)?.status !== 'deleted')
        .map(edge => ({ id: String(edge.id), source: String(edge.source), target: String(edge.target) })),
      canvasSize(),
    )
    if (currentRunId !== layoutRunId) return
    nodes.value = nodes.value.map(node => ({ ...node, position: positions.get(String(node.id)) || node.position }))
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
  if (layoutMode.value === 'relation') void applyRelationLayout()
  else layoutMode.value = 'relation'
}

function undoCanvasLayout() {
  if (!undoLayoutPositions) return
  layoutRunId += 1
  nodes.value = nodes.value.map(node => ({ ...node, position: undoLayoutPositions?.get(String(node.id)) || node.position }))
  undoLayoutPositions = null
  canUndoLayout.value = false
  layoutLoading.value = false
  layoutMode.value = 'manual'
  fitViewTrigger.value += 1
}

function relationLinePresentation(style: RelationLineStyle) {
  if (style === 'straight') return { type: 'straight' as const }
  if (style === 'polyline') return { type: 'smoothstep' as const, pathOptions: { borderRadius: 0, offset: 28 } }
  return { type: 'default' as const, pathOptions: { curvature: 0.25 } }
}

function edgeAppearance(relation: DraftRelation) {
  if (relation.draftStatus === 'added') return { color: 'var(--app-success)', dash: undefined, prefix: relation.draftOrigin === 'ai' ? 'AI 待新增' : '待新增' }
  if (relation.draftStatus === 'updated') return { color: 'var(--app-warning)', dash: undefined, prefix: '待更新' }
  if (relation.draftStatus === 'deleted') return { color: 'var(--app-text-faint)', dash: '7 6', prefix: '待删除' }
  if (!relation.enabled) return { color: 'var(--app-text-muted)', dash: '4 5', prefix: '已停用' }
  return { color: 'var(--app-accent)', dash: undefined, prefix: '' }
}

function rebuildCanvas() {
  layoutRunId += 1
  layoutLoading.value = false
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
        sourceLabels: Array.from(new Set((bindingByEntity.value.get(entityId) || []).map(binding => binding.sourceKey || '').filter(Boolean))),
        batchMode: batchMode.value,
      },
    }
  })
  nodes.value = nextNodes
  const initialPositions = calculateResponsiveGridLayout(layoutNodes(), canvasSize())
  nodes.value = nextNodes.map(node => ({
    ...node,
    position: previousPositions.get(String(node.id)) || initialPositions.get(String(node.id)) || node.position,
  }))
  edges.value = workingRelations.value
    .filter(relation => batchMode.value || relation.enabled)
    .filter(relation => visibleEntityIds.value.has(String(relation.sourceEntityId)) && visibleEntityIds.value.has(String(relation.targetEntityId)))
    .map((relation) => {
      const appearance = edgeAppearance(relation)
      const relationLabel = relation.relationName || relation.relationCode
      const label = `${appearance.prefix ? `[${appearance.prefix}] ` : ''}${relationLabel} · ${relationModeLabel(relation.resultMode)}`
      return {
        id: String(relation.id),
        source: String(relation.sourceEntityId),
        target: String(relation.targetEntityId),
        sourceHandle: `out:${relation.sourceFieldId}`,
        targetHandle: `in:${relation.targetFieldId}`,
        label,
        ...relationLinePresentation(lineStyle.value),
        markerEnd: { type: MarkerType.ArrowClosed, color: appearance.color, width: 18, height: 18 },
        animated: false,
        selectable: true,
        focusable: true,
        style: { stroke: appearance.color, strokeWidth: relation.draftStatus === 'deleted' ? 1.5 : 2, strokeDasharray: appearance.dash },
        labelStyle: { fill: appearance.color, fontSize: 11, fontWeight: 600 },
        labelShowBg: true,
        labelBgStyle: { fill: 'var(--app-surface-solid)', stroke: appearance.color, strokeWidth: 1 },
        labelBgPadding: [8, 5] as [number, number],
        labelBgBorderRadius: 6,
        ariaLabel: `${appearance.prefix || '已保存'}关系，${relationLabel}，${relationModeLabel(relation.resultMode)}`,
        data: { relation, status: relation.draftStatus },
      }
    })
  if (layoutMode.value === 'relation') void applyRelationLayout()
  else fitViewTrigger.value += 1
}

function parseHandle(handle: string | null | undefined) {
  const [, id] = String(handle || '').split(':')
  return id || ''
}

function resetRelationForm(payload?: VirtualRelationPayload) {
  Object.assign(relationForm, {
    relationCode: payload?.relationCode || '',
    relationName: payload?.relationName || '',
    resultMode: payload?.resultMode === 1 ? 1 : 0,
    sourceEntityId: payload?.sourceEntityId === undefined ? '' : String(payload.sourceEntityId),
    sourceFieldId: payload?.sourceFieldId === undefined ? '' : String(payload.sourceFieldId),
    targetEntityId: payload?.targetEntityId === undefined ? '' : String(payload.targetEntityId),
    targetFieldId: payload?.targetFieldId === undefined ? '' : String(payload.targetFieldId),
    enabled: payload?.enabled !== false,
    remark: payload?.remark || '',
  })
}

function openBlankRelation() {
  editingRelationId.value = null
  resetRelationForm()
  relationDialogVisible.value = true
}

function openCreateRelation(connection: Connection) {
  if (!batchMode.value) {
    ElMessage.info('请先进入批量操作模式，再拖拽建立关联')
    return
  }
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
  if (fieldById(sourceFieldId)?.logicalType !== fieldById(targetFieldId)?.logicalType) {
    ElMessage.warning('只有逻辑类型一致的字段才能建立关联')
    return
  }
  if (isDuplicatePair(sourceFieldId, targetFieldId, null)) {
    ElMessage.info('这两个字段之间已经存在关联或待新增关系')
    return
  }
  editingRelationId.value = null
  resetRelationForm({
    relationCode: defaultRelationCode(connection.source, sourceFieldId, connection.target, targetFieldId),
    relationName: `${fieldById(sourceFieldId)?.fieldName || sourceFieldId} → ${fieldById(targetFieldId)?.fieldName || targetFieldId}`,
    resultMode: 0,
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
  const relation = (event.edge.data as { relation?: DraftRelation } | undefined)?.relation
    || workingRelations.value.find(item => String(item.id) === String(event.edge.id))
  if (!relation) return
  editingRelationId.value = relation.id
  resetRelationForm(relation)
  relationDialogVisible.value = true
}

function isDuplicatePair(sourceFieldId: VirtualDataId, targetFieldId: VirtualDataId, ignoredId: VirtualDataId | null) {
  return draftRelations.value.some(relation => relation.draftStatus !== 'deleted'
    && String(relation.id) !== String(ignoredId)
    && String(relation.sourceFieldId) === String(sourceFieldId)
    && String(relation.targetFieldId) === String(targetFieldId))
}

function payloadEquals(left: VirtualRelationPayload, right: VirtualRelationPayload) {
  return JSON.stringify(left) === JSON.stringify(right)
}

function submitRelation() {
  if (!batchMode.value) return
  relationForm.relationCode = normalizeRelationCode(relationForm.relationCode)
  if (!relationForm.relationCode || !relationForm.relationName.trim() || !relationForm.sourceFieldId || !relationForm.targetFieldId) {
    ElMessage.warning('请填写关联编码、名称并选择完整的来源和目标字段')
    return
  }
  if (fieldById(relationForm.sourceFieldId)?.logicalType !== fieldById(relationForm.targetFieldId)?.logicalType) {
    ElMessage.warning('来源字段与目标字段的逻辑类型必须一致')
    return
  }
  if (isDuplicatePair(relationForm.sourceFieldId, relationForm.targetFieldId, editingRelationId.value)) {
    ElMessage.warning('这两个字段之间已经存在关联或待新增关系')
    return
  }
  const payload = { ...relationForm, relationName: relationForm.relationName.trim() }
  if (editingRelationId.value === null) {
    draftRelations.value.push({
      id: newDraftId(),
      ...payload,
      draftStatus: 'added',
      draftOrigin: 'manual',
    })
  }
  else {
    const index = draftRelations.value.findIndex(item => String(item.id) === String(editingRelationId.value))
    const current = draftRelations.value[index]
    if (!current) return
    const original = props.relations.find(item => String(item.id) === String(current.persistedId))
    const nextStatus: RelationDraftStatus = current.persistedId
      ? original && payloadEquals(payload, relationToPayload(original)) ? 'unchanged' : 'updated'
      : 'added'
    draftRelations.value[index] = { ...current, ...payload, draftStatus: nextStatus }
  }
  relationDialogVisible.value = false
}

function removeCurrentRelation() {
  if (!batchMode.value || editingRelationId.value === null) return
  const index = draftRelations.value.findIndex(item => String(item.id) === String(editingRelationId.value))
  const relation = draftRelations.value[index]
  if (!relation) return
  if (!relation.persistedId) draftRelations.value.splice(index, 1)
  else draftRelations.value[index] = { ...relation, draftStatus: 'deleted' }
  relationDialogVisible.value = false
}

function restoreCurrentRelation() {
  if (!currentDraft.value?.persistedId) return
  const original = props.relations.find(item => String(item.id) === String(currentDraft.value?.persistedId))
  if (!original) return
  const index = draftRelations.value.findIndex(item => String(item.id) === String(currentDraft.value?.id))
  draftRelations.value[index] = relationToDraft(original)
  relationDialogVisible.value = false
}

function swapRelationDirection() {
  if (formReadOnly.value) return
  const sourceEntityId = relationForm.sourceEntityId
  const sourceFieldId = relationForm.sourceFieldId
  relationForm.sourceEntityId = relationForm.targetEntityId
  relationForm.sourceFieldId = relationForm.targetFieldId
  relationForm.targetEntityId = sourceEntityId
  relationForm.targetFieldId = sourceFieldId
  if (relationForm.sourceFieldId && relationForm.targetFieldId) {
    relationForm.relationCode = defaultRelationCode(
      relationForm.sourceEntityId, relationForm.sourceFieldId, relationForm.targetEntityId, relationForm.targetFieldId,
    )
    relationForm.relationName = `${fieldById(relationForm.sourceFieldId)?.fieldName || relationForm.sourceFieldId} → ${fieldById(relationForm.targetFieldId)?.fieldName || relationForm.targetFieldId}`
  }
}

function enterBatchMode() {
  draftRelations.value = props.relations.map(relationToDraft)
  batchMode.value = true
}

async function cancelBatchMode(skipConfirm = false) {
  if (hasChanges.value && !skipConfirm) {
    try {
      await ElMessageBox.confirm(`当前有 ${changeCount.value} 条未保存变更，确认全部放弃吗？`, '退出批量操作', {
        type: 'warning', confirmButtonText: '放弃变更', cancelButtonText: '继续编辑',
      })
    }
    catch {
      return false
    }
  }
  batchMode.value = false
  draftRelations.value = []
  relationDialogVisible.value = false
  rebuildCanvas()
  return true
}

function submitBatch() {
  if (!hasChanges.value || batchSaving.value) return
  const payload: VirtualRelationBatchSavePayload = {
    creates: draftRelations.value.filter(item => item.draftStatus === 'added').map(stripDraftMetadata),
    updates: draftRelations.value.filter(item => item.draftStatus === 'updated' && item.persistedId !== undefined)
      .map(item => ({ id: item.persistedId as VirtualDataId, ...stripDraftMetadata(item) })),
    deletes: draftRelations.value.filter(item => item.draftStatus === 'deleted' && item.persistedId !== undefined)
      .map(item => item.persistedId as VirtualDataId),
  }
  batchSaving.value = true
  emit('saveBatch', payload, (success) => {
    batchSaving.value = false
    if (!success) return
    batchMode.value = false
    draftRelations.value = []
  })
}

function stripDraftMetadata(relation: DraftRelation): VirtualRelationPayload {
  return {
    relationCode: relation.relationCode,
    relationName: relation.relationName,
    resultMode: relation.resultMode,
    sourceEntityId: relation.sourceEntityId,
    sourceFieldId: relation.sourceFieldId,
    targetEntityId: relation.targetEntityId,
    targetFieldId: relation.targetFieldId,
    enabled: relation.enabled,
    remark: relation.remark,
  }
}

function openAiCreation() {
  if (!batchMode.value) enterBatchMode()
  if (props.entities.length > 30) {
    aiSelectedEntityIds.value = []
    aiSelectionKeyword.value = ''
    aiSelectionVisible.value = true
    return
  }
  const ids = candidateEntities.value.map(entity => entity.id)
  requestAiSuggestions(ids)
}

function toggleAiEntity(entityId: VirtualDataId, checked: boolean) {
  const id = String(entityId)
  if (checked && !aiSelectedEntityIds.value.includes(id)) {
    if (aiSelectedEntityIds.value.length >= 30) {
      ElMessage.warning('AI 单次最多分析 30 张数据表')
      return
    }
    aiSelectedEntityIds.value.push(id)
  }
  else if (!checked) {
    aiSelectedEntityIds.value = aiSelectedEntityIds.value.filter(item => item !== id)
  }
}

function submitAiSelection() {
  if (aiSelectedEntityIds.value.length < 2) {
    ElMessage.warning('请至少选择 2 张数据表进行关系分析')
    return
  }
  const ids = aiSelectedEntityIds.value
    .map(id => props.entities.find(entity => String(entity.id) === id)?.id)
    .filter((id): id is VirtualDataId => id !== undefined)
  aiSelectionVisible.value = false
  requestAiSuggestions(ids)
}

function requestAiSuggestions(entityIds: VirtualDataId[]) {
  if (entityIds.length < 2) {
    ElMessage.warning('当前范围不足 2 张数据表，无法分析关系')
    return
  }
  aiLoading.value = true
  emit('suggestRelations', entityIds.slice(0, 30), (suggestions) => {
    aiLoading.value = false
    if (!suggestions) return
    let added = 0
    let skipped = 0
    suggestions.forEach((suggestion) => {
      const relation = suggestion.relation
      if (isDuplicatePair(relation.sourceFieldId, relation.targetFieldId, null)) {
        skipped += 1
        return
      }
      draftRelations.value.push({
        id: newDraftId(),
        ...relation,
        resultMode: relation.resultMode === 1 ? 1 : 0,
        sourceEntityId: String(relation.sourceEntityId),
        sourceFieldId: String(relation.sourceFieldId),
        targetEntityId: String(relation.targetEntityId),
        targetFieldId: String(relation.targetFieldId),
        draftStatus: 'added',
        draftOrigin: 'ai',
        aiReason: suggestion.reason,
        aiConfidence: suggestion.confidence,
      })
      added += 1
    })
    if (added) {
      ElMessage.success(`AI 已生成 ${added} 条关系草稿${skipped ? `，跳过 ${skipped} 条重复建议` : ''}，请检查后批量保存`)
      fitViewTrigger.value += 1
    }
    else {
      ElMessage.info(skipped ? 'AI 建议均与当前关系重复' : 'AI 未发现足够可信的字段关联')
    }
  })
}

async function refreshCanvas() {
  if (batchMode.value && hasChanges.value) {
    try {
      await ElMessageBox.confirm('刷新会放弃当前未保存关系变更，确认继续吗？', '刷新关系画布', {
        type: 'warning', confirmButtonText: '放弃并刷新', cancelButtonText: '取消',
      })
    }
    catch {
      return
    }
    await cancelBatchMode(true)
  }
  emit('refresh')
}

function resetFilters() {
  keyword.value = ''
  selectedSources.value = []
  selectedEntityIds.value = []
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!hasChanges.value) return
  event.preventDefault()
  event.returnValue = ''
}

watch(
  () => [props.entities, props.fields, props.bindings, props.relations, keyword.value, selectedSources.value, selectedEntityIds.value, batchMode.value, draftRelations.value],
  rebuildCanvas,
  { deep: true, immediate: true },
)
watch(layoutMode, mode => { if (mode === 'relation') void applyRelationLayout() })
watch(lineStyle, rebuildCanvas)
watch([batchMode, hasChanges], ([active, dirty]) => emit('batchStateChange', active, dirty), { immediate: true })
watch(hasChanges, (dirty) => {
  if (dirty) window.addEventListener('beforeunload', handleBeforeUnload)
  else window.removeEventListener('beforeunload', handleBeforeUnload)
})

onMounted(async () => {
  if (layoutMode.value !== 'manual' || !nodes.value.length) return
  const measured = await measuredNodeDimensions()
  applyResponsiveGridLayout(measured)
  fitViewTrigger.value += 1
})

onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload))
</script>

<template>
  <Teleport defer to="#virtual-table-header-actions">
    <div class="relation-canvas-panel__actions">
      <span>显示 {{ filteredEntities.length }} 张表<span v-if="hiddenCount">，另有 {{ hiddenCount }} 张未展示</span></span>
      <template v-if="!batchMode">
        <el-button type="primary" :icon="EditPen" @click="enterBatchMode">批量操作</el-button>
        <el-button type="primary" plain :icon="MagicStick" :loading="aiLoading" @click="openAiCreation">AI 创建关联</el-button>
      </template>
      <template v-else>
        <el-button :icon="Plus" @click="openBlankRelation">新建关联</el-button>
        <el-button type="primary" plain :icon="MagicStick" :loading="aiLoading" @click="openAiCreation">AI 创建关联</el-button>
        <el-button :icon="Close" :disabled="batchSaving" @click="cancelBatchMode()">取消批量</el-button>
        <el-button type="primary" :icon="Check" :loading="batchSaving" :disabled="!hasChanges" @click="submitBatch">
          保存全部<span v-if="changeCount">（{{ changeCount }}）</span>
        </el-button>
      </template>
    </div>
  </Teleport>

  <Teleport defer to="#virtual-table-header-filters">
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
  </Teleport>

  <section class="relation-canvas-panel">
    <div v-loading="loading || aiLoading" class="relation-canvas-panel__canvas">
      <AppFlowCanvas
        ref="flowCanvas"
        v-model:nodes="nodes"
        v-model:edges="edges"
        :node-types="nodeTypes"
        :nodes-connectable="batchMode"
        :show-controls="true"
        :controls-show-fit-view="false"
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
      >
        <template #controls>
          <div class="relation-canvas-panel__canvas-tools" aria-label="画布操作">
            <label class="relation-canvas-panel__line-style">
              <span>连线</span>
              <el-select v-model="lineStyle" size="small" aria-label="连线样式">
                <el-option v-for="option in relationLineStyleOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </label>
            <el-button size="small" type="primary" plain :icon="MagicStick" :loading="layoutLoading" @click="beautifyCanvas">美化</el-button>
            <el-button-group>
              <el-button size="small" :icon="RefreshLeft" :disabled="!canUndoLayout || layoutLoading" title="撤销布局" aria-label="撤销布局" @click="undoCanvasLayout" />
              <el-button size="small" :icon="Aim" title="适应画布" aria-label="适应画布" @click="fitViewTrigger += 1" />
              <el-button size="small" :icon="RefreshRight" :loading="loading" title="刷新画布" aria-label="刷新画布" @click="refreshCanvas" />
            </el-button-group>
          </div>
        </template>
      </AppFlowCanvas>
      <div v-if="batchMode" class="relation-canvas-panel__batchbar" aria-live="polite">
        <strong>批量操作中</strong>
        <span class="change-chip is-added">待新增 {{ changeSummary.added }}</span>
        <span class="change-chip is-updated">待更新 {{ changeSummary.updated }}</span>
        <span class="change-chip is-deleted">待删除 {{ changeSummary.deleted }}</span>
        <span>保存全部后统一落库</span>
      </div>
      <div v-if="!loading && !filteredEntities.length" class="relation-canvas-panel__empty">当前筛选条件下没有可显示的虚拟表</div>
    </div>

    <el-dialog
      v-model="relationDialogVisible"
      width="700px"
      :title="editingRelationId === null ? '新建字段关联草稿' : formReadOnly && !batchMode ? '查看字段关联' : '编辑字段关联草稿'"
      append-to-body
      destroy-on-close
    >
      <el-form label-position="top" class="relation-editor" :disabled="formReadOnly">
        <div class="relation-editor__direction-note">
          <strong>先确认访问方向</strong>
          <span>来源表访问目标表时，返回单个对象还是对象集合。方向会影响 1:N 与 N:1 的表达。</span>
        </div>
        <div class="relation-editor__endpoint">
          <div>
            <span>来源表（当前对象）</span>
            <el-select v-model="relationForm.sourceEntityId" filterable aria-label="来源虚拟表" @change="relationForm.sourceFieldId = ''">
              <el-option v-for="entity in entityOptions" :key="entity.id" :label="entity.label" :value="entity.id" />
            </el-select>
            <el-select v-model="relationForm.sourceFieldId" filterable placeholder="选择来源字段" aria-label="来源字段">
              <el-option v-for="field in fieldsForEntity(relationForm.sourceEntityId)" :key="field.id" :label="fieldOptionLabel(field)" :value="String(field.id)" />
            </el-select>
          </div>
          <el-button class="relation-editor__swap" circle :icon="SwitchIcon" aria-label="交换关联方向" title="交换关联方向" @click="swapRelationDirection" />
          <div>
            <span>目标表（关联对象）</span>
            <el-select v-model="relationForm.targetEntityId" filterable aria-label="目标虚拟表" @change="relationForm.targetFieldId = ''">
              <el-option v-for="entity in entityOptions" :key="entity.id" :label="entity.label" :value="entity.id" />
            </el-select>
            <el-select v-model="relationForm.targetFieldId" filterable placeholder="选择类型匹配的目标字段" aria-label="目标字段">
              <el-option v-for="field in targetFieldOptions" :key="field.id" :label="fieldOptionLabel(field)" :value="String(field.id)" />
            </el-select>
          </div>
        </div>

        <el-form-item label="关联返回形态" required>
          <div class="relation-editor__modes">
            <button
              v-for="option in relationResultModeOptions"
              :key="option.value"
              type="button"
              :class="['relation-mode-card', { 'is-active': relationForm.resultMode === option.value }]"
              :aria-pressed="relationForm.resultMode === option.value"
              :disabled="formReadOnly"
              @click="relationForm.resultMode = option.value"
            >
              <span>{{ option.cardinality }}</span>
              <strong>{{ option.label }}</strong>
              <small>{{ option.description }}</small>
            </button>
          </div>
        </el-form-item>
        <div class="relation-editor__grid">
          <el-form-item label="关联编码" required><el-input v-model="relationForm.relationCode" maxlength="64" /></el-form-item>
          <el-form-item label="关联名称" required><el-input v-model="relationForm.relationName" maxlength="128" /></el-form-item>
        </div>
        <el-form-item label="说明">
          <el-input v-model="relationForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="状态"><el-switch v-model="relationForm.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
        <el-alert
          v-if="currentDraft?.draftOrigin === 'ai'"
          type="info"
          :closable="false"
          show-icon
          :title="`AI 建议${currentDraft.aiConfidence !== undefined ? ` · 置信度 ${Math.round(currentDraft.aiConfidence * 100)}%` : ''}`"
          :description="currentDraft.aiReason || '请人工核对字段语义和返回形态后再保存。'"
        />
      </el-form>
      <template #footer>
        <div class="relation-editor__footer">
          <el-button v-if="batchMode && currentDraft?.draftStatus === 'deleted'" :icon="RefreshLeft" @click="restoreCurrentRelation">撤销删除</el-button>
          <el-button v-else-if="batchMode && editingRelationId !== null" type="danger" plain :icon="Delete" @click="removeCurrentRelation">
            {{ currentDraft?.persistedId ? '标记删除' : '移除草稿' }}
          </el-button>
          <span />
          <el-button @click="relationDialogVisible = false">关闭</el-button>
          <el-button v-if="batchMode && currentDraft?.draftStatus !== 'deleted'" type="primary" @click="submitRelation">加入批量变更</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="aiSelectionVisible" title="选择 AI 分析的数据表" width="680px" append-to-body destroy-on-close>
      <section class="ai-relation-selection">
        <el-alert type="info" :closable="false" show-icon title="当前页面超过 30 张数据表" description="受 AI 上下文限制，请自行选择 2～30 张相关数据表。AI 结果只会加入批量草稿，不会直接落库。" />
        <div class="ai-relation-selection__toolbar">
          <el-input v-model="aiSelectionKeyword" clearable placeholder="搜索表编码或名称" :prefix-icon="Search" />
          <strong>{{ aiSelectedEntityIds.length }} / 30</strong>
        </div>
        <div class="ai-relation-selection__list">
          <label v-for="entity in filteredAiEntities" :key="entity.id">
            <el-checkbox
              :model-value="aiSelectedEntityIds.includes(String(entity.id))"
              :disabled="aiSelectedEntityIds.length >= 30 && !aiSelectedEntityIds.includes(String(entity.id))"
              @change="toggleAiEntity(entity.id, Boolean($event))"
            />
            <span><strong>{{ entity.entityName || entity.entityCode }}</strong><code>{{ entity.entityCode }}</code></span>
            <small>{{ (fieldsByEntity.get(String(entity.id)) || []).length }} 个字段</small>
          </label>
        </div>
      </section>
      <template #footer>
        <el-button @click="aiSelectionVisible = false">取消</el-button>
        <el-button type="primary" :disabled="aiSelectedEntityIds.length < 2" @click="submitAiSelection">开始 AI 分析</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.relation-canvas-panel {
  display: grid;
  grid-template-rows: minmax(0, 1fr);
  min-height: 0;
  height: 100%;
}

.relation-canvas-panel__filters,
.relation-canvas-panel__actions {
  display: flex;
  gap: var(--app-space-2);
  align-items: center;
}

.relation-canvas-panel__actions { flex-wrap: wrap; justify-content: flex-end; }
.relation-canvas-panel__filters :deep(.el-input) { width: 220px; }
.relation-canvas-panel__filters :deep(.el-select) { width: 190px; }
.relation-canvas-panel__actions span { color: var(--app-text-muted); font-size: var(--app-font-size-caption); }
.relation-canvas-panel__line-style { display: flex; gap: var(--app-space-2); align-items: center; white-space: nowrap; }
.relation-canvas-panel__line-style > span { color: var(--app-text-muted); font-size: var(--app-font-size-caption); }
.relation-canvas-panel__line-style :deep(.el-select) { width: 88px; }

.relation-canvas-panel__batchbar {
  position: absolute;
  z-index: 6;
  top: var(--app-space-3);
  right: var(--app-space-3);
  display: flex;
  gap: var(--app-space-2);
  align-items: center;
  padding: 8px var(--app-space-4);
  border: 1px solid var(--app-warning-border);
  border-radius: 9px;
  background: var(--app-warning-bg);
  box-shadow: var(--app-shadow-sm);
  color: var(--app-text);
  font-size: var(--app-font-size-caption);
}

.relation-canvas-panel__batchbar > span:last-child { color: var(--app-text-muted); }
.change-chip { padding: 2px 8px; border: 1px solid; border-radius: 999px; font-weight: 600; }
.change-chip.is-added { border-color: var(--app-success-border); background: var(--app-success-bg); color: var(--app-success); }
.change-chip.is-updated { border-color: var(--app-warning-border); background: var(--app-warning-bg); color: var(--app-warning); }
.change-chip.is-deleted { border-color: var(--app-border); background: var(--app-surface-muted); color: var(--app-text-muted); }

.relation-canvas-panel__canvas { position: relative; min-height: 0; overflow: hidden; }
.relation-canvas-panel__canvas-tools {
  display: flex;
  gap: var(--app-space-2);
  align-items: center;
  padding: 6px;
  border-left: 1px solid var(--app-border);
}
.relation-canvas-panel__canvas :deep(.vue-flow__controls) {
  display: flex;
  top: 0;
  bottom: auto;
  left: 0;
  z-index: 6;
  flex-direction: row;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-top: 0;
  border-left: 0;
  border-radius: 0 0 9px 0;
  background: var(--app-surface-raised);
  box-shadow: var(--app-shadow-sm);
}
.relation-canvas-panel__canvas :deep(.vue-flow__controls-button) {
  width: 18px;
  height: 18px;
  padding: 7px;
  border-right: 1px solid var(--app-border);
  border-bottom: 0;
  background: var(--app-surface-raised);
  color: var(--app-text);
}
.relation-canvas-panel__canvas :deep(.vue-flow__controls-button:last-child) { border-right: 0; }
.relation-canvas-panel__canvas :deep(.vue-flow__controls-button:hover) { background: var(--app-accent-bg); }
.relation-canvas-panel__canvas :deep(.vue-flow__controls-button svg) { fill: currentColor; }
.relation-canvas-panel__empty { position: absolute; inset: 0; display: grid; place-items: center; color: var(--app-text-muted); pointer-events: none; }

.relation-editor__direction-note { margin-bottom: var(--app-space-3); }
.relation-editor__direction-note strong,
.relation-editor__direction-note span { display: block; }
.relation-editor__direction-note span { margin-top: 3px; color: var(--app-text-muted); font-size: var(--app-font-size-caption); }
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
.relation-editor__endpoint > div { display: grid; gap: var(--app-space-2); min-width: 0; }
.relation-editor__endpoint span { color: var(--app-text-muted); font-size: var(--app-font-size-caption); }
.relation-editor__swap { cursor: pointer; }
.relation-editor__modes { display: grid; grid-template-columns: 1fr 1fr; gap: var(--app-space-3); width: 100%; }
.relation-mode-card {
  display: grid;
  gap: 4px;
  padding: var(--app-space-3);
  border: 1px solid var(--app-border);
  border-radius: 9px;
  background: var(--app-surface-solid);
  color: var(--app-text);
  text-align: left;
  cursor: pointer;
  transition: border-color 180ms ease, background-color 180ms ease, box-shadow 180ms ease;
}
.relation-mode-card:hover:not(:disabled),
.relation-mode-card:focus-visible { border-color: var(--app-accent); box-shadow: 0 0 0 2px var(--app-accent-bg-strong); outline: none; }
.relation-mode-card.is-active { border-color: var(--app-accent); background: var(--app-accent-bg); }
.relation-mode-card > span { color: var(--app-accent); font-size: 12px; font-weight: 700; }
.relation-mode-card > strong { color: var(--app-title); }
.relation-mode-card > small { color: var(--app-text-muted); line-height: 1.5; }
.relation-mode-card:disabled { cursor: default; opacity: 0.72; }
.relation-editor__grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--app-space-4); }
.relation-editor__footer { display: flex; gap: var(--app-space-2); align-items: center; justify-content: flex-end; }
.relation-editor__footer > span { flex: 1; }

.ai-relation-selection { display: grid; gap: var(--app-space-4); }
.ai-relation-selection__toolbar { display: flex; gap: var(--app-space-3); align-items: center; }
.ai-relation-selection__toolbar :deep(.el-input) { flex: 1; }
.ai-relation-selection__toolbar strong { min-width: 52px; color: var(--app-accent); text-align: right; }
.ai-relation-selection__list { display: grid; gap: 6px; max-height: 420px; overflow: auto; }
.ai-relation-selection__list label {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--app-space-3);
  align-items: center;
  padding: 9px var(--app-space-3);
  border: 1px solid var(--app-border-subtle);
  border-radius: 8px;
  cursor: pointer;
}
.ai-relation-selection__list label:hover { background: var(--app-accent-bg); }
.ai-relation-selection__list span strong,
.ai-relation-selection__list span code { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-relation-selection__list span code,
.ai-relation-selection__list small { color: var(--app-text-muted); font-size: 11px; }

@media (max-width: 1280px) {
  .relation-canvas-panel__filters,
  .relation-canvas-panel__actions { flex-wrap: wrap; }
}

@media (max-width: 720px) {
  .relation-canvas-panel__filters > :deep(*) { width: 100% !important; }
  .relation-canvas-panel__canvas :deep(.vue-flow__controls) {
    right: var(--app-space-3);
    flex-wrap: wrap;
  }
  .relation-canvas-panel__canvas-tools {
    width: 100%;
    border-top: 1px solid var(--app-border);
    border-left: 0;
    flex-wrap: wrap;
  }
  .relation-canvas-panel__batchbar {
    top: 112px;
    left: var(--app-space-3);
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .relation-editor__grid,
  .relation-editor__endpoint,
  .relation-editor__modes { grid-template-columns: 1fr; }
  .relation-editor__swap { justify-self: center; transform: rotate(90deg); }
}

@media (prefers-reduced-motion: reduce) {
  .relation-mode-card { transition: none; }
}
</style>
