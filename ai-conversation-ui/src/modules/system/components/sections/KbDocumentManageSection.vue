<script setup lang="ts">
import { ArrowLeft, Delete, EditPen, Plus, RefreshRight, Search, Upload, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AppPagination } from '../../../../components'
import { SERVICE_NAMES } from '../../../../config/services'
import { getEnumLabel, loadServiceEnums } from '../../../../stores/enums'
import {
  createOrUpdateAiKbDocument,
  deleteAiKbDocuments,
  getAiKbDocumentDetail,
  searchAiKbDocuments,
  syncAiKbDocuments,
  type AiKbDocumentDetail,
  type AiKbDocumentItem,
} from '../../api/aiPlatform'

type DialogMode = 'create' | 'edit'
type KbDocumentTab = 'current' | 'draft'

const DOCUMENT_TYPE_OPTIONS = [
  { label: '数据库表', value: 1 },
  { label: '常见业务', value: 2 },
  { label: '用户画像/偏好', value: 3 },
  { label: 'Render JSON 渲染场景', value: 4 },
  { label: '常见问题', value: 5 },
]

const BIZ_TYPE_OPTIONS = [
  { label: '数据库数据源', value: 1 },
  { label: '业务分析场景', value: 2 },
  { label: '用户画像/偏好场景', value: 3 },
  { label: 'Render JSON 渲染场景', value: 4 },
  { label: '常见问题场景', value: 5 },
]

const STATUS_LABELS: Record<string, string> = {
  1: '启用',
  2: '停用',
}

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const pageSize = ref(20)
const currentPage = ref(1)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const detailLoading = ref(false)
const syncSubmitting = ref(false)
const errorMessage = ref('')
const documentRows = ref<AiKbDocumentItem[]>([])
const activeTab = ref<KbDocumentTab>('current')
const dialogVisible = ref(false)
const detailVisible = ref(false)
const dialogMode = ref<DialogMode>('create')
const editingId = ref<string | number | null>(null)
const detailRecord = ref<AiKbDocumentDetail | null>(null)

const form = reactive({
  documentCode: '',
  documentName: '',
  documentType: 1,
  bizType: 1,
  content: '',
  extText: '',
})

const pageSizeOptions = [5, 10, 20, 50, 100, 200, 500]
const kbCode = computed(() => typeof route.params.sourceKey === 'string' ? route.params.sourceKey.trim() : '')
const currentKbTitle = computed(() => kbCode.value || '知识库')
const dialogTitle = computed(() => `${dialogMode.value === 'create' ? '新增' : '编辑'} KB 文档`)
const tabOptions = [
  { key: 'current' as const, label: '启用中' },
  { key: 'draft' as const, label: '已停用' },
]

const documentCards = computed(() => {
  return documentRows.value.map((item) => ({
    id: item.id,
    code: item.documentCode || '-',
    title: item.documentName || item.documentCode || '未命名文档',
    tags: [
      formatDocumentType(item.documentType),
      formatBizType(item.bizType),
      formatStatus(item.status),
    ].filter(Boolean),
    summary: item.bizKey || item.sourceSystem || '暂无业务信息',
    syncStatus: formatSyncStatus(item.providerSyncStatus),
    version: item.currentVersionNo ?? '-',
    format: item.contentFormat || '-',
    contentSize: item.contentSize ?? 0,
    updatedAt: formatDateTime(item.updateTime || item.lastGeneratedAt),
    raw: item,
  }))
})

function createEmptyForm() {
  return {
    documentCode: '',
    documentName: '',
    documentType: 1,
    bizType: 1,
    content: '',
    extText: '',
  }
}

function resetForm() {
  Object.assign(form, createEmptyForm())
}

function normalizeText(value?: string) {
  return value?.trim() || ''
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

function resolveTotal(payloadTotal?: number) {
  const numericTotal = Number(payloadTotal)
  return Number.isFinite(numericTotal) ? numericTotal : documentRows.value.length
}

function formatEnumLabel(value: string | number | undefined, options: Array<{ label: string, value: number }>) {
  const target = options.find(item => String(item.value) === String(value))
  return target?.label || (value === undefined || value === null ? '-' : String(value))
}

function formatDocumentType(value: string | number | undefined) {
  return formatEnumLabel(value, DOCUMENT_TYPE_OPTIONS)
}

function formatBizType(value: string | number | undefined) {
  return formatEnumLabel(value, BIZ_TYPE_OPTIONS)
}

function formatStatus(value: string | number | undefined) {
  return STATUS_LABELS[String(value)] || (value === undefined || value === null ? '-' : String(value))
}

function formatSyncStatus(value: string | number | undefined) {
  return String(getEnumLabel('aiKbProviderSyncStatus', value, SERVICE_NAMES.CHAT) || (value === undefined || value === null ? '-' : String(value)))
}

function parseExtText(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return {}
  }
  const parsed = JSON.parse(trimmed)
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error('扩展配置必须是 JSON 对象')
  }
  return parsed as Record<string, unknown>
}

