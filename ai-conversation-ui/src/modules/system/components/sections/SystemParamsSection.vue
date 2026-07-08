<script setup lang="ts">
import { Delete, EditPen, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { AppPagination } from '../../../../components'
import {
  createSystemSetting,
  deleteSystemSetting,
  editSystemSetting,
  searchSystemSettings,
  type SystemSettingItem,
  updateSystemSetting,
} from '../../api/systemSettings'

const keyword = ref('')
const pageSize = ref(20)
const currentPage = ref(1)
const addDialogVisible = ref(false)
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const parameterRecords = ref<SystemSettingItem[]>([])
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<string | number | null>(null)

const pageSizeOptions = [10, 20, 50, 100, 200, 500]
const addForm = reactive({
  key: '',
  value: '',
  type: 'STRING',
  desc: '',
  enabled: true,
  tags: '',
})
const typeOptions = [
  { label: '字符串', value: 'STRING' },
  { label: '数字', value: 'NUMBER' },
  { label: '布尔值', value: 'BOOLEAN' },
  { label: 'JSON', value: 'JSON' },
  { label: '密码', value: 'PASSWORD' },
]

const filteredParameterRecords = computed(() => {
  return parameterRecords.value.map((record) => ({
    id: record.id,
    key: record.settingKey || '-',
    desc: record.description || '暂无说明',
    value: formatPreviewValue(record.settingValue, record.valueType),
    rawValue: record.settingValue || '',
    rawType: record.valueType || 'STRING',
    tags: buildTags(record),
    enabled: record.enabled !== false,
  }))
})
const parsedTags = computed(() =>
  addForm.tags
    .split(/[\s,，]+/)
    .map((item) => item.trim())
    .filter(Boolean),
)

function resetAddForm() {
  addForm.key = ''
  addForm.value = ''
  addForm.type = 'STRING'
  addForm.desc = ''
  addForm.enabled = true
  addForm.tags = ''
}

function closeAddDialog() {
  addDialogVisible.value = false
}

function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  resetAddForm()
  addDialogVisible.value = true
}

function openEditDialog(record: {
  id: string | number
  key: string
  desc: string
  rawValue: string
  rawType: string
  enabled: boolean
}) {
  dialogMode.value = 'edit'
  editingId.value = record.id
  addForm.key = record.key
  addForm.value = record.rawValue
  addForm.type = record.rawType
  addForm.desc = record.desc === '暂无说明' ? '' : record.desc
  addForm.enabled = record.enabled
  addForm.tags = ''
  addDialogVisible.value = true
}

function buildTags(record: SystemSettingItem) {
  const tags: string[] = []
  if (record.valueType) {
    tags.push(record.valueType)
  }
  if (record.lastModifiedBy || record.updatedBy || record.createdBy) {
    tags.push(String(record.lastModifiedBy || record.updatedBy || record.createdBy))
  }
  return tags
}

function formatPreviewValue(value?: string, type?: string) {
  if (value === null || value === undefined || value === '') {
    return '未配置'
  }
  if (type === 'PASSWORD') {
    return '••••••••'
  }
  if (type === 'BOOLEAN') {
    return String(value).toLowerCase() === 'true' ? 'true / 开启' : 'false / 关闭'
  }
  const text = String(value)
  return text.length > 88 ? `${text.slice(0, 88)}...` : text
}

function resolveTotal(payloadTotal?: number) {
  const numericTotal = Number(payloadTotal)
  return Number.isFinite(numericTotal) ? numericTotal : parameterRecords.value.length
}

const total = ref(0)

async function loadSystemSettings() {
  loading.value = true
  errorMessage.value = ''
  try {
    const payload = await searchSystemSettings({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
    })
    parameterRecords.value = payload?.list ?? []
    total.value = resolveTotal(payload?.pageInfo?.total)
  }
  catch (error) {
    parameterRecords.value = []
    total.value = 0
    errorMessage.value = error instanceof Error ? error.message : '系统参数加载失败'
  }
  finally {
    loading.value = false
  }
}

async function handleRefresh() {
  await loadSystemSettings()
}

async function handleSearch() {
  currentPage.value = 1
  await loadSystemSettings()
}

async function handleCurrentPageChange(page: number) {
  currentPage.value = page
  await loadSystemSettings()
}

async function handlePageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  await loadSystemSettings()
}

