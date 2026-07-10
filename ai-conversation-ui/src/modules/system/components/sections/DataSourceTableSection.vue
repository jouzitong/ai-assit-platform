<script setup lang="ts">
import {
  ArrowLeft,
  Download,
  Delete,
  EditPen,
  FolderAdd,
  Key,
  Reading,
  RefreshRight,
  Search,
  Tickets,
  Upload,
  View,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AppCodeEditor, AppPagination } from '../../../../components'
import {
  listDbAccessTables,
  downloadDbMetaTemplateWorkbook,
  exportDbMetaWorkbook,
  previewDbTableKnowledge,
  searchDbTableFields,
  searchDbTables,
  streamDbMetaImportWorkbook,
  syncDbTableKnowledge,
  syncDbAccessTableMeta,
  type DbAccessTableCandidate,
  type DbTableFieldMetaItem,
  type DbTableMetaItem,
} from '../../api/dataSources'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const pageSize = ref(20)
const currentPage = ref(1)
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const tableRows = ref<DbTableMetaItem[]>([])
const tableCatalog = ref<DbTableMetaItem[]>([])
const fieldRows = ref<DbTableFieldMetaItem[]>([])
const fieldLoading = ref(false)
const fieldErrorMessage = ref('')
const importDialogVisible = ref(false)
const importFile = ref<File | null>(null)
const importError = ref('')
const importFormat = ref<'json' | 'excel'>('json')
const importSubmitting = ref(false)
const importProgressDialogVisible = ref(false)
const importProgress = ref({
  jobId: '',
  fileName: '',
  status: 'IDLE',
  stage: '',
  message: '',
  progressPercent: 0,
  createdTableCount: 0,
  updatedTableCount: 0,
  createdFieldCount: 0,
  updatedFieldCount: 0,
})
const exportDialogVisible = ref(false)
const exportFormat = ref<'json' | 'excel'>('json')
const exportSubmitting = ref(false)
const templateSubmitting = ref(false)
const knowledgePreviewDialogVisible = ref(false)
const knowledgePreviewLoading = ref(false)
const knowledgePreviewError = ref('')
const knowledgePreviewTableName = ref('')
const knowledgePreviewType = ref('markdown')
const knowledgePreviewContent = ref('')
const knowledgeSyncSubmitting = ref(false)
const knowledgeSyncTarget = ref('')
const tableSyncDialogVisible = ref(false)
const tableSyncLoading = ref(false)
const tableSyncSubmitting = ref(false)
const tableSyncError = ref('')
const tableSyncAllowUpdate = ref(false)
const tableSyncCandidates = ref<Array<{
  name: string
  comment: string
  synced: boolean
  fieldCount: number | null
}>>([])
const tableSyncSelectedTables = ref<string[]>([])

const sourceKey = computed(() => typeof route.params.sourceKey === 'string' ? route.params.sourceKey : '')
const currentMode = computed(() => route.query.mode === 'fields' ? 'fields' : 'tables')
const selectedTableName = computed(() => typeof route.query.table === 'string' ? route.query.table : '')
const activeTable = computed(() => tableCatalog.value.find(item => item.tableName === selectedTableName.value) || null)
const tableSyncSelectedCount = computed(() => tableSyncSelectedTables.value.length)
const tableSyncCandidateCount = computed(() => tableSyncCandidates.value.length)
const tableSyncPendingCount = computed(() => tableSyncCandidates.value.filter(item => !item.synced).length)
const tableSyncAllChecked = computed(() => tableSyncCandidateCount.value > 0 && tableSyncSelectedCount.value === tableSyncCandidateCount.value)
const tableSyncIndeterminate = computed(() => tableSyncSelectedCount.value > 0 && tableSyncSelectedCount.value < tableSyncCandidateCount.value)
const importFormatLabel = computed(() => importFormat.value === 'json' ? 'JSON' : 'Excel')
const knowledgePreviewFormat = computed(() => knowledgePreviewType.value === 'markdown' ? 'markdown' : 'text')

function resolveTotal(payloadTotal?: number) {
  const numericTotal = Number(payloadTotal)
  return Number.isFinite(numericTotal) ? numericTotal : tableRows.value.length
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

async function loadTables() {
  if (!sourceKey.value) {
    tableRows.value = []
    total.value = 0
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    const payload = await searchDbTables({
      page: currentPage.value,
      size: pageSize.value,
      sourceKey: sourceKey.value,
      keyword: keyword.value.trim() || undefined,
    })
    tableRows.value = payload?.list ?? []
    total.value = resolveTotal(payload?.pageInfo?.total)
  }
  catch (error) {
    tableRows.value = []
    total.value = 0
    errorMessage.value = error instanceof Error ? error.message : '数据表列表加载失败'
  }
  finally {
    loading.value = false
  }
}

async function loadTableCatalog() {
  if (!sourceKey.value) {
    tableCatalog.value = []
    return
  }

  try {
    const payload = await searchDbTables({
      page: 1,
      size: 500,
      sourceKey: sourceKey.value,
    })
    tableCatalog.value = payload?.list ?? []
  }
  catch {
    tableCatalog.value = []
  }
}

async function loadFields() {
  if (!sourceKey.value || !selectedTableName.value) {
    fieldRows.value = []
    fieldErrorMessage.value = ''
    return
  }

  fieldLoading.value = true
  fieldErrorMessage.value = ''
  try {
    const payload = await searchDbTableFields({
      page: 1,
      size: 500,
      sourceKey: sourceKey.value,
      tableName: selectedTableName.value,
    })
    fieldRows.value = (payload?.list ?? []).slice().sort((a, b) => Number(a.ordinalPosition ?? 0) - Number(b.ordinalPosition ?? 0))
  }
  catch (error) {
    fieldRows.value = []
    fieldErrorMessage.value = error instanceof Error ? error.message : '字段列表加载失败'
  }
  finally {
    fieldLoading.value = false
  }
}

async function handleSearch() {
  currentPage.value = 1
  await loadTables()
}

async function handleRefresh() {
  await loadTables()
}

async function handleCurrentPageChange(page: number) {
  currentPage.value = page
  await loadTables()
}

async function handlePageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  await loadTables()
}