function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

async function openEditDialog(row: AiKbDocumentItem) {
  const documentCode = row.documentCode || ''
  if (!kbCode.value || !documentCode) {
    ElMessage.error('缺少知识库或文档编码')
    return
  }

  detailLoading.value = true
  try {
    const detail = await getAiKbDocumentDetail(kbCode.value, documentCode)
    dialogMode.value = 'edit'
    editingId.value = detail.id ?? row.id
    form.documentCode = detail.documentCode || ''
    form.documentName = detail.documentName || ''
    form.documentType = Number(detail.documentType || 1)
    form.bizType = Number(detail.bizType || 1)
    form.content = detail.renderedContent || ''
    form.extText = detail.metaJson ? JSON.stringify(detail.metaJson, null, 2) : ''
    dialogVisible.value = true
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文档详情加载失败')
  }
  finally {
    detailLoading.value = false
  }
}

async function openDetailDialog(row: AiKbDocumentItem) {
  const documentCode = row.documentCode || ''
  if (!kbCode.value || !documentCode) {
    ElMessage.error('缺少知识库或文档编码')
    return
  }

  detailLoading.value = true
  try {
    detailRecord.value = await getAiKbDocumentDetail(kbCode.value, documentCode)
    detailVisible.value = true
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文档详情加载失败')
  }
  finally {
    detailLoading.value = false
  }
}

async function handleDelete(row: AiKbDocumentItem) {
  const documentCode = row.documentCode || ''
  try {
    await ElMessageBox.confirm(
      `确认删除文档「${row.documentName || documentCode || '-'}」吗？删除后不可恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )

    await deleteAiKbDocuments({
      kbCode: kbCode.value,
      documentCodes: [documentCode],
    })
    if (documentCards.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    ElMessage.success('文档删除成功')
    await loadDocuments()
  }
  catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '文档删除失败')
  }
}

function validateForm() {
  if (!kbCode.value) {
    return '缺少知识库编码'
  }
  if (!normalizeText(form.documentCode)) {
    return '请输入文档编码'
  }
  if (!normalizeText(form.documentName)) {
    return '请输入文档名称'
  }
  if (!normalizeText(form.content)) {
    return '请输入文档内容'
  }
  return ''
}

async function handleSubmit() {
  const validationError = validateForm()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }

  saving.value = true
  try {
    await createOrUpdateAiKbDocument({
      kbId: kbCode.value,
      documentId: normalizeText(form.documentCode),
      documentName: normalizeText(form.documentName),
      documentType: Number(form.documentType),
      bizType: Number(form.bizType),
      content: form.content,
      canUpdate: dialogMode.value === 'edit',
      ext: parseExtText(form.extText),
    })
    dialogVisible.value = false
    if (dialogMode.value === 'create') {
      currentPage.value = 1
    }
    ElMessage.success(`文档${dialogMode.value === 'create' ? '新增' : '更新'}成功`)
    await loadDocuments()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `文档${dialogMode.value === 'create' ? '新增' : '更新'}失败`)
  }
  finally {
    saving.value = false
  }
}

async function loadDocuments() {
  if (!kbCode.value) {
    documentRows.value = []
    total.value = 0
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    const payload = await searchAiKbDocuments({
      kbCode: kbCode.value,
      keyword: normalizeText(keyword.value) || undefined,
      tab: activeTab.value,
      page: currentPage.value,
      size: pageSize.value,
    })
    documentRows.value = payload?.list ?? []
    total.value = resolveTotal(payload?.pageInfo?.total)
  }
  catch (error) {
    documentRows.value = []
    total.value = 0
    errorMessage.value = error instanceof Error ? error.message : 'KB 文档列表加载失败'
  }
  finally {
    loading.value = false
  }
}

async function handleSearch() {
  currentPage.value = 1
  await loadDocuments()
}

async function handleRefresh() {
  await loadDocuments()
}

async function handleSyncKnowledgeBase() {
  if (!kbCode.value) {
    ElMessage.error('缺少知识库编码，无法发起同步')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认同步知识库「${kbCode.value}」下的当前文档吗？`,
      '知识库同步',
      {
        type: 'warning',
        confirmButtonText: '确认同步',
        cancelButtonText: '取消',
      },
    )

    syncSubmitting.value = true
    const result = await syncAiKbDocuments({
      kbCode: kbCode.value,
    })
    const acceptedCount = Number(result?.acceptedCount || 0)
    const skippedCount = Array.isArray(result?.skippedDocumentCodes) ? result.skippedDocumentCodes.length : 0
    ElMessage.success(`知识库同步已发起，受理 ${acceptedCount} 条，跳过 ${skippedCount} 条`)
    await loadDocuments()
  }
  catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '知识库同步失败')
  }
  finally {
    syncSubmitting.value = false
  }
}

