<script setup lang="ts">
import { ChatDotRound, Delete, EditPen, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { AppPagination } from '../../../../components'
import {
  createAiKbStore,
  createAiModelManage,
  deleteAiKbStore,
  deleteAiModelManage,
  editAiKbStore,
  editAiModelManage,
  searchAiKbStores,
  searchAiModelManages,
  testAiModelChat,
  updateAiKbStore,
  updateAiModelManage,
  type AiKbStoreItem,
  type AiKbStoreUpsertPayload,
  type AiModelManageItem,
  type AiModelTestChatMessage,
  type AiModelManageUpsertPayload,
} from '../../api/aiPlatform'

type PlatformTab = 'model' | 'kb'
type DialogMode = 'create' | 'edit'
type TestChatMessage = AiModelTestChatMessage & {
  id: string
  status?: 'success' | 'error'
  durationMs?: number
}
type PlatformCard = {
  id: string | number
  entityType: PlatformTab
  title: string
  code: string
  tags: string[]
  summary: string
  extras: Array<{ label: string, value: string }>
  enabled?: boolean
  raw: AiModelManageItem | AiKbStoreItem
}

const props = defineProps<{
  activeTab: PlatformTab
}>()
const router = useRouter()
const keyword = ref('')
const pageSize = ref(20)
const currentPage = ref(1)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('create')
const editingId = ref<string | number | null>(null)
const statusUpdatingKey = ref('')
const testDialogVisible = ref(false)
const testingChat = ref(false)
const testInput = ref('')
const testContextKey = ref('')
const testMessagesRef = ref<HTMLElement | null>(null)
const testMessages = ref<TestChatMessage[]>([])
const modelRecords = ref<AiModelManageItem[]>([])
const kbRecords = ref<AiKbStoreItem[]>([])

const pageSizeOptions = [5, 10, 20, 50, 100, 200, 500]

const modelForm = reactive({
  modelCode: '',
  modelName: '',
  clientType: 1,
  baseUrl: '',
  apiModel: '',
  apiKey: '',
  enabled: true,
  extJsonText: '',
})

const kbForm = reactive({
  kbCode: '',
  kbName: '',
  description: '',
  embeddingModel: '',
  permission: 'team',
  chunkMethod: 'naive',
  parserConfigText: '',
  parseType: '',
  pipelineId: '',
  tagsText: '',
  enabled: true,
  extJsonText: '',
})

const chatClientTypeOptions = [
  { value: 1, label: '通用 Spring AI 客户端' },
  { value: 2, label: 'AI Agent 客户端' },
]

function resolveChatClientTypeName(clientType?: number) {
  return chatClientTypeOptions.find(item => item.value === clientType)?.label || '未配置客户端类型'
}

const currentTabLabel = computed(() => {
  return props.activeTab === 'model' ? '模型' : '知识库'
})
const currentDialogTitle = computed(() => `${dialogMode.value === 'create' ? '新增' : '编辑'}${currentTabLabel.value}`)
const createButtonLabel = computed(() => `新增${currentTabLabel.value}`)
const currentSearchPlaceholder = computed(() => props.activeTab === 'model'
  ? '搜索名称 / 编码 / 客户端类型'
  : '搜索名称 / 编码 / 知识库客户端')

const currentCards = computed<PlatformCard[]>(() => {
  if (props.activeTab === 'model') {
    return modelRecords.value.map((item) => ({
      id: item.id,
      entityType: 'model',
      title: item.modelName || item.modelCode || '未命名模型',
      code: item.modelCode || '-',
      tags: [
        resolveChatClientTypeName(item.clientType),
        item.enabled === false ? '停用' : '启用',
      ],
      summary: item.apiModel || item.baseUrl || '暂无模型说明',
      extras: [
        { label: '客户端类型', value: resolveChatClientTypeName(item.clientType) },
        { label: 'Base URL', value: item.baseUrl || '-' },
        { label: '密钥', value: item.apiKeyMasked || '未配置' },
        { label: '更新时间', value: formatDateTime(item.updateTime || item.createTime) },
      ],
      enabled: item.enabled !== false,
      raw: item,
    }))
  }

  return kbRecords.value.map((item) => ({
    id: item.id,
    entityType: 'kb',
    title: item.kbName || item.kbCode || '未命名知识库',
    code: item.kbCode || '-',
    tags: [
      item.chunkMethod ? `分片：${item.chunkMethod}` : 'RAGFlow Dataset',
      ...(item.tags || []).slice(0, 3),
      item.enabled === false ? '停用' : '启用',
    ],
    summary: item.description || item.providerKbId || '暂无知识库说明',
      extras: [
        { label: '远端 KB ID', value: item.providerKbId || '-' },
        { label: 'Embedding 模型', value: item.embeddingModel || 'Provider 默认' },
        { label: '权限', value: item.permission || 'team' },
        { label: '认证快照', value: resolveKbAuthSummary(item.auth) },
        { label: '标签数', value: String(item.tags?.length || 0) },
      { label: '更新时间', value: formatDateTime(item.updateTime || item.createTime) },
    ],
    enabled: item.enabled !== false,
    raw: item,
  }))
})

function resetModelForm() {
  modelForm.modelCode = ''
  modelForm.modelName = ''
  modelForm.clientType = 1
  modelForm.baseUrl = ''
  modelForm.apiModel = ''
  modelForm.apiKey = ''
  modelForm.enabled = true
  modelForm.extJsonText = ''
}

function resetKbForm() {
  kbForm.kbCode = ''
  kbForm.kbName = ''
  kbForm.description = ''
  kbForm.embeddingModel = ''
  kbForm.permission = 'team'
  kbForm.chunkMethod = 'naive'
  kbForm.parserConfigText = ''
  kbForm.parseType = ''
  kbForm.pipelineId = ''
  kbForm.tagsText = ''
  kbForm.enabled = true
  kbForm.extJsonText = ''
}

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

function normalizeText(value?: string) {
  return value?.trim() || ''
}

function formatJsonText(value?: Record<string, unknown> | null) {
  if (!value || Object.keys(value).length === 0) {
    return ''
  }
  return JSON.stringify(value, null, 2)
}

function resolveKbAuthSummary(auth?: AiKbStoreItem['auth']) {
  if (!auth?.type) return '未保存'
  if (auth.type === 2) return `阿里云 AK/SK · ${auth.accessKeyIdMasked || '已保存'}`
  return `Bearer Token · ${auth.apiKeyMasked || '已保存'}`
}

function parseJsonText(value: string, label: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  try {
    const parsed = JSON.parse(trimmed)
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      throw new Error(`${label}必须是 JSON 对象`)
    }
    return parsed as Record<string, unknown>
  }
  catch (error) {
    throw new Error(error instanceof Error && error.message ? error.message : `${label}格式不正确`)
  }
}

