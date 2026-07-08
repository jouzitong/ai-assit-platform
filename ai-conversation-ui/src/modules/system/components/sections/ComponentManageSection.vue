<script setup lang="ts">
import { MoreFilled, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { AppPagination } from '../../../../components'
import {
  createRenderComponent,
  deleteRenderComponent,
  getRenderComponentSummary,
  listRenderComponentCategories,
  searchRenderComponents,
  type RenderComponentCategoryItem,
  type RenderComponentItem,
  type RenderComponentStatus,
  type RenderComponentUpsertPayload,
  updateRenderComponent,
} from '../../api/renderComponents'

withDefaults(defineProps<{
  title?: string
  description?: string
}>(), {
  title: '组件管理',
  description: '已对接组件实体表，支持真实分类、搜索和分页。',
})

const EFFECTIVE_STATUS_DRAFT = 1
const EFFECTIVE_STATUS_PUBLISHED = 2
const EFFECTIVE_STATUS_DISABLED = 3

const componentKeyword = ref('')
const activeCategory = ref('all')
const pageSize = ref(20)
const currentPage = ref(1)
const total = ref(0)
const loading = ref(false)
const summaryLoading = ref(false)
const errorMessage = ref('')
const categoryError = ref('')
const componentRecords = ref<RenderComponentItem[]>([])
const categoryRecords = ref<RenderComponentCategoryItem[]>([])
const componentDialogVisible = ref(false)
const componentDialogMode = ref<'create' | 'edit'>('create')
const componentSubmitting = ref(false)
const editingComponentId = ref<string | number | null>(null)
const summary = ref({
  total: 0,
  published: 0,
  draft: 0,
  disabled: 0,
  categories: 0,
})

const componentForm = reactive({
  key: '',
  name: '',
  category: '',
  status: EFFECTIVE_STATUS_DRAFT as RenderComponentStatus,
  docMarkdown: '',
  exampleJson: '',
})

const pageSizeOptions = [5, 10, 20, 50, 100, 200, 500]

const categoryOptions = computed(() => {
  return categoryRecords.value
    .map(item => item.category || '')
    .filter(Boolean)
    .map(item => ({ label: item, value: item }))
})

const componentCategories = computed(() => {
  const totalCount = Number(summary.value.total || 0)
  return [
    { key: 'all', label: '全部组件', count: totalCount },
    ...categoryRecords.value.map((item) => ({
      key: item.category || '__uncategorized__',
      label: item.label || item.category || '未分类',
      count: Number(item.count || 0),
    })),
  ]
})

const filteredComponentRecords = computed(() => {
  return componentRecords.value.map((record) => ({
    id: record.id,
    key: record.key || '-',
    name: record.name || '未命名组件',
    category: record.category || '未分类',
    status: formatStatus(record.status),
    statusType: resolveStatusType(record.status),
    updatedAt: formatDateTime(record.updateTime || record.createTime),
    updatedBy: record.updatedBy || record.createdBy || '-',
    docPreview: formatPreview(record.docMarkdown),
    examplePreview: formatPreview(record.exampleJson),
  }))
})

function resolveTotal(payloadTotal?: number) {
  const numericTotal = Number(payloadTotal)
  return Number.isFinite(numericTotal) ? numericTotal : componentRecords.value.length
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

function formatPreview(value?: string) {
  if (!value) {
    return '暂无内容'
  }
  const normalized = value.replace(/\s+/g, ' ').trim()
  return normalized.length > 88 ? `${normalized.slice(0, 88)}...` : normalized
}

function formatStatus(status?: RenderComponentStatus) {
  if (status === EFFECTIVE_STATUS_PUBLISHED || status === 'PUBLISHED') {
    return '已发布'
  }
  if (status === EFFECTIVE_STATUS_DRAFT || status === 'DRAFT') {
    return '草稿'
  }
  if (status === EFFECTIVE_STATUS_DISABLED || status === 'DISABLED') {
    return '已停用'
  }
  return status || '-'
}

function resolveStatusType(status?: RenderComponentStatus) {
  if (status === EFFECTIVE_STATUS_PUBLISHED || status === 'PUBLISHED') {
    return 'primary'
  }
  if (status === EFFECTIVE_STATUS_DRAFT || status === 'DRAFT') {
    return 'warning'
  }
  if (status === EFFECTIVE_STATUS_DISABLED || status === 'DISABLED') {
    return 'info'
  }
  return ''
}

function resetComponentForm() {
  editingComponentId.value = null
  componentForm.key = ''
  componentForm.name = ''
  componentForm.category = ''
  componentForm.status = EFFECTIVE_STATUS_DRAFT
  componentForm.docMarkdown = ''
  componentForm.exampleJson = ''
}

async function loadSummary() {
  summaryLoading.value = true
  try {
    const payload = await getRenderComponentSummary()
    summary.value = {
      total: Number(payload?.total || 0),
      published: Number(payload?.published || 0),
      draft: Number(payload?.draft || 0),
      disabled: Number(payload?.disabled || 0),
      categories: Number(payload?.categories || 0),
    }
  }
  catch {
    summary.value = {
      total: 0,
      published: 0,
      draft: 0,
      disabled: 0,
      categories: 0,
    }
  }
  finally {
    summaryLoading.value = false
  }
}

async function loadCategories() {
  categoryError.value = ''
  try {
    categoryRecords.value = await listRenderComponentCategories()
  }
  catch (error) {
    categoryRecords.value = []
    categoryError.value = error instanceof Error ? error.message : '分类加载失败'
  }
}

async function loadComponents() {
  loading.value = true
  errorMessage.value = ''
  try {
    const payload = await searchRenderComponents({
      page: currentPage.value,
      size: pageSize.value,
      keyword: componentKeyword.value.trim() || undefined,
      category: activeCategory.value === 'all' ? undefined : activeCategory.value,
    })
    componentRecords.value = payload?.list ?? []
    total.value = resolveTotal(payload?.pageInfo?.total)
  }
  catch (error) {
    componentRecords.value = []
    total.value = 0
    errorMessage.value = error instanceof Error ? error.message : '组件列表加载失败'
  }
  finally {
    loading.value = false
  }
}

async function loadPageData() {
  await Promise.all([loadSummary(), loadCategories(), loadComponents()])
}

async function handleSearch() {
  currentPage.value = 1
  await loadComponents()
}

async function handleRefresh() {
  await loadPageData()
}

async function handleCurrentPageChange(page: number) {
  currentPage.value = page
  await loadComponents()
}

async function handlePageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  await loadComponents()
}

async function handleSelectCategory(categoryKey: string) {
  activeCategory.value = categoryKey
  currentPage.value = 1
  await loadComponents()
}

function handleCreateComponent() {
  resetComponentForm()
  componentDialogMode.value = 'create'
  componentDialogVisible.value = true
}

function openEditComponentDialog(record: {
  id: string | number
  key: string
  name: string
  category: string
}) {
  const raw = componentRecords.value.find(item => item.id === record.id)
  componentDialogMode.value = 'edit'
  editingComponentId.value = record.id
  componentForm.key = record.key === '-' ? '' : record.key
  componentForm.name = record.name
  componentForm.category = record.category === '未分类' ? '' : record.category
  componentForm.status = raw?.status ?? EFFECTIVE_STATUS_DRAFT
  componentForm.docMarkdown = raw?.docMarkdown || ''
  componentForm.exampleJson = raw?.exampleJson || ''
  componentDialogVisible.value = true
}

async function handleDeleteComponent(record: { id: string | number, name: string }) {
  try {
    await ElMessageBox.confirm(`确定删除组件“${record.name}”吗？`, '删除组件', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteRenderComponent(record.id)
    ElMessage.success('组件已删除')
    await loadPageData()
  }
  catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '删除组件失败')
  }
}

