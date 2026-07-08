<script setup lang="ts">
import { Delete, EditPen, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AppCodeEditor, AppPagination } from '../../../../components'
import {
  searchAiKbStores,
  searchAiModelManages,
  type AiKbStoreItem,
  type AiModelManageItem,
} from '../../api/aiPlatform'
import {
  createNode,
  createSkill,
  createTool,
  deleteSkill,
  deleteTool,
  deleteNode,
  deleteWorkflow,
  searchSkills,
  searchNodes,
  searchTools,
  searchWorkflows,
  updateNode,
  updateSkill,
  updateTool,
  type AiNodeItem,
  type AiNodeUpsertPayload,
  type AiSkillItem,
  type AiToolItem,
  type AiWorkflowItem,
} from '../../api/workflow'

type WorkflowTab = 'workflow' | 'node' | 'skill' | 'tool'
type WorkflowCard = AiWorkflowItem | AiNodeItem | AiSkillItem | AiToolItem

const route = useRoute()
const router = useRouter()
const validTabs: WorkflowTab[] = ['workflow', 'node', 'skill', 'tool']
const pageSizeOptions = [5, 10, 20, 50, 100, 200, 500]
const tabOptions = [
  { key: 'workflow' as const, label: '流程配置' },
  { key: 'node' as const, label: '节点配置' },
  { key: 'skill' as const, label: 'Skill 管理' },
  { key: 'tool' as const, label: 'Tool 管理' },
]

const activeTab = ref<WorkflowTab>('workflow')
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')

const workflowRows = ref<AiWorkflowItem[]>([])
const nodeRows = ref<AiNodeItem[]>([])
const skillRows = ref<AiSkillItem[]>([])
const toolRows = ref<AiToolItem[]>([])
const modelOptions = ref<AiModelManageItem[]>([])
const kbOptions = ref<AiKbStoreItem[]>([])

const nodeDialogVisible = ref(false)
const nodeDialogMode = ref<'create' | 'edit'>('create')
const editingNodeId = ref<string | number | null>(null)
const nodeSchemaEditorFormat = ref<'json' | 'python' | 'javascript' | 'markdown' | 'asciidoc' | 'text'>('json')
const nodeForm = reactive({
  code: '',
  name: '',
  desc: '',
  executeType: 1 as number,
  modelCode: '',
  skillRefs: [] as string[],
  toolRefs: [] as string[],
  kbRefs: [] as string[],
  inputConfig: [
    {
      role: 'system',
      content: '',
    },
  ] as Array<{ role: string, content: string }>,
  outputType: '',
  storeAs: '',
  schemaText: '',
  enabled: true,
  remark: '',
})

const skillDialogVisible = ref(false)
const skillDialogMode = ref<'create' | 'edit'>('create')
const editingSkillId = ref<string | number | null>(null)
const skillForm = reactive({
  code: '',
  name: '',
  desc: '',
  content: '',
  toolRefs: [] as string[],
  enabled: true,
  remark: '',
})

const toolDialogVisible = ref(false)
const toolDialogMode = ref<'create' | 'edit'>('create')
const editingToolId = ref<string | number | null>(null)
const toolForm = reactive({
  code: '',
  name: '',
  desc: '',
  content: '',
  runtimeType: 'PYTHON',
  enabled: true,
  remark: '',
})

const toolEditorFormat = computed<'python' | 'javascript'>({
  get: () => toolForm.runtimeType === 'JAVASCRIPT' ? 'javascript' : 'python',
  set: (value) => {
    toolForm.runtimeType = value === 'javascript' ? 'JAVASCRIPT' : 'PYTHON'
  },
})

const currentLabel = computed(() => {
  switch (activeTab.value) {
    case 'workflow':
      return '流程'
    case 'node':
      return '节点'
    case 'skill':
      return 'Skill'
    default:
      return 'Tool'
  }
})

const currentRows = computed<WorkflowCard[]>(() => {
  switch (activeTab.value) {
    case 'workflow':
      return workflowRows.value
    case 'node':
      return nodeRows.value
    case 'skill':
      return skillRows.value
    default:
      return toolRows.value
  }
})

const isSkillTab = computed(() => activeTab.value === 'skill')
const isToolTab = computed(() => activeTab.value === 'tool')
const isNodeTab = computed(() => activeTab.value === 'node')
const nodeDialogTitle = computed(() => `${nodeDialogMode.value === 'create' ? '新增' : '编辑'} 节点`)
const skillDialogTitle = computed(() => `${skillDialogMode.value === 'create' ? '新增' : '编辑'} Skill`)
const toolDialogTitle = computed(() => `${toolDialogMode.value === 'create' ? '新增' : '编辑'} Tool`)

const currentSearchPlaceholder = computed(() => {
  switch (activeTab.value) {
    case 'workflow':
    case 'node':
      return `搜索${currentLabel.value}名称 / 编码 / 类型`
    case 'skill':
      return '搜索 Skill 名称 / 编码 / 说明'
    default:
      return '搜索 Tool 名称 / 编码 / 运行时'
  }
})