async function navigateBack() {
  await router.push('/settings/system/data-source')
}

function handleTableAction(actionLabel: string, row: DbTableMetaItem) {
  if (actionLabel === '字段') {
    void router.push({
      path: `/settings/system/data-source/${sourceKey.value}`,
      query: {
        mode: 'fields',
        table: row.tableName || '',
      },
    })
    return
  }
  if (actionLabel === '知识库预览') {
    void openKnowledgePreview(row)
    return
  }
  if (actionLabel === '同步') {
    void syncKnowledgeBase(row)
    return
  }
  ElMessage.info(`${actionLabel}：${row.tableName || '-'}`)
}

function handleHeaderAction(actionLabel: string) {
  if (actionLabel === '导入') {
    openImportDialog()
    return
  }
  if (actionLabel === '导出') {
    openExportDialog()
    return
  }
  if (actionLabel === '表格同步') {
    void openTableSyncDialog()
    return
  }
  if (actionLabel === '知识库同步') {
    void syncKnowledgeBase()
    return
  }
  ElMessage.info(`${actionLabel}：${sourceKey.value || '-'}`)
}

function openImportDialog() {
  importDialogVisible.value = true
  importError.value = ''
  importFormat.value = 'json'
  importFile.value = null
}

function closeImportDialog() {
  importDialogVisible.value = false
  importError.value = ''
  importFile.value = null
  importFormat.value = 'json'
}

function openExportDialog() {
  exportDialogVisible.value = true
  exportFormat.value = 'json'
}

function closeExportDialog() {
  exportDialogVisible.value = false
  exportFormat.value = 'json'
}

function resetKnowledgePreview() {
  knowledgePreviewLoading.value = false
  knowledgePreviewError.value = ''
  knowledgePreviewTableName.value = ''
  knowledgePreviewType.value = 'markdown'
  knowledgePreviewContent.value = ''
}

function closeKnowledgePreviewDialog() {
  knowledgePreviewDialogVisible.value = false
  resetKnowledgePreview()
}

async function openKnowledgePreview(row: DbTableMetaItem) {
  if (!sourceKey.value) {
    ElMessage.error('当前数据源信息不完整，无法预览知识库')
    return
  }

  const tableName = row.tableName || ''
  if (!tableName) {
    ElMessage.error('当前数据表信息不完整，无法预览知识库')
    return
  }

  knowledgePreviewDialogVisible.value = true
  knowledgePreviewLoading.value = true
  knowledgePreviewError.value = ''
  knowledgePreviewTableName.value = tableName
  knowledgePreviewType.value = 'markdown'
  knowledgePreviewContent.value = ''

  try {
    const payload = await previewDbTableKnowledge(sourceKey.value, tableName)
    knowledgePreviewType.value = String(payload?.type || 'markdown').toLowerCase()
    knowledgePreviewContent.value = payload?.content || ''
  }
  catch (error) {
    knowledgePreviewError.value = error instanceof Error ? error.message : '知识库预览加载失败'
  }
  finally {
    knowledgePreviewLoading.value = false
  }
}

function handleImportFileChange(uploadFile: { raw?: File }) {
  const file = uploadFile.raw || null
  if (!file) {
    return
  }
  const extension = String(file.name || '').split('.').pop()?.toLowerCase()
  if (extension === 'xlsx') {
    importFormat.value = 'excel'
  }
  else if (extension === 'json') {
    importFormat.value = 'json'
  }
  importFile.value = file
  importError.value = ''
}

function beforeImportUpload() {
  return false
}

function isImportFileFormatMatched(file: File, format: 'json' | 'excel') {
  const extension = String(file.name || '').split('.').pop()?.toLowerCase()
  return format === 'json' ? extension === 'json' : extension === 'xlsx'
}

function resetImportProgress() {
  importProgress.value = {
    jobId: '',
    fileName: '',
    status: 'IDLE',
    stage: '',
    message: '',
    progressPercent: 0,
    createdTableCount: 0,
    updatedTableCount: 0,
    createdFieldCount: 0,
    updatedFieldCount: 0,
  }
}

function parseImportStreamChunk(chunk: string) {
  const lines = chunk.split('\n')
  const dataLines: string[] = []

  lines.forEach((line) => {
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  })

  const rawData = dataLines.join('\n')
  if (!rawData) {
    return null
  }

  try {
    return JSON.parse(rawData) as Record<string, unknown>
  }
  catch {
    return null
  }
}

