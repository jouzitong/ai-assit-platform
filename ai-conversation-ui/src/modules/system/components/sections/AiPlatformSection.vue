<script setup lang="ts">
import { Delete, EditPen, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createAiKbStore,
  createAiModelManage,
  deleteAiKbStore,
  deleteAiModelManage,
  editAiKbStore,
  editAiModelManage,
  searchAiFlowSkills,
  searchAiKbStores,
  searchAiModelManages,
  updateAiKbStore,
  updateAiModelManage,
  type AiFlowSkillItem,
  type AiKbStoreItem,
  type AiKbStoreUpsertPayload,
  type AiModelManageItem,
  type AiModelManageUpsertPayload,
} from '../../api/aiPlatform'

type PlatformTab = 'model' | 'kb' | 'skill' | 'tool'
type DialogMode = 'create' | 'edit'
type PlatformCard = {
  id: string | number
  entityType: PlatformTab
  title: string
  code: string
  tags: string[]
  summary: string
  extras: Array<{ label: string, value: string }>
  enabled?: boolean
  raw: AiModelManageItem | AiKbStoreItem | AiFlowSkillItem
}

const activeTab = ref<PlatformTab>('model')
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
const modelRecords = ref<AiModelManageItem[]>([])
const kbRecords = ref<AiKbStoreItem[]>([])
const skillRecords = ref<AiFlowSkillItem[]>([])

const pageSizeOptions = [10, 20, 50, 100, 200, 500]

const modelForm = reactive({
  modelCode: '',
  modelName: '',
  providerCode: '',
  providerName: '',
  baseUrl: '',
  apiModel: '',
  apiKey: '',
  enabled: true,
  extJsonText: '',
})

const kbForm = reactive({
  kbCode: '',
  kbName: '',
  providerKbId: '',
  url: '',
  tagsText: '',
  enabled: true,
  extJsonText: '',
})

const tabOptions = [
  { key: 'model' as const, label: '模型管理' },
  { key: 'kb' as const, label: '知识库管理' },
  { key: 'skill' as const, label: 'Skill 管理' },
  { key: 'tool' as const, label: 'Tool 管理' },
]

const isToolTab = computed(() => activeTab.value === 'tool')
const isEditableTab = computed(() => activeTab.value === 'model' || activeTab.value === 'kb')
const currentTabLabel = computed(() => {
  switch (activeTab.value) {
    case 'model':
      return '模型'
    case 'kb':
      return '知识库'
    case 'skill':
      return 'Skill'
    default:
      return 'Tool'
  }
})
const currentDialogTitle = computed(() => `${dialogMode.value === 'create' ? '新增' : '编辑'}${currentTabLabel.value}`)
const createButtonLabel = computed(() => `新增${currentTabLabel.value}`)
const currentSearchPlaceholder = computed(() => {
  switch (activeTab.value) {
    case 'model':
      return '搜索名称 / 编码 / Provider'
    case 'kb':
      return '搜索名称 / 编码 / Provider KB'
    case 'skill':
      return '搜索 Skill 名称 / 编码 / 类型'
    default:
      return 'Tool 管理接口待接入'
  }
})

