<script setup lang="ts">
import { Delete, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import {
  deleteNode,
  deleteWorkflow,
  searchNodes,
  searchWorkflows,
  type AiNodeItem,
  type AiWorkflowItem,
} from '../../api/workflow'

type WorkflowTab = 'workflow' | 'node'

const activeTab = ref<WorkflowTab>('workflow')
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const workflowRows = ref<AiWorkflowItem[]>([])
const nodeRows = ref<AiNodeItem[]>([])

const pageSizeOptions = [10, 20, 50, 100, 200, 500]
const tabOptions = [
  { key: 'workflow' as const, label: '流程配置' },
  { key: 'node' as const, label: '节点配置' },
]

const currentLabel = computed(() => activeTab.value === 'workflow' ? '流程' : '节点')
const currentRows = computed(() => activeTab.value === 'workflow' ? workflowRows.value : nodeRows.value)
const filteredRows = computed(() => {
  const normalized = keyword.value.trim().toLowerCase()
  const source = currentRows.value
  if (!normalized) {
    return source
  }
  return source.filter((item) => {
    return [item.name, item.code, item.type].some(value => String(value || '').toLowerCase().includes(normalized))
  })
})

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

function formatConfigPreview(value?: Record<string, unknown> | null) {
  if (!value || Object.keys(value).length === 0) {
    return '暂无配置'
  }
  const text = JSON.stringify(value)
  return text.length > 96 ? `${text.slice(0, 96)}...` : text
}

async function loadData() {
  loading.value = true
  errorMessage.value = ''
  try {
    if (activeTab.value === 'workflow') {
      const payload = await searchWorkflows({
        page: currentPage.value,
        size: pageSize.value,
      })
      workflowRows.value = payload?.list ?? []
      total.value = resolveTotal(payload?.pageInfo?.total, workflowRows.value.length)
      return
    }

    const payload = await searchNodes({
      page: currentPage.value,
      size: pageSize.value,
    })
    nodeRows.value = payload?.list ?? []
    total.value = resolveTotal(payload?.pageInfo?.total, nodeRows.value.length)
  }
  catch (error) {
    total.value = 0
    errorMessage.value = error instanceof Error ? error.message : `${currentLabel.value}列表加载失败`
  }
  finally {
    loading.value = false
  }
}

async function handleRefresh() {
  await loadData()
}

async function handleSearch() {
  currentPage.value = 1
  await loadData()
}

async function handleChangeTab(tab: WorkflowTab) {
  if (activeTab.value === tab) {
    return
  }
  activeTab.value = tab
  keyword.value = ''
  currentPage.value = 1
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

async function handleDelete(row: AiWorkflowItem | AiNodeItem) {
  const label = activeTab.value === 'workflow' ? '流程' : '节点'
  try {
    await ElMessageBox.confirm(
      `确认删除${label}「${row.name || row.code || '-'}」吗？删除后不可恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )

    if (activeTab.value === 'workflow') {
      await deleteWorkflow(row.id)
    } else {
      await deleteNode(row.id)
    }

    if (currentRows.value.length === 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    ElMessage.success(`${label}删除成功`)
    await loadData()
  }
  catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : `${label}删除失败`)
  }
}

onMounted(() => {
  void loadData()
})
</script>

<template>
  <section class="workflow-page">
    <div class="workflow-shell">
      <header class="workflow-shell__header">
        <div class="workflow-shell__tabs">
          <el-tag
            v-for="tab in tabOptions"
            :key="tab.key"
            :type="activeTab === tab.key ? 'primary' : 'info'"
            effect="plain"
            class="workflow-shell__tab"
            @click="handleChangeTab(tab.key)"
          >
            {{ tab.label }}
          </el-tag>
        </div>
        <div class="workflow-shell__tools">
          <el-input
            v-model="keyword"
            :placeholder="`搜索${currentLabel}名称 / 编码 / 类型`"
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
        </div>
      </header>

      <main class="workflow-shell__main">
        <div v-if="errorMessage" class="workflow-shell__state workflow-shell__state--error">
          {{ errorMessage }}
        </div>
        <div v-else-if="loading" class="workflow-shell__state">
          正在加载{{ currentLabel }}列表...
        </div>
        <div v-else-if="!filteredRows.length" class="workflow-shell__state">
          当前没有{{ currentLabel }}数据
        </div>
        <div v-else class="workflow-grid">
          <article v-for="row in filteredRows" :key="row.id" class="workflow-card">
            <div class="workflow-card__head">
              <div>
                <h3>{{ row.name || row.code || `未命名${currentLabel}` }}</h3>
                <p>{{ row.code || '-' }}</p>
              </div>
              <div class="workflow-card__tags">
                <el-tag size="small" effect="plain">{{ row.type || '未配置类型' }}</el-tag>
                <el-tag size="small" effect="plain" :type="row.enabled === false ? 'info' : 'success'">
                  {{ row.enabled === false ? '停用' : '启用' }}
                </el-tag>
              </div>
            </div>
            <div class="workflow-card__summary">{{ formatConfigPreview(row.config) }}</div>
            <div class="workflow-card__meta">
              <div class="workflow-card__meta-item">
                <span>类型</span>
                <strong>{{ row.type || '-' }}</strong>
              </div>
              <div class="workflow-card__meta-item">
                <span>更新时间</span>
                <strong>{{ formatDateTime(row.updateTime || row.createTime) }}</strong>
              </div>
            </div>
            <div class="workflow-card__actions">
              <el-button plain circle type="danger" title="删除" @click="handleDelete(row)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </article>
        </div>
      </main>

      <footer class="workflow-shell__footer">
        <div class="workflow-shell__footer-total">Total {{ total }}</div>
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
      </footer>
    </div>
  </section>
</template>

<style scoped>
.workflow-page {
  display: flex;
  flex: 1;
  min-height: 0;
}

.workflow-shell {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  flex: 1;
  min-height: 0;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
  overflow: hidden;
}

.workflow-shell__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-bottom: 1px solid #eef2f7;
}

.workflow-shell__tabs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-shell__tab {
  cursor: pointer;
}

.workflow-shell__tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-shell__tools :deep(.el-input) {
  width: 260px;
}

.workflow-shell__main {
  min-height: 0;
  padding: 12px;
  background: #f8fafc;
  overflow-y: auto;
}

.workflow-shell__state {
  display: grid;
  place-items: center;
  min-height: 260px;
  color: #6b7280;
  font-size: 13px;
}

.workflow-shell__state--error {
  color: #dc2626;
}

.workflow-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 10px;
}

.workflow-card {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
}

.workflow-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.workflow-card__head h3 {
  margin: 0;
  color: #111827;
  font-size: 14px;
  line-height: 1.3;
}

.workflow-card__head p {
  margin: 2px 0 0;
  color: #64748b;
  font-size: 11px;
}

.workflow-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
}

.workflow-card__summary {
  color: #334155;
  font-size: 12px;
  line-height: 1.45;
  word-break: break-all;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.workflow-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 10px;
}

.workflow-card__meta-item {
  display: grid;
  gap: 2px;
}

.workflow-card__meta-item span {
  color: #94a3b8;
  font-size: 11px;
}

.workflow-card__meta-item strong {
  color: #111827;
  font-size: 11px;
  line-height: 1.35;
  word-break: break-all;
}

.workflow-card__actions {
  display: flex;
  justify-content: flex-end;
}

.workflow-card__actions :deep(.el-button.is-circle) {
  padding: 7px;
}

.workflow-shell__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 40px;
  padding: 0 14px;
  border-top: 1px solid #eef2f7;
  background: #fff;
}

.workflow-shell__footer-total {
  color: #6b7280;
  font-size: 12px;
}

@media (max-width: 960px) {
  .workflow-shell__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .workflow-shell__tools {
    width: 100%;
    flex-wrap: wrap;
  }

  .workflow-shell__tools :deep(.el-input) {
    width: 100%;
  }

  .workflow-card__meta {
    grid-template-columns: 1fr;
  }

  .workflow-shell__footer {
    height: auto;
    padding: 10px 12px;
    flex-wrap: wrap;
  }
}
</style>