async function submitComponentForm() {
  if (!componentForm.key.trim()) {
    ElMessage.warning('请输入组件标识')
    return
  }
  if (!componentForm.name.trim()) {
    ElMessage.warning('请输入组件名称')
    return
  }

  componentSubmitting.value = true
  try {
    const payload: RenderComponentUpsertPayload = {
      key: componentForm.key.trim(),
      name: componentForm.name.trim(),
      category: componentForm.category.trim() || undefined,
      status: componentForm.status,
      docMarkdown: componentForm.docMarkdown,
      exampleJson: componentForm.exampleJson,
    }
    if (componentDialogMode.value === 'create') {
      await createRenderComponent(payload)
      ElMessage.success('组件已创建')
    } else {
      if (!editingComponentId.value) {
        throw new Error('未找到可编辑的组件')
      }
      await updateRenderComponent(editingComponentId.value, payload)
      ElMessage.success('组件已更新')
    }
    componentDialogVisible.value = false
    resetComponentForm()
    await loadPageData()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '组件保存失败')
  }
  finally {
    componentSubmitting.value = false
  }
}

onMounted(() => {
  void loadPageData()
})
</script>

<template>
  <section class="system-settings-component-page">
    <el-container class="component-manage-layout">
      <el-aside width="220px" class="component-manage-layout__aside">
        <div class="component-manage-layout__aside-title">组件分类</div>
        <div class="component-manage-layout__aside-summary">
          <span>已发布 {{ summary.published }}</span>
          <span>草稿 {{ summary.draft }}</span>
          <span>停用 {{ summary.disabled }}</span>
        </div>
        <div v-if="categoryError" class="component-manage-layout__aside-error">
          {{ categoryError }}
        </div>
        <button
          v-for="category in componentCategories"
          :key="category.key"
          :class="['component-manage-category', { 'is-active': activeCategory === category.key }]"
          type="button"
          @click="handleSelectCategory(category.key)"
        >
          <span>{{ category.label }}</span>
          <strong>{{ category.count }}</strong>
        </button>
      </el-aside>

      <el-container class="component-manage-layout__body">
        <el-header class="component-manage-layout__header">
          <div class="component-manage-layout__title">
            <h3>{{ title }}</h3>
            <p>{{ description }}</p>
          </div>
          <div class="component-manage-layout__tools">
            <el-input v-model="componentKeyword" placeholder="搜索组件名称 / Key / 分类" clearable @keyup.enter="handleSearch">
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button plain :loading="loading || summaryLoading" @click="handleRefresh">
              <el-icon><RefreshRight /></el-icon>
              刷新
            </el-button>
            <el-button type="primary" @click="handleCreateComponent">
              <el-icon><Plus /></el-icon>
              新建组件
            </el-button>
          </div>
        </el-header>

        <el-main class="component-manage-layout__main">
          <div v-if="errorMessage" class="component-manage-layout__state component-manage-layout__state--error">
            {{ errorMessage }}
          </div>
          <div v-else-if="loading" class="component-manage-layout__state">
            正在加载组件列表...
          </div>
          <div v-else-if="!filteredComponentRecords.length" class="component-manage-layout__state">
            当前筛选条件下没有组件
          </div>
          <div v-else class="component-manage-layout__grid">
            <div
              v-for="record in filteredComponentRecords"
              :key="record.id"
              class="component-manage-card"
            >
              <div class="component-manage-card__row">
                <div>
                  <div class="component-manage-card__name">{{ record.name }}</div>
                  <div class="component-manage-card__meta">{{ record.key }}</div>
                </div>
                <div class="component-manage-card__actions">
                  <el-tag size="small" effect="plain" :type="record.statusType">
                    {{ record.status }}
                  </el-tag>
                  <div class="component-manage-card__more-anchor">
                    <el-dropdown
                      trigger="click"
                      placement="bottom-end"
                      @command="(command) => command === 'edit' ? openEditComponentDialog(record) : handleDeleteComponent(record)"
                    >
                      <button class="component-manage-card__more" type="button" @click.stop>
                        <el-icon><MoreFilled /></el-icon>
                      </button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="edit">编辑</el-dropdown-item>
                          <el-dropdown-item command="delete">删除</el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </div>
              </div>
              <div class="component-manage-card__info">
                <span>{{ record.category }}</span>
                <span>{{ record.updatedBy }}</span>
                <span>{{ record.updatedAt }}</span>
              </div>
              <div class="component-manage-card__content">
                <div class="component-manage-card__section">
                  <label>文档</label>
                  <p>{{ record.docPreview }}</p>
                </div>
                <div class="component-manage-card__section">
                  <label>示例</label>
                  <p>{{ record.examplePreview }}</p>
                </div>
              </div>
            </div>
          </div>
        </el-main>

        <el-footer class="component-manage-layout__footer">
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
    </el-container>
  </section>

  <el-dialog
    v-model="componentDialogVisible"
    :title="componentDialogMode === 'create' ? '新建组件' : '编辑组件'"
    width="620px"
    @closed="resetComponentForm"
  >
    <el-form label-width="88px">
      <el-form-item label="组件标识">
        <el-input v-model="componentForm.key" placeholder="请输入组件 Key" />
      </el-form-item>
      <el-form-item label="组件名称">
        <el-input v-model="componentForm.name" placeholder="请输入组件名称" />
      </el-form-item>
      <el-form-item label="分类">
        <el-select
          v-model="componentForm.category"
          allow-create
          clearable
          default-first-option
          filterable
          placeholder="请输入或选择分类"
          style="width: 100%"
        >
          <el-option
            v-for="option in categoryOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="componentForm.status" placeholder="请选择状态" style="width: 100%">
          <el-option label="草稿" :value="EFFECTIVE_STATUS_DRAFT" />
          <el-option label="已发布" :value="EFFECTIVE_STATUS_PUBLISHED" />
          <el-option label="已停用" :value="EFFECTIVE_STATUS_DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item label="说明文档">
        <el-input v-model="componentForm.docMarkdown" type="textarea" :rows="8" placeholder="请输入组件文档" />
      </el-form-item>
      <el-form-item label="示例 JSON">
        <el-input v-model="componentForm.exampleJson" type="textarea" :rows="8" placeholder="请输入示例 JSON" />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="component-manage-dialog__footer">
        <el-button @click="componentDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="componentSubmitting" @click="submitComponentForm">保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.system-settings-component-page {
  display: flex;
  flex: 1;
  min-height: 0;
}

