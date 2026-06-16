import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { downloadDbMetaTemplateWorkbook, exportDbMetaWorkbook, searchDbDataSources, searchDbTableFields, searchDbTables, streamDbMetaImportWorkbook } from '../../../../../../api/dbEngine'
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
  const pageSizeOptions = [10, 20, 50]
  let importProgressStreamAbortController = null

  const sourceKey = computed(() => String(route.params.sourceKey ?? ''))
  const currentSource = computed(() => {
    return sourceList.value.find(item => item.key === sourceKey.value) ?? sourceList.value[0] ?? null
  })
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
    await Promise.all([loadSourceList(), loadTables()])
  }

  async function loadSourceList() {
    sourceLoading.value = true
    sourceError.value = ''
    try {
      const payload = unwrapPayload(
        await searchDbDataSources({
          page: 1,
          size: 200
        })
      )
      sourceList.value = (payload?.list ?? []).map(mapSourceItem)
    } catch (error) {
      sourceError.value = error.message || '数据源列表加载失败'
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
      const payload = unwrapPayload(
        await searchDbTables({
          page: pagination.page,
          size: pagination.size,
          sourceKey: sourceKey.value
        })
      )
      tableList.value = (payload?.list ?? []).map(mapTableItem)
      pagination.total = resolvePageTotal(payload?.pageInfo?.total, tableList.value.length)
      if (!selectedTableName.value && tableList.value.length) {
        selectedTableName.value = tableList.value[0].name
      }
    } catch (error) {
      tableError.value = error.message || '数据表列表加载失败'
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
      const payload = unwrapPayload(
        await searchDbTableFields({
          page: 1,
          size: 500,
          sourceKey: sourceKey.value,
          tableName: selectedTableName.value
        })
      )
      fieldList.value = (payload?.list ?? []).map(mapFieldItem)
    } catch (error) {
      fieldError.value = error.message || '字段列表加载失败'
      fieldList.value = []
    } finally {
      fieldLoading.value = false
    }
  }

  function unwrapPayload(response) {
    return response?.data ?? response
  }

  function resolvePageTotal(total, listLength) {
    const parsed = Number(total)
    if (Number.isFinite(parsed) && parsed > 0) {
      return parsed
    }
    if (listLength > 0) {
      return listLength
    }
    return listLength
  }

  function mapSourceItem(item) {
    return {
      key: item.sourceKey || String(item.id ?? ''),
      name: item.sourceName || item.sourceKey || '未命名数据源',
      type: formatSourceType(item.sourceType)
    }
  }

  function mapTableItem(item) {
    return {
      id: item.id,
      name: item.tableName,
      columns: item.columnCount ?? 0,
      rows: formatRowCount(item.rowCount),
      partition: item.partitionKey || '无',
      freshness: formatFreshness(item.freshnessSeconds),
      status: resolveTableStatus(item),
      statusLabel: resolveTableStatusLabel(item),
      raw: item
    }
  }

  function mapFieldItem(item) {
    return {
      id: item.id,
      name: item.columnName,
      type: formatFieldType(item),
      indexName: item.primaryKey ? 'PRIMARY' : '',
      relatedTable: '',
      description: item.columnComment || item.remark || '-',
      statusLabel: resolveFieldStatusLabel(item),
      raw: item
    }
  }

  function formatSourceType(value) {
    const labelMap = {
      DATABASE: '数据库',
      HTTP_API: 'HTTP API',
      SERVICE_API: '服务接口',
      FILE: '文件',
      STREAM: '流式数据'
    }
    return labelMap[value] || value || '-'
  }

  function formatRowCount(value) {
    if (value === null || value === undefined) {
      return '-'
    }
    const numeric = Number(value)
    if (!Number.isFinite(numeric)) {
      return String(value)
    }
    return numeric.toLocaleString('zh-CN')
  }

  function formatFreshness(value) {
    const numeric = Number(value)
    if (!Number.isFinite(numeric) || numeric < 0) {
      return '-'
    }
    if (numeric < 60) {
      return `${numeric} sec`
    }
    if (numeric < 3600) {
      return `${Math.round(numeric / 60)} min`
    }
    return `${Math.round(numeric / 3600)} h`
  }

  function resolveTableStatus(item) {
    if (item?.enabled === false) {
      return 'offline'
    }
    if (String(item?.status || '').toUpperCase() === 'ACTIVE') {
      return 'ready'
    }
    return 'warning'
  }

  function resolveTableStatusLabel(item) {
    if (item?.enabled === false) {
      return '已停用'
    }
    if (String(item?.status || '').toUpperCase() === 'ACTIVE') {
      return '可用'
    }
    return item?.status || '待校验'
  }

  function resolveFieldStatusLabel(item) {
    if (item?.primaryKey) {
      return '主键'
    }
    if (item?.partitionKey) {
      return '分区字段'
    }
    if (item?.fieldRole) {
      return item.fieldRole
    }
    if (item?.nullable === false) {
      return '必填'
    }
    return '字段'
  }

  function buildImportNotice(payload) {
    if (!payload) {
      return '导入成功'
    }
    return [
      `表 新增 ${payload.tableCreatedCount ?? 0} / 更新 ${payload.tableUpdatedCount ?? 0}`,
      `字段 新增 ${payload.fieldCreatedCount ?? 0} / 更新 ${payload.fieldUpdatedCount ?? 0}`,
      `索引 新增 ${payload.indexCreatedCount ?? 0} / 更新 ${payload.indexUpdatedCount ?? 0}`
    ].join('，')
  }

  function isImportFileFormatMatched(file, format) {
    const extension = String(file?.name || '').split('.').pop()?.toLowerCase()
    if (format === 'json') {
      return extension === 'json'
    }
    return extension === 'xlsx'
  }

  function importFormatLabel() {
    return importFormat.value === 'json' ? 'JSON' : 'Excel'
  }

  async function consumeImportStream(sourceKey, file, options = {}) {
    const { showTerminalToast = false } = options
    stopImportProgressStream()
    const abortController = new AbortController()
    importProgressStreamAbortController = abortController
    const response = await streamDbMetaImportWorkbook(sourceKey, file, abortController.signal)
    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('导入进度流不可用')
    }

    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    try {
      while (true) {
        const { value, done } = await reader.read()
        if (done) {
          break
        }
        buffer += decoder.decode(value, { stream: true })
        const frames = buffer.split('\n\n')
        buffer = frames.pop() ?? ''
        for (const frame of frames) {
          await handleImportStreamFrame(frame, showTerminalToast)
        }
      }
      if (buffer.trim()) {
        await handleImportStreamFrame(buffer, showTerminalToast)
      }
    } catch (error) {
      if (abortController.signal.aborted) {
        return
      }
      throw error
    } finally {
      if (importProgressStreamAbortController === abortController) {
        importProgressStreamAbortController = null
      }
    }
  }

  async function handleImportStreamFrame(frame, showTerminalToast) {
    const lines = String(frame || '')
      .split('\n')
      .map(line => line.trim())
      .filter(Boolean)
    if (!lines.length) {
      return
    }
    let eventName = 'progress'
    const dataLines = []
    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventName = line.slice('event:'.length).trim() || 'progress'
        continue
      }
      if (line.startsWith('data:')) {
        dataLines.push(line.slice('data:'.length).trim())
      }
    }
    if (!dataLines.length) {
      return
    }
    const payload = JSON.parse(dataLines.join('\n'))
    applyImportJobProgress(payload)
    const status = String(payload?.status || '')
    if (eventName === 'complete' || status === 'COMPLETED') {
      stopImportProgressStream()
      if (showTerminalToast) {
        showPopup.success(buildImportNotice(payload?.result), { title: 'Import Complete', duration: 3200 })
      }
      await refreshPage()
      return
    }
    if (eventName === 'failed' || status === 'FAILED') {
      stopImportProgressStream()
      if (showTerminalToast) {
        showPopup.error(payload?.message || '导入失败')
      }
    }
  }

  function stopImportProgressStream() {
    if (importProgressStreamAbortController) {
      importProgressStreamAbortController.abort()
      importProgressStreamAbortController = null
    }
  }

  function applyImportJobProgress(payload) {
    importJobProgress.jobId = payload?.jobId || ''
    importJobProgress.sourceKey = payload?.sourceKey || ''
    importJobProgress.fileName = payload?.fileName || ''
    importJobProgress.status = payload?.status || ''
    importJobProgress.stage = payload?.stage || ''
    importJobProgress.progressPercent = Number(payload?.progressPercent ?? 0)
    importJobProgress.message = payload?.message || ''
    importJobProgress.recentMessages = Array.isArray(payload?.recentMessages) ? payload.recentMessages : []
    importJobProgress.summary = payload?.summary || createEmptyImportProgressSummary()
    importJobProgress.result = payload?.result || null
  }

  function resetImportJobProgress() {
    Object.assign(importJobProgress, createEmptyImportJobProgress())
    stopImportProgressStream()
  }

  function resolveImportStageLabel(stage) {
    const labelMap = {
      QUEUED: '等待处理',
      PARSING: '解析文件',
      IMPORTING_TABLES: '导入表',
      IMPORTING_FIELDS: '导入字段',
      IMPORTING_INDEXES: '导入索引',
      FINALIZING: '收尾同步',
      COMPLETED: '导入完成',
      FAILED: '导入失败'
    }
    return labelMap[stage] || '处理中'
  }

  function createEmptyImportProgressSummary() {
    return {
      tableTotal: 0,
      tableProcessed: 0,
      tableCreatedCount: 0,
      tableUpdatedCount: 0,
      fieldTotal: 0,
      fieldProcessed: 0,
      fieldCreatedCount: 0,
      fieldUpdatedCount: 0,
      indexTotal: 0,
      indexProcessed: 0,
      indexCreatedCount: 0,
      indexUpdatedCount: 0
    }
  }

  function createEmptyImportJobProgress() {
    return {
      jobId: '',
      sourceKey: '',
      fileName: '',
      status: '',
      stage: '',
      progressPercent: 0,
      message: '',
      recentMessages: [],
      summary: createEmptyImportProgressSummary(),
      result: null
    }
  }

  function triggerBrowserDownload(blob, filename) {
    const objectUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = filename
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    window.setTimeout(() => {
      document.body.removeChild(link)
      window.URL.revokeObjectURL(objectUrl)
    }, 1000)
  }

  function formatFieldType(item) {
    const baseType = item?.dataType || '-'
    const scale = item?.columnScale
    const precision = item?.columnPrecision
    const length = item?.columnLength
    if (Number.isFinite(Number(precision)) && Number(precision) > 0) {
      if (Number.isFinite(Number(scale)) && Number(scale) >= 0) {
        return `${baseType}(${precision},${scale})`
      }
      return `${baseType}(${precision})`
    }
    if (Number.isFinite(Number(length)) && Number(length) > 0) {
      return `${baseType}(${length})`
    }
    return baseType
  }

  return {
    currentSource,
    currentSourceList: sourceList,
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
    templateSubmitting,
    handleSourceChange,
    handlePageChange,
    handlePageSizeChange,
    openFieldWorkbench,
    selectTable,
    formatEmpty,
    goBack,
    statusClass,
    pagedTables,
    refreshPage,
    openImportDialog,
    closeImportDialog,
    openImportProgressDialog,
    closeImportProgressDialog,
    openExportDialog,
    closeExportDialog,
    handleImportDragEnter,
    handleImportDragLeave,
    handleImportFile,
    submitImport,
    exportWorkbook,
    downloadTemplateWorkbook,
    importFormatLabel
  }
}
