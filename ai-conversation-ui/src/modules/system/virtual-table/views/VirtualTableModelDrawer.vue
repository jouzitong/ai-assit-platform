<script setup lang="ts">
import { Check, Delete, EditPen, MagicStick, Plus, RefreshRight, Setting } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'
import { AppCodeEditor } from '../../../../components'
import type { DbDataSourceItem, DbTableFieldMetaItem, DbTableMetaItem } from '../../api/dataSources'
import type {
  FieldTransformPortItem,
  FieldTransformPortPayload,
  FieldSide,
  FieldTransformRuleItem,
  FieldTransformRulePayload,
  FieldTransformScriptGeneratePayload,
  VirtualBindingItem,
  VirtualBindingPayload,
  VirtualDataId,
  VirtualEntityItem,
  VirtualEntityPayload,
  VirtualFieldItem,
  VirtualFieldPayload,
} from '../../api/virtualData'
import { bindingRoleLabel, catalogStatusLabel, catalogStatusType, logicalTypeLabel, logicalTypeOptions } from '../data/options'
import type { VirtualTableWorkspace } from '../service/virtualTable'

const props = defineProps<{
  modelValue: boolean
  entity: VirtualEntityItem | null
  workspace: VirtualTableWorkspace
  dataSources: DbDataSourceItem[]
  physicalTables: DbTableMetaItem[]
  physicalTableLoading?: boolean
  loading?: boolean
  descriptionGenerating?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  refresh: []
  loadPhysicalTables: [sourceKey: string]
  saveEntity: [id: VirtualDataId, payload: VirtualEntityPayload]
  saveField: [id: VirtualDataId | null, payload: VirtualFieldPayload]
  deleteField: [id: VirtualDataId]
  saveBinding: [id: VirtualDataId | null, payload: VirtualBindingPayload]
  deleteBinding: [id: VirtualDataId]
  saveRule: [id: VirtualDataId | null, payload: FieldTransformRulePayload, ports: Array<FieldTransformPortPayload & { id?: VirtualDataId }>, existingPorts: FieldTransformPortItem[]]
  updateRuleEnabled: [rule: FieldTransformRuleItem, enabled: boolean]
  deleteRule: [id: VirtualDataId]
  validateRule: [id: VirtualDataId]
  generateScript: [payload: FieldTransformScriptGeneratePayload, apply: (script: string) => void, fail: (message: string) => void]
  generateDescription: [id: VirtualDataId, currentDescription: string, apply: (description: string) => void]
}>()

type PortEditorRow = FieldTransformPortPayload & { id?: VirtualDataId }

const activeTab = ref('fields')
const entityDialogVisible = ref(false)
const fieldDialogVisible = ref(false)
const bindingDialogVisible = ref(false)
const ruleDialogVisible = ref(false)
const aiScriptDialogVisible = ref(false)
const aiScriptLoading = ref(false)
const aiScriptRequirement = ref('')
const editingFieldId = ref<VirtualDataId | null>(null)
const editingBindingId = ref<VirtualDataId | null>(null)
const editingRuleId = ref<VirtualDataId | null>(null)
const existingRulePorts = ref<FieldTransformPortItem[]>([])
const portRows = ref<PortEditorRow[]>([])
const scriptTemplateValue = ref('')
const routingConfigText = ref('{\n  "version": 1,\n  "strategy": 0,\n  "shardFields": []\n}')

const entityForm = reactive<VirtualEntityPayload>({
  entityCode: '',
  entityName: '',
  description: '',
  status: 0,
  catalogVersion: 0,
  enabled: true,
})

const fieldForm = reactive<VirtualFieldPayload>({
  entityId: '',
  fieldCode: '',
  fieldName: '',
  logicalType: 0,
  nullable: true,
  primaryKey: false,
  ordinalPosition: 0,
  defaultValue: '',
  enabled: true,
  remark: '',
})

const bindingForm = reactive<VirtualBindingPayload>({
  entityId: '',
  bindingCode: '',
  bindingGroup: 'default',
  bindingRole: 0,
  physicalTableMetaId: '',
  sourceKey: '',
  physicalTableName: '',
  readable: true,
  writable: true,
  readWeight: 100,
  writePriority: 0,
  routingConfig: { version: 1, strategy: 0, shardFields: [] },
  enabled: true,
  remark: '',
})

const ruleForm = reactive<FieldTransformRulePayload>({
  bindingId: '',
  ruleCode: '',
  ruleName: '',
  transformMode: 2,
  readTransformerCode: 'identity',
  readTransformerVersion: 1,
  writeTransformerCode: 'identity',
  writeTransformerVersion: 1,
  readConfig: { configVersion: 1 },
  writeConfig: { configVersion: 1 },
  scriptCode: '',
  enabled: true,
  remark: '',
})

