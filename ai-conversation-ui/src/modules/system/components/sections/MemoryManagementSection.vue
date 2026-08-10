<script setup lang="ts">
import { Delete, EditPen, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  AppDialog,
  AppPagination,
  LayoutActionBar,
  LayoutPageHeader,
  useAppConfirm,
} from '../../../../components'
import {
  clearManagedLongTermMemories,
  correctManagedMemory,
  createManagedLongTermMemory,
  disableManagedMemory,
  fetchManagedLongTermMemories,
  forgetManagedMemory,
  restoreManagedMemory,
  type MemoryItemStatus,
  type MemoryManagementItem,
  type MemoryType,
} from '../../api/memory'

type DialogMode = 'create' | 'edit'
type AlertType = 'success' | 'info' | 'warning' | 'error'

const appConfirm = useAppConfirm()
const keyword = ref('')
const pageSize = ref(20)
const currentPage = ref(1)
const loading = ref(false)
const saving = ref(false)
const operationMemoryRef = ref('')
const errorMessage = ref('')
const providerStatus = ref('')
const memoryLag = ref(false)
const records = ref<MemoryManagementItem[]>([])
const processingRecords = ref<MemoryManagementItem[]>([])
const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('create')
const editingMemory = ref<MemoryManagementItem | null>(null)
const draft = reactive({ content: '' })

const pageSizeOptions = [10, 20, 50, 100]
const providerAvailable = computed(() => providerStatus.value === 'AVAILABLE')
const providerAlertType = computed<AlertType>(() => {
  if (providerStatus.value === 'AVAILABLE') return 'success'
  if (providerStatus.value === 'DISABLED') return 'warning'
  if (providerStatus.value === 'BINDING_UNAVAILABLE') return 'info'
  return 'error'
})
const providerStatusMessage = computed(() => {
  switch (providerStatus.value) {
    case 'AVAILABLE':
      return 'RAGFlow 记忆服务可用。页面仅展示当前账号经过服务端归属校验的长期记忆。'
    case 'DISABLED':
      return '当前环境未启用 Chat Memory，不能新增或修改记忆。'
    case 'BINDING_UNAVAILABLE':
      return '当前账号尚未建立可用的 RAGFlow 记忆绑定，请先完成一次已启用 Memory 的聊天。'
    case 'SECURITY_REJECTED':
      return 'Provider 返回的数据未通过归属校验，系统已拒绝展示和操作这些数据。'
    case 'UNAVAILABLE':
      return 'RAGFlow 记忆服务暂不可用，当前不能执行记忆操作。'
    default:
      return '正在读取 RAGFlow 记忆服务状态。'
  }
})
const allRecords = computed(() => [...records.value, ...processingRecords.value])
const filteredRecords = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLocaleLowerCase()
  if (!normalizedKeyword) {
    return allRecords.value
  }
  return allRecords.value.filter((item) => [
    item.content,
    resolveMemoryType(item.memoryType),
    resolveSourceSession(item.sourceSessionCode),
    item.sourceRoundCode,
    resolveMemoryStatus(item.status),
  ].filter(Boolean).join(' ').toLocaleLowerCase().includes(normalizedKeyword))
})
const total = computed(() => filteredRecords.value.length)
const pagedRecords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredRecords.value.slice(start, start + pageSize.value)
})
const hasProcessingRecords = computed(() => processingRecords.value.length > 0 || memoryLag.value)
const dialogTitle = computed(() => dialogMode.value === 'create' ? '新增长期记忆' : '修正长期记忆')
const dialogDescription = computed(() => dialogMode.value === 'create'
  ? '内容将直接写入 RAGFlow，并由 Provider 异步提炼为可召回的长期记忆。'
  : '修正会停用原记忆，并在 RAGFlow 中异步生成新的记忆内容。')
const dialogConfirmText = computed(() => dialogMode.value === 'create' ? '确认新增' : '确认修正')

function resolveMemoryType(type?: MemoryType | null) {
  const labels: Record<MemoryType, string> = {
    RAW: '原始记忆',
    SEMANTIC: '语义记忆',
    EPISODIC: '情景记忆',
    PROCEDURAL: '工作方式',
  }
  return type ? labels[type] || type : '待提炼'
}

