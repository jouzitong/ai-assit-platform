import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  deleteAiKnowledgeBaseDocuments,
  listAiKnowledgeBaseDocuments,
  listAiKnowledgeBases,
  syncAiKnowledgeBaseDocuments,
  upsertAiKnowledgeBaseDocument
} from '../../../../api/aiChat'
import { showPopup } from '../../../../utils/popup'

export function useKnowledgePage() {
  const router = useRouter()
  const keyword = ref('')
  const bizTypeCode = ref('')
  const activeTab = ref('current')
  const page = ref(1)
  const pageSize = ref(10)
  const totalItems = ref(0)
  const sourceList = ref([])
  const loading = ref(false)
  const errorMessage = ref('')
  const createDialogVisible = ref(false)
  const createSubmitting = ref(false)
  const createError = ref('')
  const createForm = reactive(createEmptyKbForm())
  const knowledgeBaseOptions = ref([])
  const batchMode = ref(false)
  const selectedDocumentCodes = ref([])
  let reloadTimer = null

  const filteredSources = computed(() => sourceList.value)
  const selectedKnowledgeBase = computed(() => {
    return knowledgeBaseOptions.value.find(item => item.kbId === createForm.kbId) || null
  })

  onMounted(() => {
    loadKnowledgeBaseOptions()
    loadDataSources()
  })

  onBeforeUnmount(() => {
    if (reloadTimer) {
      clearTimeout(reloadTimer)
      reloadTimer = null
    }
  })

  watch([keyword, bizTypeCode, activeTab], () => {
    if (reloadTimer) {
      clearTimeout(reloadTimer)
    }
    page.value = 1
    reloadTimer = setTimeout(() => {
      loadDataSources()
    }, 250)
  })

  watch(page, () => {
    loadDataSources()
  })

  watch(pageSize, () => {
    page.value = 1
    loadDataSources()
  })

  async function loadDataSources(options = {}) {
    const { showLoadingPopup = false, showSuccessPopup = false } = options
    loading.value = true
    errorMessage.value = ''
    if (showLoadingPopup) {
      showPopup.info('知识库文档刷新中...', { title: 'Loading', duration: 1600 })
    }
    try {
      const payload = await listAiKnowledgeBaseDocuments(buildListRequestPayload({
        keyword: keyword.value,
        bizTypeCode: bizTypeCode.value,
        tab: activeTab.value,
        page: page.value,
        size: pageSize.value
      }))
      const normalized = normalizeDocumentList(payload)
      sourceList.value = normalized.list.map(mapDocumentItem)
      totalItems.value = normalized.total
      if (showSuccessPopup) {
        showPopup.success('知识库文档列表已刷新')
      }
    } catch (error) {
      errorMessage.value = error.message || '知识库文档列表加载失败'
      sourceList.value = []
      totalItems.value = 0
      showPopup.error(errorMessage.value)
    } finally {
      loading.value = false
    }
  }

  function openSource(itemOrKey) {
    const item = typeof itemOrKey === 'object'
      ? itemOrKey
      : sourceList.value.find(entry => entry.key === itemOrKey || entry.documentCode === itemOrKey)
    if (!item?.documentCode) {
      showPopup.warning('当前文档缺少 documentCode，无法查看正文')
      return
    }
    router.push({
      path: `/knowledge/${encodeURIComponent(item.documentCode)}`,
      query: {
        kbCode: item.kbCode || ''
      }
    })
  }

  async function triggerKnowledgeSync() {
    const syncRows = resolveTargetRows().filter(shouldSyncDocument)
    if (!syncRows.length) {
      showPopup.warning('当前没有待同步或同步失败的文档')
      return
    }

    loading.value = true
    errorMessage.value = ''
    showPopup.info(`知识库文档同步中，共 ${syncRows.length} 个文档...`, { title: 'Syncing', duration: 1600 })
    try {
      const response = await syncAiKnowledgeBaseDocuments({
        documentCodes: syncRows.map(item => item.documentCode).filter(Boolean)
      })
      const acceptedCount = Number(response?.acceptedCount ?? 0)
      const skippedCount = Array.isArray(response?.skippedDocumentCodes)
        ? response.skippedDocumentCodes.length
        : 0
      showPopup.success(`知识库文档同步完成，已同步 ${acceptedCount} 个文档${skippedCount ? `，跳过 ${skippedCount} 个` : ''}`)
      await loadDataSources()
    } catch (error) {
      errorMessage.value = error.message || '知识库文档同步失败'
      showPopup.error(errorMessage.value)
    } finally {
      loading.value = false
    }
  }

  async function deleteKnowledgeDocuments() {
    const targetRows = resolveTargetRows()
    if (!targetRows.length) {
      showPopup.warning('请先选择要删除的文档')
      return
    }
    if (!window.confirm(`确认删除已选择的 ${targetRows.length} 个知识库文档吗？`)) {
      return
    }
    loading.value = true
    errorMessage.value = ''
    try {
      const response = await deleteAiKnowledgeBaseDocuments({
        documentCodes: targetRows.map(item => item.documentCode).filter(Boolean)
      })
      const deletedCount = Number(response?.deletedCount ?? 0)
      const skippedCount = Array.isArray(response?.skippedDocumentCodes)
        ? response.skippedDocumentCodes.length
        : 0
      showPopup.success(`知识库文档删除完成，已删除 ${deletedCount} 个文档${skippedCount ? `，跳过 ${skippedCount} 个` : ''}`)
      clearBatchSelection()
      await loadDataSources()
    } catch (error) {
      errorMessage.value = error.message || '知识库文档删除失败'
      showPopup.error(errorMessage.value)
    } finally {
      loading.value = false
    }
  }

  function openCreateDialog() {
    Object.assign(createForm, createEmptyKbForm())
    createError.value = ''
    createDialogVisible.value = true
    loadKnowledgeBaseOptions({ showErrorPopup: false })
  }

  function closeCreateDialog() {
    if (createSubmitting.value) {
      return
    }
    createDialogVisible.value = false
    createError.value = ''
  }

  async function submitCreateDialog() {
    createError.value = ''
    const validationError = validateCreateForm(createForm)
    if (validationError) {
      createError.value = validationError
      return
    }

    createSubmitting.value = true
    try {
      const kb = selectedKnowledgeBase.value
      if (!kb?.kbId) {
        throw new Error('请选择有效的知识库')
      }
      await upsertAiKnowledgeBaseDocument({
        kbId: kb.kbId,
        documentId: createForm.documentCode.trim(),
        documentName: createForm.documentName.trim() || createForm.documentCode.trim(),
        content: createForm.content
      })
      createDialogVisible.value = false
      showPopup.success('知识文档已保存')
      await loadDataSources()
    } catch (error) {
      createError.value = error.message || '知识文档保存失败'
    } finally {
      createSubmitting.value = false
    }
  }

  async function loadKnowledgeBaseOptions(options = {}) {
    const { showErrorPopup = true } = options
    try {
      const payload = await listAiKnowledgeBases({})
      knowledgeBaseOptions.value = normalizeKnowledgeBases(payload)
      if (!createForm.kbId && knowledgeBaseOptions.value.length > 0) {
        createForm.kbId = knowledgeBaseOptions.value[0].kbId
      }
    } catch (error) {
      knowledgeBaseOptions.value = []
      if (showErrorPopup) {
        showPopup.error(error.message || '知识库列表加载失败')
      }
    }
  }

  function showPendingAction(label = '当前操作') {
    showPopup.warning(`${label}功能建设中`)
  }

  function enterBatchMode() {
    batchMode.value = true
    clearBatchSelection()
  }

  function exitBatchMode() {
    batchMode.value = false
    clearBatchSelection()
  }

  function toggleDocumentSelection(documentCode, checked) {
    if (!documentCode) {
      return
    }
    if (checked) {
      if (!selectedDocumentCodes.value.includes(documentCode)) {
        selectedDocumentCodes.value = [...selectedDocumentCodes.value, documentCode]
      }
      return
    }
    selectedDocumentCodes.value = selectedDocumentCodes.value.filter(item => item !== documentCode)
  }

  function toggleSelectAll(checked, rows) {
    selectedDocumentCodes.value = checked
      ? rows.map(item => item.documentCode).filter(Boolean)
      : []
  }

  function clearBatchSelection() {
    selectedDocumentCodes.value = []
  }

  function resolveTargetRows() {
    if (!batchMode.value) {
      return filteredSources.value
    }
    return filteredSources.value.filter(item => selectedDocumentCodes.value.includes(item.documentCode))
  }

  return {
    activeTab,
    page,
    pageSize,
    totalItems,
    keyword,
    bizTypeCode,
    sourceList,
    loading,
    errorMessage,
    createDialogVisible,
    createSubmitting,
    createError,
    createForm,
    knowledgeBaseOptions,
    selectedKnowledgeBase,
    batchMode,
    selectedDocumentCodes,
    filteredSources,
    openSource,
    triggerKnowledgeSync,
    deleteKnowledgeDocuments,
    loadDataSources,
    openCreateDialog,
    closeCreateDialog,
    submitCreateDialog,
    showPendingAction,
    enterBatchMode,
    exitBatchMode,
    toggleDocumentSelection,
    toggleSelectAll
  }
}