const sortedFields = computed(() => [...props.workspace.fields].sort((left, right) => Number(left.ordinalPosition || 0) - Number(right.ordinalPosition || 0)))
const selectedBinding = computed(() => props.workspace.bindings.find(binding => String(binding.id) === String(ruleForm.bindingId)))
const databaseSourceOptions = computed(() => {
  const options = props.dataSources.filter((source) => {
    if (source.enabled === false || !source.sourceKey) return false
    const configType = 'configType' in (source.config || {}) ? String(source.config?.configType || '').toUpperCase() : ''
    if (configType) return configType === 'DATABASE'
    return Number(source.sourceType) === 1 || String(source.sourceType || '').toUpperCase() === 'DATABASE'
  })
  if (bindingForm.sourceKey && !options.some(source => source.sourceKey === bindingForm.sourceKey)) {
    return [{ id: 'current-binding-source', sourceKey: bindingForm.sourceKey, sourceName: bindingForm.sourceKey }, ...options]
  }
  return options
})
const bindingTableOptions = computed<DbTableMetaItem[]>(() => {
  const options = props.physicalTables.filter(table => (
    String(table.sourceKey || '') === String(bindingForm.sourceKey || '') && Boolean(table.tableName)
  ))
  if (bindingForm.physicalTableMetaId && !options.some(table => String(table.id) === String(bindingForm.physicalTableMetaId))) {
    options.unshift({
      id: bindingForm.physicalTableMetaId,
      sourceKey: bindingForm.sourceKey,
      tableName: bindingForm.physicalTableName,
      enabled: true,
    })
  }
  return options
})
const availablePhysicalFields = computed(() => props.workspace.physicalFields.filter((field) => {
  if (!selectedBinding.value) return true
  return field.sourceKey === selectedBinding.value.sourceKey && field.tableName === selectedBinding.value.physicalTableName
}))

function prettyJson(value: unknown, fallback: Record<string, unknown>) {
  return JSON.stringify(value && typeof value === 'object' ? value : fallback, null, 2)
}

function parseJsonObject(value: string, label: string) {
  try {
    const parsed = JSON.parse(value || '{}')
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error()
    return parsed as Record<string, unknown>
  }
  catch {
    ElMessage.warning(`${label}必须是合法的 JSON 对象`)
    return null
  }
}

function openEntityEditor() {
  if (!props.entity) return
  Object.assign(entityForm, {
    entityCode: props.entity.entityCode || '',
    entityName: props.entity.entityName || '',
    description: props.entity.description || '',
    status: props.entity.status ?? 0,
    catalogVersion: props.entity.catalogVersion || 0,
    enabled: props.entity.enabled !== false,
  })
  entityDialogVisible.value = true
}

function submitEntity() {
  if (!props.entity || !entityForm.entityCode.trim() || !entityForm.entityName.trim()) {
    ElMessage.warning('虚拟表编码和名称不能为空')
    return
  }
  emit('saveEntity', props.entity.id, { ...entityForm })
  entityDialogVisible.value = false
}

function requestDescriptionGeneration() {
  if (!props.entity) return
  emit('generateDescription', props.entity.id, entityForm.description || '', (description) => {
    entityForm.description = description
  })
}

function openFieldEditor(field?: VirtualFieldItem) {
  if (!props.entity) return
  editingFieldId.value = field?.id || null
  Object.assign(fieldForm, {
    entityId: props.entity.id,
    fieldCode: field?.fieldCode || '',
    fieldName: field?.fieldName || '',
    logicalType: field?.logicalType ?? 0,
    nullable: field?.nullable !== false,
    primaryKey: field?.primaryKey === true,
    ordinalPosition: field?.ordinalPosition ?? sortedFields.value.length,
    defaultValue: field?.defaultValue || '',
    enabled: field?.enabled !== false,
    remark: field?.remark || '',
  })
  fieldDialogVisible.value = true
}

function submitField() {
  if (!fieldForm.fieldCode.trim() || !fieldForm.fieldName.trim()) {
    ElMessage.warning('字段编码和名称不能为空')
    return
  }
  emit('saveField', editingFieldId.value, { ...fieldForm })
  fieldDialogVisible.value = false
}

function openBindingEditor(binding?: VirtualBindingItem) {
  if (!props.entity) return
  editingBindingId.value = binding?.id || null
  Object.assign(bindingForm, {
    entityId: props.entity.id,
    bindingCode: binding?.bindingCode || '',
    bindingGroup: binding?.bindingGroup || 'default',
    bindingRole: binding?.bindingRole ?? 0,
    physicalTableMetaId: binding?.physicalTableMetaId || '',
    sourceKey: binding?.sourceKey || '',
    physicalTableName: binding?.physicalTableName || '',
    readable: binding?.readable !== false,
    writable: binding?.writable !== false,
    readWeight: binding?.readWeight ?? 100,
    writePriority: binding?.writePriority ?? 0,
    routingConfig: binding?.routingConfig || {},
    enabled: binding?.enabled !== false,
    remark: binding?.remark || '',
  })
  routingConfigText.value = prettyJson(binding?.routingConfig, { version: 1, strategy: 0, shardFields: [] })
  if (bindingForm.sourceKey) emit('loadPhysicalTables', bindingForm.sourceKey)
  bindingDialogVisible.value = true
}

function handleBindingSourceChange(sourceKey: string) {
  bindingForm.sourceKey = sourceKey || ''
  bindingForm.physicalTableMetaId = ''
  bindingForm.physicalTableName = ''
  emit('loadPhysicalTables', bindingForm.sourceKey)
}

