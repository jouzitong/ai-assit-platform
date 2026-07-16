<script setup lang="ts" generic="T extends CatalogItem">
import { Delete, EditPen, Plus, RefreshRight, Search, Select } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AppPagination, LayoutPageHeader } from '../../../../components'
import type { CatalogItem, CatalogQuery, PageResult, ValidationReport } from '../types'

const props = defineProps<{
  title: string
  description: string
  resourceLabel: string
  routeBase: string
  listRequest: (query: CatalogQuery) => Promise<PageResult<T> | T[]>
  deleteRequest: (code: string) => Promise<unknown>
  validateRequest: (code: string, version: number) => Promise<ValidationReport>
  publishRequest: (code: string, version: number) => Promise<unknown>
}>()

const router = useRouter()
const rows = ref<T[]>([])
const loading = ref(false)
const actionCode = ref('')
const errorMessage = ref('')
const keyword = ref('')
const status = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const emptyText = computed(() => errorMessage.value || `暂无${props.resourceLabel}`)

function effectiveStatus(item: CatalogItem) {
  return item.draftVersion && item.status === 'PUBLISHED' ? 'DRAFT' : item.status
}

function normalizedResult(result: PageResult<T> | T[]) {
  if (Array.isArray(result)) {
    const normalizedKeyword = keyword.value.trim().toLowerCase()
    const filtered = result.filter((item) => {
      const matchesKeyword = !normalizedKeyword
        || [item.code, item.name, item.description]
          .some(value => String(value || '').toLowerCase().includes(normalizedKeyword))
      const matchesStatus = !status.value || effectiveStatus(item) === status.value
      return matchesKeyword && matchesStatus
    })
    const start = (currentPage.value - 1) * pageSize.value
    return { list: filtered.slice(start, start + pageSize.value), total: filtered.length }
  }
  const list = result?.list || []
  return { list, total: Number(result?.pageInfo?.total ?? list.length) }
}

function definitionVersion(row: T) {
  return Number(row.draftVersion || row.version || row.currentPublishedVersion || 1)
}

function formatDate(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function statusType(value?: string) {
  if (value === 'PUBLISHED') return 'success'
  if (value === 'VALIDATED') return 'primary'
  if (value === 'DEPRECATED' || value === 'ARCHIVED') return 'info'
  return 'warning'
}

async function loadRows() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await props.listRequest({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      status: status.value || undefined,
    })
    const normalized = normalizedResult(result)
    rows.value = normalized.list
    total.value = normalized.total
  }
  catch (error) {
    rows.value = []
    total.value = 0
    errorMessage.value = error instanceof Error ? error.message : `${props.resourceLabel}列表加载失败`
  }
  finally {
    loading.value = false
  }
}

async function search() {
  currentPage.value = 1
  await loadRows()
}

async function openEditor(code = 'new') {
  await router.push(`${props.routeBase}/${encodeURIComponent(code)}`)
}

async function validateRow(row: T) {
  actionCode.value = row.code
  try {
    const report = await props.validateRequest(row.code, definitionVersion(row))
    const issues = [...(report.issues || []), ...(report.warnings || [])]
    if (report.valid === false) {
      ElMessage.error(report.message || issues[0]?.message || '校验未通过')
      return
    }
    ElMessage.success(report.message || (issues.length ? `校验通过，存在 ${issues.length} 条提示` : '校验通过'))
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '校验失败')
  }
  finally {
    actionCode.value = ''
  }
}

async function publishRow(row: T) {
  try {
    await ElMessageBox.confirm(
      `发布${props.resourceLabel}“${row.name || row.code}”的版本 ${definitionVersion(row)}？发布后运行时将只读该版本快照。`,
      `发布${props.resourceLabel}`,
      { type: 'warning', confirmButtonText: '确认发布', cancelButtonText: '取消' },
    )
    actionCode.value = row.code
    await props.publishRequest(row.code, definitionVersion(row))
    ElMessage.success('发布成功')
    await loadRows()
  }
  catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '发布失败')
  }
  finally {
    actionCode.value = ''
  }
}

async function removeRow(row: T) {
  try {
    await ElMessageBox.confirm(`确认删除${props.resourceLabel}“${row.name || row.code}”吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    actionCode.value = row.code
    await props.deleteRequest(row.code)
    ElMessage.success('删除成功')
    if (rows.value.length === 1 && currentPage.value > 1) currentPage.value -= 1
    await loadRows()
  }
  catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
  finally {
    actionCode.value = ''
  }
}

onMounted(loadRows)
</script>

<template>
  <section class="agent-catalog-page">
    <div class="agent-catalog-page__shell">
      <LayoutPageHeader :title="title" :description="description">
        <template #actions>
          <el-button type="primary" :icon="Plus" @click="openEditor()">新增{{ resourceLabel }}</el-button>
        </template>
      </LayoutPageHeader>

      <slot name="before-list" />

      <div class="agent-catalog-page__filters">
        <el-input
          v-model="keyword"
          clearable
          :prefix-icon="Search"
          :placeholder="`搜索${resourceLabel}名称、编码或说明`"
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="status" clearable placeholder="全部状态" @change="search">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已校验" value="VALIDATED" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已废弃" value="DEPRECATED" />
          <el-option label="已归档" value="ARCHIVED" />
        </el-select>
        <el-button :icon="RefreshRight" :loading="loading" @click="loadRows">刷新</el-button>
      </div>

      <div v-if="errorMessage" class="agent-catalog-page__error" role="alert">
        <span>{{ errorMessage }}</span>
        <el-button link type="primary" @click="loadRows">重试</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        border
        stripe
        row-key="code"
        :empty-text="emptyText"
        class="agent-catalog-page__table"
      >
        <el-table-column prop="code" label="编码" min-width="180" fixed="left" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="description" label="说明" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(effectiveStatus(row))" effect="plain">{{ effectiveStatus(row) || 'DRAFT' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="100" align="center">
          <template #default="{ row }">v{{ definitionVersion(row) }}</template>
        </el-table-column>
        <el-table-column label="启用" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === false ? 'info' : 'success'" effect="plain">
              {{ row.enabled === false ? '停用' : '启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDate(row.updateTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" :icon="EditPen" @click="openEditor(row.code)">编辑</el-button>
            <el-button link :icon="Select" :loading="actionCode === row.code" @click="validateRow(row)">校验</el-button>
            <el-button link type="success" :disabled="effectiveStatus(row) === 'PUBLISHED'" @click="publishRow(row)">发布</el-button>
            <el-button link type="danger" :icon="Delete" @click="removeRow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <AppPagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        @current-change="loadRows"
        @size-change="search"
      />
    </div>
  </section>
</template>

<style scoped>
.agent-catalog-page {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  container-type: inline-size;
}

.agent-catalog-page__shell {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--app-space-4);
  min-width: 0;
  min-height: 0;
  padding: var(--app-space-5);
  border: 1px solid var(--system-border);
  border-radius: var(--app-radius-xl);
  background: var(--system-surface-strong);
  box-shadow: var(--system-shadow);
}

.agent-catalog-page__filters {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 160px auto;
  gap: var(--app-space-3);
  align-items: center;
}

.agent-catalog-page__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-3);
  padding: var(--app-space-3) var(--app-space-4);
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: var(--app-radius-lg);
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.agent-catalog-page__table {
  flex: 1;
  min-height: 240px;
}

@container (max-width: 720px) {
  .agent-catalog-page__filters {
    grid-template-columns: 1fr;
  }
}
</style>
