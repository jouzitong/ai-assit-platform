import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  deleteDbTableMetaCascade,
  downloadDbMetaTemplateWorkbook,
  exportDbMetaWorkbook,
  listDbAccessTables,
  previewDbTableKnowledge,
  searchDbDataSources,
  searchDbTableFields,
  searchDbTables,
  streamDbMetaImportWorkbook,
  syncDbAccessTableMeta,
  syncDbTableKnowledge
} from '../../../../../../api/dbEngine'
import { showPopup } from '../../../../../../utils/popup'

export function useDataSourceManagePage() {
  const route = useRoute()
  const router = useRouter()
  const sourceList = ref([])
  const tableList = ref([])
  const fieldList = ref([])
  const sourceLoading = ref(false)
  const tableLoading = ref(false)
  const fieldLoading = ref(false)
  const importSubmitting = ref(false)
  const exportSubmitting = ref(false)
  const templateSubmitting = ref(false)
  const sourceError = ref('')
  const tableError = ref('')
  const fieldError = ref('')
  const importDialogVisible = ref(false)
  const importDragActive = ref(false)
  const importFile = ref(null)
  const importError = ref('')
  const importFormat = ref('json')
  const importProgressDialogVisible = ref(false)
  const importJobProgress = reactive(createEmptyImportJobProgress())
  const exportDialogVisible = ref(false)
  const exportFormat = ref('json')
  const tableSyncDialogVisible = ref(false)
  const tableSyncLoading = ref(false)
  const tableSyncSubmitting = ref(false)
  const tableSyncError = ref('')
  const tableSyncAllowUpdate = ref(false)
  const tableSyncCandidates = ref([])
  const tableSyncSelectedTables = ref([])
  const knowledgePreviewVisible = ref(false)
  const knowledgePreviewLoading = ref(false)
  const knowledgePreviewError = ref('')
  const knowledgePreviewData = reactive(createEmptyKnowledgePreview())
  const knowledgeSyncSubmitting = ref(false)
  const knowledgeSyncTarget = ref('')
  const tableDeleteSubmitting = ref(false)
  const tableDeleteTarget = ref('')
  const pageSizeOptions = [10, 20, 50]
  let importProgressStreamAbortController = null

  const sourceKey = computed(() => String(route.params.sourceKey ?? ''))
  const currentSource = computed(() => {
    return sourceList.value.find(item => item.key === sourceKey.value) ?? sourceList.value[0] ?? null
  })
  const currentSourceList = computed(() => sourceList.value)
  const currentTables = computed(() => tableList.value)
  const fieldWorkbenchVisible = ref(false)
  const selectedTableName = ref('')
  const pagination = reactive({
    page: 1,
    size: 10,
    total: 0
  })

  const totalPages = computed(() => Math.max(1, Math.ceil(pagination.total / pagination.size)))
  const pagedTables = computed(() => currentTables.value)
  const pageSummary = computed(() => {
    if (!pagination.total) {
      return '第 0 - 0 条，共 0 条'
    }
    const start = (pagination.page - 1) * pagination.size + 1
    const end = Math.min(pagination.page * pagination.size, pagination.total)
    return `第 ${start} - ${end} 条，共 ${pagination.total} 条`
  })
  const selectedTable = computed(() => {
    return currentTables.value.find(item => item.name === selectedTableName.value) ?? currentTables.value[0] ?? null
  })
  const selectedFields = computed(() => fieldList.value)
  const importJobActive = computed(() => ['PENDING', 'RUNNING'].includes(String(importJobProgress.status || '')))
  const importProgressNoticeVisible = computed(() => importJobActive.value && !importProgressDialogVisible.value)
  const importProgressStageLabel = computed(() => resolveImportStageLabel(importJobProgress.stage))
  const importProgressSummary = computed(() => importJobProgress.summary || createEmptyImportProgressSummary())
  const importActionLabel = computed(() => importJobActive.value ? '查看进度' : '导入')
  const importFormatLabel = computed(() => importFormat.value === 'excel' ? 'Excel' : 'JSON')
  const tableSyncSelectedCount = computed(() => tableSyncSelectedTables.value.length)
  const tableSyncCandidateCount = computed(() => tableSyncCandidates.value.length)
  const tableSyncPendingCount = computed(() => tableSyncCandidates.value.filter(item => !item.synced).length)
  const tableSyncAllChecked = computed(() => {
    return tableSyncCandidateCount.value > 0 && tableSyncSelectedCount.value === tableSyncCandidateCount.value
  })
  const tableSyncIndeterminate = computed(() => {
    return tableSyncSelectedCount.value > 0 && tableSyncSelectedCount.value < tableSyncCandidateCount.value
  })

  onMounted(async () => {
    await loadInitialData()
  })

  onBeforeUnmount(() => {
    stopImportProgressStream()
  })

  watch(sourceKey, async () => {
    pagination.page = 1
    fieldWorkbenchVisible.value = false
    selectedTableName.value = ''
    await loadInitialData()
  })

  function statusClass(status) {
    return `is-${status}`
  }

  function handlePageChange(page) {
    pagination.page = Math.min(Math.max(page, 1), totalPages.value)
    loadTables()
  }

  function handlePageSizeChange(event) {
    pagination.size = Number(event.target.value)
    pagination.page = 1
    loadTables()
  }

  function handleSourceChange(event) {
    router.push(`/settings/system/data-source/${event.target.value}`)
  }

  async function openFieldWorkbench(item) {
    selectedTableName.value = item.name
    fieldWorkbenchVisible.value = true
    await loadFields()
  }

  async function selectTable(item) {
    selectedTableName.value = item.name
    await loadFields()
  }

  function formatEmpty(value) {
    return value?.trim ? (value.trim() || '无') : (value || '无')
  }

  function goBack() {
    router.push('/settings/system/data-source')
  }

  function openImportDialog() {
    if (importJobActive.value) {
      importProgressDialogVisible.value = true
      return
    }
    importDialogVisible.value = true
    importDragActive.value = false
    importError.value = ''
    importFormat.value = 'json'
  }

  function closeImportDialog() {
    importDialogVisible.value = false
    resetImportDialogState()
  }

  function resetImportDialogState() {
    importDragActive.value = false
    importError.value = ''
    importFormat.value = 'json'
    importFile.value = null
  }

  function openImportProgressDialog() {
    if (!importJobProgress.jobId) {
      return
    }
    importProgressDialogVisible.value = true
  }

  function closeImportProgressDialog() {
    importProgressDialogVisible.value = false
  }

  function openExportDialog() {
    exportDialogVisible.value = true
    exportFormat.value = 'json'
  }

  function closeExportDialog() {
    exportDialogVisible.value = false
    exportFormat.value = 'json'
  }

  async function openTableSyncDialog() {
    if (!sourceKey.value) {
      showPopup.error('当前数据源信息不完整，无法同步表格')
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
      const response = await listDbAccessTables({
        sourceKey: sourceKey.value
      })
      const payload = unwrapPayload(response)
      tableSyncCandidates.value = (payload?.tables ?? []).map(mapTableSyncCandidate)
      tableSyncSelectedTables.value = tableSyncCandidates.value
        .filter(item => !item.synced)
        .map(item => item.name)
    } catch (error) {
      tableSyncError.value = error.message || '同步表格列表加载失败'
      tableSyncCandidates.value = []
      tableSyncSelectedTables.value = []
    } finally {
      tableSyncLoading.value = false
    }
  }

  function toggleTableSyncSelection(tableName, checked) {
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

  function toggleAllTableSyncSelection(checked) {
    tableSyncSelectedTables.value = checked
      ? tableSyncCandidates.value.map(item => item.name)
      : []
  }

  async function submitTableSync() {
    if (!sourceKey.value) {
      showPopup.error('当前数据源信息不完整，无法同步表格')
      return
    }
    if (!tableSyncSelectedTables.value.length) {
      tableSyncError.value = '请至少勾选一张需要同步的数据表'
      return
    }
    tableSyncSubmitting.value = true
    tableSyncError.value = ''
    try {
      const response = await syncDbAccessTableMeta({
        sourceKey: sourceKey.value,
        tables: tableSyncSelectedTables.value,
        allowUpdate: tableSyncAllowUpdate.value
      })
      const payload = unwrapPayload(response) || {}
      const createdTableCount = Number(payload.createdTableCount ?? 0)
      const updatedTableCount = Number(payload.updatedTableCount ?? 0)
      const createdFieldCount = Number(payload.createdFieldCount ?? 0)
      const updatedFieldCount = Number(payload.updatedFieldCount ?? 0)
      await refreshPage()
      showPopup.success(`表格同步完成：新增表 ${createdTableCount}，更新表 ${updatedTableCount}，新增字段 ${createdFieldCount}，更新字段 ${updatedFieldCount}`)
      closeTableSyncDialog()
    } catch (error) {
      tableSyncError.value = error.message || '表格同步失败'
    } finally {
      tableSyncSubmitting.value = false
    }
  }

  async function openKnowledgePreview(item) {
    if (!sourceKey.value || !item?.name) {
      showPopup.error('当前表信息不完整，无法预览知识库')
      return
    }
    knowledgePreviewVisible.value = true
    knowledgePreviewLoading.value = true
    knowledgePreviewError.value = ''
    knowledgePreviewData.tableName = item.name
    knowledgePreviewData.type = ''
    knowledgePreviewData.content = ''
    try {
      const response = await previewDbTableKnowledge(sourceKey.value, item.name)
      const payload = unwrapPayload(response)
      knowledgePreviewData.tableName = item.name
      knowledgePreviewData.type = payload?.type || 'markdown'
      knowledgePreviewData.content = payload?.content || ''
    } catch (error) {
      knowledgePreviewError.value = error.message || '知识库预览加载失败'
    } finally {
      knowledgePreviewLoading.value = false
    }
  }

  function closeKnowledgePreview() {
    knowledgePreviewVisible.value = false
    knowledgePreviewLoading.value = false
    knowledgePreviewError.value = ''
    Object.assign(knowledgePreviewData, createEmptyKnowledgePreview())
  }

  async function syncKnowledgeBase(item = null) {
    if (!sourceKey.value) {
      showPopup.error('当前数据源信息不完整，无法同步知识库')
      return
    }
    const tableName = item?.name || ''
    const confirmMessage = tableName
      ? `确认同步数据表「${tableName}」到知识库吗？`
      : `确认同步当前数据源「${currentSource.value?.name || sourceKey.value}」下的全部数据表到知识库吗？`
    if (!window.confirm(confirmMessage)) {
      return
    }
    knowledgeSyncSubmitting.value = true
    knowledgeSyncTarget.value = tableName
    try {
      const response = await syncDbTableKnowledge({
        sourceKey: sourceKey.value,
        tableName: tableName || undefined
      })
      const payload = unwrapPayload(response) || {}
      const totalCount = Number(payload.totalCount ?? 0)
      const createdCount = Number(payload.createdCount ?? 0)
      const updatedCount = Number(payload.updatedCount ?? 0)
      const unchangedCount = Number(payload.unchangedCount ?? 0)
      showPopup.success(`知识库同步完成：共 ${totalCount} 张表，新增 ${createdCount}，更新 ${updatedCount}，未变更 ${unchangedCount}`)
    } catch (error) {
      showPopup.error(error.message || '知识库同步失败')
    } finally {
      knowledgeSyncSubmitting.value = false
      knowledgeSyncTarget.value = ''
    }
  }

  async function deleteTable(item) {
    const tableName = item?.name || ''
    if (!sourceKey.value || !tableName) {
      showPopup.error('当前表信息不完整，无法删除')
      return
    }
    const confirmed = window.confirm(`确认删除表「${tableName}」的元数据吗？这会同时删除字段和索引元数据。`)
    if (!confirmed) {
      return
    }
    tableDeleteSubmitting.value = true
    tableDeleteTarget.value = tableName
    try {
      const response = await deleteDbTableMetaCascade({
        sourceKey: sourceKey.value,
        tableName
      })
      const payload = unwrapPayload(response) || {}
      const deletedTableCount = Number(payload.deletedTableCount ?? 0)
      const deletedFieldCount = Number(payload.deletedFieldCount ?? 0)
      const deletedIndexCount = Number(payload.deletedIndexCount ?? 0)
      await refreshPage()
      showPopup.success(`删除完成：表 ${deletedTableCount}，字段 ${deletedFieldCount}，索引 ${deletedIndexCount}`)
    } catch (error) {
      showPopup.error(error.message || '删除失败')
    } finally {
      tableDeleteSubmitting.value = false
      tableDeleteTarget.value = ''
    }
  }

  function handleImportDragEnter() {
    importDragActive.value = true
  }

  function handleImportDragLeave() {
    importDragActive.value = false
  }

  function handleImportFile(file) {
    if (!file) {
      return
    }
    const extension = String(file.name || '').split('.').pop()?.toLowerCase()
    if (extension === 'xlsx') {
      importFormat.value = 'excel'
    } else if (extension === 'json') {
      importFormat.value = 'json'
    }
    importFile.value = file
    importError.value = ''
    importDragActive.value = false
  }

  async function submitImport() {
    if (!currentSource.value?.key) {
      importError.value = '当前没有可用数据源'
      return
    }
    if (!importFile.value) {
      importError.value = `请先选择要导入的${importFormat.value === 'json' ? ' JSON ' : ' Excel '}文件`
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
      resetImportJobProgress()
      importJobProgress.jobId = ''
      importJobProgress.sourceKey = currentSource.value.key
      importJobProgress.fileName = importFile.value.name
      importJobProgress.status = 'PENDING'
      importJobProgress.stage = 'QUEUED'
      importJobProgress.message = '导入任务已创建，等待后台处理'
      closeImportDialog()
      importProgressDialogVisible.value = true
      await consumeImportStream(currentSource.value.key, importFile.value, { showTerminalToast: true })
    } catch (error) {
      importError.value = error.message || '导入失败'
    } finally {
      importSubmitting.value = false
    }
  }

  async function exportWorkbook() {
    if (!currentSource.value?.key) {
      showPopup.error('当前没有可导出的数据源')
      return
    }
    exportSubmitting.value = true
    try {
      const { blob, filename } = await exportDbMetaWorkbook(currentSource.value.key, exportFormat.value)
      triggerBrowserDownload(blob, filename)
      closeExportDialog()
      showPopup.success('导出成功')
    } catch (error) {
      showPopup.error(error.message || '导出失败')
    } finally {
      exportSubmitting.value = false
    }
  }

  async function downloadTemplateWorkbook() {
    templateSubmitting.value = true
    try {
      const { blob, filename } = await downloadDbMetaTemplateWorkbook(importFormat.value)
      triggerBrowserDownload(blob, filename)
      showPopup.success('模板下载成功')
    } catch (error) {
      showPopup.error(error.message || '模板下载失败')
    } finally {
      templateSubmitting.value = false
    }
  }

  async function refreshPage() {
    if (fieldWorkbenchVisible.value) {
      await Promise.all([loadSourceList(), loadFields()])
      return
    }
    await Promise.all([loadSourceList(), loadTables()])
  }

  async function loadInitialData() {
    await loadSourceList()
    await loadTables()
    if (fieldWorkbenchVisible.value) {
      await loadFields()
    }
  }

  async function loadSourceList() {
    sourceLoading.value = true
    sourceError.value = ''
    try {
      const response = await searchDbDataSources({ page: 1, size: 200 })
      const payload = unwrapPayload(response)
      sourceList.value = (payload?.list ?? []).map(mapSourceItem)
    } catch (error) {
      sourceError.value = error.message || '数据源加载失败'
      sourceList.value = []
    } finally {
      sourceLoading.value = false
    }
  }

  async function loadTables() {
    if (!sourceKey.value) {
      tableList.value = []
      pagination.total = 0
      return
    }
    tableLoading.value = true
    tableError.value = ''
    try {
      const response = await searchDbTables({
        sourceKey: sourceKey.value,
        page: pagination.page,
        size: pagination.size
      })
      const payload = unwrapPayload(response)
      const records = payload?.list ?? payload?.records ?? []
      tableList.value = records.map(mapTableItem)
      pagination.total = payload?.total ?? records.length
      if (!selectedTableName.value && tableList.value.length) {
        selectedTableName.value = tableList.value[0].name
      }
    } catch (error) {
      tableError.value = error.message || '数据表加载失败'
      tableList.value = []
      pagination.total = 0
    } finally {
      tableLoading.value = false
    }
  }

  async function loadFields() {
    if (!sourceKey.value || !selectedTableName.value) {
      fieldList.value = []
      return
    }
    fieldLoading.value = true
    fieldError.value = ''
    try {
      const response = await searchDbTableFields({
        sourceKey: sourceKey.value,
        tableName: selectedTableName.value,
        page: 1,
        size: 500
      })
      const payload = unwrapPayload(response)
      fieldList.value = (payload?.list ?? payload?.records ?? []).map(mapFieldItem)
    } catch (error) {
      fieldError.value = error.message || '字段加载失败'
      fieldList.value = []
    } finally {
      fieldLoading.value = false
    }
  }

  function unwrapPayload(response) {
    return response?.data ?? response
  }

  function mapSourceItem(item) {
    const databaseConfig = item?.config?.database ?? {}
    const host = databaseConfig.host
      ? `${databaseConfig.host}${databaseConfig.port ? `:${databaseConfig.port}` : ''}`
      : item?.config?.endpoint || '-'
    return {
      key: item.sourceKey || String(item.id ?? ''),
      name: item.sourceName || item.sourceKey || '未命名数据源',
      type: item.sourceType || '-',
      host,
      raw: item
    }
  }

  function mapTableItem(item) {
    return {
      name: item.tableName || item.name || '-',
      comment: item.tableComment || item.comment || item.remark || '-',
      columns: item.fieldCount ?? item.columnCount ?? item.columns ?? '-',
      rows: item.rowCount ?? item.rows ?? '-',
      partition: item.partitionKey || item.partition || 'none',
      freshness: formatFreshness(item.freshness ?? item.freshnessSeconds ?? item.syncLag),
      status: normalizeStatus(item),
      statusLabel: normalizeStatusLabel(item)
    }
  }

  function mapTableSyncCandidate(item) {
    return {
      name: item.tableName || '-',
      comment: item.tableComment || item.tableMeta?.tableComment || '',
      type: item.tableType || item.tableMeta?.tableType || '',
      synced: Boolean(item.synced),
      localColumnCount: item.tableMeta?.columnCount ?? 0
    }
  }

  function mapFieldItem(item) {
    return {
      name: item.columnName || item.fieldName || item.name || '-',
      type: item.dataType || item.fieldType || item.type || '-',
      indexName: item.indexName || '',
      relatedTable: item.relatedTable || '',
      description: item.columnComment || item.description || item.comment || item.remark || '',
      statusLabel: normalizeFieldRoleLabel(item.fieldRole || item.roleLabel || item.statusLabel)
    }
  }

  function formatFreshness(value) {
    if (value === null || value === undefined || value === '') {
      return '-'
    }
    const seconds = Number(value)
    if (Number.isNaN(seconds)) {
      return String(value)
    }
    if (seconds < 60) {
      return `${seconds}s`
    }
    if (seconds < 3600) {
      return `${Math.floor(seconds / 60)}m`
    }
    if (seconds < 86400) {
      return `${Math.floor(seconds / 3600)}h`
    }
    return `${Math.floor(seconds / 86400)}d`
  }

  function normalizeFieldRoleLabel(role) {
    const roleLabelMap = {
      PRIMARY_KEY: '主键字段',
      PARTITION_KEY: '分区字段',
      DIMENSION: '维度字段',
      METRIC: '指标字段',
      FACT: '事实字段'
    }
    const normalizedRole = String(role || '').toUpperCase()
    return roleLabelMap[normalizedRole] || role || '业务字段'
  }

  function normalizeStatus(item) {
    if (item?.enabled === false) {
      return 'offline'
    }
    if (String(item?.status || '').toUpperCase() === 'ACTIVE') {
      return 'ready'
    }
    return 'draft'
  }

  function normalizeStatusLabel(item) {
    if (item?.enabled === false) {
      return '已停用'
    }
    if (String(item?.status || '').toUpperCase() === 'ACTIVE') {
      return '可用'
    }
    return item?.statusLabel || '待配置'
  }

  function isImportFileFormatMatched(file, format) {
    const extension = String(file?.name || '').split('.').pop()?.toLowerCase()
    return format === 'excel' ? extension === 'xlsx' : extension === 'json'
  }

  async function consumeImportStream(currentSourceKey, file, options = {}) {
    stopImportProgressStream()
    importProgressStreamAbortController = new AbortController()
    try {
      await streamDbMetaImportWorkbook(currentSourceKey, file, importFormat.value, {
        signal: importProgressStreamAbortController.signal,
        onMessage: handleImportProgressMessage
      })
      if (options.showTerminalToast) {
        showPopup.success('导入任务已提交')
      }
      await refreshPage()
    } finally {
      stopImportProgressStream()
    }
  }

  function handleImportProgressMessage(message) {
    if (!message || typeof message !== 'object') {
      return
    }
    importJobProgress.jobId = message.jobId || importJobProgress.jobId
    importJobProgress.sourceKey = message.sourceKey || importJobProgress.sourceKey
    importJobProgress.fileName = message.fileName || importJobProgress.fileName
    importJobProgress.status = message.status || importJobProgress.status
    importJobProgress.stage = message.stage || importJobProgress.stage
    importJobProgress.message = message.message || importJobProgress.message
    importJobProgress.progressPercent = Number(message.progressPercent ?? importJobProgress.progressPercent ?? 0)
    importJobProgress.summary = message.summary || importJobProgress.summary
  }

  function stopImportProgressStream() {
    if (importProgressStreamAbortController) {
      importProgressStreamAbortController.abort()
      importProgressStreamAbortController = null
    }
  }

  function triggerBrowserDownload(blob, filename) {
    const url = window.URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = filename || 'download.bin'
    anchor.click()
    window.URL.revokeObjectURL(url)
  }

  function resolveImportStageLabel(stage) {
    const labelMap = {
      QUEUED: '排队中',
      UPLOADING: '上传中',
      PARSING: '解析中',
      IMPORTING: '导入中',
      COMPLETED: '已完成',
      FAILED: '失败'
    }
    return labelMap[stage] || stage || '处理中'
  }

  function createEmptyImportJobProgress() {
    return {
      jobId: '',
      sourceKey: '',
      fileName: '',
      status: '',
      stage: '',
      message: '',
      progressPercent: 0,
      summary: createEmptyImportProgressSummary()
    }
  }

  function createEmptyImportProgressSummary() {
    return {
      processed: 0,
      success: 0,
      failed: 0
    }
  }

  function createEmptyKnowledgePreview() {
    return {
      tableName: '',
      type: '',
      content: ''
    }
  }

  function resetImportJobProgress() {
    Object.assign(importJobProgress, createEmptyImportJobProgress())
  }

  return {
    currentSource,
    currentSourceList,
    pagedTables,
    currentTables,
    fieldWorkbenchVisible,
    pageSizeOptions,
    pagination,
    pageSummary,
    totalPages,
    selectedTableName,
    selectedTable,
    selectedFields,
    sourceLoading,
    tableLoading,
    fieldLoading,
    sourceError,
    tableError,
    fieldError,
    importDialogVisible,
    importDragActive,
    importFile,
    importError,
    importFormat,
    importSubmitting,
    importProgressDialogVisible,
    importJobProgress,
    importJobActive,
    importProgressNoticeVisible,
    importProgressStageLabel,
    importProgressSummary,
    importActionLabel,
    exportDialogVisible,
    exportFormat,
    exportSubmitting,
    tableSyncDialogVisible,
    tableSyncLoading,
    tableSyncSubmitting,
    tableSyncError,
    tableSyncAllowUpdate,
    tableSyncCandidates,
    tableSyncSelectedTables,
    tableSyncSelectedCount,
    tableSyncCandidateCount,
    tableSyncPendingCount,
    tableSyncAllChecked,
    tableSyncIndeterminate,
    knowledgePreviewVisible,
    knowledgePreviewLoading,
    knowledgePreviewError,
    knowledgePreviewData,
    knowledgeSyncSubmitting,
    knowledgeSyncTarget,
    tableDeleteSubmitting,
    tableDeleteTarget,
    templateSubmitting,
    handleSourceChange,
    handlePageChange,
    handlePageSizeChange,
    openFieldWorkbench,
    selectTable,
    formatEmpty,
    goBack,
    statusClass,
    refreshPage,
    openImportDialog,
    closeImportDialog,
    openImportProgressDialog,
    closeImportProgressDialog,
    openExportDialog,
    closeExportDialog,
    openTableSyncDialog,
    closeTableSyncDialog,
    toggleTableSyncSelection,
    toggleAllTableSyncSelection,
    submitTableSync,
    openKnowledgePreview,
    closeKnowledgePreview,
    syncKnowledgeBase,
    deleteTable,
    handleImportDragEnter,
    handleImportDragLeave,
    handleImportFile,
    submitImport,
    exportWorkbook,
    downloadTemplateWorkbook,
    importFormatLabel
  }
}