function syncBindingTable(tableId: VirtualDataId) {
  const table = bindingTableOptions.value.find(item => String(item.id) === String(tableId))
  if (!table) return
  bindingForm.physicalTableMetaId = table.id
  bindingForm.physicalTableName = table.tableName || ''
}

function submitBinding() {
  const routingConfig = parseJsonObject(routingConfigText.value, '路由配置')
  if (!routingConfig) return
  if (!bindingForm.bindingCode.trim() || !bindingForm.sourceKey.trim() || !bindingForm.physicalTableName.trim() || !bindingForm.physicalTableMetaId) {
    ElMessage.warning('绑定编码、数据源、物理表和物理表元数据 ID 不能为空')
    return
  }
  emit('saveBinding', editingBindingId.value, { ...bindingForm, routingConfig })
  bindingDialogVisible.value = false
}

function selectedPhysicalCode() {
  const row = portRows.value.find(item => item.fieldSide === 0 && item.physicalFieldMetaId)
  return props.workspace.physicalFields.find(field => String(field.id) === String(row?.physicalFieldMetaId))?.columnName || 'physical_field'
}

function selectedVirtualCode() {
  const row = portRows.value.find(item => item.fieldSide === 1 && item.virtualFieldId)
  return sortedFields.value.find(field => String(field.id) === String(row?.virtualFieldId))?.fieldCode || 'virtual_field'
}

function buildScriptTemplate() {
  const physicalCode = selectedPhysicalCode()
  const virtualCode = selectedVirtualCode()
  return `def read(inputs, context):
    # 读取：物理字段 -> 虚拟字段
    # inputs 的键是下方选择的字段编码；返回值的键是虚拟字段编码。
    value = inputs.get("${physicalCode}")
    return {
        "${virtualCode}": value,
    }

def write(inputs, context):
    # 写回：虚拟字段 -> 物理字段
    # 使用 "字段名" in inputs 判断本次请求是否传入了该字段。
    return {
        "${physicalCode}": inputs.get("${virtualCode}"),
    }`
}

function resetScriptTemplate() {
  ruleForm.scriptCode = buildScriptTemplate()
  scriptTemplateValue.value = ruleForm.scriptCode
}

function openAiScriptDialog() {
  aiScriptRequirement.value = ''
  aiScriptDialogVisible.value = true
}

function buildAiScriptPayload(requirement: string): FieldTransformScriptGeneratePayload | null {
  if (!props.entity || !ruleForm.bindingId) return null

  const physicalFields = availablePhysicalFields.value
    .map(field => ({
      code: field.columnName || '',
      name: field.columnComment || field.columnName || '',
      dataType: field.dataType || '',
      nullable: field.nullable,
      primaryKey: field.primaryKey,
      remark: field.remark || '',
    }))
    .filter(field => field.code)
  const virtualFields = sortedFields.value
    .map(field => ({
      code: field.fieldCode || '',
      name: field.fieldName || field.fieldCode || '',
      dataType: logicalTypeLabel(field.logicalType),
      nullable: field.nullable,
      primaryKey: field.primaryKey,
      remark: field.remark || '',
    }))
    .filter(field => field.code)
  const mappings = portRows.value.flatMap((port) => {
    if (port.fieldSide === 0) {
      const field = availablePhysicalFields.value.find(item => String(item.id) === String(port.physicalFieldMetaId))
      if (!field?.columnName) return []
      return [{
        side: 'physical' as const,
        code: field.columnName,
        name: field.columnComment || field.columnName,
        dataType: field.dataType || '',
        requiredOnWrite: false,
      }]
    }
    const field = sortedFields.value.find(item => String(item.id) === String(port.virtualFieldId))
    if (!field?.fieldCode) return []
    return [{
      side: 'virtual' as const,
      code: field.fieldCode,
      name: field.fieldName || field.fieldCode,
      dataType: logicalTypeLabel(field.logicalType),
      requiredOnWrite: port.requiredOnWrite === true,
    }]
  })

  return {
    entityId: props.entity.id,
    bindingId: ruleForm.bindingId,
    ruleName: ruleForm.ruleName,
    requirement,
    currentScript: ruleForm.scriptCode || '',
    physicalFields,
    virtualFields,
    mappings,
  }
}

function submitAiScript() {
  const requirement = aiScriptRequirement.value.trim()
  if (!requirement) {
    ElMessage.warning('请先描述需要完成的字段转换需求')
    return
  }
  const payload = buildAiScriptPayload(requirement)
  if (!payload) {
    ElMessage.warning('请先选择物理绑定')
    return
  }
  if (!payload.mappings.some(item => item.side === 'physical') || !payload.mappings.some(item => item.side === 'virtual')) {
    ElMessage.warning('请至少选择一个物理字段和一个虚拟字段')
    return
  }

  aiScriptLoading.value = true
  emit('generateScript', payload, (script) => {
    ruleForm.scriptCode = script.trim()
    scriptTemplateValue.value = ruleForm.scriptCode
    aiScriptLoading.value = false
    aiScriptDialogVisible.value = false
    ElMessage.success('AI 脚本已生成，请检查后保存')
  }, (message) => {
    aiScriptLoading.value = false
    ElMessage.error(message || 'AI 脚本生成失败')
  })
}