function buildStatusUpdateKey(card: PlatformCard) {
  return `${card.entityType}:${card.id}`
}

function isCardStatusUpdating(card: PlatformCard) {
  return statusUpdatingKey.value === buildStatusUpdateKey(card)
}

function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  if (props.activeTab === 'model') {
    resetModelForm()
  } else if (props.activeTab === 'kb') {
    resetKbForm()
  }
  dialogVisible.value = true
}

function openEditDialog(card: PlatformCard) {
  dialogMode.value = 'edit'
  editingId.value = card.id

  if (card.entityType === 'model') {
    const item = card.raw as AiModelManageItem
    modelForm.modelCode = item.modelCode || ''
    modelForm.modelName = item.modelName || ''
    modelForm.clientType = item.clientType || 1
    modelForm.baseUrl = item.baseUrl || ''
    modelForm.apiModel = item.apiModel || ''
    modelForm.apiKey = ''
    modelForm.enabled = item.enabled !== false
    modelForm.extJsonText = formatJsonText(item.extJson)
  } else if (card.entityType === 'kb') {
    const item = card.raw as AiKbStoreItem
    kbForm.kbCode = item.kbCode || ''
    kbForm.kbName = item.kbName || ''
    kbForm.description = item.description || ''
    kbForm.embeddingModel = item.embeddingModel || ''
    kbForm.permission = item.permission || 'team'
    kbForm.chunkMethod = item.chunkMethod || ''
    kbForm.parserConfigText = formatJsonText(item.parserConfig)
    kbForm.parseType = item.parseType || ''
    kbForm.pipelineId = item.pipelineId || ''
    kbForm.tagsText = (item.tags || []).join(', ')
    kbForm.enabled = item.enabled !== false
    kbForm.extJsonText = formatJsonText(item.extJson)
  }

  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
}

async function navigateToKbDocuments(card: PlatformCard) {
  if (card.entityType !== 'kb') {
    return
  }
  const item = card.raw as AiKbStoreItem
  const targetKbCode = item.kbCode || card.code
  if (!targetKbCode || targetKbCode === '-') {
    ElMessage.error('缺少知识库编码，无法进入文档管理')
    return
  }
  await router.push(`/settings/system/ai-platform/${targetKbCode}`)
}