async function consumeImportStream(file: File) {
  const response = await streamDbMetaImportWorkbook(sourceKey.value, file)
  if (!response.body) {
    throw new Error('导入进度流为空')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }

    buffer += decoder.decode(value, { stream: true })
    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() ?? ''

    chunks.forEach((chunk) => {
      const parsed = parseImportStreamChunk(chunk.trim())
      if (!parsed) {
        return
      }

      importProgress.value.jobId = String(parsed.jobId ?? importProgress.value.jobId)
      importProgress.value.fileName = String(parsed.fileName ?? importProgress.value.fileName)
      importProgress.value.status = String(parsed.status ?? importProgress.value.status)
      importProgress.value.stage = String(parsed.stage ?? importProgress.value.stage)
      importProgress.value.message = String(parsed.message ?? importProgress.value.message)
      importProgress.value.progressPercent = Number(parsed.progressPercent ?? importProgress.value.progressPercent ?? 0)
      importProgress.value.createdTableCount = Number(parsed.createdTableCount ?? importProgress.value.createdTableCount ?? 0)
      importProgress.value.updatedTableCount = Number(parsed.updatedTableCount ?? importProgress.value.updatedTableCount ?? 0)
      importProgress.value.createdFieldCount = Number(parsed.createdFieldCount ?? importProgress.value.createdFieldCount ?? 0)
      importProgress.value.updatedFieldCount = Number(parsed.updatedFieldCount ?? importProgress.value.updatedFieldCount ?? 0)
    })
  }
}

async function submitImport() {
  if (!sourceKey.value) {
    importError.value = '当前没有可用数据源'
    return
  }
  if (!importFile.value) {
    importError.value = `请先选择要导入的 ${importFormatLabel.value} 文件`
    return
  }
  if (!isImportFileFormatMatched(importFile.value, importFormat.value)) {
    importError.value = importFormat.value === 'json'
      ? '当前选择的是 JSON 导入，请上传 .json 文件'
      : '当前选择的是 Excel 导入，请上传 .xlsx 文件'
    return
  }

  importSubmitting.value = true
  importError.value = ''
  try {
    resetImportProgress()
    importProgress.value.fileName = importFile.value.name
    importProgress.value.status = 'PENDING'
    importProgress.value.stage = 'QUEUED'
    importProgress.value.message = '导入任务已创建，等待后台处理'
    importProgressDialogVisible.value = true
    await consumeImportStream(importFile.value)
    await loadTables()
    await loadTableCatalog()
    ElMessage.success('导入完成')
    closeImportDialog()
  }
  catch (error) {
    importError.value = error instanceof Error ? error.message : '导入失败'
  }
  finally {
    importSubmitting.value = false
  }
}

function triggerBrowserDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

async function exportWorkbook() {
  if (!sourceKey.value) {
    ElMessage.error('当前没有可导出的数据源')
    return
  }
  exportSubmitting.value = true
  try {
    const format = exportFormat.value === 'json' ? 'json' : 'xlsx'
    const { blob, filename } = await exportDbMetaWorkbook(sourceKey.value, format)
    triggerBrowserDownload(blob, filename)
    closeExportDialog()
    ElMessage.success('导出成功')
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败')
  }
  finally {
    exportSubmitting.value = false
  }
}

async function downloadTemplateWorkbook() {
  templateSubmitting.value = true
  try {
    const format = importFormat.value === 'json' ? 'json' : 'xlsx'
    const { blob, filename } = await downloadDbMetaTemplateWorkbook(format)
    triggerBrowserDownload(blob, filename)
    ElMessage.success('模板下载成功')
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模板下载失败')
  }
  finally {
    templateSubmitting.value = false
  }
}

function mapTableSyncCandidate(item: DbAccessTableCandidate) {
  return {
    name: item.tableName || '-',
    comment: item.tableComment || '暂无说明',
    synced: item.existsInMeta === true,
    fieldCount: item.fieldCount ?? null,
  }
}

async function openTableSyncDialog() {
  if (!sourceKey.value) {
    ElMessage.error('当前数据源信息不完整，无法同步表格')
    return
  }
  tableSyncDialogVisible.value = true
  tableSyncError.value = ''
  tableSyncAllowUpdate.value = false
  await loadTableSyncCandidates()
}

function closeTableSyncDialog() {
  tableSyncDialogVisible.value = false
  tableSyncLoading.value = false
  tableSyncSubmitting.value = false
  tableSyncError.value = ''
  tableSyncAllowUpdate.value = false
  tableSyncCandidates.value = []
  tableSyncSelectedTables.value = []
}

async function loadTableSyncCandidates() {
  if (!sourceKey.value) {
    tableSyncCandidates.value = []
    tableSyncSelectedTables.value = []
    return
  }
  tableSyncLoading.value = true
  tableSyncError.value = ''
  try {
    const payload = await listDbAccessTables({ sourceKey: sourceKey.value })
    tableSyncCandidates.value = (payload?.tables ?? []).map(mapTableSyncCandidate)
    tableSyncSelectedTables.value = tableSyncCandidates.value.filter(item => !item.synced).map(item => item.name)
  }
  catch (error) {
    tableSyncError.value = error instanceof Error ? error.message : '同步表格列表加载失败'
    tableSyncCandidates.value = []
    tableSyncSelectedTables.value = []
  }
  finally {
    tableSyncLoading.value = false
  }
}