function resolveMemoryStatus(status?: MemoryItemStatus | null) {
  const labels: Record<MemoryItemStatus, string> = {
    ACTIVE: '已启用',
    DISABLED: '已停用',
    PROCESSING: '处理中',
    FAILED: '处理失败',
    FORGOTTEN: '已遗忘',
  }
  return status ? labels[status] || status : '处理中'
}

function resolveStatusTagType(status?: MemoryItemStatus | null) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'DISABLED') return 'info'
  if (status === 'FAILED' || status === 'FORGOTTEN') return 'danger'
  return 'warning'
}

function resolveSourceSession(sessionCode?: string | null) {
  if (!sessionCode) return '—'
  return sessionCode === 'long-term-manual' ? '手动新增' : sessionCode
}

function formatTime(value?: string | null) {
  if (!value) return '—'
  const time = new Date(value)
  if (Number.isNaN(time.getTime())) return value
  return time.toLocaleString('zh-CN', { hour12: false })
}

function memoryRowKey(item: MemoryManagementItem) {
  return item.memoryRef || `processing-${item.sourceRoundCode || item.sourceSessionCode || 'unknown'}-${item.createdAt || ''}`
}

function isActionDisabled(item: MemoryManagementItem) {
  return !providerAvailable.value || !item.memoryRef || saving.value || operationMemoryRef.value === item.memoryRef
}

function resetDialog() {
  editingMemory.value = null
  draft.content = ''
}

function openCreateDialog() {
  dialogMode.value = 'create'
  resetDialog()
  dialogVisible.value = true
}

function openEditDialog(item: MemoryManagementItem) {
  if (isActionDisabled(item)) return
  dialogMode.value = 'edit'
  editingMemory.value = item
  draft.content = item.content || ''
  dialogVisible.value = true
}

async function loadMemories(resetPage = false) {
  if (resetPage) {
    currentPage.value = 1
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const payload = await fetchManagedLongTermMemories()
    records.value = Array.isArray(payload?.items) ? payload.items : []
    processingRecords.value = Array.isArray(payload?.processingItems) ? payload.processingItems : []
    providerStatus.value = payload?.providerStatus || 'UNAVAILABLE'
    memoryLag.value = Boolean(payload?.memoryLag)
    const maxPage = Math.max(1, Math.ceil(total.value / pageSize.value))
    if (currentPage.value > maxPage) {
      currentPage.value = maxPage
    }
  }
  catch (error) {
    records.value = []
    processingRecords.value = []
    memoryLag.value = false
    providerStatus.value = 'UNAVAILABLE'
    errorMessage.value = error instanceof Error ? error.message : '长期记忆加载失败'
  }
  finally {
    loading.value = false
  }
}

async function submitDialog() {
  const content = draft.content.trim()
  if (!content) {
    ElMessage.error('请输入长期记忆内容')
    return
  }

  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await createManagedLongTermMemory({ content, confirmed: true })
      ElMessage.success('已提交长期记忆，RAGFlow 完成提炼后会显示在列表中')
    }
    else if (editingMemory.value?.memoryRef) {
      await correctManagedMemory(editingMemory.value.memoryRef, { content, confirmed: true })
      ElMessage.success('已提交记忆修正，RAGFlow 完成提炼后会更新列表')
    }
    else {
      ElMessage.error('缺少可修正的记忆标识')
      return
    }
    dialogVisible.value = false
    await loadMemories(true)
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '记忆保存失败')
  }
  finally {
    saving.value = false
  }
}

async function toggleMemory(item: MemoryManagementItem) {
  if (isActionDisabled(item) || !item.memoryRef) return
  operationMemoryRef.value = item.memoryRef
  try {
    if (item.status === 'DISABLED') {
      await restoreManagedMemory(item.memoryRef)
      ElMessage.success('记忆已恢复')
    }
    else {
      await disableManagedMemory(item.memoryRef)
      ElMessage.success('记忆已停用')
    }
    await loadMemories()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '记忆状态更新失败')
  }
  finally {
    operationMemoryRef.value = ''
  }
}