async function handleCurrentPageChange(page: number) {
  currentPage.value = page
  await loadDocuments()
}

async function handlePageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  await loadDocuments()
}

async function handleChangeTab(tab: KbDocumentTab) {
  if (activeTab.value === tab) {
    return
  }
  activeTab.value = tab
  currentPage.value = 1
  await loadDocuments()
}

async function navigateBack() {
  await router.push('/settings/system/ai-platform')
}

watch(kbCode, () => {
  keyword.value = ''
  currentPage.value = 1
  void loadDocuments()
})

onMounted(() => {
  void loadServiceEnums(SERVICE_NAMES.CHAT)
  void loadDocuments()
})
</script>

<template>
  <section class="kb-document-page">
    <div class="kb-document-shell">
      <header class="kb-document-shell__header">
        <div class="kb-document-shell__title">
          <el-button text @click="navigateBack">
            <el-icon><ArrowLeft /></el-icon>
            返回知识库
          </el-button>
          <div>
            <h3>{{ currentKbTitle }}</h3>
            <p>维护当前知识库下的 KB 文档与正文内容。</p>
          </div>
        </div>
        <div class="kb-document-shell__tools">
          <div class="kb-document-shell__tabs">
            <el-tag
              v-for="tab in tabOptions"
              :key="tab.key"
              :type="activeTab === tab.key ? 'primary' : 'info'"
              effect="plain"
              class="kb-document-shell__tab"
              @click="handleChangeTab(tab.key)"
            >
              {{ tab.label }}
            </el-tag>
          </div>
          <el-input v-model="keyword" placeholder="搜索文档名称 / 编码 / bizKey" clearable @keyup.enter="handleSearch">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button plain :loading="loading" @click="handleRefresh">
            <el-icon><RefreshRight /></el-icon>
            刷新
          </el-button>
          <el-button plain :loading="syncSubmitting" @click="handleSyncKnowledgeBase">
            <el-icon><Upload /></el-icon>
            知识库同步
          </el-button>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新增文档
          </el-button>
        </div>
      </header>

      <main class="kb-document-shell__main">
        <div v-if="errorMessage" class="kb-document-shell__state kb-document-shell__state--error">
          {{ errorMessage }}
        </div>
        <div v-else-if="loading" class="kb-document-shell__state">
          正在加载 KB 文档列表...
        </div>
        <div v-else-if="!documentCards.length" class="kb-document-shell__state">
          当前知识库下没有文档
        </div>
        <div v-else class="kb-document-grid">
          <article v-for="card in documentCards" :key="card.id" class="kb-document-card">
            <div class="kb-document-card__head">
              <div>
                <h3>{{ card.title }}</h3>
                <p>{{ card.code }}</p>
              </div>
              <div class="kb-document-card__tags">
                <el-tag v-for="tag in card.tags" :key="tag" size="small" effect="plain">
                  {{ tag }}
                </el-tag>
              </div>
            </div>
            <div class="kb-document-card__summary">{{ card.summary }}</div>
            <div class="kb-document-card__meta">
              <div class="kb-document-card__meta-item">
                <span>同步状态</span>
                <strong>{{ card.syncStatus }}</strong>
              </div>
              <div class="kb-document-card__meta-item">
                <span>版本</span>
                <strong>{{ card.version }}</strong>
              </div>
              <div class="kb-document-card__meta-item">
                <span>格式</span>
                <strong>{{ card.format }}</strong>
              </div>
              <div class="kb-document-card__meta-item">
                <span>大小</span>
                <strong>{{ card.contentSize }}</strong>
              </div>
              <div class="kb-document-card__meta-item">
                <span>更新时间</span>
                <strong>{{ card.updatedAt }}</strong>
              </div>
            </div>
            <div class="kb-document-card__actions">
              <el-button plain circle title="详情" :loading="detailLoading" @click="openDetailDialog(card.raw)">
                <el-icon><View /></el-icon>
              </el-button>
              <el-button plain circle title="编辑" :loading="detailLoading" @click="openEditDialog(card.raw)">
                <el-icon><EditPen /></el-icon>
              </el-button>
              <el-button plain circle type="danger" title="删除" @click="handleDelete(card.raw)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </article>
        </div>
      </main>

      <footer class="kb-document-shell__footer">
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="820px" destroy-on-close>
      <el-form label-width="112px" class="kb-document-dialog-form">
        <el-form-item label="文档编码" required>
          <el-input v-model="form.documentCode" :disabled="dialogMode === 'edit'" placeholder="建议使用唯一业务编码" />
        </el-form-item>
        <el-form-item label="文档名称" required>
          <el-input v-model="form.documentName" placeholder="请输入文档名称" />
        </el-form-item>
        <el-form-item label="文档类型" required>
          <el-select v-model="form.documentType" placeholder="请选择文档类型">
            <el-option v-for="item in DOCUMENT_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务类型" required>
          <el-select v-model="form.bizType" placeholder="请选择业务类型">
            <el-option v-for="item in BIZ_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="正文内容" required>
          <el-input v-model="form.content" type="textarea" :rows="14" placeholder="请输入 Markdown 文档内容" />
        </el-form-item>
        <el-form-item label="扩展配置">
          <el-input
            v-model="form.extText"
            type="textarea"
            :rows="6"
            placeholder="{&#10;  &quot;sourceSystem&quot;: &quot;manual&quot;&#10;}"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="kb-document-dialog__footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmit">
            {{ dialogMode === 'create' ? '确认新增' : '确认保存' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="KB 文档详情" width="880px" destroy-on-close>
      <div v-if="detailRecord" class="kb-document-detail">
        <div class="kb-document-detail__summary">
          <div><span>文档编码</span><strong>{{ detailRecord.documentCode || '-' }}</strong></div>
          <div><span>文档名称</span><strong>{{ detailRecord.documentName || '-' }}</strong></div>
          <div><span>文档类型</span><strong>{{ formatDocumentType(detailRecord.documentType) }}</strong></div>
          <div><span>业务类型</span><strong>{{ formatBizType(detailRecord.bizType) }}</strong></div>
          <div><span>状态</span><strong>{{ formatStatus(detailRecord.status) }}</strong></div>
          <div><span>同步状态</span><strong>{{ formatSyncStatus(detailRecord.providerSyncStatus) }}</strong></div>
        </div>
        <div class="kb-document-detail__block">
          <h4>正文内容</h4>
          <pre>{{ detailRecord.renderedContent || '暂无正文内容' }}</pre>
        </div>
        <div class="kb-document-detail__block">
          <h4>Meta JSON</h4>
          <pre>{{ detailRecord.metaJson ? JSON.stringify(detailRecord.metaJson, null, 2) : '{}' }}</pre>
        </div>
        <div class="kb-document-detail__block">
          <h4>Ext JSON</h4>
          <pre>{{ detailRecord.extJson ? JSON.stringify(detailRecord.extJson, null, 2) : '{}' }}</pre>
        </div>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.kb-document-page {
  display: flex;
  flex: 1;
  min-height: 0;
}

.kb-document-shell {
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

.kb-document-shell__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--system-border-subtle);
}

.kb-document-shell__title {
  display: grid;
  gap: 6px;
}

.kb-document-shell__title h3 {
  margin: 0;
  color: var(--system-title);
  font-size: 17px;
}

.kb-document-shell__title p {
  margin: 0;
  color: var(--system-text-soft);
  font-size: 12px;
}

.kb-document-shell__tools {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.kb-document-shell__tabs {
  display: flex;
  align-items: center;
  gap: 6px;
}

.kb-document-shell__tab {
  cursor: pointer;
}

.kb-document-shell__tools :deep(.el-input) {
  width: 240px;
}

.kb-document-shell__tools :deep(.el-input__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
}

.kb-document-shell__tools :deep(.el-input__inner),
.kb-document-shell__tools :deep(.el-input__prefix-inner) {
  color: var(--system-text);
}

.kb-document-shell__tools :deep(.el-button) {
  border-radius: 10px;
}

.kb-document-shell__tools :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.kb-document-shell__tools :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-accent-text);
  color: #08111f;
}