function toggleTableSyncSelection(tableName: string, checked: boolean) {
  if (!tableName) {
    return
  }
  if (checked) {
    if (!tableSyncSelectedTables.value.includes(tableName)) {
      tableSyncSelectedTables.value = [...tableSyncSelectedTables.value, tableName]
    }
    return
  }
  tableSyncSelectedTables.value = tableSyncSelectedTables.value.filter(item => item !== tableName)
}

function toggleAllTableSyncSelection(checked: boolean) {
  tableSyncSelectedTables.value = checked ? tableSyncCandidates.value.map(item => item.name) : []
}

async function submitTableSync() {
  if (!sourceKey.value) {
    ElMessage.error('当前数据源信息不完整，无法同步表格')
    return
  }
  if (!tableSyncSelectedTables.value.length) {
    tableSyncError.value = '请至少勾选一张需要同步的数据表'
    return
  }
  tableSyncSubmitting.value = true
  tableSyncError.value = ''
  try {
    const payload = await syncDbAccessTableMeta({
      sourceKey: sourceKey.value,
      tables: tableSyncSelectedTables.value,
      allowUpdate: tableSyncAllowUpdate.value,
    })
    await loadTables()
    await loadTableCatalog()
    ElMessage.success(
      `表格同步完成：新增表 ${Number(payload?.createdTableCount ?? 0)}，更新表 ${Number(payload?.updatedTableCount ?? 0)}，新增字段 ${Number(payload?.createdFieldCount ?? 0)}，更新字段 ${Number(payload?.updatedFieldCount ?? 0)}`,
    )
    closeTableSyncDialog()
  }
  catch (error) {
    tableSyncError.value = error instanceof Error ? error.message : '表格同步失败'
  }
  finally {
    tableSyncSubmitting.value = false
  }
}

async function syncKnowledgeBase(row?: DbTableMetaItem) {
  if (!sourceKey.value) {
    ElMessage.error('当前数据源信息不完整，无法同步知识库')
    return
  }
  if (knowledgeSyncSubmitting.value) {
    return
  }

  const tableName = row?.tableName || ''
  const title = tableName ? '同步当前表到知识库' : '同步当前数据源到知识库'
  const message = tableName
    ? `确认同步数据表「${tableName}」到知识库吗？`
    : `确认同步当前数据源「${sourceKey.value}」下的全部数据表到知识库吗？`

  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: '确认同步',
      cancelButtonText: '取消',
      draggable: true,
      overflow: true,
    })
  }
  catch {
    return
  }

  knowledgeSyncSubmitting.value = true
  knowledgeSyncTarget.value = tableName
  try {
    const payload = await syncDbTableKnowledge({
      sourceKey: sourceKey.value,
      tableName: tableName || undefined,
    })
    ElMessage.success(
      `知识库同步完成：共 ${Number(payload?.totalCount ?? 0)} 张表，新增 ${Number(payload?.createdCount ?? 0)}，更新 ${Number(payload?.updatedCount ?? 0)}，未变更 ${Number(payload?.unchangedCount ?? 0)}`,
    )
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识库同步失败')
  }
  finally {
    knowledgeSyncSubmitting.value = false
    knowledgeSyncTarget.value = ''
  }
}

watch(sourceKey, () => {
  keyword.value = ''
  currentPage.value = 1
  void loadTables()
  void loadTableCatalog()
  void loadFields()
})

watch([currentMode, selectedTableName], () => {
  if (currentMode.value === 'fields') {
    void loadFields()
  }
})

onMounted(() => {
  void loadTables()
  void loadTableCatalog()
  void loadFields()
})
</script>

