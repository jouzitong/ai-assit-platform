<script setup lang="ts">
import { MoreFilled, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { AppPagination } from '../../../../components'
import ComponentAssetEditorDialog from '../component-manage/views/ComponentAssetEditorDialog.vue'
import {
  getComponentAssetCardInfo,
  getComponentAssetContentFingerprint,
  getComponentAssetKnowledgeState,
  getKnowledgeDocumentCode,
  normalizeComponentAssetDesiredStatus,
  RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY,
  withComponentAssetDesiredStatus,
  withComponentAssetPendingKnowledgeCleanup,
  withComponentAssetPendingKnowledgeSync,
  type ComponentAssetKnowledgeDocumentIdentity,
  type ComponentAssetPendingKnowledgeSync,
  type ComponentAssetSubmission,
} from '../component-manage/service/componentAsset'
import {
  createOrUpdateAiKbDocument,
  deleteAiKbDocuments,
  getAiKbSyncTask,
  searchAiKbDocuments,
  syncAiKbDocuments,
} from '../../api/aiPlatform'
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
import { getEnabledSystemSettingValue } from '../../api/systemSettings'

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
const KNOWLEDGE_SYNC_POLL_INTERVAL_MS = 800
const KNOWLEDGE_SYNC_WAIT_TIMEOUT_MS = 30_000

const componentKeyword = ref('')
const activeCategory = ref('all')
const pageSize = ref(20)
const currentPage = ref(1)
const total = ref(0)
const loading = ref(false)
const summaryLoading = ref(false)
const knowledgeBaseLoading = ref(false)
const errorMessage = ref('')
const categoryError = ref('')
const knowledgeBaseId = ref('')
const knowledgeBaseError = ref('')
const componentRecords = ref<RenderComponentItem[]>([])
const componentIndexRecords = ref<RenderComponentItem[]>([])
const componentIndexLoading = ref(false)
const categoryRecords = ref<RenderComponentCategoryItem[]>([])
const componentDialogVisible = ref(false)
const componentDialogInstanceKey = ref(0)
const componentDialogMode = ref<'create' | 'edit'>('create')
const componentSubmitting = ref(false)
const editingComponentId = ref<string | number | null>(null)
const editingComponent = ref<RenderComponentItem | null>(null)
const editingComponentRevision = ref('')
const summary = ref({
  total: 0,
  published: 0,
  draft: 0,
  disabled: 0,
  categories: 0,
})

interface KnowledgeDocumentIdentity {
  kbCode: string
  documentCode: string
}

interface ActiveKnowledgeSyncAttempt {
  taskCode: string
  componentId: string | number
  assetKey: string
  documentName: string
  contentFingerprint: string
  desiredStatus: ComponentAssetPendingKnowledgeSync['desiredStatus']
  target: KnowledgeDocumentIdentity
}

class KnowledgeSyncTerminalError extends Error {
  override name = 'KnowledgeSyncTerminalError'
}

const activeKnowledgeSyncAttempt = ref<ActiveKnowledgeSyncAttempt | null>(null)

const pageSizeOptions = [5, 10, 20, 50, 100, 200, 500]

const categoryOptions = computed(() => {
  return categoryRecords.value
    .map(item => item.category || '')
    .filter(Boolean)
    .map(item => ({ label: item, value: item }))
})

const existingRendererSourceKeys = computed(() => [...new Set(
  componentIndexRecords.value
    .map((record) => {
      const asset = getComponentAssetCardInfo(record)
      return asset.sourceKey || record.key || ''
    })
    .filter(Boolean),
)])

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
    const knowledgeAsset = asset as typeof asset & {
      knowledgeBaseId?: string
      knowledgeBaseCode?: string
    }
    return {
      id: record.id,
      key: record.key || '',
      name: record.name || '未命名组件',
      category: record.category || '未分类',
      status: formatStatus(record.status),
      statusType: resolveStatusType(record.status),
      updatedAt: formatDateTime(record.updateTime || record.createTime),
      updatedBy: record.updatedBy || record.createdBy || '-',
      sourceName: asset.sourceName,
      sourceKey: asset.sourceKey,
      parameterCount: asset.parameterCount,
      knowledgeBaseId: knowledgeAsset.knowledgeBaseId || knowledgeAsset.knowledgeBaseCode || '',
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
  editingComponentRevision.value = ''
}

function getComponentRecordRevision(record: RenderComponentItem | null | undefined) {
  if (!record) return ''
  return JSON.stringify({
    id: record.id,
    key: record.key || '',
    name: record.name || '',
    category: record.category || '',
    status: record.status ?? '',
    docMarkdown: record.docMarkdown || '',
    exampleJson: record.exampleJson || '',
    updateTime: record.updateTime || '',
  })
}

function toKnowledgeDocumentIdentity(
  identity: ComponentAssetKnowledgeDocumentIdentity,
): KnowledgeDocumentIdentity {
  return {
    kbCode: identity.knowledgeBaseId,
    documentCode: identity.documentCode,
  }
}

function toAssetKnowledgeDocumentIdentity(
  identity: KnowledgeDocumentIdentity,
): ComponentAssetKnowledgeDocumentIdentity {
  return {
    knowledgeBaseId: identity.kbCode,
    documentCode: identity.documentCode,
  }
}

function getPersistedKnowledgeDocumentState(record = editingComponent.value) {
  if (componentDialogMode.value !== 'edit' || !record) {
    return {
      current: null,
      pendingCleanup: [] as KnowledgeDocumentIdentity[],
      pendingSync: null as ComponentAssetPendingKnowledgeSync | null,
    }
  }
  const state = getComponentAssetKnowledgeState(record)
  return {
    current: state.current ? toKnowledgeDocumentIdentity(state.current) : null,
    pendingCleanup: state.pendingCleanup.map(toKnowledgeDocumentIdentity),
    pendingSync: state.pendingSync,
  }
}

function toPersistedKnowledgeSyncAttempt(
  attempt: ActiveKnowledgeSyncAttempt,
): ComponentAssetPendingKnowledgeSync {
  return {
    taskCode: attempt.taskCode,
    componentId: String(attempt.componentId),
    assetKey: attempt.assetKey,
    documentName: attempt.documentName,
    contentFingerprint: attempt.contentFingerprint,
    desiredStatus: attempt.desiredStatus,
    target: toAssetKnowledgeDocumentIdentity(attempt.target),
  }
}

function isSamePersistedKnowledgeSyncAttempt(
  persisted: ComponentAssetPendingKnowledgeSync | null,
  active: ActiveKnowledgeSyncAttempt,
) {
  return Boolean(
    persisted
    && persisted.taskCode === active.taskCode
    && persisted.componentId === String(active.componentId)
    && persisted.assetKey === active.assetKey
    && persisted.documentName === active.documentName
    && persisted.contentFingerprint === active.contentFingerprint
    && persisted.desiredStatus === active.desiredStatus
    && persisted.target.knowledgeBaseId === active.target.kbCode
    && persisted.target.documentCode === active.target.documentCode,
  )
}

function isSameKnowledgeDocument(
  left: KnowledgeDocumentIdentity | null,
  right: KnowledgeDocumentIdentity,
) {
  return Boolean(
    left
    && left.kbCode === right.kbCode
    && left.documentCode === right.documentCode,
  )
}

function uniqueKnowledgeDocuments(identities: readonly KnowledgeDocumentIdentity[]) {
  return identities.filter((identity, index) => identities.findIndex(item => (
    item.kbCode === identity.kbCode && item.documentCode === identity.documentCode
  )) === index)
}

async function removeKnowledgeDocuments(identities: readonly KnowledgeDocumentIdentity[]) {
  const failures: string[] = []
  const documentsByKnowledgeBase = new Map<string, string[]>()
  uniqueKnowledgeDocuments(identities).forEach((identity) => {
    const documentCodes = documentsByKnowledgeBase.get(identity.kbCode) || []
    documentCodes.push(identity.documentCode)
    documentsByKnowledgeBase.set(identity.kbCode, documentCodes)
  })

  for (const [kbCode, documentCodes] of documentsByKnowledgeBase) {
    try {
      const result = await deleteAiKbDocuments({ kbCode, documentCodes })
      const skippedDocumentCodes = result?.skippedDocumentCodes || []
      const skippedStillPresent = (await Promise.all(skippedDocumentCodes.map(async (documentCode) => {
        const page = await searchAiKbDocuments({
          kbCode,
          documentCode,
          page: 1,
          size: 1,
        })
        return (page?.list || []).some(item => item.documentCode === documentCode)
          ? documentCode
          : ''
      }))).filter(Boolean)
      if (skippedStillPresent.length) {
        failures.push(`${kbCode}：文档删除未完成（${skippedStillPresent.join('、')}）`)
      }
    }
    catch (error) {
      failures.push(`${kbCode}：${error instanceof Error ? error.message : '未知错误'}`)
    }
  }
  return failures
}

function delay(milliseconds: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, milliseconds)
  })
}