.kb-document-shell__main {
  min-height: 0;
  padding: 10px 12px;
  background: var(--system-surface-muted);
  overflow-y: auto;
}

.kb-document-shell__state {
  display: grid;
  place-items: center;
  min-height: 260px;
  color: var(--system-text-muted);
  font-size: 13px;
}

.kb-document-shell__state--error {
  color: var(--system-danger);
}

.kb-document-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 10px;
}

.kb-document-card {
  display: grid;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--system-border);
  border-radius: 12px;
  background: var(--system-surface-solid);
}

.kb-document-card__head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.kb-document-card__head h3 {
  margin: 0;
  color: var(--system-title);
  font-size: 14px;
  line-height: 1.3;
}

.kb-document-card__head p {
  margin: 2px 0 0;
  color: var(--system-text-soft);
  font-size: 11px;
  line-height: 1.3;
}

.kb-document-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
}

.kb-document-card__tags :deep(.el-tag) {
  padding: 0 6px;
}

.kb-document-card__summary {
  color: var(--system-text);
  font-size: 12px;
  line-height: 1.45;
  word-break: break-all;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.kb-document-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 10px;
}

.kb-document-card__meta-item {
  display: grid;
  gap: 2px;
}

.kb-document-card__meta-item span {
  color: var(--system-text-faint);
  font-size: 11px;
}