async function deleteMemory(item: MemoryManagementItem) {
  if (isActionDisabled(item) || !item.memoryRef) return
  const confirmed = await appConfirm('删除后无法恢复，该记忆将从 RAGFlow 中永久遗忘。是否继续？', {
    title: '永久删除记忆',
    confirmButtonText: '确认删除',
    danger: true,
  })
  if (!confirmed) return

  operationMemoryRef.value = item.memoryRef
  try {
    await forgetManagedMemory(item.memoryRef)
    ElMessage.success('记忆已删除')
    await loadMemories(true)
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除记忆失败')
  }
  finally {
    operationMemoryRef.value = ''
  }
}

async function clearMemories() {
  if (!providerAvailable.value || !records.value.length || saving.value) return
  const confirmed = await appConfirm('这会清空当前账号的全部长期记忆，并在 RAGFlow 中切换到新的空记忆空间。该操作不可恢复。', {
    title: '清空长期记忆',
    confirmButtonText: '确认清空',
    danger: true,
  })
  if (!confirmed) return

  saving.value = true
  try {
    await clearManagedLongTermMemories()
    ElMessage.success('已提交清空操作，旧记忆将由后台异步清理')
    await loadMemories(true)
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '清空长期记忆失败')
  }
  finally {
    saving.value = false
  }
}

function handleCurrentPageChange(page: number) {
  currentPage.value = page
}

function handlePageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
}

watch(keyword, () => {
  currentPage.value = 1
})

onMounted(() => {
  void loadMemories()
})
</script>

