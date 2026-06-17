import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getKnowledgeDocumentDetail } from '../../../../../api/knowledge'
import { showPopup } from '../../../../../utils/popup'

export function useKnowledgeManagePage() {
  const route = useRoute()
  const router = useRouter()
  const detail = ref(null)
  const loading = ref(false)
  const errorMessage = ref('')

  const kbCode = computed(() => String(route.query.kbCode ?? ''))
  const documentCode = computed(() => String(route.params.sourceKey ?? ''))
  const contentText = computed(() => {
    if (detail.value?.renderedContent) {
      return detail.value.renderedContent
    }
    if (detail.value?.contentJson && Object.keys(detail.value.contentJson).length) {
      return JSON.stringify(detail.value.contentJson, null, 2)
    }
    return '暂无文档内容'
  })
  const metaItems = computed(() => ([
    { label: '知识库编码', value: detail.value?.kbCode || '-' },
    { label: '文档编码', value: detail.value?.documentCode || '-' },
    { label: '文档名称', value: detail.value?.documentName || '-' },
    { label: '文档类型', value: detail.value?.documentType || '-' },
    { label: '业务类型', value: detail.value?.bizType || '-' },
    { label: '业务唯一键', value: detail.value?.bizKey || '-' },
    { label: '来源系统', value: detail.value?.sourceSystem || '-' },
    { label: '状态', value: detail.value?.status || '-' },
    { label: '草稿版本号', value: detail.value?.draftVersionNo ?? '-' },
    { label: '内容格式', value: detail.value?.contentFormat || '-' },
    { label: '内容大小', value: detail.value?.contentSize ?? '-' },
    { label: '审核状态', value: detail.value?.reviewStatus || '-' },
    { label: '最近生成时间', value: formatDateTime(detail.value?.lastGeneratedAt) },
    { label: '备注', value: detail.value?.remark || '-' }
  ]))

  onMounted(() => {
    loadDetail()
  })

  watch([kbCode, documentCode], () => {
    loadDetail()
  })

  async function loadDetail(options = {}) {
    const { showPopupNotice = false } = options
    if (!kbCode.value || !documentCode.value) {
      errorMessage.value = '缺少 kbCode 或 documentCode，无法查看文档内容'
      detail.value = null
      return
    }
    loading.value = true
    errorMessage.value = ''
    if (showPopupNotice) {
      showPopup.info('文档内容刷新中...', { title: 'Loading', duration: 1600 })
    }
    try {
      detail.value = await getKnowledgeDocumentDetail({
        kbCode: kbCode.value,
        documentCode: documentCode.value
      })
      if (showPopupNotice) {
        showPopup.success('文档内容已刷新')
      }
    } catch (error) {
      errorMessage.value = error.message || '文档内容加载失败'
      detail.value = null
      showPopup.error(error.message || '文档内容加载失败')
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

  return {
    detail,
    kbCode,
    documentCode,
    contentText,
    metaItems,
    loading,
    errorMessage,
    goBack,
    refreshPage
  }
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 19)
}