.kb-document-card__meta-item strong {
  color: var(--system-title);
  font-size: 11px;
  line-height: 1.35;
  word-break: break-all;
}

.kb-document-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.kb-document-card__actions :deep(.el-button.is-circle) {
  padding: 7px;
}

.kb-document-shell__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 40px;
  padding: 0 14px;
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-solid);
}

.kb-document-page :deep(.el-overlay-dialog) {
  display: flex;
  align-items: center;
  justify-content: center;
}

.kb-document-page :deep(.el-dialog) {
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
}

.kb-document-page :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.kb-document-page :deep(.el-dialog__body) {
  background: var(--system-surface-strong);
}

.kb-document-page :deep(.el-dialog__footer) {
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.kb-document-dialog-form :deep(.el-form-item__label) {
  color: var(--system-text-soft);
}

.kb-document-dialog-form :deep(.el-input__wrapper),
.kb-document-dialog-form :deep(.el-textarea__inner),
.kb-document-dialog-form :deep(.el-select__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
  color: var(--system-text);
}

.kb-document-dialog-form :deep(.el-textarea__inner),
.kb-document-detail pre {
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
}

.kb-document-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.kb-document-dialog__footer :deep(.el-button) {
  min-width: 76px;
  border-radius: 10px;
}

.kb-document-dialog__footer :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.kb-document-dialog__footer :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-accent-text);
  color: #08111f;
}

.kb-document-detail {
  display: grid;
  gap: 16px;
}

.kb-document-detail__summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.kb-document-detail__summary div {
  display: grid;
  gap: 4px;
}

.kb-document-detail__summary span {
  color: var(--system-text-faint);
  font-size: 12px;
}

.kb-document-detail__summary strong {
  color: var(--system-title);
  font-size: 13px;
  word-break: break-all;
}

.kb-document-detail__block {
  display: grid;
  gap: 8px;
}

.kb-document-detail__block h4 {
  margin: 0;
  color: var(--system-title);
  font-size: 14px;
}

.kb-document-detail__block pre {
  margin: 0;
  padding: 12px;
  border-radius: 12px;
  background: var(--system-surface-muted);
  color: var(--system-text);
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 960px) {
  .kb-document-shell__header {
    flex-direction: column;
  }

  .kb-document-shell__tools {
    justify-content: flex-start;
  }

  .kb-document-shell__tools :deep(.el-input) {
    width: 100%;
  }

  .kb-document-card__meta,
  .kb-document-detail__summary {
    grid-template-columns: 1fr;
  }

  .kb-document-card__actions {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .kb-document-shell__footer {
    height: auto;
    padding: 10px 12px;
    flex-wrap: wrap;
  }
}
</style>