function syncScriptTemplate() {
  if (ruleForm.scriptCode && ruleForm.scriptCode !== scriptTemplateValue.value) return
  ruleForm.scriptCode = buildScriptTemplate()
  scriptTemplateValue.value = ruleForm.scriptCode
}

function scriptMode(code: string) {
  const readable = /(^|\n)\s*def\s+read\s*\(/.test(code)
  const writable = /(^|\n)\s*def\s+write\s*\(/.test(code)
  return readable && writable ? 2 : readable ? 0 : writable ? 1 : null
}

function openRuleEditor(rule?: FieldTransformRuleItem) {
  editingRuleId.value = rule?.id || null
  Object.assign(ruleForm, {
    bindingId: rule?.bindingId || props.workspace.bindings[0]?.id || '',
    ruleCode: rule?.ruleCode || '',
    ruleName: rule?.ruleName || '',
    transformMode: rule?.transformMode ?? 2,
    readTransformerCode: rule?.readTransformerCode || 'identity',
    readTransformerVersion: rule?.readTransformerVersion || 1,
    writeTransformerCode: rule?.writeTransformerCode || 'identity',
    writeTransformerVersion: rule?.writeTransformerVersion || 1,
    readConfig: rule?.readConfig || { configVersion: 1 },
    writeConfig: rule?.writeConfig || { configVersion: 1 },
    scriptCode: rule?.scriptCode || '',
    enabled: rule?.enabled !== false,
    remark: rule?.remark || '',
  })
  scriptTemplateValue.value = ''
  existingRulePorts.value = rule ? props.workspace.ports.filter(port => String(port.ruleId) === String(rule.id)) : []
  const loadedPorts: PortEditorRow[] = []
  existingRulePorts.value.forEach((port) => {
    const fieldSide = port.fieldSide ?? 1
    const row: PortEditorRow = {
      id: port.id,
      ruleId: port.ruleId || '',
      fieldSide,
      portCode: port.portCode?.trim() || nextPortCode(fieldSide, loadedPorts),
      virtualFieldId: port.virtualFieldId,
      physicalFieldMetaId: port.physicalFieldMetaId,
      physicalColumnName: port.physicalColumnName,
      ordinalPosition: port.ordinalPosition || 0,
      requiredOnWrite: port.requiredOnWrite === true,
      remark: port.remark || '',
    }
    loadedPorts.push(row)
  })
  portRows.value = loadedPorts
  if (!portRows.value.length) {
    addPort(0)
    addPort(1)
  }
  if (!ruleForm.scriptCode) syncScriptTemplate()
  ruleDialogVisible.value = true
}

function nextPortCode(side: FieldSide, rows: PortEditorRow[] = portRows.value) {
  const prefix = side === 0 ? 'physical' : 'virtual'
  const usedCodes = new Set(rows
    .filter(item => item.fieldSide === side)
    .map(item => item.portCode.trim())
    .filter(Boolean))
  let candidate = prefix
  let index = 1
  while (usedCodes.has(candidate)) candidate = `${prefix}${index++}`
  return candidate
}

function addPort(side: FieldSide) {
  portRows.value.push({
    ruleId: editingRuleId.value || '',
    fieldSide: side,
    portCode: nextPortCode(side),
    ordinalPosition: portRows.value.filter(item => item.fieldSide === side).length,
    requiredOnWrite: false,
    remark: '',
  })
}

function syncPhysicalPort(row: PortEditorRow) {
  const field = props.workspace.physicalFields.find(item => String(item.id) === String(row.physicalFieldMetaId))
  row.physicalColumnName = field?.columnName || ''
  syncScriptTemplate()
}

function syncVirtualPort() {
  syncScriptTemplate()
}

function submitRule() {
  if (!ruleForm.bindingId || !ruleForm.ruleCode.trim() || !ruleForm.ruleName.trim()) {
    ElMessage.warning('绑定、规则编码和规则名称不能为空')
    return
  }
  if (!portRows.value.length) {
    ElMessage.warning('至少配置一个物理字段和一个虚拟字段')
    return
  }
  const invalidPort = portRows.value.some(port => port.fieldSide === 1 ? !port.virtualFieldId : !port.physicalFieldMetaId)
  if (invalidPort) {
    ElMessage.warning('每个端口都必须选择对应字段')
    return
  }
  const scriptCode = String(ruleForm.scriptCode || '').trim()
  const mode = scriptMode(scriptCode)
  if (scriptCode && mode === null) {
    ElMessage.warning('请至少定义 read 或 write 方法')
    return
  }
  const payload: FieldTransformRulePayload = scriptCode
    ? {
        ...ruleForm,
        scriptCode,
        transformMode: mode as 0 | 1 | 2,
        readTransformerCode: mode === 1 ? undefined : 'script',
        readTransformerVersion: mode === 1 ? undefined : 1,
        writeTransformerCode: mode === 0 ? undefined : 'script',
        writeTransformerVersion: mode === 0 ? undefined : 1,
        readConfig: {},
        writeConfig: {},
      }
    : { ...ruleForm }
  emit('saveRule', editingRuleId.value, payload, portRows.value.map(port => ({ ...port })), existingRulePorts.value)
  ruleDialogVisible.value = false
}

function updateRuleEnabled(rule: FieldTransformRuleItem, enabled: boolean) {
  emit('updateRuleEnabled', rule, enabled)
}

async function confirmDelete(type: 'field' | 'binding' | 'rule', id: VirtualDataId, label: string) {
  try {
    await ElMessageBox.confirm(`确认删除「${label}」吗？请先确保没有其他规则或关联引用它。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    if (type === 'field') emit('deleteField', id)
    if (type === 'binding') emit('deleteBinding', id)
    if (type === 'rule') emit('deleteRule', id)
  }
  catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

function bindingLabel(bindingId?: VirtualDataId) {
  const binding = props.workspace.bindings.find(item => String(item.id) === String(bindingId))
  return binding ? `${binding.bindingCode} · ${binding.sourceKey}/${binding.physicalTableName}` : '-'
}

watch(() => props.modelValue, (visible) => {
  if (visible) activeTab.value = 'fields'
})
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    size="82%"
    append-to-body
    destroy-on-close
    class="virtual-model-drawer"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="virtual-model-drawer__header">
        <div>
          <span>虚拟模型配置</span>
          <h2>{{ entity?.entityName || entity?.entityCode }}</h2>
          <code>{{ entity?.entityCode }}</code>
        </div>
        <el-tag :type="catalogStatusType(entity?.status)">{{ catalogStatusLabel(entity?.status) }}</el-tag>
        <el-button :icon="EditPen" @click="openEntityEditor">编辑基础信息</el-button>
        <el-button :icon="RefreshRight" :loading="loading" @click="emit('refresh')">刷新</el-button>
      </div>
    </template>

    <div v-loading="loading" class="virtual-model-drawer__body">
      <div class="virtual-model-drawer__summary">
        <div><span>字段</span><strong>{{ workspace.fields.length }}</strong></div>
        <div><span>物理绑定</span><strong>{{ workspace.bindings.length }}</strong></div>
        <div><span>转换规则</span><strong>{{ workspace.rules.length }}</strong></div>
        <div><span>目录版本</span><strong>v{{ entity?.catalogVersion || 0 }}</strong></div>
      </div>

      <el-tabs v-model="activeTab" class="virtual-model-drawer__tabs">
        <el-tab-pane label="虚拟字段" name="fields">
          <div class="virtual-model-drawer__section-head">
            <div><h3>虚拟字段</h3><p>一个物理字段可拆成多个虚拟字段，多个物理字段也可由转换规则合并。</p></div>
            <el-button type="primary" :icon="Plus" @click="openFieldEditor()">新增字段</el-button>
          </div>
          <el-table :data="sortedFields" row-key="id">
            <el-table-column prop="fieldName" label="字段名称" min-width="150" />
            <el-table-column label="字段编码" min-width="150"><template #default="{ row }"><code>{{ row.fieldCode }}</code></template></el-table-column>
            <el-table-column label="逻辑类型" width="120"><template #default="{ row }">{{ logicalTypeLabel(row.logicalType) }}</template></el-table-column>
            <el-table-column label="约束" width="150"><template #default="{ row }"><el-tag v-if="row.primaryKey" size="small">主键</el-tag><span v-else>{{ row.nullable ? '可空' : '必填' }}</span></template></el-table-column>
            <el-table-column prop="ordinalPosition" label="顺序" width="80" align="center" />
            <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag size="small" :type="row.enabled === false ? 'info' : 'success'">{{ row.enabled === false ? '停用' : '启用' }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button text type="primary" @click="openFieldEditor(row)">编辑</el-button><el-button text type="danger" @click="confirmDelete('field', row.id, row.fieldName || row.fieldCode)">删除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="物理绑定" name="bindings">
          <div class="virtual-model-drawer__section-head">
            <div><h3>物理表映射</h3><p>同一个虚拟表可绑定主表、副本或分片，并独立维护读写与路由策略。</p></div>
            <el-button type="primary" :icon="Plus" @click="openBindingEditor()">新增绑定</el-button>
          </div>
          <el-table :data="workspace.bindings" row-key="id">
            <el-table-column prop="bindingCode" label="绑定编码" min-width="130" />
            <el-table-column label="角色" width="110"><template #default="{ row }">{{ bindingRoleLabel(row.bindingRole) }}</template></el-table-column>
            <el-table-column prop="sourceKey" label="数据源" min-width="140" />
            <el-table-column prop="physicalTableName" label="物理表" min-width="160" />
            <el-table-column label="能力" width="130"><template #default="{ row }">{{ row.readable ? '读' : '' }}{{ row.readable && row.writable ? ' / ' : '' }}{{ row.writable ? '写' : '' }}</template></el-table-column>
            <el-table-column prop="bindingGroup" label="绑定组" width="110" />
            <el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button text type="primary" @click="openBindingEditor(row)">编辑</el-button><el-button text type="danger" @click="confirmDelete('binding', row.id, row.bindingCode)">删除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="字段转换规则" name="rules">
          <div class="virtual-model-drawer__section-head">
            <div><h3>字段变换规则</h3><p>配置物理端口和虚拟端口，实现字段拆分、合并、JSON 处理和语义映射。</p></div>
            <el-button type="primary" :icon="Plus" :disabled="!workspace.bindings.length" @click="openRuleEditor()">新增规则</el-button>
          </div>
          <el-table :data="workspace.rules" row-key="id">
            <el-table-column prop="ruleName" label="规则名称" min-width="160" />
            <el-table-column label="规则编码" min-width="150"><template #default="{ row }"><code>{{ row.ruleCode }}</code></template></el-table-column>
            <el-table-column label="物理绑定" min-width="220"><template #default="{ row }">{{ bindingLabel(row.bindingId) }}</template></el-table-column>
            <el-table-column label="端口" width="80" align="center"><template #default="{ row }">{{ workspace.ports.filter(item => String(item.ruleId) === String(row.id)).length }}</template></el-table-column>
            <el-table-column label="状态" width="120"><template #default="{ row }"><el-switch :model-value="row.enabled !== false" active-text="启用" inactive-text="停用" @update:model-value="updateRuleEnabled(row, Boolean($event))" /></template></el-table-column>
            <el-table-column label="操作" width="220" fixed="right"><template #default="{ row }"><el-button text type="primary" :icon="Setting" @click="openRuleEditor(row)">配置</el-button><el-button text :icon="Check" @click="emit('validateRule', row.id)">校验</el-button><el-button text type="danger" @click="confirmDelete('rule', row.id, row.ruleName || row.ruleCode)">删除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="entityDialogVisible" title="编辑虚拟表" width="620px" append-to-body>
      <el-form label-position="top">
        <div class="virtual-editor-grid"><el-form-item label="虚拟表编码"><el-input v-model="entityForm.entityCode" maxlength="64" /></el-form-item><el-form-item label="虚拟表名称"><el-input v-model="entityForm.entityName" maxlength="128" /></el-form-item></div>
        <el-form-item class="virtual-description-editor">
          <template #label>
            <div class="virtual-description-editor__label">
              <span>说明</span>
              <el-button
                text
                type="primary"
                size="small"
                :icon="MagicStick"
                :loading="descriptionGenerating"
                :disabled="descriptionGenerating"
                @click="requestDescriptionGeneration"
              >AI 智能补充</el-button>
            </div>
          </template>
          <el-input v-model="entityForm.description" type="textarea" :rows="5" maxlength="512" show-word-limit :disabled="descriptionGenerating" />
          <p class="virtual-description-editor__hint">以 Markdown 格式面向知识库语义检索生成；仅使用数据表定义、字段和关联，不包含物理映射。生成后请确认内容再保存。</p>
        </el-form-item>
        <el-form-item label="状态"><el-switch v-model="entityForm.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="entityDialogVisible = false">取消</el-button><el-button type="primary" @click="submitEntity">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="fieldDialogVisible" :title="editingFieldId === null ? '新增虚拟字段' : '编辑虚拟字段'" width="680px" append-to-body>
      <el-form label-position="top"><div class="virtual-editor-grid"><el-form-item label="字段编码"><el-input v-model="fieldForm.fieldCode" /></el-form-item><el-form-item label="字段名称"><el-input v-model="fieldForm.fieldName" /></el-form-item><el-form-item label="逻辑类型"><el-select v-model="fieldForm.logicalType"><el-option v-for="option in logicalTypeOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item><el-form-item label="显示顺序"><el-input-number v-model="fieldForm.ordinalPosition" :min="0" /></el-form-item></div><div class="virtual-editor-switches"><el-checkbox v-model="fieldForm.nullable">允许为空</el-checkbox><el-checkbox v-model="fieldForm.primaryKey">逻辑主键</el-checkbox><el-checkbox v-model="fieldForm.enabled">启用字段</el-checkbox></div><el-form-item label="默认值"><el-input v-model="fieldForm.defaultValue" /></el-form-item><el-form-item label="备注"><el-input v-model="fieldForm.remark" type="textarea" :rows="2" /></el-form-item></el-form>
      <template #footer><el-button @click="fieldDialogVisible = false">取消</el-button><el-button type="primary" @click="submitField">保存字段</el-button></template>
    </el-dialog>

    <el-dialog v-model="bindingDialogVisible" :title="editingBindingId === null ? '新增物理绑定' : '编辑物理绑定'" width="760px" append-to-body>
      <el-form label-position="top"><div class="virtual-editor-grid virtual-editor-grid--3"><el-form-item label="绑定编码"><el-input v-model="bindingForm.bindingCode" /></el-form-item><el-form-item label="绑定组"><el-input v-model="bindingForm.bindingGroup" /></el-form-item><el-form-item label="角色"><el-select v-model="bindingForm.bindingRole"><el-option label="主表" :value="0" /><el-option label="副本" :value="1" /></el-select></el-form-item><el-form-item label="数据源"><el-select v-model="bindingForm.sourceKey" filterable placeholder="请选择数据库数据源" @change="handleBindingSourceChange"><el-option v-for="source in databaseSourceOptions" :key="source.sourceKey || source.id" :label="`${source.sourceName || source.sourceKey} · ${source.sourceKey}`" :value="source.sourceKey" /></el-select></el-form-item><el-form-item label="物理表（元数据）"><el-select v-model="bindingForm.physicalTableMetaId" filterable :loading="physicalTableLoading" :disabled="!bindingForm.sourceKey" placeholder="请选择物理表" @change="syncBindingTable"><el-option v-for="table in bindingTableOptions" :key="table.id" :label="`${table.tableName} · 元数据 ID ${table.id}${table.tableComment ? ` · ${table.tableComment}` : ''}`" :value="table.id" /></el-select></el-form-item><el-form-item label="读权重"><el-input-number v-model="bindingForm.readWeight" :min="0" /></el-form-item><el-form-item label="写优先级"><el-input-number v-model="bindingForm.writePriority" :min="0" /></el-form-item></div><div class="virtual-editor-switches"><el-checkbox v-model="bindingForm.readable">允许读取</el-checkbox><el-checkbox v-model="bindingForm.writable">允许写入</el-checkbox><el-checkbox v-model="bindingForm.enabled">启用绑定</el-checkbox></div><el-form-item label="路由配置（JSON 对象）"><el-input v-model="routingConfigText" type="textarea" :rows="7" /></el-form-item><el-form-item label="备注"><el-input v-model="bindingForm.remark" /></el-form-item></el-form>
      <template #footer><el-button @click="bindingDialogVisible = false">取消</el-button><el-button type="primary" @click="submitBinding">保存绑定</el-button></template>
    </el-dialog>

    <el-dialog v-model="ruleDialogVisible" :title="editingRuleId === null ? '新增字段变换规则' : '配置字段变换规则'" width="980px" append-to-body>
      <el-form label-position="top" class="transform-rule-editor">
        <div class="virtual-editor-grid virtual-editor-grid--3"><el-form-item label="物理绑定"><el-select v-model="ruleForm.bindingId" filterable><el-option v-for="binding in workspace.bindings" :key="binding.id" :label="bindingLabel(binding.id)" :value="binding.id" /></el-select></el-form-item><el-form-item label="规则编码"><el-input v-model="ruleForm.ruleCode" /></el-form-item><el-form-item label="规则名称"><el-input v-model="ruleForm.ruleName" /></el-form-item></div>
        <section class="transform-rule-editor__script"><header><div><strong>转换脚本（Python-like）</strong><p>脚本只处理字段值；inputs 的键是所选字段编码，返回对象的键必须是目标字段编码。仅支持受限语法。</p></div><div class="transform-rule-editor__script-actions"><el-button text type="primary" size="small" :icon="MagicStick" @click="openAiScriptDialog">AI 编写</el-button><el-button text type="primary" size="small" @click="resetScriptTemplate">恢复示例模板</el-button></div></header><AppCodeEditor v-model="ruleForm.scriptCode" format="python" :show-format-switcher="false" toolbar-label="Python-like" min-height="300px" /></section>
        <section class="transform-rule-editor__ports"><header><div><strong>字段映射</strong><p>程序内部自动维护映射编码和顺序，用户只需选择物理字段、虚拟字段。</p></div><div><el-button :icon="Plus" @click="addPort(0)">物理字段</el-button><el-button :icon="Plus" @click="addPort(1)">虚拟字段</el-button></div></header><div v-for="(port, index) in portRows" :key="port.id || `${port.fieldSide}-${index}`" class="transform-rule-editor__port"><el-tag :type="port.fieldSide === 0 ? 'warning' : 'success'">{{ port.fieldSide === 0 ? '物理' : '虚拟' }}</el-tag><el-select v-if="port.fieldSide === 0" v-model="port.physicalFieldMetaId" filterable placeholder="选择物理字段" @change="syncPhysicalPort(port)"><el-option v-for="field in availablePhysicalFields" :key="field.id" :label="`${field.columnName} · ${field.dataType}`" :value="field.id" /></el-select><el-select v-else v-model="port.virtualFieldId" filterable placeholder="选择虚拟字段" @change="syncVirtualPort"><el-option v-for="field in sortedFields" :key="field.id" :label="`${field.fieldName} · ${field.fieldCode}`" :value="field.id" /></el-select><el-checkbox v-if="port.fieldSide === 1" v-model="port.requiredOnWrite">写入必填</el-checkbox><span v-else></span><el-button text type="danger" :icon="Delete" aria-label="删除字段" @click="portRows.splice(index, 1)" /></div></section>
        <div class="virtual-editor-switches"><el-checkbox v-model="ruleForm.enabled">启用规则</el-checkbox></div><el-form-item label="备注"><el-input v-model="ruleForm.remark" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="ruleDialogVisible = false">取消</el-button><el-button type="primary" @click="submitRule">保存规则与端口</el-button></template>
    </el-dialog>

    <el-dialog v-model="aiScriptDialogVisible" title="AI 编写转换脚本" width="680px" append-to-body destroy-on-close>
      <p class="transform-rule-editor__ai-hint">请描述想要实现的字段转换逻辑，AI 会结合当前虚拟表、物理字段、虚拟字段、字段映射和当前模板生成脚本。生成结果只回填编辑器，不会自动保存。</p>
      <el-form label-position="top">
        <el-form-item label="需求描述" required>
          <el-input v-model="aiScriptRequirement" type="textarea" :rows="7" maxlength="4000" show-word-limit :disabled="aiScriptLoading" placeholder="例如：实体字段 a 的值为 1、2、3 时写入 vb_f_1，值为 4、5、6 时写入 vb_f_2；写入时两个虚拟字段不能同时传入。" />
        </el-form-item>
      </el-form>
      <template #footer><el-button :disabled="aiScriptLoading" @click="aiScriptDialogVisible = false">取消</el-button><el-button type="primary" :loading="aiScriptLoading" @click="submitAiScript">生成脚本</el-button></template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.virtual-model-drawer__header {
  display: flex;
  gap: var(--app-space-3);
  align-items: center;
  width: 100%;
}

.virtual-model-drawer__header > div:first-child {
  min-width: 0;
  margin-right: auto;
}

.virtual-model-drawer__header span,
.virtual-model-drawer__header code {
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.virtual-model-drawer__header h2 {
  margin: 2px 0;
  color: var(--app-title);
  font-size: 20px;
}

.virtual-model-drawer__body {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--app-space-4);
  min-height: 100%;
}

.virtual-model-drawer__summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--app-space-3);
}

.virtual-model-drawer__summary > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--app-space-3) var(--app-space-4);
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: var(--app-surface-muted);
}

.virtual-model-drawer__summary span {
  color: var(--app-text-muted);
}

.virtual-model-drawer__summary strong {
  color: var(--app-title);
  font-size: 20px;
}

.virtual-model-drawer__tabs {
  min-height: 0;
}

.virtual-model-drawer__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--app-space-3);
}

.virtual-model-drawer__section-head h3,
.virtual-model-drawer__section-head p {
  margin: 0;
}

.virtual-model-drawer__section-head h3 {
  color: var(--app-title);
}

.virtual-model-drawer__section-head p {
  margin-top: 4px;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.virtual-editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--app-space-4);
}

.virtual-editor-grid--3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.virtual-editor-grid :deep(.el-select),
.virtual-editor-grid :deep(.el-input-number) {
  width: 100%;
}

.virtual-editor-switches {
  display: flex;
  flex-wrap: wrap;
  gap: var(--app-space-4);
  margin-bottom: var(--app-space-4);
  padding: var(--app-space-3);
  border-radius: 8px;
  background: var(--app-surface-muted);
}

.virtual-description-editor__label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.virtual-description-editor__hint {
  margin: var(--app-space-2) 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
  line-height: 1.6;
}

.transform-rule-editor__script {
  margin-bottom: var(--app-space-4);
  padding: var(--app-space-4);
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: var(--app-surface-muted);
}

.transform-rule-editor__script header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--app-space-3);
  margin-bottom: var(--app-space-3);
}

.transform-rule-editor__script-actions {
  display: flex;
  flex-shrink: 0;
  gap: var(--app-space-2);
}

.transform-rule-editor__script p {
  margin: 3px 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.transform-rule-editor__ai-hint {
  margin: 0 0 var(--app-space-4);
  color: var(--app-text-muted);
  line-height: 1.6;
}

.transform-rule-editor__script :deep(.app-code-editor) {
  min-height: 300px;
}

.transform-rule-editor__ports code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.transform-rule-editor__ports {
  margin-bottom: var(--app-space-4);
  padding: var(--app-space-4);
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: var(--app-surface-muted);
}

.transform-rule-editor__ports header {
  display: flex;
  gap: var(--app-space-4);
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--app-space-3);
}

.transform-rule-editor__ports p {
  margin: 3px 0 0;
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
}

.transform-rule-editor__port {
  display: grid;
  grid-template-columns: 70px minmax(180px, 1fr) max-content 38px;
  gap: var(--app-space-2);
  align-items: center;
  margin-top: var(--app-space-2);
}

.transform-rule-editor__port > * {
  min-width: 0;
}

.transform-rule-editor__port :deep(.el-input),
.transform-rule-editor__port :deep(.el-select),
.transform-rule-editor__port :deep(.el-input-number) {
  width: 100%;
  min-width: 0;
}

.transform-rule-editor__port :deep(.el-checkbox) {
  min-width: 0;
  white-space: nowrap;
}

@media (max-width: 980px) {
  .virtual-model-drawer__summary,
  .virtual-editor-grid--3 {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .transform-rule-editor__port {
    grid-template-columns: 80px minmax(0, 1fr);
  }
}

@media (max-width: 680px) {
  .virtual-model-drawer__summary,
  .virtual-editor-grid,
  .virtual-editor-grid--3,
  .transform-rule-editor__configs {
    grid-template-columns: 1fr;
  }
}
</style>