.component-manage-layout {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
  box-shadow: var(--system-shadow);
}

.component-manage-layout__aside {
  min-height: 0;
  padding: 14px 12px;
  border-right: 1px solid var(--system-border-subtle);
  background: var(--system-surface-muted);
  overflow-y: auto;
}

.component-manage-layout__aside-title {
  margin-bottom: 8px;
  color: var(--system-text-muted);
  font-size: 12px;
  font-weight: 600;
}

.component-manage-layout__aside-summary {
  display: grid;
  gap: 4px;
  margin-bottom: 10px;
  color: var(--system-text-soft);
  font-size: 12px;
}

.component-manage-layout__aside-error {
  margin-bottom: 8px;
  color: var(--system-danger);
  font-size: 12px;
}

.component-manage-category {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 34px;
  padding: 0 10px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--system-text);
  font-size: 13px;
  cursor: pointer;
}

.component-manage-category.is-active {
  background: var(--system-accent-bg);
  color: var(--system-accent-text);
}

.component-manage-category strong {
  font-size: 12px;
  font-weight: 600;
}

.component-manage-layout__body {
  min-width: 0;
  min-height: 0;
}

.component-manage-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 66px;
  padding: 0 16px;
  border-bottom: 1px solid var(--system-border-subtle);
}

.component-manage-layout__title h3 {
  margin: 0;
  color: var(--system-title);
  font-size: 16px;
}