<template>
  <section class="data-source-table-page">
    <el-container class="data-source-table-layout">
      <el-header class="data-source-table-layout__header">
        <div class="data-source-table-layout__header-inner">
          <div class="data-source-table-layout__title">
            <el-button text @click="navigateBack">
              <el-icon><ArrowLeft /></el-icon>
              返回数据源列表
            </el-button>
            <h3>{{ sourceKey || '数据表列表' }}</h3>
            <p>当前数据源下的数据表元信息。</p>
          </div>
          <div class="data-source-table-layout__tools">
            <el-input v-model="keyword" placeholder="搜索表名 / 表说明" clearable @keyup.enter="handleSearch">
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button plain @click="handleHeaderAction('导入')">
              <el-icon><Upload /></el-icon>
              导入
            </el-button>
            <el-button plain @click="handleHeaderAction('导出')">
              <el-icon><Download /></el-icon>
              导出
            </el-button>
            <el-button plain @click="handleHeaderAction('表格同步')">
              <el-icon><RefreshRight /></el-icon>
              表格同步
            </el-button>
            <el-button plain :loading="knowledgeSyncSubmitting && !knowledgeSyncTarget" :disabled="knowledgeSyncSubmitting" @click="handleHeaderAction('知识库同步')">
              <el-icon v-if="!knowledgeSyncSubmitting || knowledgeSyncTarget"><FolderAdd /></el-icon>
              知识库同步
            </el-button>
            <el-button plain @click="handleRefresh">
              <el-icon><RefreshRight /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </el-header>

      <el-main class="data-source-table-layout__main">
        <div class="data-source-table-layout__panel">
          <div v-if="currentMode === 'tables' && errorMessage" class="data-source-table-state data-source-table-state--error">
            {{ errorMessage }}
          </div>
          <section v-else-if="currentMode === 'fields'" class="data-source-field-browser">
            <aside class="data-source-field-browser__sidebar">
              <div class="data-source-field-browser__sidebar-title">表切换</div>
              <h4>表名称</h4>
              <button
                v-for="table in tableCatalog"
                :key="table.id"
                :class="['data-source-field-browser__table', { 'is-active': table.tableName === selectedTableName }]"
                type="button"
                @click="router.push({ path: `/settings/system/data-source/${sourceKey}`, query: { mode: 'fields', table: table.tableName || '' } })"
              >
                <strong>{{ table.tableName }}</strong>
                <span>{{ table.columnCount ?? '-' }} 字段 · {{ table.enabled === false ? '停用' : '可用' }}</span>
              </button>
            </aside>

            <section class="data-source-field-browser__content">
              <section class="data-source-field-browser__summary">
                <div class="data-source-field-browser__label">表元数据信息</div>
                <h3>{{ activeTable?.tableName || '-' }}</h3>
                <div class="data-source-field-browser__cards">
                  <div class="data-source-field-browser__card">
                    <span>状态</span>
                    <strong>{{ activeTable?.status || '-' }}</strong>
                  </div>
                  <div class="data-source-field-browser__card data-source-field-browser__card--remark">
                    <span>备注</span>
                    <p>{{ activeTable?.remark || '暂无备注' }}</p>
                  </div>
                </div>
              </section>

              <section class="data-source-field-browser__table-wrap">
                <div class="data-source-field-browser__label">字段列表</div>
                <h3>{{ activeTable?.tableName || '-' }}</h3>
                <div v-if="fieldErrorMessage" class="data-source-table-state data-source-table-state--error">
                  {{ fieldErrorMessage }}
                </div>
                <el-table
                  v-else
                  v-loading="fieldLoading"
                  :data="fieldRows"
                  border
                  stripe
                  height="100%"
                  class="data-source-table"
                  empty-text="暂无字段"
                  header-cell-class-name="data-source-table__header-cell"
                >
                  <el-table-column prop="columnName" label="字段名" min-width="180" show-overflow-tooltip />
                  <el-table-column prop="dataType" label="类型" width="120" />
                  <el-table-column prop="primaryKey" label="索引" width="110" align="center">
                    <template #default="{ row }">
                      {{ row.primaryKey ? '主键' : '无' }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="tableName" label="关联表" min-width="140" align="center">
                    <template #default>无</template>
                  </el-table-column>
                  <el-table-column prop="columnComment" label="描述" min-width="180" show-overflow-tooltip>
                    <template #default="{ row }">
                      {{ row.columnComment || '-' }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="fieldRole" label="角色" width="120" align="center">
                    <template #default="{ row }">
                      {{ row.fieldRole || '-' }}
                    </template>
                  </el-table-column>
                </el-table>
              </section>
            </section>
          </section>
          <el-table
            v-else
            v-loading="loading"
            :data="tableRows"
            height="100%"
            border
            stripe
            class="data-source-table"
            empty-text="暂无数据表"
            header-cell-class-name="data-source-table__header-cell"
          >
            <el-table-column prop="tableName" label="表名" min-width="220" fixed="left" show-overflow-tooltip />
            <el-table-column prop="tableComment" label="表说明" min-width="220" show-overflow-tooltip />
            <el-table-column prop="tableType" label="类型" width="100" />
            <el-table-column prop="layerType" label="分层" width="100" />
            <el-table-column prop="columnCount" label="字段数" width="90" align="center" />
            <el-table-column prop="rowCount" label="数据量" min-width="120" align="right">
              <template #default="{ row }">
                {{ row.rowCount ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="partitionKey" label="分区键" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.partitionKey || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="enabled" label="启用" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" :type="row.enabled === false ? 'info' : 'primary'">
                  {{ row.enabled === false ? '否' : '是' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastScanAt" label="最近扫描" min-width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.lastScanAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="lastSyncAt" label="最近同步" min-width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.lastSyncAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="250" fixed="right" align="center">
              <template #default="{ row }">
                <div class="data-source-table__actions">
                  <el-tooltip content="数据查询" placement="top">
                    <el-button circle plain @click="handleTableAction('数据查询', row)">
                      <el-icon><Search /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="知识库预览" placement="top">
                    <el-button
                      circle
                      plain
                      :loading="knowledgePreviewLoading && knowledgePreviewTableName === (row.tableName || '')"
                      @click="handleTableAction('知识库预览', row)"
                    >
                      <el-icon><View /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="字段" placement="top">
                    <el-button circle plain @click="handleTableAction('字段', row)">
                      <el-icon><Tickets /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="权限" placement="top">
                    <el-button circle plain @click="handleTableAction('权限', row)">
                      <el-icon><Key /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="删除" placement="top">
                    <el-button circle plain type="danger" @click="handleTableAction('删除', row)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="更新" placement="top">
                    <el-button circle plain type="primary" @click="handleTableAction('更新', row)">
                      <el-icon><EditPen /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="同步" placement="top">
                    <el-button
                      circle
                      plain
                      type="primary"
                      :loading="knowledgeSyncSubmitting && knowledgeSyncTarget === (row.tableName || '')"
                      :disabled="knowledgeSyncSubmitting && knowledgeSyncTarget !== (row.tableName || '')"
                      @click="handleTableAction('同步', row)"
                    >
                      <el-icon><RefreshRight /></el-icon>
                    </el-button>
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-main>

      <el-footer class="data-source-table-layout__footer">
        <div v-if="currentMode === 'tables'" class="data-source-table-layout__footer-inner">
          <AppPagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[5, 10, 20, 50, 100, 200, 500]"
            :total="total"
            :pager-count="5"
            @current-change="handleCurrentPageChange"
            @size-change="handlePageSizeChange"
          />
        </div>
      </el-footer>
    </el-container>

    <el-dialog
      v-model="knowledgePreviewDialogVisible"
      :title="knowledgePreviewTableName ? `知识库预览 · ${knowledgePreviewTableName}` : '知识库预览'"
      width="820"
      draggable
      overflow
      destroy-on-close
      @closed="closeKnowledgePreviewDialog"
    >
      <section class="knowledge-preview-dialog">
        <div v-if="knowledgePreviewLoading" class="knowledge-preview-dialog__state">
          正在加载知识库预览...
        </div>
        <div v-else-if="knowledgePreviewError" class="knowledge-preview-dialog__state knowledge-preview-dialog__state--error">
          {{ knowledgePreviewError }}
        </div>
        <div v-else-if="!knowledgePreviewContent" class="knowledge-preview-dialog__state">
          暂无预览内容
        </div>
        <AppCodeEditor
          v-else
          :model-value="knowledgePreviewContent"
          :format="knowledgePreviewFormat"
          readonly
          :show-format-switcher="false"
          :toolbar-label="knowledgePreviewFormat === 'markdown' ? 'Markdown' : 'Text'"
          min-height="420px"
        />
      </section>
      <template #footer>
        <div class="knowledge-preview-dialog__footer">
          <el-button @click="closeKnowledgePreviewDialog">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importDialogVisible"
      title="导入元数据"
      width="560"
      draggable
      overflow
      destroy-on-close
      @closed="closeImportDialog"
    >
      <section class="data-transfer-dialog">
        <el-form label-position="top">
          <el-form-item label="导入格式">
            <el-radio-group v-model="importFormat">
              <el-radio-button label="json">JSON</el-radio-button>
              <el-radio-button label="excel">Excel</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="选择文件">
            <el-upload
              class="data-transfer-dialog__upload"
              drag
              action="#"
              :show-file-list="false"
              :auto-upload="false"
              :before-upload="beforeImportUpload"
              :on-change="handleImportFileChange"
            >
              <el-icon class="data-transfer-dialog__upload-icon"><Upload /></el-icon>
              <div class="el-upload__text">拖拽文件到这里，或点击选择文件</div>
              <template #tip>
                <div class="el-upload__tip">
                  当前文件：{{ importFile?.name || '未选择' }}
                </div>
              </template>
            </el-upload>
          </el-form-item>
          <div v-if="importError" class="data-transfer-dialog__error">{{ importError }}</div>
        </el-form>
      </section>
      <template #footer>
        <div class="data-transfer-dialog__footer">
          <el-button :loading="templateSubmitting" @click="downloadTemplateWorkbook">
            下载模板
          </el-button>
          <el-button @click="closeImportDialog">取消</el-button>
          <el-button type="primary" :loading="importSubmitting" @click="submitImport">
            {{ importSubmitting ? '导入中...' : '开始导入' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="exportDialogVisible"
      title="导出元数据"
      width="460"
      draggable
      overflow
      destroy-on-close
      @closed="closeExportDialog"
    >
      <section class="data-transfer-dialog">
        <el-form label-position="top">
          <el-form-item label="导出格式">
            <el-radio-group v-model="exportFormat">
              <el-radio-button label="json">JSON</el-radio-button>
              <el-radio-button label="excel">Excel</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </section>
      <template #footer>
        <div class="data-transfer-dialog__footer">
          <el-button @click="closeExportDialog">取消</el-button>
          <el-button type="primary" :loading="exportSubmitting" @click="exportWorkbook">
            {{ exportSubmitting ? '导出中...' : '确认导出' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importProgressDialogVisible"
      title="导入进度"
      width="520"
      draggable
      overflow
      destroy-on-close
    >
      <section class="import-progress-dialog">
        <div class="import-progress-dialog__item">
          <span>文件</span>
          <strong>{{ importProgress.fileName || '-' }}</strong>
        </div>
        <div class="import-progress-dialog__item">
          <span>状态</span>
          <strong>{{ importProgress.status || '-' }}</strong>
        </div>
        <div class="import-progress-dialog__item">
          <span>阶段</span>
          <strong>{{ importProgress.stage || '-' }}</strong>
        </div>
        <div class="import-progress-dialog__item">
          <span>说明</span>
          <strong>{{ importProgress.message || '-' }}</strong>
        </div>
        <el-progress :percentage="Math.max(0, Math.min(100, importProgress.progressPercent || 0))" />
        <div class="import-progress-dialog__summary">
          <span>新增表 {{ importProgress.createdTableCount }}</span>
          <span>更新表 {{ importProgress.updatedTableCount }}</span>
          <span>新增字段 {{ importProgress.createdFieldCount }}</span>
          <span>更新字段 {{ importProgress.updatedFieldCount }}</span>
        </div>
      </section>
      <template #footer>
        <div class="data-transfer-dialog__footer">
          <el-button @click="importProgressDialogVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="tableSyncDialogVisible"
      title="表格同步"
      width="720"
      draggable
      overflow
      destroy-on-close
      @closed="closeTableSyncDialog"
    >
      <section class="table-sync-dialog">
        <div class="table-sync-dialog__toolbar">
          <el-checkbox
            :model-value="tableSyncAllChecked"
            :indeterminate="tableSyncIndeterminate"
            @change="toggleAllTableSyncSelection(Boolean($event))"
          >
            全选
          </el-checkbox>
          <el-checkbox v-model="tableSyncAllowUpdate">
            允许更新已存在元数据
          </el-checkbox>
          <div class="table-sync-dialog__stats">
            <span>共 {{ tableSyncCandidateCount }} 张</span>
            <span>待同步 {{ tableSyncPendingCount }} 张</span>
            <span>已选择 {{ tableSyncSelectedCount }} 张</span>
          </div>
        </div>

        <div v-if="tableSyncError" class="table-sync-dialog__state table-sync-dialog__state--error">
          {{ tableSyncError }}
        </div>
        <div v-else-if="tableSyncLoading" class="table-sync-dialog__state">
          正在加载可同步的数据表...
        </div>
        <div v-else-if="!tableSyncCandidates.length" class="table-sync-dialog__state">
          当前数据源下没有可同步的数据表。
        </div>
        <div v-else class="table-sync-dialog__list">
          <label
            v-for="item in tableSyncCandidates"
            :key="item.name"
            class="table-sync-dialog__item"
          >
            <el-checkbox
              :model-value="tableSyncSelectedTables.includes(item.name)"
              @change="toggleTableSyncSelection(item.name, Boolean($event))"
            />
            <div class="table-sync-dialog__item-body">
              <div class="table-sync-dialog__item-head">
                <strong>{{ item.name }}</strong>
                <el-tag size="small" effect="plain" :type="item.synced ? 'warning' : 'primary'">
                  {{ item.synced ? '已存在' : '待新增' }}
                </el-tag>
              </div>
              <p>{{ item.comment }}</p>
              <span>{{ item.fieldCount ?? '-' }} 字段</span>
            </div>
          </label>
        </div>
      </section>

      <template #footer>
        <div class="table-sync-dialog__footer">
          <el-button @click="closeTableSyncDialog">取消</el-button>
          <el-button type="primary" :loading="tableSyncSubmitting" :disabled="!tableSyncSelectedCount" @click="submitTableSync">
            {{ tableSyncSubmitting ? '同步中...' : `开始同步（${tableSyncSelectedCount}）` }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.data-source-table-page {
  display: flex;
  flex: 1;
  min-height: 0;
}

.data-source-table-layout {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
  box-shadow: var(--system-shadow);
}

.data-source-table-layout__header {
  height: 82px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--system-border-subtle);
}

.data-source-table-layout__header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  height: 100%;
  margin: 0 auto;
}

.data-source-table-layout__title {
  display: grid;
  gap: 2px;
}

.data-source-table-layout__title h3 {
  margin: 0;
  color: var(--system-title);
  font-size: 16px;
}

.data-source-table-layout__title p {
  margin: 0;
  color: var(--system-text-muted);
  font-size: 12px;
}

.data-source-table-layout__tools {
  display: flex;
  align-items: center;
  gap: 6px;
}

.data-source-table-layout__tools :deep(.el-input) {
  width: 220px;
}

.data-source-table-layout__tools :deep(.el-button) {
  height: 30px;
  padding: 0 10px;
  font-size: 12px;
}

.data-source-table-layout__tools :deep(.el-input__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
}

.data-source-table-layout__tools :deep(.el-input__inner),
.data-source-table-layout__tools :deep(.el-input__prefix-inner) {
  color: var(--system-text);
}

.data-source-table-layout__tools :deep(.el-button:not(.el-button--text)) {
  border-radius: 10px;
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.data-source-table-layout__tools :deep(.el-button--text) {
  color: var(--system-accent-text);
}

.data-source-table-layout__main {
  display: flex;
  justify-content: center;
  min-height: 0;
  padding: 12px 16px;
  background: var(--system-surface-muted);
  overflow: hidden;
}

.data-source-table-layout__panel {
  width: 100%;
  height: 100%;
}

.data-source-field-browser {
  display: grid;
  grid-template-columns: minmax(220px, 16.6667%) minmax(0, 1fr);
  gap: 14px;
  height: 100%;
}

.data-source-field-browser__sidebar,
.data-source-field-browser__summary,
.data-source-field-browser__table-wrap {
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-solid);
}

.data-source-field-browser__sidebar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  overflow-y: auto;
}

.data-source-field-browser__sidebar-title,
.data-source-field-browser__label {
  color: var(--system-accent-text);
  font-size: 12px;
  font-weight: 600;
}

.data-source-field-browser__sidebar h4,
.data-source-field-browser__summary h3,
.data-source-field-browser__table-wrap h3 {
  margin: 0;
  color: var(--system-title);
  font-size: 16px;
}

.data-source-field-browser__table {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 12px 10px;
  border: 1px solid var(--system-border);
  border-radius: 16px;
  background: var(--system-surface-solid);
  text-align: left;
  cursor: pointer;
}

.data-source-field-browser__table strong {
  color: var(--system-title);
  font-size: 14px;
}

.data-source-field-browser__table span {
  color: var(--system-text-soft);
  font-size: 12px;
}

.data-source-field-browser__table.is-active {
  border-color: var(--system-accent-border);
  background: var(--system-accent-bg);
}

.data-source-field-browser__content {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
  min-width: 0;
  min-height: 0;
}

.data-source-field-browser__summary {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
}

.data-source-field-browser__cards {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 10px;
}

.data-source-field-browser__card {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border: 1px solid var(--system-border);
  border-radius: 14px;
  background: var(--system-surface-solid);
  min-width: 0;
}

.data-source-field-browser__card span {
  color: var(--system-text-muted);
  font-size: 12px;
}

.data-source-field-browser__card strong {
  color: var(--system-title);
  font-size: 14px;
}

.data-source-field-browser__card--remark p {
  margin: 0;
  color: var(--system-title);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}

.data-source-field-browser__table-wrap {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 10px;
  min-height: 0;
  padding: 14px 16px;
}

.data-source-table {
  height: 100%;
}

.data-source-table :deep(.data-source-table__header-cell) {
  background: var(--system-table-header-bg);
  color: var(--system-table-header-text);
  font-weight: 600;
}

.data-source-table-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--system-text-muted);
  font-size: 13px;
}

.data-source-table-state--error {
  color: var(--system-danger);
}

.data-source-table-layout__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  padding: 0 16px;
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-solid);
}

.data-source-table-layout__footer-inner {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  width: 100%;
  margin: 0 auto;
}

.data-source-table-page :deep(.el-overlay-dialog) {
  display: flex;
  align-items: center;
  justify-content: center;
}

.data-source-table-page :deep(.el-dialog) {
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
}

.data-source-table-page :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.data-source-table-page :deep(.el-dialog__body) {
  background: var(--system-surface-strong);
}

.data-source-table-page :deep(.el-dialog__footer) {
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.data-source-table-page :deep(.el-form-item__label) {
  color: var(--system-text-soft);
}

.data-source-table-page :deep(.el-input__wrapper),
.data-source-table-page :deep(.el-textarea__inner),
.data-source-table-page :deep(.el-select__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
  color: var(--system-text);
}

.table-sync-dialog {
  display: grid;
  gap: 12px;
}

.knowledge-preview-dialog {
  display: grid;
  min-height: 420px;
}

.knowledge-preview-dialog__state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  color: var(--system-text-muted);
  font-size: 13px;
}

.knowledge-preview-dialog__state--error {
  color: var(--system-danger);
}

.knowledge-preview-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.table-sync-dialog__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.table-sync-dialog__stats {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--system-text-muted);
  font-size: 12px;
}

.table-sync-dialog__state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  color: var(--system-text-muted);
  font-size: 13px;
}

.table-sync-dialog__state--error {
  color: var(--system-danger);
}

.table-sync-dialog__list {
  display: grid;
  gap: 10px;
  max-height: 420px;
  overflow-y: auto;
}

.table-sync-dialog__item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--system-border);
  border-radius: 14px;
  background: var(--system-surface-solid);
}