async function waitForKnowledgeTaskTerminal(taskCode: string) {
  const deadline = Date.now() + KNOWLEDGE_SYNC_WAIT_TIMEOUT_MS
  let lastPollingError = ''
  while (Date.now() <= deadline) {
    let task
    try {
      task = await getAiKbSyncTask(taskCode)
      lastPollingError = ''
    }
    catch (error) {
      // 传输失败不能证明任务失败；保留 taskCode，并在等待窗口内继续确认。
      lastPollingError = error instanceof Error ? error.message : '同步任务状态查询失败'
      await delay(KNOWLEDGE_SYNC_POLL_INTERVAL_MS)
      continue
    }
    const status = Number(task?.status)
    if (status === 3 || status === 4 || status === 5) return task
    await delay(KNOWLEDGE_SYNC_POLL_INTERVAL_MS)
  }
  const pollingDetail = lastPollingError ? `，最近一次查询失败：${lastPollingError}` : ''
  throw new Error(`同步任务状态确认超时，任务编码和旧知识文档已保留${pollingDetail}`)
}

async function waitForKnowledgeSync(
  taskCode: string,
  expectedDocument: KnowledgeDocumentIdentity,
) {
  const task = await waitForKnowledgeTaskTerminal(taskCode)
  const status = Number(task?.status)
  if (status === 4 || status === 5) {
    throw new KnowledgeSyncTerminalError(
      task?.errorMessage || `同步任务 ${status === 4 ? '执行失败' : '已取消'}`,
    )
  }
  const documentResult = task.resultJson?.documents?.find(
    item => item.documentCode === expectedDocument.documentCode,
  )
  if (task.kbCode !== expectedDocument.kbCode || documentResult?.status !== 'SUCCESS') {
    throw new KnowledgeSyncTerminalError('同步任务结果与目标知识文档不一致')
  }
}