.component-manage-layout__title p {
  margin: 3px 0 0;
  color: var(--system-text-muted);
  font-size: 12px;
}

.component-manage-layout__tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.component-manage-layout__tools :deep(.el-input) {
  width: 240px;
}

.component-manage-layout__tools :deep(.el-input__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
}

.component-manage-layout__tools :deep(.el-input__inner),
.component-manage-layout__tools :deep(.el-input__prefix-inner) {
  color: var(--system-text);
}

.component-manage-layout__tools :deep(.el-input__inner::placeholder) {
  color: var(--system-text-faint);
}

.component-manage-layout__tools :deep(.el-button) {
  border-radius: 10px;
}

.component-manage-layout__tools :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.component-manage-layout__tools :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-primary-button-bg);
  color: var(--system-primary-button-text);
}

.component-manage-layout__main {
  min-height: 0;
  padding: 14px 16px;
  background: var(--system-surface-muted);
  overflow-y: auto;
}

.component-manage-layout__state {
  display: grid;
  place-items: center;
  min-height: 240px;
  color: var(--system-text-muted);
  font-size: 13px;
}

.component-manage-layout__state--error {
  color: var(--system-danger);
}

.component-manage-layout__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  align-content: start;
  gap: 10px;
}

.component-manage-card {
  position: relative;
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--system-border);
  border-radius: 14px;
  background: var(--system-surface-solid);
}

