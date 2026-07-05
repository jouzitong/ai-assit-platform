<script setup lang="ts">
import { Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import {
  getRenderComponentSummary,
  listRenderComponentCategories,
  searchRenderComponents,
  type RenderComponentCategoryItem,
  type RenderComponentItem,
  type RenderComponentStatus,
} from '../../api/renderComponents'

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
const summary = ref({
  total: 0,
  published: 0,
  draft: 0,
  disabled: 0,
  categories: 0,
})

const pageSizeOptions = [10, 20, 50, 100, 200, 500]

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
  if (status === 'PUBLISHED') {
    return '已发布'
  }
  if (status === 'DRAFT') {
    return '草稿'
  }
  if (status === 'DISABLED') {
    return '已停用'
  }
  return status || '-'
}

function resolveStatusType(status?: RenderComponentStatus) {
  if (status === 'PUBLISHED') {
    return 'primary'
  }
  if (status === 'DRAFT') {
    return 'warning'
  }
  if (status === 'DISABLED') {
    return 'info'
  }
  return ''
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
  ElMessage.info('新建组件表单下一步再接')
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
            <h3>组件管理</h3>
            <p>已对接组件实体表，支持真实分类、搜索和分页。</p>
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
                <el-tag size="small" effect="plain" :type="record.statusType">
                  {{ record.status }}
                </el-tag>
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
          <div class="component-manage-layout__footer-total">
            Total {{ total }}
          </div>
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="pageSizeOptions"
            :pager-count="5"
            layout="sizes, prev, pager, next"
            :total="total"
            @current-change="handleCurrentPageChange"
            @size-change="handlePageSizeChange"
          />
        </el-footer>
      </el-container>
    </el-container>
  </section>
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
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.component-manage-layout__aside {
  min-height: 0;
  padding: 14px 12px;
  border-right: 1px solid #eef2f7;
  background: #fbfcfd;
  overflow-y: auto;
}

.component-manage-layout__aside-title {
  margin-bottom: 8px;
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
}

.component-manage-layout__aside-summary {
  display: grid;
  gap: 4px;
  margin-bottom: 10px;
  color: #64748b;
  font-size: 12px;
}

.component-manage-layout__aside-error {
  margin-bottom: 8px;
  color: #dc2626;
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
  color: #374151;
  font-size: 13px;
  cursor: pointer;
}

.component-manage-category.is-active {
  background: #eef4ff;
  color: #1d4ed8;
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
  border-bottom: 1px solid #eef2f7;
}

.component-manage-layout__title h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
}

.component-manage-layout__title p {
  margin: 3px 0 0;
  color: #6b7280;
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

.component-manage-layout__main {
  min-height: 0;
  padding: 14px 16px;
  background: #f8fafc;
  overflow-y: auto;
}

.component-manage-layout__state {
  display: grid;
  place-items: center;
  min-height: 240px;
  color: #6b7280;
  font-size: 13px;
}

.component-manage-layout__state--error {
  color: #dc2626;
}

.component-manage-layout__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  align-content: start;
  gap: 10px;
}

.component-manage-card {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid #e8edf3;
  border-radius: 14px;
  background: #fff;
}

.component-manage-card__row,
.component-manage-card__info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.component-manage-card__name {
  color: #111827;
  font-size: 14px;
  font-weight: 600;
}

.component-manage-card__meta,
.component-manage-card__info {
  color: #6b7280;
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
  color: #94a3b8;
  font-size: 12px;
}

.component-manage-card__section p {
  margin: 0;
  color: #334155;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
}

.component-manage-layout__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 44px;
  padding: 0 16px;
  border-top: 1px solid #eef2f7;
  background: #fff;
}

.component-manage-layout__footer-total {
  color: #6b7280;
  font-size: 12px;
  white-space: nowrap;
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