const currentCards = computed<PlatformCard[]>(() => {
  if (activeTab.value === 'model') {
    return modelRecords.value.map((item) => ({
      id: item.id,
      entityType: 'model',
      title: item.modelName || item.modelCode || '未命名模型',
      code: item.modelCode || '-',
      tags: [
        item.providerName || item.providerCode || '未配置 Provider',
        item.enabled === false ? '停用' : '启用',
      ],
      summary: item.apiModel || item.baseUrl || '暂无模型说明',
      extras: [
        { label: 'Provider', value: item.providerCode || '-' },
        { label: 'Base URL', value: item.baseUrl || '-' },
        { label: '密钥', value: item.apiKeyMasked || '未配置' },
        { label: '更新时间', value: formatDateTime(item.updateTime || item.createTime) },
      ],
      enabled: item.enabled !== false,
      raw: item,
    }))
  }

  if (activeTab.value === 'skill') {
    return skillRecords.value.map((item) => ({
      id: item.id,
      entityType: 'skill',
      title: item.name || item.code || '未命名 Skill',
      code: item.code || '-',
      tags: [
        item.type || '未配置类型',
        ...(item.config?.supportedPhases || []).slice(0, 2),
        item.enabled === false ? '停用' : '启用',
      ],
      summary: item.config?.summary || '暂无 Skill 摘要',
      extras: [
        { label: '类型', value: item.type || '-' },
        { label: '支持阶段', value: (item.config?.supportedPhases || []).join(', ') || '-' },
        { label: '状态', value: item.enabled === false ? '停用' : '启用' },
        { label: '更新时间', value: formatDateTime(item.updateTime || item.createTime) },
      ],
      enabled: item.enabled !== false,
      raw: item,
    }))
  }

  if (activeTab.value === 'tool') {
    return []
  }

  return kbRecords.value.map((item) => ({
    id: item.id,
    entityType: 'kb',
    title: item.kbName || item.kbCode || '未命名知识库',
    code: item.kbCode || '-',
    tags: [
      ...(item.tags || []).slice(0, 3),
      item.enabled === false ? '停用' : '启用',
    ],
    summary: item.url || item.providerKbId || '暂无知识库说明',
    extras: [
      { label: 'Provider KB', value: item.providerKbId || '-' },
      { label: '地址', value: item.url || '-' },
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
  modelForm.providerCode = ''
  modelForm.providerName = ''
  modelForm.baseUrl = ''
  modelForm.apiModel = ''
  modelForm.apiKey = ''
  modelForm.enabled = true
  modelForm.extJsonText = ''
}

function resetKbForm() {
  kbForm.kbCode = ''
  kbForm.kbName = ''
  kbForm.providerKbId = ''
  kbForm.url = ''
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
  if (activeTab.value === 'model') {
    resetModelForm()
  } else if (activeTab.value === 'kb') {
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
    modelForm.providerCode = item.providerCode || ''
    modelForm.providerName = item.providerName || ''
    modelForm.baseUrl = item.baseUrl || ''
    modelForm.apiModel = item.apiModel || ''
    modelForm.apiKey = ''
    modelForm.enabled = item.enabled !== false
    modelForm.extJsonText = formatJsonText(item.extJson)
  } else if (card.entityType === 'kb') {
    const item = card.raw as AiKbStoreItem
    kbForm.kbCode = item.kbCode || ''
    kbForm.kbName = item.kbName || ''
    kbForm.providerKbId = item.providerKbId || ''
    kbForm.url = item.url || ''
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
    providerCode: normalizeText(modelForm.providerCode) || undefined,
    providerName: normalizeText(modelForm.providerName) || undefined,
    baseUrl: normalizeText(modelForm.baseUrl) || undefined,
    apiModel: normalizeText(modelForm.apiModel) || undefined,
    apiKey: normalizeText(modelForm.apiKey) || undefined,
    enabled: modelForm.enabled,
    extJson: parseJsonText(modelForm.extJsonText, '扩展配置'),
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
    providerKbId: normalizeText(kbForm.providerKbId) || undefined,
    url: normalizeText(kbForm.url) || undefined,
    tags,
    enabled: kbForm.enabled,
    extJson: parseJsonText(kbForm.extJsonText, '扩展配置'),
  }
}

function validateCurrentForm() {
  if (activeTab.value === 'model') {
    if (!normalizeText(modelForm.modelCode)) {
      return '请输入模型编码'
    }
    if (!normalizeText(modelForm.modelName)) {
      return '请输入模型名称'
    }
    if (!normalizeText(modelForm.apiModel)) {
      return '请输入 Provider 模型标识'
    }
    if (dialogMode.value === 'create' && !normalizeText(modelForm.apiKey)) {
      return '新增模型时必须填写 API Key'
    }
    return ''
  }

  if (activeTab.value === 'kb') {
    if (!normalizeText(kbForm.kbCode)) {
      return '请输入知识库编码'
    }
    if (!normalizeText(kbForm.kbName)) {
      return '请输入知识库名称'
    }
  }

  return ''
}

async function handleSubmitDialog() {
  const validationError = validateCurrentForm()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }

  saving.value = true
  try {
    if (activeTab.value === 'model') {
      const payload = buildModelPayload()
      if (dialogMode.value === 'create') {
        await createAiModelManage(payload)
        currentPage.value = 1
        ElMessage.success('模型新增成功')
      } else if (editingId.value !== null) {
        await updateAiModelManage(editingId.value, payload)
        ElMessage.success('模型更新成功')
      }
    } else if (activeTab.value === 'kb') {
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
    if (activeTab.value === 'tool') {
      total.value = 0
      return
    }

    if (activeTab.value === 'model') {
      const payload = await searchAiModelManages({
        page: currentPage.value,
        size: pageSize.value,
        keyword: keyword.value.trim() || undefined,
      })
      modelRecords.value = payload?.list ?? []
      total.value = resolveTotal(payload?.pageInfo?.total, modelRecords.value.length)
      return
    }

    if (activeTab.value === 'skill') {
      const payload = await searchAiFlowSkills({
        page: currentPage.value,
        size: pageSize.value,
        keyword: keyword.value.trim() || undefined,
      })
      skillRecords.value = payload?.list ?? []
      total.value = resolveTotal(payload?.pageInfo?.total, skillRecords.value.length)
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

async function handleChangeTab(tab: PlatformTab) {
  if (activeTab.value === tab) {
    return
  }
  dialogVisible.value = false
  activeTab.value = tab
  keyword.value = ''
  currentPage.value = 1
  await loadData()
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

onMounted(() => {
  void loadData()
})
</script>

<template>
  <section class="ai-platform-page">
    <div class="ai-platform-shell">
      <header class="ai-platform-shell__header">
        <div class="ai-platform-shell__tabs">
          <el-tag
            v-for="tab in tabOptions"
            :key="tab.key"
            :type="activeTab === tab.key ? 'primary' : 'info'"
            effect="plain"
            class="ai-platform-shell__tab"
            @click="handleChangeTab(tab.key)"
          >
            {{ tab.label }}
          </el-tag>
        </div>
        <div class="ai-platform-shell__tools">
          <el-input
            v-model="keyword"
            :placeholder="currentSearchPlaceholder"
            :disabled="isToolTab"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button plain :loading="loading" :disabled="isToolTab" @click="handleRefresh">
            <el-icon><RefreshRight /></el-icon>
            刷新
          </el-button>
          <el-button v-if="isEditableTab" type="primary" @click="openCreateDialog">
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
        <div v-else-if="activeTab === 'tool'" class="ai-platform-shell__state">
          Tool 管理接口暂未接入，当前先保留标签入口。
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
        <div class="ai-platform-shell__footer-total">Total {{ total }}</div>
        <el-pagination
          v-if="!isToolTab"
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="pageSizeOptions"
          :pager-count="5"
          layout="sizes, prev, pager, next"
          :total="total"
          @current-change="handleCurrentPageChange"
          @size-change="handlePageSizeChange"
        />
        <span v-else class="ai-platform-shell__footer-hint">待接入 Tool 管理接口</span>
      </footer>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="currentDialogTitle"
      width="720px"
      destroy-on-close
    >
      <el-form v-if="activeTab === 'model'" label-width="112px" class="ai-platform-dialog-form">
        <el-form-item label="模型编码" required>
          <el-input v-model="modelForm.modelCode" placeholder="例如：gpt-4o-mini" />
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="modelForm.modelName" placeholder="请输入模型名称" />
        </el-form-item>
        <el-form-item label="Provider Code">
          <el-input v-model="modelForm.providerCode" placeholder="例如：openai" />
        </el-form-item>
        <el-form-item label="Provider Name">
          <el-input v-model="modelForm.providerName" placeholder="例如：OpenAI" />
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="modelForm.baseUrl" placeholder="https://api.example.com/v1" />
        </el-form-item>
        <el-form-item label="Provider 模型" required>
          <el-input v-model="modelForm.apiModel" placeholder="请输入真实 Provider 模型标识" />
        </el-form-item>
        <el-form-item :label="dialogMode === 'create' ? 'API Key' : 'API Key（留空不改）'">
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

      <el-form v-else-if="activeTab === 'kb'" label-width="112px" class="ai-platform-dialog-form">
        <el-form-item label="知识库编码" required>
          <el-input v-model="kbForm.kbCode" placeholder="请输入知识库编码" />
        </el-form-item>
        <el-form-item label="知识库名称" required>
          <el-input v-model="kbForm.kbName" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="Provider KB">
          <el-input v-model="kbForm.providerKbId" placeholder="请输入 Provider 侧 KB ID" />
        </el-form-item>
        <el-form-item label="访问地址">
          <el-input v-model="kbForm.url" placeholder="请输入知识库地址" />
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
          <el-button type="primary" :loading="saving" @click="handleSubmitDialog">
            {{ dialogMode === 'create' ? '确认新增' : '确认保存' }}
          </el-button>
        </div>
      </template>
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
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
  overflow: hidden;
}

.ai-platform-shell__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid #eef2f7;
}

.ai-platform-shell__tabs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-platform-shell__tab {
  cursor: pointer;
}

.ai-platform-shell__tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-platform-shell__tools :deep(.el-input) {
  width: 260px;
}

.ai-platform-shell__main {
  min-height: 0;
  padding: 14px 16px;
  background: #f8fafc;
  overflow-y: auto;
}

.ai-platform-shell__state {
  display: grid;
  place-items: center;
  min-height: 260px;
  color: #6b7280;
  font-size: 13px;
}

.ai-platform-shell__state--error {
  color: #dc2626;
}

.ai-platform-shell__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
  align-content: start;
}

.ai-platform-card {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #fff;
}

.ai-platform-card--clickable {
  cursor: pointer;
}

.ai-platform-card--clickable:hover {
  border-color: #bfd4ff;
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.08);
}

.ai-platform-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.ai-platform-card__head h3 {
  margin: 0;
  color: #111827;
  font-size: 15px;
}

.ai-platform-card__head p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
}

.ai-platform-card__tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.ai-platform-card__summary {
  color: #334155;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
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
  color: #94a3b8;
  font-size: 12px;
}

.ai-platform-card__meta-item strong {
  color: #111827;
  font-size: 12px;
  word-break: break-all;
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
  color: #475569;
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
  justify-content: space-between;
  gap: 12px;
  height: 44px;
  padding: 0 16px;
  border-top: 1px solid #eef2f7;
  background: #fff;
}

.ai-platform-shell__footer-total {
  color: #6b7280;
  font-size: 12px;
  white-space: nowrap;
}

.ai-platform-shell__footer-hint {
  color: #94a3b8;
  font-size: 12px;
}

.ai-platform-dialog-form :deep(.el-textarea__inner) {
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
}

.ai-platform-dialog__footer {
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