function buildListRequestPayload({ keyword, bizTypeCode, tab, page, size }) {
  const payload = {}
  if (keyword.trim()) {
    payload.keyword = keyword.trim()
  }
  if (bizTypeCode !== '' && bizTypeCode !== null && bizTypeCode !== undefined) {
    payload.bizTypeCode = Number(bizTypeCode)
  }
  payload.tab = tab || 'current'
  payload.page = page
  payload.size = size
  return payload
}

function createEmptyKbForm() {
  return {
    kbId: '',
    documentCode: '',
    documentName: '',
    content: ''
  }
}

function validateCreateForm(form) {
  if (!form.kbId.trim()) {
    return '请选择知识库'
  }
  if (!form.documentCode.trim()) {
    return '请输入文档编码'
  }
  if (!String(form.content || '').trim()) {
    return '请输入文档内容'
  }
  return ''
}

function normalizeKnowledgeBases(payload) {
  const list = Array.isArray(payload) ? payload : Array.isArray(payload?.data) ? payload.data : []
  return list
    .map(item => ({
      kbId: item?.kbId ? String(item.kbId) : '',
      kbName: item?.kbName ? String(item.kbName) : '',
      bizType: item?.bizType ?? null,
      providerKbId: item?.providerKbId ? String(item.providerKbId) : ''
    }))
    .filter(item => item.kbId)
}