async function settleActiveKnowledgeSyncAttempt(
  shouldSettle: (attempt: ActiveKnowledgeSyncAttempt) => boolean = () => true,
) {
  const attempt = activeKnowledgeSyncAttempt.value
  if (!attempt || !shouldSettle(attempt)) return
  await waitForKnowledgeTaskTerminal(attempt.taskCode)
  if (activeKnowledgeSyncAttempt.value?.taskCode === attempt.taskCode) {
    activeKnowledgeSyncAttempt.value = null
  }
}

async function restorePersistedKnowledgeSyncAttempt(record: RenderComponentItem | null | undefined) {
  if (!record) return
  const pendingSync = getComponentAssetKnowledgeState(record).pendingSync
  if (!pendingSync) return
  const activeAttempt = activeKnowledgeSyncAttempt.value
  if (activeAttempt && activeAttempt.taskCode !== pendingSync.taskCode) {
    // 单页面只维护一个轮询坐标；切换资产前先收敛旧任务，再恢复服务端资产中的任务。
    await settleActiveKnowledgeSyncAttempt()
  }
  activeKnowledgeSyncAttempt.value = {
    taskCode: pendingSync.taskCode,
    componentId: pendingSync.componentId,
    assetKey: pendingSync.assetKey,
    documentName: pendingSync.documentName,
    contentFingerprint: pendingSync.contentFingerprint,
    desiredStatus: pendingSync.desiredStatus,
    target: toKnowledgeDocumentIdentity(pendingSync.target),
  }
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

async function loadKnowledgeBaseId() {
  knowledgeBaseLoading.value = true
  knowledgeBaseError.value = ''
  try {
    knowledgeBaseId.value = await getEnabledSystemSettingValue(RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY)
    if (!knowledgeBaseId.value) {
      knowledgeBaseError.value = `系统参数 ${RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY} 未配置或未启用`
    }
  }
  catch (error) {
    knowledgeBaseId.value = ''
    knowledgeBaseError.value = error instanceof Error
      ? `知识库配置加载失败：${error.message}`
      : '知识库配置加载失败'
  }
  finally {
    knowledgeBaseLoading.value = false
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

async function loadComponentIndex() {
  if (componentIndexLoading.value) return false
  componentIndexLoading.value = true
  try {
    const size = 500
    const firstPage = await searchRenderComponents({ page: 1, size })
    const records = [...(firstPage?.list || [])]
    const expectedTotal = Number(firstPage?.pageInfo?.total || records.length)
    let page = 2
    while (records.length < expectedTotal) {
      const payload = await searchRenderComponents({ page, size })
      const pageRecords = payload?.list || []
      if (!pageRecords.length) break
      records.push(...pageRecords)
      page++
    }
    componentIndexRecords.value = records
    return true
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? `组件资产索引加载失败：${error.message}` : '组件资产索引加载失败')
    return false
  }
  finally {
    componentIndexLoading.value = false
  }
}

async function loadPageData() {
  await Promise.all([loadSummary(), loadCategories(), loadComponents(), loadKnowledgeBaseId()])
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

async function handleCreateComponent() {
  if (!await loadComponentIndex()) return
  resetComponentEditor()
  componentDialogMode.value = 'create'
  componentDialogInstanceKey.value++
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
  editingComponentRevision.value = getComponentRecordRevision(raw)
  componentDialogInstanceKey.value++
  componentDialogVisible.value = true
}

function handleEditExistingComponent(sourceKey: string) {
  const normalizedSourceKey = sourceKey.toLowerCase()
  const raw = componentIndexRecords.value.find((record) => {
    const asset = getComponentAssetCardInfo(record)
    return asset.sourceKey.toLowerCase() === normalizedSourceKey
      || record.key?.toLowerCase() === normalizedSourceKey
  })
  if (!raw) {
    ElMessage.error('未找到已创建的组件知识资产，请刷新页面后重试')
    return
  }
  componentDialogMode.value = 'edit'
  editingComponentId.value = raw.id
  editingComponent.value = raw
  editingComponentRevision.value = getComponentRecordRevision(raw)
  componentDialogInstanceKey.value++
}

async function handleDeleteComponent(record: {
  id: string | number
  key: string
  name: string
  knowledgeBaseId?: string
}) {
  let componentStagedForDelete = false
  try {
    await ElMessageBox.confirm(`确定删除组件“${record.name}”及其关联知识文档吗？`, '删除组件', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })

    const displayedRecord = componentRecords.value.find(item => item.id === record.id)
    if (!displayedRecord) {
      throw new Error('未找到待删除的组件数据')
    }
    const displayedRevision = getComponentRecordRevision(displayedRecord)
    if (!await loadComponentIndex()) {
      throw new Error('无法读取组件最新状态，删除已中止')
    }
    const rawRecord = componentIndexRecords.value.find(
      item => String(item.id) === String(record.id),
    )
    if (!rawRecord) {
      throw new Error('组件已被删除或不可用，请刷新列表')
    }
    if (getComponentRecordRevision(rawRecord) !== displayedRevision) {
      throw new Error('组件已在其他页面被更新，删除已中止；请刷新列表后重新确认')
    }
    await restorePersistedKnowledgeSyncAttempt(rawRecord)
    let deleteStagedPayload: ComponentAssetSubmission = {
      key: rawRecord.key || record.key,
      name: rawRecord.name || record.name,
      category: rawRecord.category,
      status: EFFECTIVE_STATUS_DRAFT,
      docMarkdown: rawRecord.docMarkdown || '',
      exampleJson: rawRecord.exampleJson || '',
    }
    if (getComponentAssetCardInfo(rawRecord).isAsset) {
      deleteStagedPayload = withComponentAssetDesiredStatus(
        deleteStagedPayload,
        EFFECTIVE_STATUS_DRAFT,
      )
    }
    const rawPendingSync = getComponentAssetKnowledgeState(rawRecord).pendingSync
    if (rawPendingSync) {
      deleteStagedPayload = withComponentAssetPendingKnowledgeSync(deleteStagedPayload, {
        ...rawPendingSync,
        desiredStatus: EFFECTIVE_STATUS_DRAFT,
      })
    }
    await updateRenderComponent(record.id, deleteStagedPayload)
    componentStagedForDelete = true

    if (!await loadComponentIndex()) {
      throw new Error('组件已暂存为草稿，但无法确认最新状态，删除已中止')
    }
    const stagedRecord = componentIndexRecords.value.find(
      item => String(item.id) === String(record.id),
    )
    const stagedRevision = getComponentRecordRevision(stagedRecord)
    if (!stagedRecord || !stagedRevision) {
      throw new Error('组件已暂存为草稿，但无法读取草稿，删除已中止')
    }

    const stagedAssetKey = stagedRecord.key || record.key
    await settleActiveKnowledgeSyncAttempt(attempt => (
      String(attempt.componentId) === String(record.id)
      || attempt.assetKey.toLowerCase() === stagedAssetKey.toLowerCase()
    ))
    if (!await loadComponentIndex()) {
      throw new Error('组件已暂存为草稿，但无法完成删除前快照校验')
    }
    const latestStagedRecord = componentIndexRecords.value.find(
      item => String(item.id) === String(record.id),
    )
    if (getComponentRecordRevision(latestStagedRecord) !== stagedRevision) {
      throw new Error('组件在删除准备期间被其他页面更新，删除已中止')
    }

    const state = getComponentAssetKnowledgeState(latestStagedRecord)
    const fallbackKnowledgeBaseId = record.knowledgeBaseId?.trim() || knowledgeBaseId.value.trim()
    const fallbackDocumentCode = stagedAssetKey.trim()
      ? getKnowledgeDocumentCode(stagedAssetKey)
      : ''
    const cleanupTargets = uniqueKnowledgeDocuments([
      ...(state.current ? [toKnowledgeDocumentIdentity(state.current)] : []),
      ...state.pendingCleanup.map(toKnowledgeDocumentIdentity),
      ...(!state.current && fallbackKnowledgeBaseId && fallbackDocumentCode
        ? [{ kbCode: fallbackKnowledgeBaseId, documentCode: fallbackDocumentCode }]
        : []),
    ])
    const cleanupFailures = await removeKnowledgeDocuments(cleanupTargets)
    if (cleanupFailures.length) {
      throw new Error(`组件已暂存为草稿，知识文档清理失败，组件未删除：${cleanupFailures.join('；')}`)
    }

    try {
      const deleted = await deleteRenderComponent(record.id)
      if (!deleted) {
        throw new Error('删除接口返回失败')
      }
    }
    catch (error) {
      const reason = error instanceof Error ? error.message : '未知错误'
      throw new Error(
        cleanupTargets.length
          ? `关联知识文档已清理，但组件删除失败；组件已保留为草稿：${reason}`
          : `组件删除失败，组件已保留为草稿：${reason}`,
      )
    }
    ElMessage.success('组件及关联知识文档已删除')
    await loadPageData()
  }
  catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '删除组件失败')
    if (componentStagedForDelete) {
      await loadPageData()
    }
  }
}