function buildModelPayload(): AiModelManageUpsertPayload {
  return {
    modelCode: normalizeText(modelForm.modelCode) || undefined,
    modelName: normalizeText(modelForm.modelName) || undefined,
    clientType: modelForm.clientType,
    baseUrl: modelForm.clientType === 1 ? normalizeText(modelForm.baseUrl) || undefined : undefined,
    apiModel: normalizeText(modelForm.apiModel) || undefined,
    apiKey: modelForm.clientType === 1 ? normalizeText(modelForm.apiKey) || undefined : undefined,
    enabled: modelForm.enabled,
    extJson: parseJsonText(modelForm.extJsonText, '扩展配置'),
  }
}

function buildModelTestPayload() {
  const extJson = parseJsonText(modelForm.extJsonText, '扩展配置')
  return {
    id: dialogMode.value === 'edit' ? editingId.value : null,
    clientType: modelForm.clientType,
    baseUrl: normalizeText(modelForm.baseUrl) || undefined,
    apiModel: normalizeText(modelForm.apiModel) || undefined,
    apiKey: normalizeText(modelForm.apiKey) || undefined,
    messages: testMessages.value
      .filter(item => item.role === 'system' || item.role === 'user' || item.role === 'assistant')
      .map(item => ({
        role: item.role,
        content: item.content,
      })),
    extJson,
  }
}

function buildKbPayload(): AiKbStoreUpsertPayload {
  const tags = kbForm.tagsText
    .split(/[\n,，]+/)
    .map(item => item.trim())
    .filter(Boolean)

  return {
    kbCode: normalizeText(kbForm.kbCode) || undefined,
    kbName: normalizeText(kbForm.kbName) || undefined,
    description: normalizeText(kbForm.description) || undefined,
    embeddingModel: normalizeText(kbForm.embeddingModel) || undefined,
    permission: normalizeText(kbForm.permission) || undefined,
    chunkMethod: normalizeText(kbForm.chunkMethod) || undefined,
    parserConfig: parseJsonText(kbForm.parserConfigText, '分片高级配置'),
    parseType: normalizeText(kbForm.parseType) || undefined,
    pipelineId: normalizeText(kbForm.pipelineId) || undefined,
    tags,
    enabled: kbForm.enabled,
    extJson: parseJsonText(kbForm.extJsonText, '扩展配置'),
  }
}

function validateCurrentForm() {
  if (props.activeTab === 'model') {
    if (!normalizeText(modelForm.modelCode)) {
      return '请输入模型编码'
    }
    if (!normalizeText(modelForm.modelName)) {
      return '请输入模型名称'
    }
    if (!normalizeText(modelForm.apiModel)) {
      return '请输入远端模型标识'
    }
    if (modelForm.clientType === 1) {
      if (!normalizeText(modelForm.baseUrl)) {
        return '通用 Spring AI 客户端必须填写 Base URL'
      }
      if (dialogMode.value === 'create' && !normalizeText(modelForm.apiKey)) {
        return '新增通用 Spring AI 模型时必须填写 API Key'
      }
    }
    return ''
  }

  if (props.activeTab === 'kb') {
    if (!normalizeText(kbForm.kbCode)) {
      return '请输入知识库编码'
    }
    if (!normalizeText(kbForm.kbName)) {
      return '请输入知识库名称'
    }
    if (normalizeText(kbForm.pipelineId) && (normalizeText(kbForm.chunkMethod) || normalizeText(kbForm.parserConfigText))) {
      return '自定义 Pipeline 不能与内置分片方式或分片高级配置同时使用'
    }
  }

  return ''
}


function validateModelTestForm() {
  if (!normalizeText(modelForm.baseUrl)) {
    return '请输入 Base URL'
  }
  if (!normalizeText(modelForm.apiModel)) {
    return '请输入 Provider 模型标识'
  }
  if (dialogMode.value === 'create' && !normalizeText(modelForm.apiKey)) {
    return '新增模型测试时必须填写 API Key'
  }
  return ''
}

function buildTestContextKey() {
  return [
    dialogMode.value,
    editingId.value ?? 'new',
    modelForm.clientType,
    normalizeText(modelForm.baseUrl),
    normalizeText(modelForm.apiModel),
  ].join('|')
}

function createTestMessage(role: TestChatMessage['role'], content: string, extra: Partial<TestChatMessage> = {}): TestChatMessage {
  return {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    role,
    content,
    ...extra,
  }
}

function openTestDialog() {
  const validationError = validateModelTestForm()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }
  try {
    parseJsonText(modelForm.extJsonText, '扩展配置')
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '扩展配置格式不正确')
    return
  }

  const nextContextKey = buildTestContextKey()
  if (testContextKey.value !== nextContextKey) {
    testContextKey.value = nextContextKey
    testMessages.value = []
    testInput.value = ''
  }
  testDialogVisible.value = true
}