const filteredRows = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  const source = currentRows.value
  if (!normalized) {
    return source
  }
  return source.filter((item) => {
    if (activeTab.value === 'node') {
      const node = item as AiNodeItem
      return [node.name, node.code, node.desc, formatExecuteType(node.executeType), node.modelCode]
        .some(value => String(value || '').toLowerCase().includes(normalized))
    }
    if (activeTab.value === 'skill') {
      const skill = item as AiSkillItem
      return [skill.name, skill.code, skill.desc, skill.content].some(value => String(value || '').toLowerCase().includes(normalized))
    }
    if (activeTab.value === 'tool') {
      const tool = item as AiToolItem
      return [tool.name, tool.code, tool.runtimeType, tool.desc].some(value => String(value || '').toLowerCase().includes(normalized))
    }
    return [item.name, item.code, item.type].some(value => String(value || '').toLowerCase().includes(normalized))
  })
})

function resolveTotal(payloadTotal?: number, fallback = 0) {
  const numericTotal = Number(payloadTotal)
  return Number.isFinite(numericTotal) ? numericTotal : fallback
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

function formatConfigPreview(value?: Record<string, unknown> | null) {
  if (!value || Object.keys(value).length === 0) {
    return '暂无配置'
  }
  const text = JSON.stringify(value)
  return text.length > 96 ? `${text.slice(0, 96)}...` : text
}

function formatExecuteType(value?: number | string) {
  if (value === 1 || value === '1' || value === 'CHAT') {
    return 'CHAT'
  }
  if (value === 2 || value === '2' || value === 'AGENT') {
    return 'AGENT'
  }
  return String(value || '-')
}

function formatToolSyncStatus(value?: number | string) {
  if (value === 1 || value === '1') {
    return '待同步'
  }
  if (value === 2 || value === '2') {
    return '同步成功'
  }
  if (value === 3 || value === '3') {
    return '同步失败'
  }
  return String(value || '-')
}

function normalizeTab(value: unknown): WorkflowTab {
  const raw = Array.isArray(value) ? value[0] : value
  return validTabs.includes(raw as WorkflowTab) ? raw as WorkflowTab : 'workflow'
}

function resetSkillForm() {
  skillForm.code = ''
  skillForm.name = ''
  skillForm.desc = ''
  skillForm.content = ''
  skillForm.toolRefs = []
  skillForm.enabled = true
  skillForm.remark = ''
  editingSkillId.value = null
}

function resetToolForm() {
  toolForm.code = ''
  toolForm.name = ''
  toolForm.desc = ''
  toolForm.content = ''
  toolForm.runtimeType = 'PYTHON'
  toolForm.enabled = true
  toolForm.remark = ''
  editingToolId.value = null
}

function resetNodeForm() {
  nodeForm.code = ''
  nodeForm.name = ''
  nodeForm.desc = ''
  nodeForm.executeType = 1
  nodeForm.modelCode = ''
  nodeForm.skillRefs = []
  nodeForm.toolRefs = []
  nodeForm.kbRefs = []
  nodeForm.inputConfig = [
    {
      role: 'system',
      content: '',
    },
  ]
  nodeForm.outputType = ''
  nodeForm.storeAs = ''
  nodeForm.schemaText = ''
  nodeSchemaEditorFormat.value = 'json'
  nodeForm.enabled = true
  nodeForm.remark = ''
  editingNodeId.value = null
}

function resolveSchemaEditorFormat(outputType?: string | null) {
  const normalized = String(outputType || '').trim().toLowerCase()
  if (normalized === 'markdown') {
    return 'markdown'
  }
  if (normalized === 'javascript') {
    return 'javascript'
  }
  if (normalized === 'python') {
    return 'python'
  }
  if (normalized === 'asciidoc') {
    return 'asciidoc'
  }
  if (normalized === 'text') {
    return 'text'
  }
  return 'json'
}

async function ensureToolOptions() {
  if (toolRows.value.length > 0) {
    return
  }
  const payload = await searchTools({ page: 1, size: 200 })
  toolRows.value = payload?.list ?? []
}

async function ensureNodeOptions() {
  const tasks: Promise<unknown>[] = []

  if (skillRows.value.length <= 0) {
    tasks.push(
      searchSkills({ page: 1, size: 200 }).then((payload) => {
        skillRows.value = payload?.list ?? []
      }),
    )
  }

  if (toolRows.value.length <= 0) {
    tasks.push(
      searchTools({ page: 1, size: 200 }).then((payload) => {
        toolRows.value = payload?.list ?? []
      }),
    )
  }

  if (modelOptions.value.length <= 0) {
    tasks.push(
      searchAiModelManages({ page: 1, size: 200 }).then((payload) => {
        modelOptions.value = payload?.list ?? []
      }),
    )
  }

  if (kbOptions.value.length <= 0) {
    tasks.push(
      searchAiKbStores({ page: 1, size: 200 }).then((payload) => {
        kbOptions.value = payload?.list ?? []
      }),
    )
  }

  if (tasks.length > 0) {
    await Promise.all(tasks)
  }
}

async function loadData() {
  loading.value = true
  errorMessage.value = ''
  try {
    if (activeTab.value === 'workflow') {
      const payload = await searchWorkflows({
        page: currentPage.value,
        size: pageSize.value,
      })
      workflowRows.value = payload?.list ?? []
      total.value = resolveTotal(payload?.pageInfo?.total, workflowRows.value.length)
      return
    }

    if (activeTab.value === 'node') {
      const payload = await searchNodes({
        page: currentPage.value,
        size: pageSize.value,
      })
      nodeRows.value = payload?.list ?? []
      total.value = resolveTotal(payload?.pageInfo?.total, nodeRows.value.length)
      return
    }

    if (activeTab.value === 'skill') {
      const payload = await searchSkills({
        page: currentPage.value,
        size: pageSize.value,
        name: keyword.value.trim() || undefined,
      })
      skillRows.value = payload?.list ?? []
      total.value = resolveTotal(payload?.pageInfo?.total, skillRows.value.length)
      return
    }

    const payload = await searchTools({
      page: currentPage.value,
      size: pageSize.value,
    })
    toolRows.value = payload?.list ?? []
    total.value = resolveTotal(payload?.pageInfo?.total, toolRows.value.length)
  }
  catch (error) {
    total.value = 0
    errorMessage.value = error instanceof Error ? error.message : `${currentLabel.value}列表加载失败`
  }
  finally {
    loading.value = false
  }
}

async function handleRefresh() {
  await loadData()
}

async function handleSearch() {
  currentPage.value = 1
  await loadData()
}

async function handleChangeTab(tab: WorkflowTab) {
  if (activeTab.value === tab && route.query.tab === tab) {
    return
  }
  await router.replace({
    query: {
      ...route.query,
      tab,
    },
  })
}

async function handleCurrentPageChange(page: number) {
  currentPage.value = page
  await loadData()
}

async function handlePageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  await loadData()
}