async function submitComponentForm(payload: ComponentAssetSubmission) {
  if (knowledgeBaseLoading.value) {
    ElMessage.warning('知识库配置仍在加载，请稍后再试')
    return
  }

  // 保存前重新解析一次系统参数，避免页面长时间打开或多标签修改后继续写入旧知识库。
  const displayedKnowledgeBaseId = knowledgeBaseId.value
  componentSubmitting.value = true
  await loadKnowledgeBaseId()
  componentSubmitting.value = false
  if (!knowledgeBaseId.value) {
    ElMessage.error(knowledgeBaseError.value || `请先配置并启用系统参数 ${RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY}`)
    return
  }
  if (knowledgeBaseId.value !== displayedKnowledgeBaseId) {
    ElMessage.warning(`系统参数 ${RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY} 已变化，已刷新知识库 ID，请确认后再次保存`)
    return
  }

  let latestEditingRecord = editingComponent.value
  if (componentDialogMode.value === 'edit') {
    componentSubmitting.value = true
    const indexLoaded = await loadComponentIndex()
    componentSubmitting.value = false
    if (!indexLoaded) return
    const latestRecord = componentIndexRecords.value.find(
      item => String(item.id) === String(editingComponentId.value),
    )
    if (!latestRecord) {
      ElMessage.error('组件已被删除或不可用，请关闭编辑窗口后刷新列表')
      return
    }
    latestEditingRecord = latestRecord
    if (
      editingComponentRevision.value
      && getComponentRecordRevision(latestRecord) !== editingComponentRevision.value
    ) {
      ElMessage.error('该组件已在其他页面被更新。当前编辑内容已保留，请关闭后重新打开并合并修改')
      return
    }
  }

  const savedMode = componentDialogMode.value
  // `render.component.kbId` stores the local knowledge-base business code used by the KB API.
  const targetKnowledgeBaseCode = knowledgeBaseId.value
  const publishKnowledgeDocument = payload.status === EFFECTIVE_STATUS_PUBLISHED || payload.status === 'PUBLISHED'
  const previousKnowledgeState = getPersistedKnowledgeDocumentState(latestEditingRecord)
  const currentKnowledgeDocument = {
    kbCode: targetKnowledgeBaseCode,
    documentCode: getKnowledgeDocumentCode(payload.key),
  }
  const pendingKnowledgeCleanup = uniqueKnowledgeDocuments([
    ...previousKnowledgeState.pendingCleanup,
    ...(previousKnowledgeState.current
      && !isSameKnowledgeDocument(previousKnowledgeState.current, currentKnowledgeDocument)
      ? [previousKnowledgeState.current]
      : []),
  ]).filter(identity => !isSameKnowledgeDocument(identity, currentKnowledgeDocument))
  componentSubmitting.value = true
  let assetSaved = false
  let savedComponentId: string | number | null = null
  let retryPayload: ComponentAssetSubmission | null = null
  try {
    await restorePersistedKnowledgeSyncAttempt(latestEditingRecord)
    let savedPayload = withComponentAssetPendingKnowledgeCleanup(
      payload,
      pendingKnowledgeCleanup.map(toAssetKnowledgeDocumentIdentity),
    )
    if (previousKnowledgeState.pendingSync) {
      savedPayload = withComponentAssetPendingKnowledgeSync(
        savedPayload,
        {
          ...previousKnowledgeState.pendingSync,
          desiredStatus: normalizeComponentAssetDesiredStatus(payload.status),
        },
      )
    }
    const savedContentFingerprint = publishKnowledgeDocument
      ? await getComponentAssetContentFingerprint(savedPayload.docMarkdown)
      : ''
    retryPayload = savedPayload
    // 知识文档完成前统一暂存为草稿，避免出现“已发布但知识库缺失”的中间状态。
    const stagedPayload = {
      ...savedPayload,
      status: EFFECTIVE_STATUS_DRAFT,
    }
    if (savedMode === 'create') {
      const created = await createRenderComponent(stagedPayload)
      savedComponentId = created?.id ?? null
    } else {
      if (editingComponentId.value == null) {
        throw new Error('未找到可编辑的组件')
      }
      savedComponentId = editingComponentId.value
      await updateRenderComponent(editingComponentId.value, stagedPayload)
    }
    assetSaved = true
    if (savedComponentId == null) {
      throw new Error('保存结果缺少组件 ID，无法完成知识文档流程')
    }
    if (!await loadComponentIndex()) {
      throw new Error('无法确认组件草稿状态，知识文档流程已中止')
    }
    const stagedRecord = componentIndexRecords.value.find(
      item => String(item.id) === String(savedComponentId),
    )
    let stagedRevision = getComponentRecordRevision(stagedRecord)
    if (!stagedRevision) {
      throw new Error('未找到已暂存的组件草稿，知识文档流程已中止')
    }

    if (publishKnowledgeDocument) {
      const reusableAttempt = activeKnowledgeSyncAttempt.value
      const canResumeAttempt = Boolean(
        reusableAttempt
        && String(reusableAttempt.componentId) === String(savedComponentId)
        && reusableAttempt.assetKey === savedPayload.key
        && reusableAttempt.documentName === savedPayload.name
        && reusableAttempt.contentFingerprint === savedContentFingerprint
        && isSameKnowledgeDocument(reusableAttempt.target, currentKnowledgeDocument),
      )
      if (!canResumeAttempt) {
        // 当前页面只保留一个同步任务坐标；创建新任务前先确认旧任务已终止，避免并发写同一 Provider。
        await settleActiveKnowledgeSyncAttempt()
        await createOrUpdateAiKbDocument({
          kbCode: currentKnowledgeDocument.kbCode,
          documentId: currentKnowledgeDocument.documentCode,
          documentName: savedPayload.name,
          documentType: 4,
          bizType: 4,
          content: savedPayload.docMarkdown,
          canUpdate: true,
          enabled: true,
          ext: {
            sourceSystem: 'renderComponent',
            sourceKey: savedPayload.key,
            componentKey: savedPayload.key,
          },
        })
        const syncResult = await syncAiKbDocuments({
          kbCode: currentKnowledgeDocument.kbCode,
          documentCodes: [currentKnowledgeDocument.documentCode],
        })
        if (!syncResult?.taskCode) {
          throw new Error('同步任务创建失败，未返回任务编码')
        }
        activeKnowledgeSyncAttempt.value = {
          taskCode: syncResult.taskCode,
          componentId: savedComponentId,
          assetKey: savedPayload.key,
          documentName: savedPayload.name,
          contentFingerprint: savedContentFingerprint,
          desiredStatus: normalizeComponentAssetDesiredStatus(savedPayload.status),
          target: currentKnowledgeDocument,
        }
      }
      const activeAttempt = activeKnowledgeSyncAttempt.value
      if (!activeAttempt) {
        throw new Error('未找到可持久化的同步任务坐标')
      }
      savedPayload = withComponentAssetPendingKnowledgeSync(
        savedPayload,
        toPersistedKnowledgeSyncAttempt(activeAttempt),
      )
      retryPayload = savedPayload
      await updateRenderComponent(savedComponentId, {
        ...savedPayload,
        status: EFFECTIVE_STATUS_DRAFT,
      })
      if (!await loadComponentIndex()) {
        throw new Error('同步任务已创建，但无法确认任务坐标已持久化')
      }
      const syncStagedRecord = componentIndexRecords.value.find(
        item => String(item.id) === String(savedComponentId),
      )
      stagedRevision = getComponentRecordRevision(syncStagedRecord)
      if (!stagedRevision) {
        throw new Error('同步任务已创建，但未找到持久化后的组件草稿')
      }
      const persistedAttempt = getComponentAssetKnowledgeState(syncStagedRecord).pendingSync
      if (!isSamePersistedKnowledgeSyncAttempt(persistedAttempt, activeAttempt)) {
        throw new Error('同步任务已创建，但任务坐标写回校验失败；当前页面已保留任务，请勿关闭并再次保存重试')
      }
      try {
        await waitForKnowledgeSync(activeAttempt.taskCode, currentKnowledgeDocument)
        activeKnowledgeSyncAttempt.value = null
      }
      catch (error) {
        if (error instanceof KnowledgeSyncTerminalError) {
          activeKnowledgeSyncAttempt.value = null
          savedPayload = withComponentAssetPendingKnowledgeSync(savedPayload, null)
          retryPayload = savedPayload
          await updateRenderComponent(savedComponentId, {
            ...savedPayload,
            status: EFFECTIVE_STATUS_DRAFT,
          })
        }
        throw error
      }
    }
    else {
      await settleActiveKnowledgeSyncAttempt(attempt => (
        String(attempt.componentId) === String(savedComponentId)
        || attempt.assetKey.toLowerCase() === savedPayload.key.toLowerCase()
        || isSameKnowledgeDocument(attempt.target, currentKnowledgeDocument)
      ))
      // 草稿和停用资产不应继续留在 Provider；Markdown 仍保存在组件资产中，重新发布时会重建。
      const currentCleanupFailures = await removeKnowledgeDocuments([currentKnowledgeDocument])
      if (currentCleanupFailures.length) {
        throw new Error(currentCleanupFailures.join('；'))
      }
    }

    if (!await loadComponentIndex()) {
      throw new Error('无法确认组件最新状态，最终状态更新已中止')
    }
    const latestStagedRecord = componentIndexRecords.value.find(
      item => String(item.id) === String(savedComponentId),
    )
    if (getComponentRecordRevision(latestStagedRecord) !== stagedRevision) {
      throw new Error('组件在知识文档处理期间被其他页面更新，已停止覆盖其最新内容')
    }

    const previousCleanupFailures = await removeKnowledgeDocuments(pendingKnowledgeCleanup)
    const completedSyncPayload = withComponentAssetPendingKnowledgeSync(savedPayload, null)
    const finalPayload = previousCleanupFailures.length
      ? completedSyncPayload
      : withComponentAssetPendingKnowledgeCleanup(completedSyncPayload, [])
    await updateRenderComponent(savedComponentId, finalPayload)

    componentDialogVisible.value = false
    resetComponentEditor()
    const resultMessage = `组件数字资产已${savedMode === 'create' ? '创建' : '更新'}，${publishKnowledgeDocument ? '知识库同步已完成' : '知识文档已从目标知识库移除'}`
    if (previousCleanupFailures.length) {
      ElMessage.warning(`${resultMessage}；旧知识文档清理失败，已保留记录供下次保存重试：${previousCleanupFailures.join('；')}`)
    }
    else {
      ElMessage.success(resultMessage)
    }
    await loadPageData()
  }
  catch (error) {
    if (assetSaved) {
      await loadPageData()
      const retryIndexLoaded = await loadComponentIndex()
      if (savedComponentId == null) {
        savedComponentId = componentIndexRecords.value.find(
          item => item.key?.toLowerCase() === payload.key.toLowerCase(),
        )?.id ?? null
      }
      const reason = error instanceof Error ? error.message : '未知错误'
      if (savedComponentId != null && retryPayload) {
        componentDialogMode.value = 'edit'
        editingComponentId.value = savedComponentId
        editingComponent.value = {
          id: savedComponentId,
          ...retryPayload,
        }
        editingComponentRevision.value = retryIndexLoaded
          ? getComponentRecordRevision(componentIndexRecords.value.find(
              item => String(item.id) === String(savedComponentId),
            ))
          : ''
        componentDialogInstanceKey.value++
        ElMessage.error(`组件已暂存为草稿，但知识文档${publishKnowledgeDocument ? '写入或同步' : '移除'}未完成：${reason}；编辑窗口已保留，可直接再次保存重试`)
      }
      else {
        componentDialogVisible.value = false
        resetComponentEditor()
        ElMessage.error(`组件已暂存为草稿，但知识文档流程未完成：${reason}；请刷新列表后编辑重试`)
      }
      return
    }
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
            <el-button plain :loading="loading || summaryLoading || knowledgeBaseLoading" @click="handleRefresh">
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
                  <div class="component-manage-card__meta">{{ record.key || '-' }}</div>
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
                  <small>{{ record.knowledgeBaseId || '待指定知识库' }}</small>
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
    :key="componentDialogInstanceKey"
    v-model="componentDialogVisible"
    :mode="componentDialogMode"
    :initial-value="editingComponent"
    :category-options="categoryOptions"
    :existing-source-keys="existingRendererSourceKeys"
    :submitting="componentSubmitting"
    :knowledge-base-id="knowledgeBaseId"
    :knowledge-base-loading="knowledgeBaseLoading"
    :knowledge-base-error="knowledgeBaseError"
    @closed="resetComponentEditor"
    @edit-existing="handleEditExistingComponent"
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