function clearTestMessages() {
  testMessages.value = []
}

async function scrollTestMessagesToBottom() {
  await nextTick()
  if (testMessagesRef.value) {
    testMessagesRef.value.scrollTop = testMessagesRef.value.scrollHeight
  }
}

async function sendTestMessage() {
  const content = testInput.value.trim()
  if (!content || testingChat.value) {
    return
  }
  const validationError = validateModelTestForm()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }

  testMessages.value.push(createTestMessage('user', content))
  void scrollTestMessagesToBottom()
  testInput.value = ''
  testingChat.value = true
  try {
    const payload = buildModelTestPayload()
    const result = await testAiModelChat(payload)
    if (result?.success) {
      testMessages.value.push(createTestMessage('assistant', result.answer || '模型返回为空', {
        status: 'success',
        durationMs: result.durationMs,
      }))
      void scrollTestMessagesToBottom()
      return
    }
    testMessages.value.push(createTestMessage('assistant', result?.errorMessage || '模型连接测试失败', {
      status: 'error',
      durationMs: result?.durationMs,
    }))
    void scrollTestMessagesToBottom()
  }
  catch (error) {
    testMessages.value.push(createTestMessage('assistant', error instanceof Error ? error.message : '模型连接测试失败', {
      status: 'error',
    }))
    void scrollTestMessagesToBottom()
  }
  finally {
    testingChat.value = false
  }
}

async function handleSubmitDialog() {
  const validationError = validateCurrentForm()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }

  saving.value = true
  try {
    if (props.activeTab === 'model') {
      const payload = buildModelPayload()
      if (dialogMode.value === 'create') {
        await createAiModelManage(payload)
        currentPage.value = 1
        ElMessage.success('模型新增成功')
      } else if (editingId.value !== null) {
        await updateAiModelManage(editingId.value, payload)
        ElMessage.success('模型更新成功')
      }
    } else if (props.activeTab === 'kb') {
      const payload = buildKbPayload()
      if (dialogMode.value === 'create') {
        await createAiKbStore(payload)
        currentPage.value = 1
        ElMessage.success('知识库新增成功')
      } else if (editingId.value !== null) {
        await updateAiKbStore(editingId.value, payload)
        ElMessage.success('知识库更新成功')
      }
    }

    dialogVisible.value = false
    await loadData()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `${currentDialogTitle.value}失败`)
  }
  finally {
    saving.value = false
  }
}

