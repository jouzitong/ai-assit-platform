<script setup lang="ts">
import { MoreFilled, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { AppPagination } from '../../../../components'
import ComponentAssetEditorDialog from '../component-manage/views/ComponentAssetEditorDialog.vue'
import {
  getComponentAssetCardInfo,
  type ComponentAssetSubmission,
} from '../component-manage/service/componentAsset'
import {
  createRenderComponent,
  deleteRenderComponent,
  getRenderComponentSummary,
  listRenderComponentCategories,
  searchRenderComponents,
  type RenderComponentCategoryItem,
  type RenderComponentItem,
  type RenderComponentStatus,
  updateRenderComponent,
} from '../../api/renderComponents'

withDefaults(defineProps<{
  title?: string
  description?: string
}>(), {
  title: '组件管理',
  description: '将 Application 组件配置为可检索、可同步的系统数字资产。',
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
const editingComponent = ref<RenderComponentItem | null>(null)
const summary = ref({
  total: 0,
  published: 0,
  draft: 0,
  disabled: 0,
  categories: 0,
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
  return componentRecords.value.map((record) => {
    const asset = getComponentAssetCardInfo(record)
    return {
      id: record.id,
      key: record.key || '-',
      name: record.name || '未命名组件',
      category: record.category || '未分类',
      status: formatStatus(record.status),
      statusType: resolveStatusType(record.status),
      updatedAt: formatDateTime(record.updateTime || record.createTime),
      updatedBy: record.updatedBy || record.createdBy || '-',
      sourceName: asset.sourceName,
      sourceKey: asset.sourceKey,
      parameterCount: asset.parameterCount,
      knowledgeBaseCode: asset.knowledgeBaseCode || '待指定知识库',
      documentSize: record.docMarkdown?.length || 0,
      isAsset: asset.isAsset,
      assetLabel: asset.isAsset ? '数字资产' : '历史配置',
    }
  })
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

function resetComponentEditor() {
  editingComponentId.value = null
  editingComponent.value = null
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
  resetComponentEditor()
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
  if (!raw) {
    ElMessage.error('未找到可编辑的组件数据')
    return
  }
  componentDialogMode.value = 'edit'
  editingComponentId.value = record.id
  editingComponent.value = raw
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

async function submitComponentForm(payload: ComponentAssetSubmission) {
  componentSubmitting.value = true
  try {
    if (componentDialogMode.value === 'create') {
      await createRenderComponent(payload)
      ElMessage.success('组件数字资产已创建')
    } else {
      if (editingComponentId.value == null) {
        throw new Error('未找到可编辑的组件')
      }
      await updateRenderComponent(editingComponentId.value, payload)
      ElMessage.success('组件数字资产已更新')
    }
    componentDialogVisible.value = false
    resetComponentEditor()
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
              新建资产
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
                  <el-tag size="small" effect="plain" :type="record.isAsset ? 'success' : 'info'">
                    {{ record.assetLabel }}
                  </el-tag>
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
                  <label>Application 来源</label>
                  <p>{{ record.sourceName }}</p>
                  <code v-if="record.sourceKey" class="component-manage-card__source-key">{{ record.sourceKey }}</code>
                </div>
                <div class="component-manage-card__section">
                  <label>知识资产</label>
                  <p>{{ record.parameterCount }} 个参数 · {{ record.documentSize.toLocaleString() }} 字符文档</p>
                  <small>{{ record.knowledgeBaseCode }}</small>
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

  <ComponentAssetEditorDialog
    v-model="componentDialogVisible"
    :mode="componentDialogMode"
    :initial-value="editingComponent"
    :category-options="categoryOptions"
    :submitting="componentSubmitting"
    @closed="resetComponentEditor"
    @submit="submitComponentForm"
  />
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

.component-manage-card__section small,
.component-manage-card__source-key {
  width: fit-content;
  max-width: 100%;
  overflow: hidden;
  color: var(--system-text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.component-manage-card__source-key {
  color: var(--system-accent-text);
}

.component-manage-card__actions {
  display: flex;
  align-items: center;
  gap: 6px;
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
