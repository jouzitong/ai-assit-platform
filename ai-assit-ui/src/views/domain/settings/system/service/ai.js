import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  createAiChatModelManage,
  createAiKnowledgeBase,
  deleteAiChatModelManage,
  deleteAiKbStore,
  editAiChatModelManage,
  editAiKbStore,
  getAiChatModelManage,
  searchAiChatModelManages,
  searchAiKbStores,
  updateAiChatModelManage,
  updateAiKbStore
} from '../../../../../api/aiChat'
import {
  createKbForm,
  createModelForm,
  enabledOptions,
  pageSizeOptions
} from '../data/ai'
import { showPopup } from '../../../../../utils/popup'

export function useAiPage() {
  const activeTab = ref('model')
  const loading = reactive({
    model: false,
    kb: false,
    modelSaving: false,
    kbSaving: false
  })

  const modelFilters = reactive({
    keyword: '',
    providerCode: '',
    enabled: ''
  })

  const kbFilters = reactive({
    keyword: '',
    enabled: ''
  })

  const modelPagination = reactive({
    page: 1,
    size: 10,
    total: 0
  })

  const kbPagination = reactive({
    page: 1,
    size: 10,
    total: 0
  })

  const modelList = ref([])
  const kbList = ref([])

  const modelDialogVisible = ref(false)
  const modelDialogMode = ref('create')
  const modelError = ref('')
  const modelForm = reactive(createModelForm())

  const kbDialogVisible = ref(false)
  const kbDialogMode = ref('create')
  const kbError = ref('')
  const kbForm = reactive(createKbForm())

  const currentTotal = computed(() => (activeTab.value === 'model' ? modelPagination.total : kbPagination.total))

  const currentPage = computed({
    get: () => (activeTab.value === 'model' ? modelPagination.page : kbPagination.page),
    set: (value) => {
      if (activeTab.value === 'model') {
        modelPagination.page = value
      } else {
        kbPagination.page = value
      }
    }
  })

  const currentSize = computed({
    get: () => (activeTab.value === 'model' ? modelPagination.size : kbPagination.size),
    set: (value) => {
      if (activeTab.value === 'model') {
        modelPagination.size = value
      } else {
        kbPagination.size = value
      }
    }
  })

  const pageSummary = computed(() => {
    const total = currentTotal.value
    const page = currentPage.value
    const size = currentSize.value

    if (!total) {
      return '第 0 - 0 条，共 0 条'
    }

    const start = (page - 1) * size + 1
    const end = Math.min(page * size, total)
    return `第 ${start} - ${end} 条，共 ${total} 条`
  })

  const totalPages = computed(() => Math.max(1, Math.ceil(currentTotal.value / currentSize.value)))

  watch(activeTab, async () => {
    if (activeTab.value === 'kb') {
      await loadKbPage()
      return
    }
    await loadModelPage()
  })

  onMounted(async () => {
    await Promise.all([loadModelPage(), loadKbPage()])
  })

  function buildModelQuery() {
    return {
      page: modelPagination.page,
      size: modelPagination.size,
      keyword: modelFilters.keyword || undefined,
      providerCode: modelFilters.providerCode || undefined,
      enabled: parseBooleanFilter(modelFilters.enabled)
    }
  }

  function buildKbQuery() {
    return {
      page: kbPagination.page,
      size: kbPagination.size,
      keyword: kbFilters.keyword || undefined,
      enabled: parseBooleanFilter(kbFilters.enabled)
    }
  }

  function parseBooleanFilter(value) {
    if (value === '' || value === null || value === undefined) {
      return undefined
    }
    return value === 'true'
  }

  async function loadActiveTab() {
    if (activeTab.value === 'model') {
      await loadModelPage()
    } else {
      await loadKbPage()
    }
  }

  async function loadModelPage() {
    loading.model = true
    try {
      const payload = unwrapPayload(await searchAiChatModelManages(buildModelQuery()))
      modelList.value = (payload?.list ?? []).map(normalizeModelRow)
      modelPagination.total = resolvePageTotal(payload?.pageInfo?.total, modelList.value.length)
    } catch (error) {
      showPopup.error(error.message || 'Model 列表加载失败')
    } finally {
      loading.model = false
    }
  }

  async function loadKbPage() {
    loading.kb = true
    try {
      const payload = unwrapPayload(await searchAiKbStores(buildKbQuery()))
      kbList.value = (payload?.list ?? []).map(normalizeKbRow)
      kbPagination.total = resolvePageTotal(payload?.pageInfo?.total, kbList.value.length)
    } catch (error) {
      showPopup.error(error.message || 'KB 列表加载失败')
    } finally {
      loading.kb = false
    }
  }

  function openModelCreate() {
    modelDialogMode.value = 'create'
    modelError.value = ''
    Object.assign(modelForm, createModelForm())
    modelDialogVisible.value = true
  }

  async function openModelEdit(row) {
    modelDialogMode.value = 'edit'
    modelError.value = ''
    try {
      const detail = unwrapPayload(await getAiChatModelManage(row.id))
      Object.assign(modelForm, createModelForm(), detail, {
        apiKey: '',
        extJson: formatJsonField(detail?.extJson)
      })
      modelDialogVisible.value = true
    } catch (error) {
      showPopup.error(error.message || 'Model 详情加载失败')
    }
  }

  function openKbCreate() {
    kbDialogMode.value = 'create'
    kbError.value = ''
    Object.assign(kbForm, createKbForm())
    kbDialogVisible.value = true
  }

  function openKbEdit(row) {
    kbDialogMode.value = 'edit'
    kbError.value = ''
    Object.assign(kbForm, createKbForm(), JSON.parse(JSON.stringify(row)), {
      tags: joinTags(row.tags),
      extJson: formatJsonField(row.extJson)
    })
    kbDialogVisible.value = true
  }

  function validateModelForm() {
    if (!modelForm.modelCode.trim()) {
      modelError.value = '请输入模型编码'
      return false
    }
    if (!modelForm.modelName.trim()) {
      modelError.value = '请输入模型名称'
      return false
    }
    if (!modelForm.apiModel.trim()) {
      modelError.value = '请输入 Provider 模型标识'
      return false
    }
    if (modelDialogMode.value === 'create' && !modelForm.apiKey.trim()) {
      modelError.value = '新增模型时必须填写 API Key'
      return false
    }
    try {
      parseJsonField(modelForm.extJson, '扩展配置')
    } catch (error) {
      modelError.value = error.message || '扩展配置格式不正确'
      return false
    }
    modelError.value = ''
    return true
  }

  function validateKbForm() {
    if (!kbForm.kbCode.trim()) {
      kbError.value = '请输入 KB 编码'
      return false
    }
    if (!kbForm.kbName.trim()) {
      kbError.value = '请输入 KB 名称'
      return false
    }
    if (kbForm.extJson.trim()) {
      try {
        parseJsonField(kbForm.extJson, '扩展信息')
      } catch (error) {
        kbError.value = error.message || '扩展信息格式不正确'
        return false
      }
    }
    if (kbForm.url.trim() && !isValidUrl(kbForm.url)) {
      kbError.value = '请求地址格式不正确'
      return false
    }
    kbError.value = ''
    return true
  }

  async function submitModelForm() {
    if (!validateModelForm()) {
      return
    }

    loading.modelSaving = true
    try {
      const extJson = parseJsonField(modelForm.extJson, '扩展配置')
      const payload = {
        modelCode: modelForm.modelCode.trim(),
        modelName: modelForm.modelName.trim(),
        providerCode: modelForm.providerCode.trim(),
        providerName: modelForm.providerName.trim(),
        baseUrl: modelForm.baseUrl.trim(),
        apiModel: modelForm.apiModel.trim(),
        enabled: modelForm.enabled,
        apiKey: modelForm.apiKey.trim() || undefined,
        extJson
      }

      if (modelDialogMode.value === 'create') {
        await createAiChatModelManage(payload)
        showPopup.success('Model 新增成功')
      } else {
        await updateAiChatModelManage(modelForm.id, payload)
        showPopup.success('Model 更新成功')
      }

      modelDialogVisible.value = false
      await loadModelPage()
    } catch (error) {
      modelError.value = error.message || 'Model 保存失败'
    } finally {
      loading.modelSaving = false
    }
  }

  async function submitKbForm() {
    if (!validateKbForm()) {
      return
    }

    loading.kbSaving = true
    try {
      const extraExt = parseJsonField(kbForm.extJson, '扩展信息')
      const tags = splitTags(kbForm.tags)
      const payload = {
        kbCode: kbForm.kbCode.trim(),
        kbName: kbForm.kbName.trim(),
        providerKbId: kbForm.providerKbId.trim() || null,
        enabled: kbForm.enabled,
        tags,
        url: kbForm.url.trim() || null,
        extJson: extraExt
      }

      if (kbDialogMode.value === 'create') {
        await createAiKnowledgeBase({
          ...payload,
          ext: extraExt
        })
        showPopup.success('KB 新增成功')
      } else {
        await updateAiKbStore(kbForm.id, payload)
        showPopup.success('KB 更新成功')
      }

      kbDialogVisible.value = false
      await loadKbPage()
    } catch (error) {
      kbError.value = error.message || 'KB 保存失败'
    } finally {
      loading.kbSaving = false
    }
  }

  async function toggleModelStatus(row) {
    const nextValue = !row.enabled
    try {
      await editAiChatModelManage(row.id, { enabled: nextValue })
      row.enabled = nextValue
      showPopup.success(`Model 已${nextValue ? '启用' : '停用'}`)
    } catch (error) {
      showPopup.error(error.message || 'Model 状态更新失败')
    }
  }

  async function toggleKbStatus(row) {
    const nextEnabled = !row.enabled
    try {
      await editAiKbStore(row.id, { enabled: nextEnabled })
      row.enabled = nextEnabled
      showPopup.success(`KB 已${row.enabled ? '启用' : '停用'}`)
    } catch (error) {
      showPopup.error(error.message || 'KB 状态更新失败')
    }
  }

  async function confirmDeleteModel(row) {
    if (!window.confirm(`确认删除 Model「${row.modelName}」吗？关联凭证也会一起删除。`)) {
      return
    }
    try {
      await deleteAiChatModelManage(row.id)
      showPopup.success('Model 已删除')
      await loadModelPage()
    } catch (error) {
      showPopup.error(error.message || 'Model 删除失败')
    }
  }

  async function confirmDeleteKb(row) {
    if (!window.confirm(`确认删除 KB「${row.kbName}」吗？`)) {
      return
    }
    try {
      await deleteAiKbStore(row.id)
      showPopup.success('KB 已删除')
      await loadKbPage()
    } catch (error) {
      showPopup.error(error.message || 'KB 删除失败')
    }
  }

  function resetModelFilters() {
    modelFilters.keyword = ''
    modelFilters.providerCode = ''
    modelFilters.enabled = ''
    modelPagination.page = 1
    loadModelPage()
  }

  function resetKbFilters() {
    kbFilters.keyword = ''
    kbFilters.enabled = ''
    kbPagination.page = 1
    loadKbPage()
  }

  function handleSearch() {
    if (activeTab.value === 'model') {
      modelPagination.page = 1
      loadModelPage()
    } else {
      kbPagination.page = 1
      loadKbPage()
    }
  }

  function handlePageChange(nextPage) {
    if (nextPage < 1 || nextPage > totalPages.value) {
      return
    }
    currentPage.value = nextPage
    loadActiveTab()
  }

  function handlePageSizeChange(event) {
    currentSize.value = Number(event.target.value)
    currentPage.value = 1
    loadActiveTab()
  }

  function openCreateByTab() {
    if (activeTab.value === 'model') {
      openModelCreate()
    } else {
      openKbCreate()
    }
  }

  function normalizeNumber(value) {
    if (value === '' || value === null || value === undefined) {
      return null
    }
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }

  function normalizeKbRow(row) {
    const extJson = row?.extJson && typeof row.extJson === 'object' ? row.extJson : {}
    return {
      ...row,
      extJson,
      enabled: row?.enabled !== false,
      tags: normalizeTags(row?.tags)
    }
  }

  function normalizeModelRow(row) {
    const ext = row?.extJson && typeof row.extJson === 'object' ? row.extJson : {}
    return {
      ...row,
      extJson: ext,
      capabilityTags: ext.capabilityTags ?? '',
      maxContextTokens: normalizeNumber(ext.maxContextTokens),
      maxOutputTokens: normalizeNumber(ext.maxOutputTokens),
      temperatureEnabled: normalizeNumber(ext.temperatureEnabled),
      priority: normalizeNumber(ext.priority),
      remark: ext.remark ?? ''
    }
  }

  function formatJsonField(value) {
    if (!value) {
      return ''
    }
    try {
      return JSON.stringify(value, null, 2)
    } catch {
      return String(value)
    }
  }

  function parseJsonField(value, label) {
    const content = String(value ?? '').trim()
    if (!content) {
      return null
    }
    try {
      const parsed = JSON.parse(content)
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        return parsed
      }
      throw new Error(`${label}必须是 JSON 对象`)
    } catch (error) {
      throw new Error(error.message || `${label}格式不正确`)
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
    return listLength
  }

  function formatDateTime(value) {
    if (!value) {
      return '-'
    }
    const date = typeof value === 'string' ? new Date(value) : value
    if (Number.isNaN(date.getTime())) {
      return String(value)
    }
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
      date.getMinutes()
    )}`
  }

  function pad(value) {
    return String(value).padStart(2, '0')
  }

  function tagList(value) {
    if (!value) {
      return []
    }
    return String(value)
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
  }

  function normalizeTags(value) {
    if (!Array.isArray(value)) {
      return []
    }
    return value.map(item => String(item).trim()).filter(Boolean)
  }

  function joinTags(value) {
    return normalizeTags(value).join(', ')
  }

  function splitTags(value) {
    if (!value) {
      return []
    }
    return String(value)
      .split(',')
      .map(item => item.trim())
      .filter(Boolean)
  }
  function isValidUrl(value) {
    try {
      const url = new URL(value)
      return Boolean(url.protocol && url.host)
    } catch {
      return false
    }
  }

  return {
    activeTab,
    loading,
    modelFilters,
    kbFilters,
    modelList,
    kbList,
    modelDialogVisible,
    modelDialogMode,
    modelError,
    modelForm,
    kbDialogVisible,
    kbDialogMode,
    kbError,
    kbForm,
    enabledOptions,
    pageSizeOptions,
    currentPage,
    currentSize,
    pageSummary,
    totalPages,
    openModelEdit,
    openKbEdit,
    submitModelForm,
    submitKbForm,
    toggleModelStatus,
    toggleKbStatus,
    confirmDeleteModel,
    confirmDeleteKb,
    resetModelFilters,
    resetKbFilters,
    handleSearch,
    handlePageChange,
    handlePageSizeChange,
    openCreateByTab,
    formatDateTime,
    tagList
  }
}