async function handleToggleEnabled(record: { id: string | number, enabled: boolean }) {
  const nextEnabled = !record.enabled
  try {
    await editSystemSetting(record.id, { enabled: nextEnabled })
    record.enabled = nextEnabled
    const target = parameterRecords.value.find(item => item.id === record.id)
    if (target) {
      target.enabled = nextEnabled
    }
    ElMessage.success(`参数已${nextEnabled ? '启用' : '停用'}`)
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态更新失败')
  }
}

function validateAddForm() {
  if (!addForm.key.trim()) {
    return '请输入参数 Key'
  }
  if (!addForm.type) {
    return '请选择参数类型'
  }
  if (addForm.type === 'JSON' && addForm.value.trim()) {
    try {
      JSON.parse(addForm.value)
    }
    catch {
      return 'JSON 类型的参数值必须是合法 JSON'
    }
  }
  if (addForm.type === 'BOOLEAN') {
    const normalized = addForm.value.trim().toLowerCase()
    if (normalized && normalized !== 'true' && normalized !== 'false') {
      return 'BOOLEAN 类型的参数值只能是 true 或 false'
    }
  }
  return ''
}

async function handleSubmitAddForm() {
  const validationError = validateAddForm()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }

  saving.value = true
  try {
    const payload = {
      settingKey: addForm.key.trim(),
      description: addForm.desc.trim() || undefined,
      settingValue: addForm.value,
      valueType: addForm.type,
      enabled: addForm.enabled,
    }

    if (dialogMode.value === 'create') {
      await createSystemSetting(payload)
      ElMessage.success('系统参数新增成功')
    }
    else if (editingId.value !== null) {
      await updateSystemSetting(editingId.value, payload)
      ElMessage.success('系统参数更新成功')
    }

    addDialogVisible.value = false
    currentPage.value = 1
    await loadSystemSettings()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `系统参数${dialogMode.value === 'create' ? '新增' : '更新'}失败`)
  }
  finally {
    saving.value = false
  }
}