<template>
  <section class="memory-management-page">
    <el-container class="memory-management-layout">
      <el-header class="memory-management-layout__header">
        <LayoutPageHeader
          title="记忆管理"
          description="维护当前账号的长期记忆。记忆正文和向量始终由 RAGFlow 管理，本系统仅提供可信控制与操作入口。"
        >
          <template #actions>
            <LayoutActionBar class="memory-management-layout__tools">
              <el-input
                v-model="keyword"
                clearable
                placeholder="搜索记忆内容、类型或来源会话"
                @clear="currentPage = 1"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
              <el-button plain :loading="loading" @click="loadMemories()">
                <el-icon><RefreshRight /></el-icon>
                刷新
              </el-button>
              <el-button type="danger" plain :disabled="!providerAvailable || !records.length || saving" @click="clearMemories">
                <el-icon><Delete /></el-icon>
                清空全部
              </el-button>
              <el-button type="primary" :disabled="!providerAvailable || saving" @click="openCreateDialog">
                <el-icon><Plus /></el-icon>
                新增记忆
              </el-button>
            </LayoutActionBar>
          </template>
        </LayoutPageHeader>
      </el-header>

      <el-main class="memory-management-layout__main">
        <el-alert
          class="memory-management-status"
          :title="providerStatusMessage"
          :type="providerAlertType"
          show-icon
          :closable="false"
        />
        <el-alert
          v-if="hasProcessingRecords"
          class="memory-management-status"
          title="存在正在提炼或同步的记忆。RAGFlow 完成异步处理后，刷新列表即可查看最终结果。"
          type="info"
          show-icon
          :closable="false"
        />
        <el-alert
          v-if="errorMessage"
          class="memory-management-status"
          :title="errorMessage"
          type="error"
          show-icon
          :closable="false"
        />

        <div v-if="loading" class="memory-management-state">正在加载长期记忆...</div>
        <el-empty
          v-else-if="!filteredRecords.length"
          class="memory-management-empty"
          :description="keyword ? '没有匹配的长期记忆' : '暂无长期记忆'"
          :image-size="76"
        />
        <el-table
          v-else
          class="memory-management-table"
          :data="pagedRecords"
          :row-key="memoryRowKey"
          stripe
        >
          <el-table-column label="记忆内容" min-width="340">
            <template #default="{ row }">
              <div class="memory-management-table__content">
                <span>{{ row.content || '记忆正在提炼，暂未生成可展示内容。' }}</span>
                <small v-if="row.sourceRoundCode">来源回合：{{ row.sourceRoundCode }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ resolveMemoryType(row.memoryType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="104">
            <template #default="{ row }">
              <el-tag size="small" :type="resolveStatusTagType(row.status)">{{ resolveMemoryStatus(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="来源" min-width="136">
            <template #default="{ row }">{{ resolveSourceSession(row.sourceSessionCode) }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <div class="memory-management-table__actions">
                <el-button link type="primary" :disabled="isActionDisabled(row)" @click="openEditDialog(row)">
                  <el-icon><EditPen /></el-icon>
                  修正
                </el-button>
                <el-button link :disabled="isActionDisabled(row)" @click="toggleMemory(row)">
                  {{ row.status === 'DISABLED' ? '恢复' : '停用' }}
                </el-button>
                <el-button link type="danger" :disabled="isActionDisabled(row)" @click="deleteMemory(row)">
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-main>

      <el-footer class="memory-management-layout__footer">
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

    <AppDialog
      v-model="dialogVisible"
      :title="dialogTitle"
      :description="dialogDescription"
      size="medium"
      action-mode="confirm"
      :confirm-text="dialogConfirmText"
      :confirming="saving"
      :confirm-disabled="!draft.content.trim()"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      @confirm="submitDialog"
      @closed="resetDialog"
    >
      <el-form label-position="top" class="memory-management-dialog__form">
        <el-form-item label="长期记忆内容" required>
          <el-input
            v-model="draft.content"
            type="textarea"
            :rows="6"
            maxlength="2000"
            show-word-limit
            :disabled="saving"
            placeholder="例如：我偏好用数据列表展示结果，并优先标注数据来源。"
          />
        </el-form-item>
        <el-alert
          title="提交后由 RAGFlow 异步提炼。编辑不是本地覆盖，而是停用旧记忆并创建经过修正的新记忆。"
          type="info"
          show-icon
          :closable="false"
        />
      </el-form>
    </AppDialog>
  </section>
</template>

<style scoped>
.memory-management-page {
  display: flex;
  flex: 1;
  min-height: 0;
  container-type: inline-size;
}

.memory-management-layout {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: var(--app-radius-xl);
  background: var(--system-surface-strong);
  box-shadow: var(--system-shadow);
}

.memory-management-layout__header {
  display: flex;
  align-items: center;
  height: var(--app-layout-header-height);
  padding: 0 var(--app-space-4);
  border-bottom: 1px solid var(--system-border-subtle);
}

.memory-management-layout__tools {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--app-space-2);
  flex-wrap: wrap;
}

.memory-management-layout__tools :deep(.el-input) {
  width: 280px;
}

.memory-management-layout__tools :deep(.el-input__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
}

.memory-management-layout__tools :deep(.el-input__wrapper:hover),
.memory-management-layout__tools :deep(.el-input__wrapper.is-focus) {
  border-color: var(--system-accent-border);
}

.memory-management-layout__main {
  display: flex;
  min-height: 0;
  padding: var(--app-space-4);
  flex-direction: column;
  gap: var(--app-space-3);
  overflow: auto;
}

.memory-management-status {
  flex: 0 0 auto;
}

.memory-management-state,
.memory-management-empty {
  flex: 1;
  display: grid;
  min-height: 220px;
  place-items: center;
  color: var(--system-text-muted);
}

.memory-management-table {
  flex: 1;
  min-width: 0;
}

.memory-management-table__content {
  display: grid;
  gap: var(--app-space-1);
  line-height: var(--app-line-height-body);
}

.memory-management-table__content > span {
  color: var(--system-text);
  word-break: break-word;
}

.memory-management-table__content > small {
  color: var(--system-text-muted);
  font-size: var(--app-font-size-caption);
}

.memory-management-table__actions {
  display: flex;
  align-items: center;
  gap: var(--app-space-1);
  white-space: nowrap;
}

.memory-management-layout__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-height: calc(var(--app-control-height-lg) + var(--app-space-4));
  padding: 0 var(--app-space-4);
  border-top: 1px solid var(--system-border-subtle);
}

.memory-management-dialog__form {
  min-width: 0;
}

@container (max-width: 920px) {
  .memory-management-layout__header {
    height: auto;
    min-height: var(--app-layout-header-height);
    padding-block: var(--app-space-3);
  }

  .memory-management-layout__tools {
    justify-content: flex-start;
  }

  .memory-management-layout__tools :deep(.el-input) {
    width: min(100%, 360px);
  }
}
</style>
