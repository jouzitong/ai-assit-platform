import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  createAiChatModelManage,
  createAiChatProviderConfig,
  deleteAiChatModelManage,
  deleteAiChatProviderConfig,
  editAiChatModelManage,
  editAiChatProviderConfig,
  getAiChatModelManage,
  searchAiChatModelManages,
  searchAiChatProviderConfigs,
  updateAiChatModelManage,
  updateAiChatProviderConfig
} from '../../../../../../../api/aiChat'
import { createModelForm, createProviderForm, enabledOptions, pageSizeOptions } from '../data'

export function useAiPage() {
  const activeTab = ref('provider')
  const loading = reactive({
    provider: false,
    model: false,
    providerSaving: false,
    modelSaving: false
  })

  const providerFilters = reactive({
    providerCode: '',
    enabled: ''
  })

  const modelFilters = reactive({
    keyword: '',
    providerCode: '',
    enabled: ''
  })

  const providerPagination = reactive({
    page: 1,
    size: 10,
    total: 0
  })

  const modelPagination = reactive({
    page: 1,
    size: 10,
    total: 0
  })

  const providerList = ref([])
  const modelList = ref([])
  const providerOptions = ref([])

  const providerDialogVisible = ref(false)
  const providerDialogMode = ref('create')
  const providerError = ref('')
  const providerForm = reactive(createProviderForm())

  const modelDialogVisible = ref(false)
  const modelDialogMode = ref('create')
  const modelError = ref('')
  const modelForm = reactive(createModelForm())

  const notice = reactive({
    type: 'success',
    text: ''
  })

  let noticeTimer = null

  const currentStats = computed(() => {
    if (activeTab.value === 'provider') {
      const enabledCount = providerList.value.filter((item) => item.enabled).length
      return [
        { label: '总记录', value: providerPagination.total },
        { label: '当前页', value: providerList.value.length },
        { label: '启用中', value: enabledCount },
        { label: '已停用', value: Math.max(providerList.value.length - enabledCount, 0) }
      ]
    }

    const enabledCount = modelList.value.filter((item) => item.enabled).length
    const credentialCount = modelList.value.filter((item) => item.credentialCode).length
    return [
      { label: '总记录', value: modelPagination.total },
      { label: '当前页', value: modelList.value.length },
      { label: '启用中', value: enabledCount },
      { label: '已绑定凭证', value: credentialCount }
    ]
  })

  const currentTotal = computed(() => (activeTab.value === 'provider' ? providerPagination.total : modelPagination.total))

  const currentPage = computed({
    get: () => (activeTab.value === 'provider' ? providerPagination.page : modelPagination.page),
    set: (value) => {
      if (activeTab.value === 'provider') {
        providerPagination.page = value
      } else {
        modelPagination.page = value
      }
    }
  })

  const currentSize = computed({
    get: () => (activeTab.value === 'provider' ? providerPagination.size : modelPagination.size),
    set: (value) => {
      if (activeTab.value === 'provider') {
        providerPagination.size = value
      } else {
        modelPagination.size = value
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
    await Promise.all([loadActiveTab(), ensureProviderOptions()])
  })

  onMounted(async () => {
    await Promise.all([loadProviderPage(), loadModelPage(), ensureProviderOptions()])
  })

  function buildProviderQuery() {
    return {
      page: providerPagination.page,
      size: providerPagination.size,
      providerCode: providerFilters.providerCode || undefined,
      enabled: parseBooleanFilter(providerFilters.enabled)
    }
  }

  function buildModelQuery() {
    return {
      page: modelPagination.page,
      size: modelPagination.size,
      keyword: modelFilters.keyword || undefined,
      providerCode: modelFilters.providerCode || undefined,
      enabled: parseBooleanFilter(modelFilters.enabled)
    }
  }

  function parseBooleanFilter(value) {
    if (value === '' || value === null || value === undefined) {
      return undefined
    }
    return value === 'true'
  }

  async function loadActiveTab() {
    if (activeTab.value === 'provider') {
      await loadProviderPage()
    } else {
      await loadModelPage()
    }
  }

  async function loadProviderPage() {
    loading.provider = true
    try {
      const payload = unwrapPayload(await searchAiChatProviderConfigs(buildProviderQuery()))
      providerList.value = payload?.list ?? []
      providerPagination.total = resolvePageTotal(payload?.pageInfo?.total, providerList.value.length)
    } catch (error) {
      showNotice(error.message || 'Provider 列表加载失败', 'error')
    } finally {
      loading.provider = false
    }
  }

  async function loadModelPage() {
    loading.model = true
    try {
      const payload = unwrapPayload(await searchAiChatModelManages(buildModelQuery()))
      modelList.value = payload?.list ?? []
      modelPagination.total = resolvePageTotal(payload?.pageInfo?.total, modelList.value.length)
    } catch (error) {
      showNotice(error.message || 'Model 列表加载失败', 'error')
    } finally {
      loading.model = false
    }
  }

  async function ensureProviderOptions() {
    try {
      const payload = unwrapPayload(
        await searchAiChatProviderConfigs({
          page: 1,
          size: 200
        })
      )
      providerOptions.value = payload?.list ?? []
    } catch (error) {
      showNotice(error.message || 'Provider 选项加载失败', 'error')
    }
  }

  function openProviderCreate() {
    providerDialogMode.value = 'create'
    providerError.value = ''
    Object.assign(providerForm, createProviderForm())
    providerDialogVisible.value = true
  }

  function openProviderEdit(row) {
    providerDialogMode.value = 'edit'
    providerError.value = ''
    Object.assign(providerForm, createProviderForm(), JSON.parse(JSON.stringify(row)))
    providerDialogVisible.value = true
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
        maxContextTokens: detail?.maxContextTokens ?? '',
        maxOutputTokens: detail?.maxOutputTokens ?? '',
        priority: detail?.priority ?? 100,
        keyVersion: detail?.keyVersion ?? 1,
        apiKeyInput: '',
        expireAt: detail?.expireAt ? formatDateTimeInput(detail.expireAt) : ''
      })
      modelDialogVisible.value = true
    } catch (error) {
      showNotice(error.message || 'Model 详情加载失败', 'error')
    }
  }

  function validateProviderForm() {
    if (!providerForm.providerCode.trim()) {
      providerError.value = '请输入 Provider 编码'
      return false
    }
    if (!providerForm.providerName.trim()) {
      providerError.value = '请输入 Provider 名称'
      return false
    }
    if (!providerForm.baseUrl.trim()) {
      providerError.value = '请输入基础地址'
      return false
    }
    providerError.value = ''
    return true
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
    if (!modelForm.providerCode) {
      modelError.value = '请选择所属 Provider'
      return false
    }
    if (!modelForm.apiModel.trim()) {
      modelError.value = '请输入 Provider 模型标识'
      return false
    }
    if (!modelForm.credentialCode.trim()) {
      modelError.value = '请输入凭证编码'
      return false
    }
    if (modelDialogMode.value === 'create' && !modelForm.apiKeyInput.trim()) {
      modelError.value = '新增模型时必须填写 API Key'
      return false
    }
    modelError.value = ''
    return true
  }

  async function submitProviderForm() {
    if (!validateProviderForm()) {
      return
    }

    loading.providerSaving = true
    try {
      const payload = {
        providerCode: providerForm.providerCode.trim(),
        providerName: providerForm.providerName.trim(),
        baseUrl: providerForm.baseUrl.trim(),
        connectTimeoutMs: normalizeNumber(providerForm.connectTimeoutMs),
        readTimeoutMs: normalizeNumber(providerForm.readTimeoutMs),
        enabled: providerForm.enabled,
        remark: providerForm.remark.trim()
      }

      if (providerDialogMode.value === 'create') {
        await createAiChatProviderConfig(payload)
        showNotice('Provider 新增成功')
      } else {
        await updateAiChatProviderConfig(providerForm.id, payload)
        showNotice('Provider 更新成功')
      }

      providerDialogVisible.value = false
      await Promise.all([loadProviderPage(), ensureProviderOptions()])
    } catch (error) {
      providerError.value = error.message || 'Provider 保存失败'
    } finally {
      loading.providerSaving = false
    }
  }

  async function submitModelForm() {
    if (!validateModelForm()) {
      return
    }

    loading.modelSaving = true
    try {
      const payload = {
        modelCode: modelForm.modelCode.trim(),
        modelName: modelForm.modelName.trim(),
        providerCode: modelForm.providerCode,
        apiModel: modelForm.apiModel.trim(),
        capabilityTags: modelForm.capabilityTags.trim(),
        maxContextTokens: normalizeNumber(modelForm.maxContextTokens),
        maxOutputTokens: normalizeNumber(modelForm.maxOutputTokens),
        temperatureEnabled: Number(modelForm.temperatureEnabled),
        enabled: modelForm.enabled,
        priority: normalizeNumber(modelForm.priority),
        remark: modelForm.remark.trim(),
        credentialId: modelForm.credentialId || undefined,
        credentialCode: modelForm.credentialCode.trim(),
        apiKeyInput: modelForm.apiKeyInput.trim() || undefined,
        apiKeyMasked: modelForm.apiKeyMasked || undefined,
        keyVersion: normalizeNumber(modelForm.keyVersion),
        credentialEnabled: modelForm.credentialEnabled,
        expireAt: modelForm.expireAt ? formatDateTimePayload(modelForm.expireAt) : null,
        credentialRemark: modelForm.credentialRemark.trim()
      }

      if (modelDialogMode.value === 'create') {
        await createAiChatModelManage(payload)
        showNotice('Model 新增成功')
      } else {
        await updateAiChatModelManage(modelForm.id, payload)
        showNotice('Model 更新成功')
      }

      modelDialogVisible.value = false
      await loadModelPage()
    } catch (error) {
      modelError.value = error.message || 'Model 保存失败'
    } finally {
      loading.modelSaving = false
    }
  }

  async function toggleProviderStatus(row) {
    const nextValue = !row.enabled
    try {
      await editAiChatProviderConfig(row.id, { enabled: nextValue })
      row.enabled = nextValue
      showNotice(`Provider 已${nextValue ? '启用' : '停用'}`)
    } catch (error) {
      showNotice(error.message || 'Provider 状态更新失败', 'error')
    }
  }

  async function toggleModelStatus(row) {
    const nextValue = !row.enabled
    try {
      await editAiChatModelManage(row.id, { enabled: nextValue })
      row.enabled = nextValue
      showNotice(`Model 已${nextValue ? '启用' : '停用'}`)
    } catch (error) {
      showNotice(error.message || 'Model 状态更新失败', 'error')
    }
  }

  async function confirmDeleteProvider(row) {
    if (!window.confirm(`确认删除 Provider「${row.providerName}」吗？`)) {
      return
    }
    try {
      await deleteAiChatProviderConfig(row.id)
      showNotice('Provider 已删除')
      await Promise.all([loadProviderPage(), ensureProviderOptions()])
    } catch (error) {
      showNotice(error.message || 'Provider 删除失败', 'error')
    }
  }

  async function confirmDeleteModel(row) {
    if (!window.confirm(`确认删除 Model「${row.modelName}」吗？关联凭证也会一起删除。`)) {
      return
    }
    try {
      await deleteAiChatModelManage(row.id)
      showNotice('Model 已删除')
      await loadModelPage()
    } catch (error) {
      showNotice(error.message || 'Model 删除失败', 'error')
    }
  }

  function resetProviderFilters() {
    providerFilters.providerCode = ''
    providerFilters.enabled = ''
    providerPagination.page = 1
    loadProviderPage()
  }

  function resetModelFilters() {
    modelFilters.keyword = ''
    modelFilters.providerCode = ''
    modelFilters.enabled = ''
    modelPagination.page = 1
    loadModelPage()
  }

  function handleSearch() {
    if (activeTab.value === 'provider') {
      providerPagination.page = 1
      loadProviderPage()
    } else {
      modelPagination.page = 1
      loadModelPage()
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
    if (activeTab.value === 'provider') {
      openProviderCreate()
    } else {
      openModelCreate()
    }
  }

  function normalizeNumber(value) {
    if (value === '' || value === null || value === undefined) {
      return null
    }
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
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

  function formatDateTimeInput(value) {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) {
      return ''
    }
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(
      date.getMinutes()
    )}`
  }

  function formatDateTimePayload(value) {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) {
      return null
    }
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
      date.getMinutes()
    )}:${pad(date.getSeconds())}`
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

  function showNotice(text, type = 'success') {
    notice.type = type
    notice.text = text
    if (noticeTimer) {
      window.clearTimeout(noticeTimer)
    }
    noticeTimer = window.setTimeout(() => {
      notice.text = ''
    }, 2400)
  }

  return {
    activeTab,
    loading,
    providerFilters,
    modelFilters,
    providerPagination,
    modelPagination,
    providerList,
    modelList,
    providerOptions,
    providerDialogVisible,
    providerDialogMode,
    providerError,
    providerForm,
    modelDialogVisible,
    modelDialogMode,
    modelError,
    modelForm,
    enabledOptions,
    pageSizeOptions,
    notice,
    currentStats,
    currentTotal,
    currentPage,
    currentSize,
    pageSummary,
    totalPages,
    openProviderCreate,
    openProviderEdit,
    openModelCreate,
    openModelEdit,
    submitProviderForm,
    submitModelForm,
    toggleProviderStatus,
    toggleModelStatus,
    confirmDeleteProvider,
    confirmDeleteModel,
    resetProviderFilters,
    resetModelFilters,
    handleSearch,
    handlePageChange,
    handlePageSizeChange,
    openCreateByTab,
    formatDateTime,
    tagList
  }
}