async function handleDelete(record: { id: string | number, key: string }) {
  try {
    await ElMessageBox.confirm(
      `确认删除系统参数「${record.key}」吗？删除后不可恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        draggable: true,
        overflow: true,
      },
    )

    await deleteSystemSetting(record.id)
    ElMessage.success('系统参数已删除')

    if (filteredParameterRecords.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    await loadSystemSettings()
  }
  catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '系统参数删除失败')
  }
}

onMounted(() => {
  void loadSystemSettings()
})
</script>

<template>
  <section class="system-params-page">
    <el-container class="system-params-layout">
      <el-header class="system-params-layout__header">
        <div class="system-params-layout__title">
          <h3>系统参数</h3>
          <p>维护系统级参数、默认开关和全局配置。</p>
        </div>
        <div class="system-params-layout__tools">
          <el-input v-model="keyword" placeholder="搜索参数名称 / 参数组 / 参数键" clearable @keyup.enter="handleSearch">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button plain @click="handleRefresh">
            <el-icon><RefreshRight /></el-icon>
            刷新
          </el-button>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新增参数
          </el-button>
        </div>
      </el-header>

      <el-main class="system-params-layout__main">
        <div v-if="loading" class="system-params-state">系统参数加载中...</div>
        <div v-else-if="errorMessage" class="system-params-state system-params-state--error">
          {{ errorMessage }}
        </div>
        <div v-else-if="!filteredParameterRecords.length" class="system-params-state">
          暂无系统参数
        </div>
        <div
          v-else
          v-for="record in filteredParameterRecords"
          :key="record.key"
          class="system-params-card"
        >
          <div class="system-params-card__head">
            <div class="system-params-card__key">{{ record.key }}</div>
            <el-switch :model-value="record.enabled" size="small" @change="handleToggleEnabled(record)" />
          </div>

          <div class="system-params-card__desc">{{ record.desc }}</div>

          <div class="system-params-card__value" :title="record.rawValue">
            {{ record.value }}
          </div>

          <div class="system-params-card__footer">
            <div class="system-params-card__tags">
              <el-tag
                v-for="tag in record.tags"
                :key="tag"
                size="small"
                effect="plain"
              >
                {{ tag }}
              </el-tag>
            </div>
            <div class="system-params-card__actions">
              <el-tooltip content="编辑" placement="top">
                <el-button circle plain type="primary" @click="openEditDialog(record)">
                  <el-icon><EditPen /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button circle plain type="danger" @click="handleDelete(record)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
      </el-main>

      <el-footer class="system-params-layout__footer">
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

    <el-dialog
      v-model="addDialogVisible"
      :title="dialogMode === 'create' ? '新增系统参数' : '编辑系统参数'"
      width="680"
      draggable
      overflow
      destroy-on-close
      @closed="resetAddForm"
    >
      <div class="system-params-dialog">
        <el-form label-position="top" class="system-params-dialog__form">
          <el-form-item label="Key">
            <el-input
              v-model="addForm.key"
              placeholder="例如：app.theme.default"
              :disabled="dialogMode === 'edit'"
            />
          </el-form-item>

          <el-form-item label="Type">
            <el-select v-model="addForm.type" placeholder="请选择参数类型">
              <el-option
                v-for="option in typeOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="Value">
            <el-input
              v-model="addForm.value"
              type="textarea"
              :rows="5"
              placeholder="请输入参数值"
            />
          </el-form-item>

          <el-form-item label="Desc">
            <el-input
              v-model="addForm.desc"
              type="textarea"
              :rows="3"
              placeholder="请输入参数说明"
            />
          </el-form-item>

          <div class="system-params-dialog__grid">
            <el-form-item label="Enabled">
              <el-switch v-model="addForm.enabled" />
            </el-form-item>

            <el-form-item label="Tags">
              <el-input
                v-model="addForm.tags"
                placeholder="支持空格、英文逗号、中文逗号分隔"
              />
            </el-form-item>
          </div>

          <div v-if="parsedTags.length" class="system-params-dialog__tags">
            <el-tag v-for="tag in parsedTags" :key="tag" size="small" effect="plain">
              {{ tag }}
            </el-tag>
          </div>
        </el-form>
      </div>

      <template #footer>
        <div class="system-params-dialog__footer">
          <el-button @click="closeAddDialog">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmitAddForm">确认</el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.system-params-page {
  display: flex;
  flex: 1;
  min-height: 0;
}

.system-params-layout {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
  box-shadow: var(--system-shadow);
}

.system-params-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 66px;
  padding: 0 16px;
  border-bottom: 1px solid var(--system-border-subtle);
}

.system-params-layout__title h3 {
  margin: 0;
  color: var(--system-title);
  font-size: 16px;
}

.system-params-layout__title p {
  margin: 3px 0 0;
  color: var(--system-text-muted);
  font-size: 12px;
}

.system-params-layout__tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.system-params-layout__tools :deep(.el-input) {
  width: 260px;
}

.system-params-layout__tools :deep(.el-input__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
}

.system-params-layout__tools :deep(.el-input__wrapper:hover) {
  border-color: var(--system-accent-border);
  background: var(--system-surface);
}

.system-params-layout__tools :deep(.el-input__wrapper.is-focus) {
  border-color: var(--system-accent-border);
  box-shadow: 0 0 0 1px var(--system-accent-border);
}

.system-params-layout__tools :deep(.el-input__inner),
.system-params-layout__tools :deep(.el-input__prefix-inner) {
  color: var(--system-text);
}

.system-params-layout__tools :deep(.el-input__inner::placeholder) {
  color: var(--system-text-faint);
}

.system-params-layout__tools :deep(.el-button) {
  border-radius: 10px;
}

.system-params-layout__tools :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.system-params-layout__tools :deep(.el-button:not(.el-button--primary):hover) {
  border-color: var(--system-accent-border);
  background: var(--system-surface);
  color: var(--system-title);
}

.system-params-layout__tools :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-accent-text);
  color: #08111f;
}

.system-params-layout__tools :deep(.el-button--primary:hover) {
  filter: brightness(1.06);
}

.system-params-layout__main {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 320px));
  align-content: start;
  justify-content: start;
  gap: 14px;
  min-height: 0;
  padding: 14px 16px;
  background: var(--system-surface-muted);
  overflow-y: auto;
}

.system-params-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  padding: 24px;
  color: var(--system-text-muted);
  font-size: 13px;
}

.system-params-state--error {
  color: var(--system-danger);
}

.system-params-card {
  display: grid;
  gap: 12px;
  max-width: 320px;
  padding: 16px;
  border: 1px solid var(--system-border);
  border-radius: 14px;
  background: var(--system-surface-solid);
  box-shadow: var(--system-shadow);
}

.system-params-card__head,
.system-params-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.system-params-card__key {
  color: var(--system-title);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  word-break: break-all;
}

.system-params-card__desc {
  color: var(--system-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.system-params-card__value {
  overflow: hidden;
  color: var(--system-title);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.system-params-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.system-params-card__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.system-params-card__actions :deep(.el-button) {
  width: 28px;
  height: 28px;
}

.system-params-layout__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 44px;
  padding: 0 16px;
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-solid);
}

.system-params-page :deep(.el-overlay-dialog) {
  display: flex;
  align-items: center;
  justify-content: center;
}

.system-params-page :deep(.el-dialog) {
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
  box-shadow: 0 24px 56px rgba(2, 6, 23, 0.28);
}

.system-params-page :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.system-params-page :deep(.el-dialog__title) {
  color: var(--system-title);
  font-size: 16px;
  font-weight: 600;
}

.system-params-page :deep(.el-dialog__headerbtn) {
  top: 18px;
  right: 18px;
}

.system-params-page :deep(.el-dialog__close) {
  color: var(--system-text-muted);
}

.system-params-page :deep(.el-dialog__headerbtn:hover .el-dialog__close) {
  color: var(--system-title);
}

.system-params-page :deep(.el-dialog__body) {
  padding: 18px 20px 14px;
  background: var(--system-surface-strong);
}

.system-params-page :deep(.el-dialog__footer) {
  padding: 14px 20px 18px;
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.system-params-dialog {
  display: grid;
  gap: 12px;
}

.system-params-dialog__form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.system-params-dialog__form :deep(.el-form-item__label) {
  color: var(--system-text-soft);
  font-size: 13px;
}

.system-params-dialog__form :deep(.el-select) {
  width: 100%;
}

.system-params-dialog__form :deep(.el-input__wrapper),
.system-params-dialog__form :deep(.el-textarea__inner),
.system-params-dialog__form :deep(.el-select__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
  color: var(--system-text);
}

.system-params-dialog__form :deep(.el-input__wrapper:hover),
.system-params-dialog__form :deep(.el-textarea__inner:hover),
.system-params-dialog__form :deep(.el-select__wrapper:hover) {
  border-color: var(--system-accent-border);
  background: var(--system-surface);
}

.system-params-dialog__form :deep(.el-input__wrapper.is-focus),
.system-params-dialog__form :deep(.el-textarea__inner:focus),
.system-params-dialog__form :deep(.el-select__wrapper.is-focused) {
  border-color: var(--system-accent-border);
  box-shadow: 0 0 0 1px var(--system-accent-border);
}

.system-params-dialog__form :deep(.el-input__inner),
.system-params-dialog__form :deep(.el-textarea__inner),
.system-params-dialog__form :deep(.el-select__selected-item),
.system-params-dialog__form :deep(.el-select__placeholder),
.system-params-dialog__form :deep(.el-input__count) {
  color: var(--system-text);
}

.system-params-dialog__form :deep(.el-input__inner::placeholder),
.system-params-dialog__form :deep(.el-textarea__inner::placeholder) {
  color: var(--system-text-faint);
}

.system-params-dialog__form :deep(.el-switch__core) {
  background: var(--system-surface-muted);
  border-color: var(--system-border);
}

.system-params-dialog__form :deep(.el-switch.is-checked .el-switch__core) {
  background: var(--system-accent-text);
  border-color: var(--system-accent-text);
}

.system-params-dialog__grid {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 12px;
}

.system-params-dialog__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.system-params-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.system-params-dialog__footer :deep(.el-button) {
  min-width: 76px;
  border-radius: 10px;
}

.system-params-dialog__footer :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.system-params-dialog__footer :deep(.el-button:not(.el-button--primary):hover) {
  border-color: var(--system-accent-border);
  background: var(--system-surface);
  color: var(--system-title);
}

.system-params-dialog__footer :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-accent-text);
  color: #08111f;
}

.system-params-dialog__footer :deep(.el-button--primary:hover) {
  filter: brightness(1.06);
}

@media (max-width: 960px) {
  .system-params-layout__header {
    flex-direction: column;
    align-items: flex-start;
    height: auto;
    padding: 12px;
  }

  .system-params-layout__tools {
    width: 100%;
    flex-wrap: wrap;
  }

  .system-params-layout__tools :deep(.el-input) {
    width: 100%;
  }

  .system-params-dialog__grid {
    grid-template-columns: 1fr;
  }

  .system-params-layout__main {
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  }

  .system-params-card {
    max-width: none;
  }
}
</style>
