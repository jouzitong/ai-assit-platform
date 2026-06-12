import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { downloadDbMetaTemplateWorkbook, exportDbMetaWorkbook, importDbMetaWorkbook, searchDbDataSources, searchDbTableFields, searchDbTables } from '../../../../../../api/dbEngine'

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
  const notice = reactive({
    type: 'success',
    text: ''
  })
  let noticeTimer = null
  const pageSizeOptions = [10, 20, 50]

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

  onMounted(async () => {
    await loadInitialData()
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
    importDialogVisible.value = true
    importDragActive.value = false
    importError.value = ''
  }

  function closeImportDialog() {
    importDialogVisible.value = false
    importDragActive.value = false
    importError.value = ''
    importFile.value = null
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
      importError.value = '请先选择要导入的 Excel 文件'
      return
    }

    importSubmitting.value = true
    importError.value = ''
    try {
      const payload = unwrapPayload(await importDbMetaWorkbook(currentSource.value.key, importFile.value))
      showNotice(buildImportNotice(payload))
      closeImportDialog()
      await refreshPage()
    } catch (error) {
      importError.value = error.message || '导入失败'
    } finally {
      importSubmitting.value = false
    }
  }

  async function exportWorkbook() {
    if (!currentSource.value?.key) {
      showNotice('当前没有可导出的数据源', 'error')
      return
    }
    exportSubmitting.value = true
    try {
      const { blob, filename } = await exportDbMetaWorkbook(currentSource.value.key)
      const objectUrl = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = filename
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(objectUrl)
      showNotice('导出成功')
    } catch (error) {
      showNotice(error.message || '导出失败', 'error')
    } finally {
      exportSubmitting.value = false
    }
  }

  async function downloadTemplateWorkbook() {
    templateSubmitting.value = true
    try {
      const { blob, filename } = await downloadDbMetaTemplateWorkbook()
      const objectUrl = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = filename
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(objectUrl)
      showNotice('模板下载成功')
    } catch (error) {
      showNotice(error.message || '模板下载失败', 'error')
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
    if (Number.isFinite(parsed) && parsed >= 0) {
      return parsed
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

  function showNotice(text, type = 'success') {
    notice.type = type
    notice.text = text
    if (noticeTimer) {
      window.clearTimeout(noticeTimer)
    }
    noticeTimer = window.setTimeout(() => {
      notice.text = ''
    }, 2600)
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
    importSubmitting,
    exportSubmitting,
    templateSubmitting,
    notice,
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
    handleImportDragEnter,
    handleImportDragLeave,
    handleImportFile,
    submitImport,
    exportWorkbook,
    downloadTemplateWorkbook
  }
}
