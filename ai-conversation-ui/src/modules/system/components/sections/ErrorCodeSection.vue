<script setup lang="ts">
import { Delete, Download, EditPen, Plus, RefreshRight, Search, Tickets, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { AppPagination } from '../../../../components'
import {
  createErrorCode,
  createErrorCodeI18n,
  deleteErrorCode,
  deleteErrorCodeI18n,
  exportErrorCodeJson,
  importErrorCodeJson,
  searchErrorCodeI18n,
  searchErrorCodes,
  updateErrorCode,
  updateErrorCodeI18n,
  type ErrorCodeI18nItem,
  type ErrorCodeItem,
} from '../../api/errorCodes'

const keyword = ref('')
const pageSize = ref(20)
const currentPage = ref(1)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const rows = ref<ErrorCodeItem[]>([])

const editorVisible = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const editingId = ref<string | number | null>(null)
const form = reactive({
  code: '',
  httpStatus: '200',
  description: '',
  tags: '',
})

const i18nDialogVisible = ref(false)
const i18nRows = ref<ErrorCodeI18nItem[]>([])
const i18nLoading = ref(false)
const activeErrCode = ref<number | null>(null)
const i18nEditorVisible = ref(false)
const i18nSaving = ref(false)
const i18nEditorMode = ref<'create' | 'edit'>('create')
const editingI18nId = ref<string | number | null>(null)
const i18nForm = reactive({
  locale: 'zh-CN',
  messageTemplate: '',
  description: '',
})

const importDialogVisible = ref(false)
const importFile = ref<File | null>(null)
const importError = ref('')
const importing = ref(false)
const exporting = ref(false)
const pageSizeOptions = [10, 20, 50, 100, 200, 500]

const normalizedRows = computed(() => rows.value.map(row => ({
  ...row,
  httpStatus: row.httpStatus ?? 200,
  description: row.description || '-',
  tags: parseTags(row.tags),
})))

function parseTags(tags?: string) {
  return String(tags || '')
    .split(/[\s,，]+/)
    .map(item => item.trim())
    .filter(Boolean)
}

function resolveTotal(payloadTotal?: number) {
  const numericTotal = Number(payloadTotal)
  return Number.isFinite(numericTotal) ? numericTotal : rows.value.length
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

async function loadErrorCodes() {
  loading.value = true
  errorMessage.value = ''
  try {
    const payload = await searchErrorCodes({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
    })
    rows.value = payload?.list ?? []
    total.value = resolveTotal(payload?.pageInfo?.total)
  }
  catch (error) {
    rows.value = []
    total.value = 0
    errorMessage.value = error instanceof Error ? error.message : '错误码加载失败'
  }
  finally {
    loading.value = false
  }
}

async function handleSearch() {
  currentPage.value = 1
  await loadErrorCodes()
}

async function handleRefresh() {
  await loadErrorCodes()
}

async function handleCurrentPageChange(page: number) {
  currentPage.value = page
  await loadErrorCodes()
}

async function handlePageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  await loadErrorCodes()
}

function resetForm() {
  form.code = ''
  form.httpStatus = '200'
  form.description = ''
  form.tags = ''
}

function openCreateDialog() {
  editorMode.value = 'create'
  editingId.value = null
  resetForm()
  editorVisible.value = true
}

function openEditDialog(row: ErrorCodeItem) {
  editorMode.value = 'edit'
  editingId.value = row.id
  form.code = row.code === undefined || row.code === null ? '' : String(row.code)
  form.httpStatus = row.httpStatus === undefined || row.httpStatus === null ? '200' : String(row.httpStatus)
  form.description = row.description || ''
  form.tags = row.tags || ''
  editorVisible.value = true
}

function closeEditor() {
  editorVisible.value = false
}

function validateForm() {
  if (!form.code.trim() || !Number.isInteger(Number(form.code))) {
    return '请输入整数错误码'
  }
  if (!form.httpStatus.trim() || !Number.isInteger(Number(form.httpStatus))) {
    return '请输入整数 HTTP 状态码'
  }
  return ''
}

async function submitForm() {
  const validationError = validateForm()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }

  saving.value = true
  try {
    const payload = {
      code: Number(form.code),
      httpStatus: Number(form.httpStatus || 200),
      description: form.description.trim() || undefined,
      tags: form.tags.trim() || undefined,
    }
    if (editorMode.value === 'create') {
      await createErrorCode(payload)
      ElMessage.success('错误码已新增')
    }
    else if (editingId.value !== null) {
      await updateErrorCode(editingId.value, payload)
      ElMessage.success('错误码已更新')
    }
    editorVisible.value = false
    await loadErrorCodes()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  }
  finally {
    saving.value = false
  }
}

