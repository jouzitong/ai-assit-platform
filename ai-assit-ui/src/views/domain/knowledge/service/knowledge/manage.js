import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAiKnowledgeBaseDocumentDetail, listAiKnowledgeBaseDocuments } from '../../../../../api/aiChat'
import { showPopup } from '../../../../../utils/popup'

export function useKnowledgeManagePage() {
  const route = useRoute()
  const router = useRouter()
  const detail = ref(null)
  const documents = ref([])
  const loading = ref(false)
  const errorMessage = ref('')

  const kbCode = computed(() => String(route.query.kbCode ?? ''))
  const documentCode = computed(() => String(route.params.sourceKey ?? ''))
  const documentList = computed(() => documents.value)
  const currentDocumentKey = computed(() => `${kbCode.value}::${documentCode.value}`)
  const contentText = computed(() => {
    if (detail.value?.renderedContent) {
      return detail.value.renderedContent
    }
    if (detail.value?.contentJson && Object.keys(detail.value.contentJson).length) {
      return JSON.stringify(detail.value.contentJson, null, 2)
    }
    return '暂无文档内容'
  })
  const summaryInfo = computed(() => ({
    kbCode: detail.value?.kbCode || '-',
    documentCode: detail.value?.documentCode || '-',
    documentType: detail.value?.documentType || '-',
    bizKey: detail.value?.bizKey || '-',
    source: detail.value?.sourceSystem || '-',
    status: detail.value?.status || '-',
    reviewStatus: detail.value?.reviewStatus || '-',
    contentSize: formatContentSize(detail.value?.contentSize),
    lastGeneratedAt: formatDateTime(detail.value?.lastGeneratedAt)
  }))

  onMounted(() => {
    loadDetail()
  })

  watch([kbCode, documentCode], () => {
    loadDetail()
  })

  async function loadDetail(options = {}) {
    const { showPopupNotice = false } = options
    if (!kbCode.value || !documentCode.value) {
      errorMessage.value = ''
      detail.value = null
      return
    }
    loading.value = true
    errorMessage.value = ''
    if (showPopupNotice) {
      showPopup.info('文档内容刷新中...', { title: 'Loading', duration: 1600 })
    }
    try {
      await loadDocumentList()
      detail.value = await getAiKnowledgeBaseDocumentDetail(kbCode.value, documentCode.value)
      if (!detail.value) {
        showPopup.warning('未找到对应的本地文档内容')
      } else if (showPopupNotice) {
        showPopup.success('文档内容已刷新')
      }
    } catch (error) {
      errorMessage.value = error.message || '文档内容加载失败'
      showPopup.error(errorMessage.value)
    } finally {
      loading.value = false
    }
  }

  async function loadDocumentList() {
    const payload = await listAiKnowledgeBaseDocuments({
      kbCode: kbCode.value || undefined
    })
    documents.value = normalizeDocumentList(payload)
  }

  function goBack() {
    router.push('/knowledge')
  }

  function refreshPage() {
    loadDetail({ showPopupNotice: true })
  }

  function selectDocument(item) {
    if (!item?.kbCode || !item?.documentCode) {
      return
    }
    router.push({
      path: `/knowledge/${encodeURIComponent(item.documentCode)}`,
      query: {
        kbCode: item.kbCode
      }
    })
  }

  return {
    detail,
    documentList,
    currentDocumentKey,
    contentText,
    summaryInfo,
    loading,
    errorMessage,
    selectDocument
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