.component-manage-card__row,
.component-manage-card__info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.component-manage-card__name {
  color: var(--system-title);
  font-size: 14px;
  font-weight: 600;
}

.component-manage-card__meta,
.component-manage-card__info {
  color: var(--system-text-muted);
  font-size: 12px;
}

.component-manage-card__meta {
  margin-top: 2px;
}

.component-manage-card__content {
  display: grid;
  gap: 8px;
}

.component-manage-card__section {
  display: grid;
  gap: 4px;
}

.component-manage-card__section label {
  color: var(--system-text-faint);
  font-size: 12px;
}

.component-manage-card__section p {
  margin: 0;
  color: var(--system-text);
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
}

.component-manage-card__actions {
  display: flex;
  align-items: center;
  gap: 0;
}

.component-manage-card__more-anchor {
  position: relative;
  width: 22px;
  height: 22px;
  margin-left: 6px;
}

.component-manage-card__more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--system-text-faint);
  cursor: pointer;
  opacity: 0;
  pointer-events: none;
  transform: scale(0.92);
  transition: opacity 0.2s ease, transform 0.2s ease, background-color 0.2s ease, color 0.2s ease;
}

.component-manage-card__more:hover {
  background: var(--system-accent-bg-strong);
  color: var(--system-accent-text);
}

.component-manage-card:hover .component-manage-card__more,
.component-manage-card__more:focus-visible {
  opacity: 1;
  pointer-events: auto;
  transform: scale(1);
}

.component-manage-layout__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 44px;
  padding: 0 16px;
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-solid);
}

.system-settings-component-page :deep(.el-overlay-dialog) {
  display: flex;
  align-items: center;
  justify-content: center;
}

.system-settings-component-page :deep(.el-dialog) {
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
}

.system-settings-component-page :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.system-settings-component-page :deep(.el-dialog__body) {
  background: var(--system-surface-strong);
}

.system-settings-component-page :deep(.el-dialog__footer) {
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.system-settings-component-page :deep(.el-form-item__label) {
  color: var(--system-text-soft);
}

.system-settings-component-page :deep(.el-input__wrapper),
.system-settings-component-page :deep(.el-textarea__inner),
.system-settings-component-page :deep(.el-select__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
  color: var(--system-text);
}

.component-manage-dialog__footer {
  display: flex;
  justify-content: flex-end;
}

.component-manage-dialog__footer :deep(.el-button) {
  min-width: 76px;
  border-radius: 10px;
}

.component-manage-dialog__footer :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.component-manage-dialog__footer :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-primary-button-bg);
  color: var(--system-primary-button-text);
}

@media (max-width: 960px) {
  .component-manage-layout__header {
    flex-direction: column;
    align-items: flex-start;
    height: auto;
    padding: 12px;
  }

  .component-manage-layout__tools {
    width: 100%;
    flex-wrap: wrap;
  }

  .component-manage-layout__tools :deep(.el-input) {
    width: 100%;
  }

  .component-manage-layout__footer {
    height: auto;
    padding: 10px 12px;
    flex-wrap: wrap;
  }
}
</style>