async function handleDelete(row: ErrorCodeItem) {
  try {
    await ElMessageBox.confirm(`确认删除错误码「${row.code ?? '-'}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      draggable: true,
      overflow: true,
    })
    await deleteErrorCode(row.id)
    ElMessage.success('错误码已删除')
    if (rows.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    await loadErrorCodes()
  }
  catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function openI18nDialog(row: ErrorCodeItem) {
  if (row.code === undefined || row.code === null) {
    ElMessage.error('当前错误码为空，无法维护文案')
    return
  }
  activeErrCode.value = row.code
  i18nDialogVisible.value = true
  await loadI18nRows()
}

async function loadI18nRows() {
  if (activeErrCode.value === null) {
    i18nRows.value = []
    return
  }
  i18nLoading.value = true
  try {
    const payload = await searchErrorCodeI18n({
      page: 1,
      size: 500,
      errCode: activeErrCode.value,
    })
    i18nRows.value = payload?.list ?? []
  }
  catch (error) {
    i18nRows.value = []
    ElMessage.error(error instanceof Error ? error.message : '多语言文案加载失败')
  }
  finally {
    i18nLoading.value = false
  }
}

function resetI18nForm() {
  i18nForm.locale = 'zh-CN'
  i18nForm.messageTemplate = ''
  i18nForm.description = ''
}

function openCreateI18nDialog() {
  i18nEditorMode.value = 'create'
  editingI18nId.value = null
  resetI18nForm()
  i18nEditorVisible.value = true
}

function openEditI18nDialog(row: ErrorCodeI18nItem) {
  i18nEditorMode.value = 'edit'
  editingI18nId.value = row.id
  i18nForm.locale = row.locale || 'zh-CN'
  i18nForm.messageTemplate = row.messageTemplate || ''
  i18nForm.description = row.description || ''
  i18nEditorVisible.value = true
}

async function submitI18nForm() {
  if (activeErrCode.value === null) {
    ElMessage.error('错误码为空')
    return
  }
  if (!i18nForm.locale.trim()) {
    ElMessage.error('请输入 locale')
    return
  }

  i18nSaving.value = true
  try {
    const payload = {
      errCode: activeErrCode.value,
      locale: i18nForm.locale.trim(),
      messageTemplate: i18nForm.messageTemplate,
      description: i18nForm.description.trim() || undefined,
    }
    if (i18nEditorMode.value === 'create') {
      await createErrorCodeI18n(payload)
      ElMessage.success('文案已新增')
    }
    else if (editingI18nId.value !== null) {
      await updateErrorCodeI18n(editingI18nId.value, payload)
      ElMessage.success('文案已更新')
    }
    i18nEditorVisible.value = false
    await loadI18nRows()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '文案保存失败')
  }
  finally {
    i18nSaving.value = false
  }
}

async function handleDeleteI18n(row: ErrorCodeI18nItem) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.locale || '-'}」文案吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      draggable: true,
      overflow: true,
    })
    await deleteErrorCodeI18n(row.id)
    ElMessage.success('文案已删除')
    await loadI18nRows()
  }
  catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '文案删除失败')
  }
}

function openImportDialog() {
  importDialogVisible.value = true
  importFile.value = null
  importError.value = ''
}

function closeImportDialog() {
  importDialogVisible.value = false
  importFile.value = null
  importError.value = ''
}

function beforeImportUpload() {
  return false
}

function handleImportFileChange(uploadFile: { raw?: File }) {
  const file = uploadFile.raw || null
  if (!file) {
    return
  }
  if (!String(file.name || '').toLowerCase().endsWith('.json')) {
    importError.value = '请上传 .json 文件'
    importFile.value = null
    return
  }
  importFile.value = file
  importError.value = ''
}

async function submitImport() {
  if (!importFile.value) {
    importError.value = '请先选择 JSON 文件'
    return
  }
  importing.value = true
  importError.value = ''
  try {
    const result = await importErrorCodeJson(importFile.value)
    ElMessage.success(`导入完成：错误码 ${Number(result?.errCodeUpserted ?? 0)}，文案 ${Number(result?.i18nUpserted ?? 0)}，跳过 ${Number(result?.skipped ?? 0)}`)
    closeImportDialog()
    await loadErrorCodes()
  }
  catch (error) {
    importError.value = error instanceof Error ? error.message : '导入失败'
  }
  finally {
    importing.value = false
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

async function handleExport() {
  exporting.value = true
  try {
    const payload = await exportErrorCodeJson()
    const blob = new Blob([JSON.stringify(payload ?? [], null, 2)], { type: 'application/json' })
    triggerBrowserDownload(blob, `error-codes-${Date.now()}.json`)
    ElMessage.success('导出成功')
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败')
  }
  finally {
    exporting.value = false
  }
}

function downloadTemplate() {
  const template = [
    {
      code: 1001001,
      httpStatus: 200,
      description: '用户不存在',
      tags: 'user,login',
      value: [
        { locale: 'zh-CN', messageTemplate: '用户不存在' },
        { locale: 'en-US', messageTemplate: 'User does not exist' },
      ],
    },
  ]
  const blob = new Blob([JSON.stringify(template, null, 2)], { type: 'application/json' })
  triggerBrowserDownload(blob, 'error-code-template.json')
}

onMounted(() => {
  void loadErrorCodes()
})
</script>

<template>
  <section class="error-code-page">
    <el-container class="error-code-layout">
      <el-header class="error-code-layout__header">
        <div class="error-code-layout__title">
          <h3>错误码管理</h3>
          <p>维护系统错误码、HTTP 状态和多语言展示文案。</p>
        </div>
        <div class="error-code-layout__tools">
          <el-input v-model="keyword" placeholder="搜索错误码 / 描述 / 标签" clearable @keyup.enter="handleSearch">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button plain @click="openImportDialog">
            <el-icon><Upload /></el-icon>
            导入
          </el-button>
          <el-button plain :loading="exporting" @click="handleExport">
            <el-icon><Download /></el-icon>
            导出
          </el-button>
          <el-button plain @click="handleRefresh">
            <el-icon><RefreshRight /></el-icon>
            刷新
          </el-button>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新增错误码
          </el-button>
        </div>
      </el-header>

      <el-main class="error-code-layout__main">
        <div v-if="errorMessage" class="error-code-state error-code-state--error">{{ errorMessage }}</div>
        <el-table
          v-else
          v-loading="loading"
          :data="normalizedRows"
          height="100%"
          border
          stripe
          class="error-code-table"
          empty-text="暂无错误码"
          header-cell-class-name="error-code-table__header-cell"
        >
          <el-table-column prop="code" label="错误码" width="140" fixed="left" />
          <el-table-column prop="httpStatus" label="HTTP" width="100" align="center" />
          <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
          <el-table-column label="标签" min-width="180">
            <template #default="{ row }">
              <div class="error-code-tags">
                <el-tag v-for="tag in row.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
                <span v-if="!row.tags.length">-</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right" align="center">
            <template #default="{ row }">
              <div class="error-code-actions">
                <el-tooltip content="文案" placement="top">
                  <el-button circle plain @click="openI18nDialog(row)">
                    <el-icon><Tickets /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="编辑" placement="top">
                  <el-button circle plain type="primary" @click="openEditDialog(row)">
                    <el-icon><EditPen /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="删除" placement="top">
                  <el-button circle plain type="danger" @click="handleDelete(row)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-main>

      <el-footer class="error-code-layout__footer">
        <AppPagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="pageSizeOptions"
          :total="total"
          :pager-count="5"
          @current-change="handleCurrentPageChange"
          @size-change="handlePageSizeChange"
        />
      </el-footer>
    </el-container>

    <el-dialog v-model="editorVisible" :title="editorMode === 'create' ? '新增错误码' : '编辑错误码'" width="620" draggable overflow destroy-on-close @closed="resetForm">
      <el-form label-position="top" class="error-code-form">
        <div class="error-code-form__grid">
          <el-form-item label="错误码">
            <el-input v-model="form.code" inputmode="numeric" placeholder="例如：1001001" :disabled="editorMode === 'edit'" />
          </el-form-item>
          <el-form-item label="HTTP 状态码">
            <el-input v-model="form.httpStatus" inputmode="numeric" placeholder="默认 200" />
          </el-form-item>
        </div>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入错误码说明" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="支持空格、英文逗号、中文逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="error-code-dialog-footer">
          <el-button @click="closeEditor">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitForm">确认</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="i18nDialogVisible" :title="`错误码 ${activeErrCode ?? '-'} 文案`" width="860" draggable overflow destroy-on-close>
      <section class="error-code-i18n">
        <div class="error-code-i18n__toolbar">
          <el-button type="primary" @click="openCreateI18nDialog">
            <el-icon><Plus /></el-icon>
            新增文案
          </el-button>
          <el-button plain @click="loadI18nRows">
            <el-icon><RefreshRight /></el-icon>
            刷新
          </el-button>
        </div>
        <el-table v-loading="i18nLoading" :data="i18nRows" border stripe height="360" empty-text="暂无文案">
          <el-table-column prop="locale" label="Locale" width="120" />
          <el-table-column prop="messageTemplate" label="消息模板" min-width="260" show-overflow-tooltip />
          <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.description || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template #default="{ row }">
              <el-button circle plain type="primary" @click="openEditI18nDialog(row)">
                <el-icon><EditPen /></el-icon>
              </el-button>
              <el-button circle plain type="danger" @click="handleDeleteI18n(row)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </el-dialog>

    <el-dialog v-model="i18nEditorVisible" :title="i18nEditorMode === 'create' ? '新增文案' : '编辑文案'" width="620" draggable overflow destroy-on-close @closed="resetI18nForm">
      <el-form label-position="top" class="error-code-form">
        <el-form-item label="Locale">
          <el-input v-model="i18nForm.locale" placeholder="例如：zh-CN / en-US" :disabled="i18nEditorMode === 'edit'" />
        </el-form-item>
        <el-form-item label="Message Template">
          <el-input v-model="i18nForm.messageTemplate" type="textarea" :rows="4" placeholder="请输入错误文案模板" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="i18nForm.description" type="textarea" :rows="3" placeholder="请输入说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="error-code-dialog-footer">
          <el-button @click="i18nEditorVisible = false">取消</el-button>
          <el-button type="primary" :loading="i18nSaving" @click="submitI18nForm">确认</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="importDialogVisible" title="导入错误码 JSON" width="560" draggable overflow destroy-on-close @closed="closeImportDialog">
      <section class="error-code-import">
        <el-upload
          class="error-code-import__upload"
          drag
          action="#"
          :show-file-list="false"
          :auto-upload="false"
          :before-upload="beforeImportUpload"
          :on-change="handleImportFileChange"
        >
          <el-icon class="error-code-import__icon"><Upload /></el-icon>
          <div class="el-upload__text">拖拽 JSON 文件到这里，或点击选择文件</div>
          <template #tip>
            <div class="el-upload__tip">当前文件：{{ importFile?.name || '未选择' }}</div>
          </template>
        </el-upload>
        <div v-if="importError" class="error-code-import__error">{{ importError }}</div>
      </section>
      <template #footer>
        <div class="error-code-dialog-footer">
          <el-button @click="downloadTemplate">下载模板</el-button>
          <el-button @click="closeImportDialog">取消</el-button>
          <el-button type="primary" :loading="importing" @click="submitImport">
            {{ importing ? '导入中...' : '开始导入' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.error-code-page {
  display: flex;
  flex: 1;
  min-height: 0;
}

.error-code-layout {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
  box-shadow: var(--system-shadow);
}

.error-code-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 66px;
  padding: 0 16px;
  border-bottom: 1px solid var(--system-border-subtle);
}

.error-code-layout__title h3 {
  margin: 0;
  color: var(--system-title);
  font-size: 16px;
}

.error-code-layout__title p {
  margin: 3px 0 0;
  color: var(--system-text-muted);
  font-size: 12px;
}

.error-code-layout__tools,
.error-code-actions,
.error-code-dialog-footer,
.error-code-i18n__toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.error-code-layout__tools :deep(.el-input) {
  width: 260px;
}

.error-code-layout__main {
  min-height: 0;
  padding: 14px 16px;
  background: var(--system-surface-muted);
  overflow: hidden;
}

.error-code-layout__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 44px;
  padding: 0 16px;
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-solid);
}

.error-code-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  color: var(--system-text-muted);
  font-size: 13px;
}

.error-code-state--error,
.error-code-import__error {
  color: var(--system-danger);
}

.error-code-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.error-code-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.error-code-form :deep(.el-input-number),
.error-code-form :deep(.el-select) {
  width: 100%;
}

.error-code-dialog-footer {
  justify-content: flex-end;
}

.error-code-i18n {
  display: grid;
  gap: 12px;
}

.error-code-i18n__toolbar {
  justify-content: flex-end;
}

.error-code-import {
  display: grid;
  gap: 12px;
}

.error-code-import__upload {
  width: 100%;
}

.error-code-import__icon {
  color: var(--system-text-muted);
  font-size: 28px;
}

.error-code-page :deep(.el-button) {
  border-radius: 10px;
}

.error-code-page :deep(.el-dialog) {
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
}

.error-code-page :deep(.el-dialog__header),
.error-code-page :deep(.el-dialog__footer) {
  border-color: var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

@media (max-width: 960px) {
  .error-code-layout__header {
    flex-direction: column;
    align-items: flex-start;
    height: auto;
    padding: 12px;
  }

  .error-code-layout__tools {
    width: 100%;
    flex-wrap: wrap;
  }

  .error-code-layout__tools :deep(.el-input) {
    width: 100%;
  }

  .error-code-form__grid {
    grid-template-columns: 1fr;
  }
}
</style>