async function handleDelete(row: WorkflowCard) {
  const label = activeTab.value === 'workflow'
    ? '流程'
    : activeTab.value === 'node'
      ? '节点'
      : activeTab.value === 'skill'
        ? 'Skill'
        : 'Tool'
  try {
    await ElMessageBox.confirm(
      `确认删除${label}「${row.name || row.code || '-'}」吗？删除后不可恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )

    if (activeTab.value === 'workflow') {
      await deleteWorkflow(row.id)
    } else if (activeTab.value === 'node') {
      await deleteNode(row.id)
    } else if (activeTab.value === 'skill') {
      await deleteSkill(row.id)
    } else {
      await deleteTool(row.id)
    }

    if (currentRows.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    ElMessage.success(`${label}删除成功`)
    await loadData()
  }
  catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : `${label}删除失败`)
  }
}

function addInputMessage() {
  nodeForm.inputConfig.push({
    role: 'user',
    content: '',
  })
}

function removeInputMessage(index: number) {
  if (nodeForm.inputConfig.length <= 1) {
    nodeForm.inputConfig[0] = { role: 'system', content: '' }
    return
  }
  nodeForm.inputConfig.splice(index, 1)
}

async function openCreateNodeDialog() {
  nodeDialogMode.value = 'create'
  resetNodeForm()
  await ensureNodeOptions()
  nodeDialogVisible.value = true
}

async function openEditNodeDialog(node: AiNodeItem) {
  nodeDialogMode.value = 'edit'
  editingNodeId.value = node.id
  nodeForm.code = node.code || ''
  nodeForm.name = node.name || ''
  nodeForm.desc = node.desc || ''
  nodeForm.executeType = Number(node.executeType || 1)
  nodeForm.modelCode = node.modelCode || ''
  nodeForm.skillRefs = [...(node.skillRefs || [])]
  nodeForm.toolRefs = [...(node.toolRefs || [])]
  nodeForm.kbRefs = [...(node.kbRefs || [])]
  nodeForm.inputConfig = (node.inputConfig || []).length > 0
    ? (node.inputConfig || []).map(item => ({
        role: item.role || 'system',
        content: item.content || '',
      }))
    : [{ role: 'system', content: '' }]
  nodeForm.outputType = node.outputConfig?.outputType || ''
  nodeForm.storeAs = node.outputConfig?.storeAs || ''
  nodeForm.schemaText = node.outputConfig?.schema ? JSON.stringify(node.outputConfig.schema, null, 2) : ''
  nodeSchemaEditorFormat.value = resolveSchemaEditorFormat(node.outputConfig?.outputType)
  nodeForm.enabled = node.enabled !== false
  nodeForm.remark = node.remark || ''
  await ensureNodeOptions()
  nodeDialogVisible.value = true
}

function buildNodePayload(): AiNodeUpsertPayload | null {
  let schema: Record<string, unknown> | null = null
  const schemaText = nodeForm.schemaText.trim()
  const isJsonOutput = nodeSchemaEditorFormat.value === 'json'

  if (schemaText && isJsonOutput) {
    try {
      schema = JSON.parse(schemaText)
    }
    catch {
      ElMessage.error('输出 Schema 必须是合法 JSON')
      return null
    }
  }

  const resolvedOutputType = isJsonOutput
    ? (schema ? 'json_schema' : 'json')
    : nodeSchemaEditorFormat.value

  return {
    code: nodeForm.code.trim(),
    name: nodeForm.name.trim(),
    desc: nodeForm.desc.trim(),
    executeType: Number(nodeForm.executeType || 1),
    modelCode: nodeForm.modelCode.trim() || undefined,
    skillRefs: nodeForm.skillRefs.filter(Boolean),
    toolRefs: nodeForm.toolRefs.filter(Boolean),
    kbRefs: nodeForm.kbRefs.filter(Boolean),
    inputConfig: nodeForm.inputConfig
      .map(item => ({
        role: item.role.trim(),
        content: item.content,
      }))
      .filter(item => item.role && item.content.trim()),
    outputConfig: (resolvedOutputType || nodeForm.storeAs.trim() || schema)
      ? {
          outputType: resolvedOutputType,
          storeAs: nodeForm.storeAs.trim() || undefined,
          schema,
        }
      : null,
    enabled: nodeForm.enabled,
    remark: nodeForm.remark.trim(),
  }
}

async function handleSubmitNode() {
  if (!nodeForm.code.trim()) {
    ElMessage.error('请输入节点编码')
    return
  }
  if (!nodeForm.name.trim()) {
    ElMessage.error('请输入节点名称')
    return
  }

  const payload = buildNodePayload()
  if (!payload) {
    return
  }

  saving.value = true
  try {
    if (nodeDialogMode.value === 'create') {
      await createNode(payload)
    } else if (editingNodeId.value !== null) {
      await updateNode(editingNodeId.value, payload)
    }
    nodeDialogVisible.value = false
    ElMessage.success(`节点${nodeDialogMode.value === 'create' ? '新增' : '更新'}成功`)
    if (nodeDialogMode.value === 'create') {
      currentPage.value = 1
    }
    await loadData()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `节点${nodeDialogMode.value === 'create' ? '新增' : '更新'}失败`)
  }
  finally {
    saving.value = false
  }
}

async function openCreateSkillDialog() {
  skillDialogMode.value = 'create'
  resetSkillForm()
  await ensureToolOptions()
  skillDialogVisible.value = true
}

async function openEditSkillDialog(skill: AiSkillItem) {
  skillDialogMode.value = 'edit'
  editingSkillId.value = skill.id
  skillForm.code = skill.code || ''
  skillForm.name = skill.name || ''
  skillForm.desc = skill.desc || ''
  skillForm.content = skill.content || ''
  skillForm.toolRefs = [...(skill.toolRefs || [])]
  skillForm.enabled = skill.enabled !== false
  skillForm.remark = skill.remark || ''
  await ensureToolOptions()
  skillDialogVisible.value = true
}

async function handleSubmitSkill() {
  if (!skillForm.code.trim()) {
    ElMessage.error('请输入 Skill 编码')
    return
  }
  if (!skillForm.name.trim()) {
    ElMessage.error('请输入 Skill 名称')
    return
  }
  if (!skillForm.content.trim()) {
    ElMessage.error('请输入 Skill 内容')
    return
  }

  saving.value = true
  try {
    const payload = {
      code: skillForm.code.trim(),
      name: skillForm.name.trim(),
      desc: skillForm.desc.trim(),
      content: skillForm.content,
      toolRefs: skillForm.toolRefs.filter(Boolean),
      enabled: skillForm.enabled,
      remark: skillForm.remark.trim(),
    }
    if (skillDialogMode.value === 'create') {
      await createSkill(payload)
    } else if (editingSkillId.value !== null) {
      await updateSkill(editingSkillId.value, payload)
    }
    skillDialogVisible.value = false
    ElMessage.success(`Skill ${skillDialogMode.value === 'create' ? '新增' : '更新'}成功`)
    if (skillDialogMode.value === 'create') {
      currentPage.value = 1
    }
    await loadData()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `Skill ${skillDialogMode.value === 'create' ? '新增' : '更新'}失败`)
  }
  finally {
    saving.value = false
  }
}

function openCreateToolDialog() {
  toolDialogMode.value = 'create'
  resetToolForm()
  toolDialogVisible.value = true
}

function openEditToolDialog(tool: AiToolItem) {
  toolDialogMode.value = 'edit'
  editingToolId.value = tool.id
  toolForm.code = tool.code || ''
  toolForm.name = tool.name || ''
  toolForm.desc = tool.desc || ''
  toolForm.content = tool.content || ''
  toolForm.runtimeType = tool.runtimeType || 'PYTHON'
  toolForm.enabled = tool.enabled !== false
  toolForm.remark = tool.remark || ''
  toolDialogVisible.value = true
}

async function handleSubmitTool() {
  if (!toolForm.code.trim()) {
    ElMessage.error('请输入 Tool 编码')
    return
  }
  if (!toolForm.name.trim()) {
    ElMessage.error('请输入 Tool 名称')
    return
  }
  if (!toolForm.content.trim()) {
    ElMessage.error('请输入 Tool 脚本内容')
    return
  }

  saving.value = true
  try {
    const payload = {
      code: toolForm.code.trim(),
      name: toolForm.name.trim(),
      desc: toolForm.desc.trim(),
      content: toolForm.content,
      runtimeType: toolForm.runtimeType,
      syncStatus: 1,
      enabled: toolForm.enabled,
      remark: toolForm.remark.trim(),
    }

    if (toolDialogMode.value === 'create') {
      await createTool(payload)
    } else if (editingToolId.value !== null) {
      await updateTool(editingToolId.value, payload)
    }

    toolDialogVisible.value = false
    ElMessage.success(`Tool ${toolDialogMode.value === 'create' ? '新增' : '更新'}成功`)
    if (toolDialogMode.value === 'create') {
      currentPage.value = 1
    }
    await loadData()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `Tool ${toolDialogMode.value === 'create' ? '新增' : '更新'}失败`)
  }
  finally {
    saving.value = false
  }
}

watch(
  () => route.query.tab,
  (value) => {
    const nextTab = normalizeTab(value)
    const rawTab = Array.isArray(value) ? value[0] : value

    if (rawTab !== nextTab) {
      void router.replace({
        query: {
          ...route.query,
          tab: nextTab,
        },
      })
      return
    }

    activeTab.value = nextTab
    keyword.value = ''
    currentPage.value = 1
    void loadData()
  },
  { immediate: true },
)
</script>

<template>
  <section class="workflow-page">
    <div class="workflow-shell">
      <header class="workflow-shell__header">
        <div class="workflow-shell__tabs">
          <el-tag
            v-for="tab in tabOptions"
            :key="tab.key"
            :type="activeTab === tab.key ? 'primary' : 'info'"
            effect="plain"
            class="workflow-shell__tab"
            @click="handleChangeTab(tab.key)"
          >
            {{ tab.label }}
          </el-tag>
        </div>
        <div class="workflow-shell__tools">
          <el-input
            v-model="keyword"
            :placeholder="currentSearchPlaceholder"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button plain :loading="loading" @click="handleRefresh">
            <el-icon><RefreshRight /></el-icon>
            刷新
          </el-button>
          <el-button v-if="isNodeTab" type="primary" @click="openCreateNodeDialog">
            <el-icon><Plus /></el-icon>
            新增节点
          </el-button>
          <el-button v-if="isSkillTab" type="primary" @click="openCreateSkillDialog">
            <el-icon><Plus /></el-icon>
            新增 Skill
          </el-button>
          <el-button v-if="isToolTab" type="primary" @click="openCreateToolDialog">
            <el-icon><Plus /></el-icon>
            新增 Tool
          </el-button>
        </div>
      </header>

      <main class="workflow-shell__main">
        <div v-if="errorMessage" class="workflow-shell__state workflow-shell__state--error">
          {{ errorMessage }}
        </div>
        <div v-else-if="loading" class="workflow-shell__state">
          正在加载{{ currentLabel }}列表...
        </div>
        <div v-else-if="!filteredRows.length" class="workflow-shell__state">
          当前没有{{ currentLabel }}数据
        </div>
        <div v-else class="workflow-grid">
          <article v-for="row in filteredRows" :key="row.id" class="workflow-card">
            <div class="workflow-card__head">
              <div>
                <h3>{{ row.name || row.code || `未命名${currentLabel}` }}</h3>
                <p>{{ row.code || '-' }}</p>
              </div>
              <div class="workflow-card__tags">
                <el-tag size="small" effect="plain">
                  {{
                    activeTab === 'tool'
                      ? ((row as AiToolItem).runtimeType || '未配置运行时')
                      : activeTab === 'node'
                        ? formatExecuteType((row as AiNodeItem).executeType)
                      : activeTab === 'skill'
                        ? `Tool x${((row as AiSkillItem).toolRefs || []).length}`
                        : (row.type || '未配置类型')
                  }}
                </el-tag>
                <el-tag size="small" effect="plain" :type="row.enabled === false ? 'info' : 'success'">
                  {{ row.enabled === false ? '停用' : '启用' }}
                </el-tag>
              </div>
            </div>
            <div class="workflow-card__summary">
              {{
                activeTab === 'skill'
                  ? ((row as AiSkillItem).desc || (row as AiSkillItem).content || '暂无 Skill 摘要')
                  : activeTab === 'tool'
                    ? ((row as AiToolItem).desc || '暂无 Tool 说明')
                    : activeTab === 'node'
                      ? ((row as AiNodeItem).desc || '暂无节点说明')
                      : formatConfigPreview((row as AiWorkflowItem).config)
              }}
            </div>
            <div class="workflow-card__meta">
              <div class="workflow-card__meta-item">
                <span>{{ activeTab === 'tool' ? '运行时' : activeTab === 'skill' ? '引用工具' : '类型' }}</span>
                <strong>
                  {{
                    activeTab === 'tool'
                      ? ((row as AiToolItem).runtimeType || '-')
                      : activeTab === 'node'
                        ? formatExecuteType((row as AiNodeItem).executeType)
                      : activeTab === 'skill'
                        ? (((row as AiSkillItem).toolRefs || []).join(', ') || '-')
                        : (row.type || '-')
                  }}
                </strong>
              </div>
              <div v-if="activeTab === 'node'" class="workflow-card__meta-item">
                <span>模型</span>
                <strong>{{ ((row as AiNodeItem).modelCode || '-') }}</strong>
              </div>
              <div v-if="activeTab === 'node'" class="workflow-card__meta-item">
                <span>能力引用</span>
                <strong>
                  {{
                    `Skill ${((row as AiNodeItem).skillRefs || []).length} / Tool ${((row as AiNodeItem).toolRefs || []).length} / KB ${((row as AiNodeItem).kbRefs || []).length}`
                  }}
                </strong>
              </div>
              <div v-if="activeTab === 'skill'" class="workflow-card__meta-item">
                <span>说明</span>
                <strong>{{ ((row as AiSkillItem).desc || '-') }}</strong>
              </div>
              <div v-if="activeTab === 'tool'" class="workflow-card__meta-item">
                <span>同步状态</span>
                <strong>{{ formatToolSyncStatus((row as AiToolItem).syncStatus) }}</strong>
              </div>
              <div class="workflow-card__meta-item">
                <span>更新时间</span>
                <strong>{{ formatDateTime(row.updateTime || row.createTime) }}</strong>
              </div>
            </div>
            <div v-if="activeTab !== 'workflow' && activeTab !== 'node' ? true : true" class="workflow-card__actions">
              <el-button
                v-if="activeTab === 'node'"
                plain
                circle
                title="编辑"
                @click="openEditNodeDialog(row as AiNodeItem)"
              >
                <el-icon><EditPen /></el-icon>
              </el-button>
              <el-button
                v-if="activeTab === 'skill'"
                plain
                circle
                title="编辑"
                @click="openEditSkillDialog(row as AiSkillItem)"
              >
                <el-icon><EditPen /></el-icon>
              </el-button>
              <el-button
                v-if="activeTab === 'tool'"
                plain
                circle
                title="编辑"
                @click="openEditToolDialog(row as AiToolItem)"
              >
                <el-icon><EditPen /></el-icon>
              </el-button>
              <el-button plain circle type="danger" title="删除" @click="handleDelete(row)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </article>
        </div>
      </main>

      <footer class="workflow-shell__footer">
        <AppPagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="pageSizeOptions"
          :total="total"
          :pager-count="5"
          @current-change="handleCurrentPageChange"
          @size-change="handlePageSizeChange"
        />
      </footer>
    </div>

    <el-dialog
      v-model="nodeDialogVisible"
      :title="nodeDialogTitle"
      width="880px"
      class="workflow-tool-dialog"
      destroy-on-close
    >
      <div class="workflow-dialog-scroll">
        <el-form label-width="110px" class="workflow-dialog-form">
          <el-form-item label="节点编码" required>
            <el-input v-model="nodeForm.code" :disabled="nodeDialogMode === 'edit'" placeholder="例如：render_agent_node" />
          </el-form-item>
          <el-form-item label="节点名称" required>
            <el-input v-model="nodeForm.name" placeholder="请输入节点名称" />
          </el-form-item>
          <el-form-item label="执行类型" required>
            <el-select v-model="nodeForm.executeType" class="workflow-dialog-form__select">
              <el-option label="CHAT" :value="1" />
              <el-option label="AGENT" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item label="模型编码">
            <el-select
              v-model="nodeForm.modelCode"
              class="workflow-dialog-form__select"
              filterable
              clearable
              placeholder="不填写则由运行时决定"
            >
              <el-option
                v-for="model in modelOptions"
                :key="model.modelCode || String(model.id)"
                :label="`${model.modelName || model.modelCode} (${model.modelCode || '-'})`"
                :value="model.modelCode || ''"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="节点说明">
            <el-input v-model="nodeForm.desc" type="textarea" :rows="3" placeholder="请输入节点说明" />
          </el-form-item>
          <el-form-item label="关联 Skill">
            <el-select
              v-model="nodeForm.skillRefs"
              class="workflow-dialog-form__select"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              placeholder="请选择 Skill"
            >
              <el-option
                v-for="skill in skillRows"
                :key="skill.code || String(skill.id)"
                :label="`${skill.name || skill.code} (${skill.code || '-'})`"
                :value="skill.code || ''"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="关联 Tool">
            <el-select
              v-model="nodeForm.toolRefs"
              class="workflow-dialog-form__select"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              placeholder="请选择 Tool"
            >
              <el-option
                v-for="tool in toolRows"
                :key="tool.code || String(tool.id)"
                :label="`${tool.name || tool.code} (${tool.code || '-'})`"
                :value="tool.code || ''"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="关联知识库">
            <el-select
              v-model="nodeForm.kbRefs"
              class="workflow-dialog-form__select"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              placeholder="请选择知识库"
            >
              <el-option
                v-for="kb in kbOptions"
                :key="kb.kbCode || String(kb.id)"
                :label="`${kb.kbName || kb.kbCode} (${kb.kbCode || '-'})`"
                :value="kb.kbCode || ''"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="输入配置">
            <div class="workflow-message-list">
              <div v-for="(message, index) in nodeForm.inputConfig" :key="index" class="workflow-message-item">
                <el-select v-model="message.role" class="workflow-message-item__role">
                  <el-option label="system" value="system" />
                  <el-option label="user" value="user" />
                  <el-option label="assistant" value="assistant" />
                </el-select>
                <el-input
                  v-model="message.content"
                  class="workflow-message-item__content"
                  type="textarea"
                  :rows="3"
                  placeholder="请输入该角色对应的提示内容"
                />
                <el-button plain circle type="danger" title="删除输入项" @click="removeInputMessage(index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <el-button plain @click="addInputMessage">
                <el-icon><Plus /></el-icon>
                新增输入项
              </el-button>
            </div>
          </el-form-item>
          <el-form-item label="结果变量">
            <el-input v-model="nodeForm.storeAs" placeholder="例如：render_result" />
          </el-form-item>
          <el-form-item label="输出 Schema">
            <AppCodeEditor
              v-model="nodeForm.schemaText"
              v-model:format="nodeSchemaEditorFormat"
              min-height="220px"
              :max-rows="12"
              placeholder="请输入输出约束；JSON 类型下会按 JSON 语法校验"
            />
          </el-form-item>
          <el-form-item label="启用状态">
            <el-switch v-model="nodeForm.enabled" inline-prompt active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="nodeForm.remark" type="textarea" :rows="2" placeholder="可选备注" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="workflow-dialog-form__footer">
          <el-button @click="nodeDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmitNode">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="skillDialogVisible"
      :title="skillDialogTitle"
      width="760px"
      class="workflow-tool-dialog"
      destroy-on-close
    >
      <div class="workflow-dialog-scroll">
        <el-form label-width="110px" class="workflow-dialog-form">
          <el-form-item label="Skill 编码" required>
            <el-input v-model="skillForm.code" :disabled="skillDialogMode === 'edit'" placeholder="例如：render_generate_review" />
          </el-form-item>
          <el-form-item label="Skill 名称" required>
            <el-input v-model="skillForm.name" placeholder="请输入 Skill 名称" />
          </el-form-item>
          <el-form-item label="Skill 说明">
            <el-input v-model="skillForm.desc" type="textarea" :rows="3" placeholder="请输入 Skill 简要说明" />
          </el-form-item>
          <el-form-item label="关联 Tool">
            <el-select
              v-model="skillForm.toolRefs"
              class="workflow-dialog-form__select"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              placeholder="请选择关联 Tool"
            >
              <el-option
                v-for="tool in toolRows"
                :key="tool.code || String(tool.id)"
                :label="`${tool.name || tool.code} (${tool.code || '-'})`"
                :value="tool.code || ''"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="规则内容" required>
            <AppCodeEditor
              v-model="skillForm.content"
              format="markdown"
              min-height="360px"
              :max-rows="20"
              placeholder="请输入 Skill Markdown 规则内容"
            />
          </el-form-item>
          <el-form-item label="启用状态">
            <el-switch v-model="skillForm.enabled" inline-prompt active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="skillForm.remark" type="textarea" :rows="2" placeholder="可选备注" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="workflow-dialog-form__footer">
          <el-button @click="skillDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmitSkill">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="toolDialogVisible"
      :title="toolDialogTitle"
      width="760px"
      class="workflow-tool-dialog"
      destroy-on-close
    >
      <div class="workflow-dialog-scroll">
        <el-form label-width="110px" class="workflow-dialog-form">
          <el-form-item label="Tool 编码" required>
            <el-input v-model="toolForm.code" :disabled="toolDialogMode === 'edit'" placeholder="例如：knowledge_base_search" />
          </el-form-item>
          <el-form-item label="Tool 名称" required>
            <el-input v-model="toolForm.name" placeholder="例如：knowledge_base_search_tool" />
          </el-form-item>
          <el-form-item label="运行时" required>
            <el-select v-model="toolForm.runtimeType" class="workflow-dialog-form__select">
              <el-option label="PYTHON" value="PYTHON" />
              <el-option label="JAVASCRIPT" value="JAVASCRIPT" />
            </el-select>
          </el-form-item>
          <el-form-item label="Tool 说明">
            <el-input v-model="toolForm.desc" type="textarea" :rows="3" placeholder="请输入 Tool 说明" />
          </el-form-item>
          <el-form-item label="脚本内容" required>
            <AppCodeEditor
              v-model="toolForm.content"
              v-model:format="toolEditorFormat"
              :show-format-switcher="false"
              min-height="360px"
              :max-rows="20"
              placeholder="请输入完整脚本内容"
            />
          </el-form-item>
          <el-form-item label="启用状态">
            <el-switch v-model="toolForm.enabled" inline-prompt active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="toolForm.remark" type="textarea" :rows="2" placeholder="可选备注" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <div class="workflow-dialog-form__footer">
          <el-button @click="toolDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmitTool">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.workflow-page {
  display: flex;
  flex: 1;
  min-height: 0;
}

.workflow-shell {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  flex: 1;
  min-height: 0;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
  box-shadow: var(--system-shadow);
  overflow: hidden;
}

.workflow-shell__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--system-border-subtle);
}

.workflow-shell__tabs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-shell__tab {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text-soft);
  cursor: pointer;
}

.workflow-shell__tab.el-tag--primary {
  border-color: var(--system-accent-border);
  background: var(--system-accent-bg-strong);
  color: var(--system-accent-text);
}

.workflow-shell__tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-shell__tools :deep(.el-input) {
  width: 260px;
}

.workflow-shell__tools :deep(.el-input__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
}

.workflow-shell__tools :deep(.el-input__inner),
.workflow-shell__tools :deep(.el-input__prefix-inner) {
  color: var(--system-text);
}

.workflow-shell__tools :deep(.el-input__inner::placeholder) {
  color: var(--system-text-faint);
}

.workflow-shell__tools :deep(.el-button) {
  border-radius: 10px;
}

.workflow-shell__tools :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.workflow-shell__tools :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-primary-button-bg);
  color: var(--system-primary-button-text);
}

.workflow-shell__main {
  min-height: 0;
  padding: 12px;
  background: var(--system-surface-muted);
  overflow-y: auto;
}

.workflow-shell__state {
  display: grid;
  place-items: center;
  min-height: 260px;
  color: var(--system-text-muted);
  font-size: 13px;
}

.workflow-shell__state--error {
  color: var(--system-danger);
}

.workflow-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 10px;
}

.workflow-card {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--system-border);
  border-radius: 12px;
  background: var(--system-surface-solid);
}

.workflow-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.workflow-card__head h3 {
  margin: 0;
  color: var(--system-title);
  font-size: 14px;
  line-height: 1.3;
}

.workflow-card__head p {
  margin: 2px 0 0;
  color: var(--system-text-soft);
  font-size: 11px;
}

.workflow-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
}

.workflow-card__tags :deep(.el-tag) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text-soft);
}

.workflow-card__tags :deep(.el-tag.el-tag--success),
.workflow-card__tags :deep(.el-tag.el-tag--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-accent-bg);
  color: var(--system-accent-text);
}

.workflow-card__tags :deep(.el-tag.el-tag--info) {
  border-color: var(--system-border);
  background: rgba(148, 163, 184, 0.12);
  color: var(--system-text-faint);
}

.workflow-card__summary {
  color: var(--system-text);
  font-size: 12px;
  line-height: 1.45;
  word-break: break-all;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.workflow-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 10px;
}

.workflow-card__meta-item {
  display: grid;
  gap: 2px;
}

.workflow-card__meta-item span {
  color: var(--system-text-faint);
  font-size: 11px;
}

.workflow-card__meta-item strong {
  color: var(--system-title);
  font-size: 11px;
  line-height: 1.35;
  word-break: break-all;
}

.workflow-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.workflow-card__actions :deep(.el-button.is-circle) {
  padding: 7px;
}

.workflow-shell__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 40px;
  padding: 0 14px;
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-solid);
}

.workflow-page :deep(.el-overlay-dialog) {
  display: flex;
  align-items: center;
  justify-content: center;
}

.workflow-page :deep(.el-dialog) {
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
}

.workflow-page :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.workflow-page :deep(.el-dialog__body) {
  background: var(--system-surface-strong);
}

.workflow-dialog-form__select {
  width: 100%;
}

.workflow-dialog-form :deep(.el-form-item__label) {
  color: var(--system-text-soft);
}

.workflow-dialog-form :deep(.el-input__wrapper),
.workflow-dialog-form :deep(.el-textarea__inner),
.workflow-dialog-form :deep(.el-select__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
  color: var(--system-text);
}

.workflow-dialog-form :deep(.el-switch__core) {
  background: var(--system-surface-muted);
  border-color: var(--system-border);
}

.workflow-dialog-form :deep(.el-switch.is-checked .el-switch__core) {
  background: var(--system-accent-text);
  border-color: var(--system-accent-text);
}

.workflow-message-list {
  display: grid;
  gap: 10px;
  width: 100%;
}

.workflow-message-item {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
}

.workflow-message-item__role {
  width: 100%;
}

.workflow-message-item__content {
  min-width: 0;
}

.workflow-dialog-scroll {
  max-height: min(68vh, 760px);
  overflow-y: auto;
  padding-right: 4px;
}

.workflow-dialog-form__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.workflow-dialog-form__footer :deep(.el-button) {
  min-width: 76px;
  border-radius: 10px;
}

.workflow-dialog-form__footer :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.workflow-dialog-form__footer :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-primary-button-bg);
  color: var(--system-primary-button-text);
}

.workflow-tool-dialog :deep(.el-dialog) {
  display: flex;
  flex-direction: column;
  max-height: 86vh;
  overflow: hidden;
}

.workflow-tool-dialog :deep(.el-dialog__body) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding-top: 8px;
  padding-bottom: 8px;
}

.workflow-tool-dialog :deep(.el-dialog__footer) {
  flex-shrink: 0;
  padding-top: 12px;
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-solid);
}

@media (max-width: 960px) {
  .workflow-shell__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .workflow-shell__tools {
    width: 100%;
    flex-wrap: wrap;
  }

  .workflow-shell__tools :deep(.el-input) {
    width: 100%;
  }

  .workflow-card__meta {
    grid-template-columns: 1fr;
  }

  .workflow-message-item {
    grid-template-columns: 1fr;
  }

  .workflow-shell__footer {
    height: auto;
    padding: 10px 12px;
    flex-wrap: wrap;
  }
}
</style>