async function handleDeleteCard(card: PlatformCard) {
  const entityLabel = card.entityType === 'model' ? '模型' : '知识库'
  try {
    await ElMessageBox.confirm(
      `确认删除${entityLabel}「${card.title}」吗？删除后不可恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )

    if (card.entityType === 'model') {
      await deleteAiModelManage(card.id)
    } else if (card.entityType === 'kb') {
      await deleteAiKbStore(card.id)
    }

    if (currentCards.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    ElMessage.success(`${entityLabel}删除成功`)
    await loadData()
  }
  catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : `${entityLabel}删除失败`)
  }
}

async function handleToggleEnabled(card: PlatformCard, nextEnabled: boolean) {
  if (card.entityType !== 'model' && card.entityType !== 'kb') {
    return
  }

  statusUpdatingKey.value = buildStatusUpdateKey(card)
  try {
    if (card.entityType === 'model') {
      await editAiModelManage(card.id, { enabled: nextEnabled })
      const target = modelRecords.value.find(item => item.id === card.id)
      if (target) {
        target.enabled = nextEnabled
      }
      ElMessage.success(`模型已${nextEnabled ? '启用' : '停用'}`)
    } else {
      await editAiKbStore(card.id, { enabled: nextEnabled })
      const target = kbRecords.value.find(item => item.id === card.id)
      if (target) {
        target.enabled = nextEnabled
      }
      ElMessage.success(`知识库已${nextEnabled ? '启用' : '停用'}`)
    }
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态更新失败')
  }
  finally {
    statusUpdatingKey.value = ''
  }
}

async function loadData() {
  loading.value = true
  errorMessage.value = ''
  try {
    if (props.activeTab === 'model') {
      const payload = await searchAiModelManages({
        page: currentPage.value,
        size: pageSize.value,
        keyword: keyword.value.trim() || undefined,
      })
      modelRecords.value = payload?.list ?? []
      total.value = resolveTotal(payload?.pageInfo?.total, modelRecords.value.length)
      return
    }

    const payload = await searchAiKbStores({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
    })
    kbRecords.value = payload?.list ?? []
    total.value = resolveTotal(payload?.pageInfo?.total, kbRecords.value.length)
  }
  catch (error) {
    total.value = 0
    errorMessage.value = error instanceof Error ? error.message : 'AI 平台数据加载失败'
  }
  finally {
    loading.value = false
  }
}

async function handleSearch() {
  currentPage.value = 1
  await loadData()
}

async function handleRefresh() {
  await loadData()
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

watch(
  () => kbForm.pipelineId,
  (pipelineId) => {
    if (pipelineId.trim()) {
      kbForm.chunkMethod = ''
      kbForm.parserConfigText = ''
    }
  },
)

watch(
  () => props.activeTab,
  () => {
    dialogVisible.value = false
    keyword.value = ''
    currentPage.value = 1
    void loadData()
  },
  { immediate: true },
)
</script>

<template>
  <section class="ai-platform-page">
    <div class="ai-platform-shell">
      <header class="ai-platform-shell__header">
        <div class="ai-platform-shell__tools">
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
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            {{ createButtonLabel }}
          </el-button>
        </div>
      </header>

      <main class="ai-platform-shell__main">
        <div v-if="errorMessage" class="ai-platform-shell__state ai-platform-shell__state--error">
          {{ errorMessage }}
        </div>
        <div v-else-if="loading" class="ai-platform-shell__state">
          正在加载{{ currentTabLabel }}列表...
        </div>
        <div v-else-if="!currentCards.length" class="ai-platform-shell__state">
          当前没有{{ currentTabLabel }}数据
        </div>
        <div v-else class="ai-platform-shell__grid">
          <article
            v-for="card in currentCards"
            :key="`${card.entityType}-${card.id}`"
            :class="['ai-platform-card', { 'ai-platform-card--clickable': card.entityType === 'kb' }]"
            @click="card.entityType === 'kb' ? navigateToKbDocuments(card) : undefined"
          >
            <div class="ai-platform-card__head">
              <div>
                <h3>{{ card.title }}</h3>
                <p>{{ card.code }}</p>
              </div>
              <div class="ai-platform-card__tags">
                <el-tag
                  v-for="tag in card.tags.filter(Boolean)"
                  :key="tag"
                  size="small"
                  effect="plain"
                >
                  {{ tag }}
                </el-tag>
              </div>
            </div>
            <div class="ai-platform-card__summary">{{ card.summary }}</div>
            <div class="ai-platform-card__meta">
              <div v-for="extra in card.extras" :key="extra.label" class="ai-platform-card__meta-item">
                <span>{{ extra.label }}</span>
                <strong>{{ extra.value }}</strong>
              </div>
            </div>
            <div v-if="card.entityType === 'model' || card.entityType === 'kb'" class="ai-platform-card__actions">
              <div class="ai-platform-card__status">
                <span>状态</span>
                <el-switch
                  :model-value="card.enabled"
                  :loading="isCardStatusUpdating(card)"
                  inline-prompt
                  active-text="启用"
                  inactive-text="停用"
                  @click.stop
                  @change="(value) => handleToggleEnabled(card, value)"
                />
              </div>
              <div class="ai-platform-card__action-buttons">
                <el-button plain circle title="编辑" @click.stop="openEditDialog(card)">
                  <el-icon><EditPen /></el-icon>
                </el-button>
                <el-button plain circle type="danger" title="删除" @click.stop="handleDeleteCard(card)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
          </article>
        </div>
      </main>

      <footer class="ai-platform-shell__footer">
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
      v-model="dialogVisible"
      :title="currentDialogTitle"
      width="720px"
      destroy-on-close
    >
      <el-form v-if="props.activeTab === 'model'" label-width="112px" class="ai-platform-dialog-form">
        <el-form-item label="模型编码" required>
          <el-input v-model="modelForm.modelCode" placeholder="例如：gpt-4o-mini" />
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="modelForm.modelName" placeholder="请输入模型名称" />
        </el-form-item>
        <el-form-item label="对话客户端类型" required>
          <el-select v-model="modelForm.clientType" class="ai-platform-dialog-form__select">
            <el-option
              v-for="option in chatClientTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="modelForm.clientType === 1" label="Base URL" required>
          <el-input v-model="modelForm.baseUrl" placeholder="https://api.example.com/v1" />
        </el-form-item>
        <el-form-item label="远端模型标识" required>
          <el-input v-model="modelForm.apiModel" placeholder="请输入客户端调用的远端模型标识" />
        </el-form-item>
        <el-form-item v-if="modelForm.clientType === 1" :label="dialogMode === 'create' ? 'API Key' : 'API Key（留空不改）'">
          <el-input v-model="modelForm.apiKey" type="password" show-password placeholder="请输入 API Key" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="modelForm.enabled" inline-prompt active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="扩展配置">
          <el-input
            v-model="modelForm.extJsonText"
            type="textarea"
            :rows="5"
            placeholder="{&#10;  &quot;temperature&quot;: 0.3&#10;}"
          />
        </el-form-item>
      </el-form>

      <el-form v-else-if="props.activeTab === 'kb'" label-width="112px" class="ai-platform-dialog-form">
        <el-alert
          title="RAGFlow 地址与 API Key 由系统参数统一维护；保存时会直接创建或更新远端 Dataset。"
          type="info"
          :closable="false"
          show-icon
          class="ai-platform-dialog-form__alert"
        />
        <el-form-item label="知识库编码" required>
          <el-input v-model="kbForm.kbCode" :disabled="dialogMode === 'edit'" placeholder="例如：faq" />
        </el-form-item>
        <el-form-item label="知识库名称" required>
          <el-input v-model="kbForm.kbName" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="kbForm.description" type="textarea" :rows="2" placeholder="说明该知识库适用的内容和检索范围" />
        </el-form-item>
        <el-form-item label="Embedding 模型">
          <el-input v-model="kbForm.embeddingModel" placeholder="留空使用 RAGFlow 默认模型" />
        </el-form-item>
        <el-form-item label="权限">
          <el-select v-model="kbForm.permission" class="ai-platform-dialog-form__select">
            <el-option label="团队可用 (team)" value="team" />
            <el-option label="私有 (private)" value="private" />
          </el-select>
        </el-form-item>
        <el-form-item label="分片方式">
          <el-select v-model="kbForm.chunkMethod" class="ai-platform-dialog-form__select" :disabled="Boolean(kbForm.pipelineId)">
            <el-option label="通用分片 (naive)" value="naive" />
            <el-option label="问答分片 (qa)" value="qa" />
            <el-option label="表格分片 (table)" value="table" />
            <el-option label="手册分片 (manual)" value="manual" />
            <el-option label="演示文稿 (presentation)" value="presentation" />
          </el-select>
        </el-form-item>
        <el-form-item label="分片高级配置">
          <el-input
            v-model="kbForm.parserConfigText"
            type="textarea"
            :rows="5"
            :disabled="Boolean(kbForm.pipelineId)"
            placeholder="{&#10;  &quot;chunk_token_num&quot;: 512,&#10;  &quot;delimiter&quot;: &quot;\n!?;。；！？&quot;&#10;}"
          />
        </el-form-item>
        <el-form-item label="自定义 Pipeline">
          <el-input v-model="kbForm.pipelineId" placeholder="可选；填写后不要使用内置分片方式" />
        </el-form-item>
        <el-form-item label="解析类型">
          <el-input v-model="kbForm.parseType" :disabled="!kbForm.pipelineId" placeholder="仅自定义 Pipeline 模式使用" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input
            v-model="kbForm.tagsText"
            type="textarea"
            :rows="3"
            placeholder="标签之间用逗号或换行分隔"
          />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="kbForm.enabled" inline-prompt active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="扩展配置">
          <el-input
            v-model="kbForm.extJsonText"
            type="textarea"
            :rows="5"
            placeholder="{&#10;  &quot;workspaceId&quot;: &quot;demo&quot;&#10;}"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="ai-platform-dialog__footer">
          <el-button @click="closeDialog">取消</el-button>
          <el-button v-if="props.activeTab === 'model' && modelForm.clientType === 1" plain :loading="testingChat" @click="openTestDialog">
            <el-icon><ChatDotRound /></el-icon>
            测试对话
          </el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmitDialog">
            {{ dialogMode === 'create' ? '确认新增' : '确认保存' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="testDialogVisible"
      :title="`测试模型连接 - ${modelForm.apiModel || modelForm.modelName || '未命名模型'}`"
      width="760px"
      destroy-on-close
    >
      <div class="ai-platform-test-chat">
        <div class="ai-platform-test-chat__meta">
          <el-tag size="small" effect="plain">{{ resolveChatClientTypeName(modelForm.clientType) }}</el-tag>
          <span>{{ modelForm.baseUrl || '-' }}</span>
        </div>
        <div ref="testMessagesRef" class="ai-platform-test-chat__messages">
          <div v-if="!testMessages.length" class="ai-platform-test-chat__empty">
            输入一条消息，测试当前模型配置是否能正常返回。
          </div>
          <div
            v-for="message in testMessages"
            :key="message.id"
            :class="[
              'ai-platform-test-chat__message',
              `ai-platform-test-chat__message--${message.role}`,
              { 'ai-platform-test-chat__message--error': message.status === 'error' },
            ]"
          >
            <div class="ai-platform-test-chat__bubble">
              <div class="ai-platform-test-chat__role">
                {{ message.role === 'user' ? '你' : 'AI' }}
                <span v-if="message.durationMs"> · {{ message.durationMs }}ms</span>
              </div>
              <div class="ai-platform-test-chat__content">{{ message.content }}</div>
            </div>
          </div>
          <div v-if="testingChat" class="ai-platform-test-chat__loading">模型响应中...</div>
        </div>
        <div class="ai-platform-test-chat__composer">
          <el-input
            v-model="testInput"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="输入测试内容"
            @keydown.enter.exact.prevent="sendTestMessage"
          />
          <div class="ai-platform-test-chat__actions">
            <el-button plain :disabled="testingChat || !testMessages.length" @click="clearTestMessages">
              清空上下文
            </el-button>
            <el-button type="primary" :loading="testingChat" :disabled="!testInput.trim()" @click="sendTestMessage">
              发送
            </el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.ai-platform-page {
  display: flex;
  flex: 1;
  min-height: 0;
}

.ai-platform-shell {
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

.ai-platform-shell__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--system-border-subtle);
}

.ai-platform-shell__tabs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-platform-shell__tab {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text-soft);
  cursor: pointer;
}

.ai-platform-shell__tab.el-tag--primary {
  border-color: var(--system-accent-border);
  background: var(--system-accent-bg-strong);
  color: var(--system-accent-text);
}

.ai-platform-shell__tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-platform-shell__tools :deep(.el-input) {
  width: 260px;
}

.ai-platform-shell__tools :deep(.el-input__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
}

.ai-platform-shell__tools :deep(.el-input__inner),
.ai-platform-shell__tools :deep(.el-input__prefix-inner) {
  color: var(--system-text);
}

.ai-platform-shell__tools :deep(.el-input__inner::placeholder) {
  color: var(--system-text-faint);
}

.ai-platform-shell__tools :deep(.el-button) {
  border-radius: 10px;
}

.ai-platform-shell__tools :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.ai-platform-shell__tools :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-primary-button-bg);
  color: var(--system-primary-button-text);
}

.ai-platform-shell__main {
  min-height: 0;
  padding: 14px 16px;
  background: var(--system-surface-muted);
  overflow-y: auto;
}

.ai-platform-shell__state {
  display: grid;
  place-items: center;
  min-height: 260px;
  color: var(--system-text-muted);
  font-size: 13px;
}

.ai-platform-shell__state--error {
  color: var(--system-danger);
}

.ai-platform-shell__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
  align-content: start;
}

.ai-platform-card {
  display: grid;
  min-width: 0;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--system-border);
  border-radius: 14px;
  background: var(--system-surface-solid);
}

.ai-platform-card--clickable {
  cursor: pointer;
}

.ai-platform-card--clickable:hover {
  border-color: var(--system-accent-border);
  box-shadow: var(--system-accent-shadow);
}

.ai-platform-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.ai-platform-card__head > :first-child,
.ai-platform-card__tags,
.ai-platform-card__meta-item {
  min-width: 0;
}

.ai-platform-card__head h3 {
  margin: 0;
  color: var(--system-title);
  font-size: 15px;
  overflow-wrap: anywhere;
}

.ai-platform-card__head p {
  margin: 4px 0 0;
  color: var(--system-text-soft);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.ai-platform-card__tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.ai-platform-card__tags :deep(.el-tag) {
  box-sizing: border-box;
  max-width: 100%;
  min-height: 24px;
  height: auto;
  white-space: normal;
  overflow-wrap: anywhere;
}

.ai-platform-card__summary {
  color: var(--system-text);
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.ai-platform-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.ai-platform-card__meta-item {
  display: grid;
  gap: 4px;
}

.ai-platform-card__meta-item span {
  color: var(--system-text-faint);
  font-size: 12px;
}

.ai-platform-card__meta-item strong {
  color: var(--system-title);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.ai-platform-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 2px;
}

.ai-platform-card__status {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--system-text-soft);
  font-size: 12px;
}

.ai-platform-card__action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-platform-shell__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 44px;
  padding: 0 16px;
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-solid);
}

.ai-platform-page :deep(.el-overlay-dialog) {
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-platform-page :deep(.el-dialog) {
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
}

.ai-platform-page :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.ai-platform-page :deep(.el-dialog__body) {
  background: var(--system-surface-strong);
}

.ai-platform-page :deep(.el-dialog__footer) {
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.ai-platform-dialog-form :deep(.el-form-item__label) {
  color: var(--system-text-soft);
}

.ai-platform-dialog-form :deep(.el-input__wrapper),
.ai-platform-dialog-form :deep(.el-textarea__inner),
.ai-platform-dialog-form :deep(.el-select__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
  color: var(--system-text);
}

.ai-platform-dialog-form :deep(.el-switch__core) {
  background: var(--system-surface-muted);
  border-color: var(--system-border);
}

.ai-platform-dialog-form :deep(.el-switch.is-checked .el-switch__core) {
  background: var(--system-accent-text);
  border-color: var(--system-accent-text);
}

.ai-platform-dialog-form :deep(.el-textarea__inner) {
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
}

.ai-platform-dialog-form__hint {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--system-border-subtle);
  border-radius: 8px;
  background: var(--system-surface-muted);
  color: var(--system-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.ai-platform-dataset-option {
  display: flex;
  align-items: center;
  min-width: 0;
  max-width: 100%;
  gap: 6px;
  line-height: 1.4;
}

.ai-platform-dataset-option span,
.ai-platform-dataset-option small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-platform-dataset-option span {
  flex: 0 1 auto;
}

.ai-platform-dataset-option small {
  flex: 1 1 auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.ai-platform-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.ai-platform-dialog__footer :deep(.el-button) {
  min-width: 76px;
  border-radius: 10px;
}

.ai-platform-dialog__footer :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.ai-platform-dialog__footer :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-primary-button-bg);
  color: var(--system-primary-button-text);
}

.ai-platform-test-chat {
  display: grid;
  grid-template-rows: auto minmax(260px, 48vh) auto;
  gap: 12px;
}

.ai-platform-test-chat__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  color: var(--system-text-soft);
  font-size: 12px;
}

.ai-platform-test-chat__meta span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-platform-test-chat__messages {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  padding: 12px;
  border: 1px solid var(--system-border-subtle);
  border-radius: 12px;
  background: var(--system-surface-muted);
  overflow-y: auto;
}

.ai-platform-test-chat__empty,
.ai-platform-test-chat__loading {
  display: grid;
  place-items: center;
  min-height: 120px;
  color: var(--system-text-muted);
  font-size: 13px;
}

.ai-platform-test-chat__loading {
  min-height: 36px;
}

.ai-platform-test-chat__message {
  display: flex;
}

.ai-platform-test-chat__message--user {
  justify-content: flex-end;
}

.ai-platform-test-chat__message--assistant {
  justify-content: flex-start;
}

.ai-platform-test-chat__bubble {
  max-width: min(86%, 560px);
  padding: 10px 12px;
  border: 1px solid var(--system-border);
  border-radius: 12px;
  background: var(--system-surface-solid);
  color: var(--system-text);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-platform-test-chat__message--user .ai-platform-test-chat__bubble {
  border-color: var(--system-accent-border);
  background: var(--system-accent-bg);
}

.ai-platform-test-chat__message--error .ai-platform-test-chat__bubble {
  border-color: color-mix(in srgb, var(--system-danger) 55%, var(--system-border));
  background: color-mix(in srgb, var(--system-danger) 10%, var(--system-surface-solid));
  color: var(--system-danger);
}

.ai-platform-test-chat__role {
  margin-bottom: 4px;
  color: var(--system-text-faint);
  font-size: 12px;
}

.ai-platform-test-chat__message--error .ai-platform-test-chat__role {
  color: var(--system-danger);
}

.ai-platform-test-chat__content {
  white-space: pre-wrap;
}

.ai-platform-test-chat__composer {
  display: grid;
  gap: 10px;
}

.ai-platform-test-chat__composer :deep(.el-textarea__inner) {
  background: var(--system-surface-muted);
  border-color: var(--system-border);
  color: var(--system-text);
  box-shadow: none;
}

.ai-platform-test-chat__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 960px) {
  .ai-platform-shell__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .ai-platform-shell__tools {
    width: 100%;
    flex-wrap: wrap;
  }

  .ai-platform-shell__tools :deep(.el-input) {
    width: 100%;
  }

  .ai-platform-card__meta {
    grid-template-columns: 1fr;
  }

  .ai-platform-card__actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .ai-platform-card__action-buttons {
    width: 100%;
    justify-content: flex-end;
  }

  .ai-platform-shell__footer {
    height: auto;
    padding: 10px 12px;
    flex-wrap: wrap;
  }
}
</style>
