import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createAiKnowledgeBase, listAiKnowledgeBaseDocuments, syncAiKnowledgeBaseDocuments } from '../../../../api/aiChat'
import { showPopup } from '../../../../utils/popup'

export function useKnowledgePage() {
  const router = useRouter()
  const keyword = ref('')
  const activeTab = ref('current')
  const sourceList = ref([])
  const loading = ref(false)
  const errorMessage = ref('')
  const createDialogVisible = ref(false)
  const createSubmitting = ref(false)
  const createError = ref('')
  const createForm = reactive(createEmptyKbForm())

  const filteredSources = computed(() => {
    const normalized = keyword.value.trim().toLowerCase()
    const matchedByTab = sourceList.value.filter(item => matchTab(item, activeTab.value))
    if (!normalized) {
      return matchedByTab
    }
    return matchedByTab.filter(item =>
      [item.kbCode, item.documentCode, item.documentName, item.bizKey, item.sourceSystem].some(value =>
        String(value ?? '').toLowerCase().includes(normalized)
      )
    )
  })

  onMounted(() => {
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
      const payload = await listAiKnowledgeBaseDocuments({})
      sourceList.value = normalizeDocumentList(payload).map(mapDocumentItem)
      if (showSuccessPopup) {
        showPopup.success('知识库文档列表已刷新')
      }
    } catch (error) {
      errorMessage.value = error.message || '知识库文档列表加载失败'
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
    const syncRows = filteredSources.value.filter(shouldSyncDocument)
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

  function openCreateDialog() {
    Object.assign(createForm, createEmptyKbForm())
    createError.value = ''
    createDialogVisible.value = true
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
      const ext = parseExtJson(createForm.extJson)
      if (createForm.workspaceId.trim()) {
        ext.workspaceId = createForm.workspaceId.trim()
      }
      if (createForm.kbEndpoint.trim()) {
        ext.kbEndpoint = createForm.kbEndpoint.trim()
      }

      await createAiKnowledgeBase({
        kbCode: createForm.kbCode.trim(),
        kbName: createForm.kbName.trim(),
        sourceType: createForm.sourceType,
        sourceKey: createForm.sourceKey.trim(),
        providerKbId: createForm.providerKbId.trim() || null,
        status: createForm.status,
        ext
      })
      createDialogVisible.value = false
      showPopup.success('知识库已新建，当前列表会在写入文档后展示对应文档')
      await loadDataSources()
    } catch (error) {
      createError.value = error.message || '知识库新建失败'
    } finally {
      createSubmitting.value = false
    }
  }

  function showPendingAction(label = '当前操作') {
    showPopup.warning(`${label}功能建设中`)
  }

  return {
    activeTab,
    keyword,
    sourceList,
    loading,
    errorMessage,
    createDialogVisible,
    createSubmitting,
    createError,
    createForm,
    filteredSources,
    openSource,
    triggerKnowledgeSync,
    loadDataSources,
    openCreateDialog,
    closeCreateDialog,
    submitCreateDialog,
    showPendingAction
  }
}

function createEmptyKbForm() {
  return {
    kbCode: '',
    kbName: '',
    sourceType: 'DB_DATA_SOURCE',
    sourceKey: '',
    providerKbId: '',
    status: 'INIT',
    workspaceId: '',
    kbEndpoint: '',
    extJson: ''
  }
}

function validateCreateForm(form) {
  if (!form.kbCode.trim()) {
    return '请输入知识库编码'
  }
  if (!form.kbName.trim()) {
    return '请输入知识库名称'
  }
  if (!form.sourceKey.trim()) {
    return '请输入业务唯一键'
  }
  try {
    parseExtJson(form.extJson)
  } catch (error) {
    return error.message
  }
  return ''
}

function parseExtJson(value) {
  const text = String(value || '').trim()
  if (!text) {
    return {}
  }
  try {
    const parsed = JSON.parse(text)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('扩展信息 JSON 必须是对象')
    }
    return { ...parsed }
  } catch (error) {
    throw new Error(error.message || '扩展信息 JSON 格式错误')
  }
}

function mapDocumentItem(item) {
  return {
    id: item?.id ?? null,
    key: item?.documentCode || String(item?.id ?? ''),
    kbCode: item?.kbCode || '-',
    documentCode: item?.documentCode || '-',
    documentName: item?.documentName || '-',
    documentType: item?.documentType || '-',
    bizType: item?.bizType || '-',
    bizKey: item?.bizKey || '-',
    sourceSystem: item?.sourceSystem || '-',
    status: formatStatus(item?.status),
    statusClass: resolveStatusClass(item?.status),
    providerDocumentId: item?.providerDocumentId || '-',
    providerSyncStatus: formatProviderSyncStatus(item?.providerSyncStatus),
    currentVersionNo: item?.currentVersionNo ?? '-',
    contentFormat: item?.contentFormat || '-',
    contentSize: formatContentSize(item?.contentSize),
    lastGeneratedAt: formatDateTime(item?.lastGeneratedAt),
    raw: item
  }
}

function normalizeDocumentList(payload) {
  if (Array.isArray(payload)) {
    return payload
  }
  if (Array.isArray(payload?.data)) {
    return payload.data
  }
  if (Array.isArray(payload?.list)) {
    return payload.list
  }
  return []
}

function matchTab(item, tabKey) {
  const rawStatus = String(item.raw?.status || '').toUpperCase()
  if (tabKey === 'history') {
    return rawStatus === 'DISABLED'
  }
  return rawStatus !== 'DISABLED'
}

function shouldSyncDocument(item) {
  const syncStatus = String(item.raw?.providerSyncStatus || '').toUpperCase()
  return !syncStatus || syncStatus === 'PENDING' || syncStatus === 'FAILED'
}

function resolveStatusClass(status) {
  const normalized = String(status || '').toUpperCase()
  if (normalized === 'ACTIVE') return 'online'
  if (normalized === 'DISABLED' || normalized === 'INACTIVE') return 'offline'
  return normalized ? 'warning' : ''
}

function formatStatus(status) {
  const statusLabelMap = {
    ACTIVE: '生效',
    DISABLED: '已停用',
    INACTIVE: '已停用'
  }
  return statusLabelMap[status] || status || '-'
}

function formatProviderSyncStatus(status) {
  const statusLabelMap = {
    PENDING: '待同步',
    RUNNING: '同步中',
    SUCCESS: '同步成功',
    FAILED: '同步失败'
  }
  return statusLabelMap[status] || status || '-'
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
