import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listAiKnowledgeBaseDocuments } from '../../../../api/aiChat'
import { showPopup } from '../../../../utils/popup'

export function useKnowledgePage() {
  const router = useRouter()
  const keyword = ref('')
  const activeTab = ref('current')
  const sourceList = ref([])
  const loading = ref(false)
  const errorMessage = ref('')

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

  function triggerKnowledgeSync() {
    showPopup.warning('知识库同步功能建设中')
  }

  function openCreateDialog() {
    showPopup.warning('新建知识库文档功能建设中')
  }

  return {
    activeTab,
    keyword,
    sourceList,
    loading,
    errorMessage,
    filteredSources,
    openSource,
    triggerKnowledgeSync,
    loadDataSources,
    openCreateDialog
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
    draftVersionNo: item?.draftVersionNo ?? '-',
    contentFormat: item?.contentFormat || '-',
    contentSize: formatContentSize(item?.contentSize),
    reviewStatus: formatReviewStatus(item?.reviewStatus),
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
  const rawReviewStatus = String(item.raw?.reviewStatus || '').toUpperCase()
  if (tabKey === 'draft') {
    return ['DRAFT', 'READY', 'REJECTED'].includes(rawReviewStatus)
  }
  if (tabKey === 'history') {
    return rawStatus === 'DISABLED'
  }
  return rawReviewStatus === 'PUBLISHED'
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

function formatReviewStatus(status) {
  const reviewLabelMap = {
    DRAFT: '草稿',
    READY: '待审核',
    REJECTED: '已驳回',
    PUBLISHED: '已发布'
  }
  return reviewLabelMap[status] || status || '-'
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