.table-sync-dialog__item-body {
  display: grid;
  gap: 4px;
}

.table-sync-dialog__item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.table-sync-dialog__item-head strong {
  color: var(--system-title);
  font-size: 14px;
}

.table-sync-dialog__item-body p {
  margin: 0;
  color: var(--system-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.table-sync-dialog__item-body span {
  color: var(--system-text-faint);
  font-size: 12px;
}

.table-sync-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.data-transfer-dialog {
  display: grid;
  gap: 12px;
}

.data-transfer-dialog__upload :deep(.el-upload-dragger) {
  width: 100%;
}

.data-transfer-dialog__upload-icon {
  font-size: 22px;
  color: var(--system-accent-text);
}

.data-transfer-dialog__error {
  color: var(--system-danger);
  font-size: 12px;
}

.data-transfer-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.knowledge-preview-dialog__footer :deep(.el-button),
.table-sync-dialog__footer :deep(.el-button),
.data-transfer-dialog__footer :deep(.el-button) {
  min-width: 76px;
  border-radius: 10px;
}

.knowledge-preview-dialog__footer :deep(.el-button:not(.el-button--primary)),
.table-sync-dialog__footer :deep(.el-button:not(.el-button--primary)),
.data-transfer-dialog__footer :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.knowledge-preview-dialog__footer :deep(.el-button--primary),
.table-sync-dialog__footer :deep(.el-button--primary),
.data-transfer-dialog__footer :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-primary-button-bg);
  color: var(--system-primary-button-text);
}

.import-progress-dialog {
  display: grid;
  gap: 12px;
}

.import-progress-dialog__item {
  display: grid;
  gap: 4px;
}

.import-progress-dialog__item span,
.import-progress-dialog__summary span {
  color: var(--system-text-muted);
  font-size: 12px;
}

.import-progress-dialog__item strong {
  color: var(--system-title);
  font-size: 13px;
}

.import-progress-dialog__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.data-source-table__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4px;
}

.data-source-table__actions :deep(.el-button) {
  width: 24px;
  height: 24px;
  min-height: 24px;
  padding: 0;
}

@media (max-width: 1024px) {
  .data-source-table-layout__header {
    height: auto;
    padding: 12px 16px;
  }

  .data-source-table-layout__header-inner {
    align-items: stretch;
    flex-direction: column;
  }

  .data-source-table-layout__tools {
    width: 100%;
    flex-wrap: wrap;
  }

  .data-source-table-layout__tools :deep(.el-input) {
    width: 100%;
  }

  .data-source-table-layout__footer-inner {
    gap: 12px;
  }

  .data-source-field-browser {
    grid-template-columns: 1fr;
  }

  .data-source-field-browser__cards {
    grid-template-columns: 1fr;
  }
}
</style>