function mapDocumentItem(item) {
  return {
    id: item?.id ?? null,
    key: item?.documentCode || String(item?.id ?? ''),
    kbCode: item?.kbCode || '-',
    documentCode: item?.documentCode || '-',
    documentName: item?.documentName || '-',
    documentType: item?.documentType ?? null,
    bizType: item?.bizType ?? null,
    bizKey: item?.bizKey || '-',
    sourceSystem: item?.sourceSystem || '-',
    status: item?.status ?? null,
    statusClass: resolveStatusClass(item?.status),
    providerDocumentId: item?.providerDocumentId || '-',
    providerSyncStatus: item?.providerSyncStatus ?? null,
    currentVersionNo: item?.currentVersionNo ?? '-',
    contentFormat: item?.contentFormat ?? null,
    contentSize: formatContentSize(item?.contentSize),
    lastGeneratedAt: formatDateTime(item?.lastGeneratedAt),
    raw: item
  }
}

function normalizeDocumentList(payload) {
  if (Array.isArray(payload)) {
    return {
      list: payload,
      total: payload.length
    }
  }
  if (Array.isArray(payload?.list)) {
    return {
      list: payload.list,
      total: resolvePageTotal(payload?.pageInfo?.total, payload.list.length)
    }
  }
  if (Array.isArray(payload?.data)) {
    return {
      list: payload.data,
      total: payload.data.length
    }
  }
  return {
    list: [],
    total: 0
  }
}

function shouldSyncDocument(item) {
  const syncStatus = item.raw?.providerSyncStatus
  return syncStatus === null || syncStatus === undefined || isPendingSyncStatus(syncStatus) || isFailedSyncStatus(syncStatus)
}

function resolveStatusClass(status) {
  if (isActiveStatus(status)) return 'online'
  if (isDisabledStatus(status)) return 'offline'
  return status === null || status === undefined || status === '' ? '' : 'warning'
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

function formatContentSize(value) {
  const size = Number(value)
  if (!Number.isFinite(size) || size < 0) {
    return '-'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function isActiveStatus(value) {
  return matchesEnumValue(value, [1, '1', 'ACTIVE'])
}

function isDisabledStatus(value) {
  return matchesEnumValue(value, [2, '2', 'DISABLED', 'INACTIVE'])
}

function isPendingSyncStatus(value) {
  return matchesEnumValue(value, [1, '1', 'PENDING'])
}

function isFailedSyncStatus(value) {
  return matchesEnumValue(value, [4, '4', 'FAILED'])
}

function matchesEnumValue(value, candidates) {
  if (value === null || value === undefined) {
    return false
  }
  const normalized = typeof value === 'string' ? value.trim().toUpperCase() : String(value)
  return candidates.some(candidate => {
    if (typeof candidate === 'string') {
      return normalized === candidate.toUpperCase()
    }
    return String(candidate) === String(value)
  })
}

function resolvePageTotal(total, fallback) {
  const numericTotal = Number(total)
  return Number.isFinite(numericTotal) ? numericTotal : fallback
}
