import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getKnowledgeDocumentByCode, KNOWLEDGE_DOCUMENTS } from '../../data'
import { showPopup } from '../../../../../utils/popup'

export function useKnowledgeManagePage() {
  const route = useRoute()
  const router = useRouter()
  const detail = ref(null)
  const loading = ref(false)
  const errorMessage = ref('')

  const kbCode = computed(() => String(route.query.kbCode ?? ''))
  const documentCode = computed(() => String(route.params.sourceKey ?? ''))
  const documentList = computed(() => KNOWLEDGE_DOCUMENTS)
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
    code: detail.value?.documentCode || '-',
    source: detail.value?.sourceSystem || '-',
    status: detail.value?.status || '-'
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
      detail.value = getKnowledgeDocumentByCode(kbCode.value, documentCode.value)
      if (!detail.value) {
        showPopup.warning('未找到对应的本地文档内容')
      } else if (showPopupNotice) {
        showPopup.success('文档内容已刷新')
      }
    } finally {
      loading.value = false
    }
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
