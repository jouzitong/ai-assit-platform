import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  createAiChatModelManage,
  createAiChatProviderConfig,
  createAiKbStore,
  deleteAiChatModelManage,
  deleteAiChatProviderConfig,
  deleteAiKbStore,
  editAiChatModelManage,
  editAiChatProviderConfig,
  editAiKbStore,
  getAiChatModelManage,
  searchAiChatModelManages,
  searchAiChatProviderConfigs,
  searchAiKbStores,
  updateAiChatModelManage,
  updateAiChatProviderConfig,
  updateAiKbStore
} from '../../../../../api/aiChat'
import {
  createKbForm,
  createModelForm,
  createProviderForm,
  enabledOptions,
  kbBizTypeOptions,
  kbStatusOptions,
  pageSizeOptions
} from '../data/ai'
import { showPopup } from '../../../../../utils/popup'

export function useAiPage() {
  const activeTab = ref('provider')
  const loading = reactive({
    provider: false,
    model: false,
    kb: false,
    providerSaving: false,
    modelSaving: false,
    kbSaving: false
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

  const kbFilters = reactive({
    keyword: '',
    providerCode: '',
    status: '',
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

  const kbPagination = reactive({
    page: 1,
    size: 10,
    total: 0
  })

  const providerList = ref([])
  const modelList = ref([])
  const kbList = ref([])
  const providerOptions = ref([])

  const providerDialogVisible = ref(false)
  const providerDialogMode = ref('create')
  const providerError = ref('')
  const providerForm = reactive(createProviderForm())

  const modelDialogVisible = ref(false)
  const modelDialogMode = ref('create')
  const modelError = ref('')
  const modelForm = reactive(createModelForm())

  const kbDialogVisible = ref(false)
  const kbDialogMode = ref('create')
  const kbError = ref('')
  const kbForm = reactive(createKbForm())

  const currentTotal = computed(() => {
    if (activeTab.value === 'provider') {
      return providerPagination.total
    }
    if (activeTab.value === 'model') {
      return modelPagination.total
    }
    return kbPagination.total
  })

  const currentPage = computed({
    get: () => {
      if (activeTab.value === 'provider') {
        return providerPagination.page
      }
      if (activeTab.value === 'model') {
        return modelPagination.page
      }
      return kbPagination.page
    },
    set: (value) => {
      if (activeTab.value === 'provider') {
        providerPagination.page = value
      } else if (activeTab.value === 'model') {
        modelPagination.page = value
      } else {
        kbPagination.page = value
      }
    }
  })

  const currentSize = computed({
    get: () => {
      if (activeTab.value === 'provider') {
        return providerPagination.size
      }
      if (activeTab.value === 'model') {
        return modelPagination.size
      }
      return kbPagination.size
    },
    set: (value) => {
      if (activeTab.value === 'provider') {
        providerPagination.size = value
      } else if (activeTab.value === 'model') {
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
      await Promise.all([loadKbPage(), ensureProviderOptions()])
      return
    }
    await Promise.all([loadActiveTab(), ensureProviderOptions()])
  })

  onMounted(async () => {
    await Promise.all([loadProviderPage(), loadModelPage(), loadKbPage(), ensureProviderOptions()])
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

  function buildKbQuery() {
    return {
      page: kbPagination.page,
      size: kbPagination.size,
      keyword: kbFilters.keyword || undefined,
      providerCode: kbFilters.providerCode || undefined,
      status: kbFilters.status || undefined,
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
    if (activeTab.value === 'provider') {
      await loadProviderPage()
    } else if (activeTab.value === 'model') {
      await loadModelPage()
    } else {
      await loadKbPage()
    }
  }

  async function loadProviderPage() {
    loading.provider = true
    try {
      const payload = unwrapPayload(await searchAiChatProviderConfigs(buildProviderQuery()))
      providerList.value = payload?.list ?? []
      providerPagination.total = resolvePageTotal(payload?.pageInfo?.total, providerList.value.length)
    } catch (error) {
      showPopup.error(error.message || 'Provider 列表加载失败')
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
      showPopup.error(error.message || 'Model 列表加载失败')
    } finally {
      loading.model = false
    }
  }

  async function loadKbPage() {
    loading.kb = true
    try {
      const payload = unwrapPayload(await searchAiKbStores(buildKbQuery()))
      kbList.value = payload?.list ?? []
      kbPagination.total = resolvePageTotal(payload?.pageInfo?.total, kbList.value.length)
    } catch (error) {
      showPopup.error(error.message || 'KB 列表加载失败')
    } finally {
      loading.kb = false
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
      showPopup.error(error.message || 'Provider 选项加载失败')
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
      currentVersionNo: row.currentVersionNo ?? ''
    })
    kbDialogVisible.value = true
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

  function validateKbForm() {
    if (!kbForm.kbCode.trim()) {
      kbError.value = '请输入 KB 编码'
      return false
    }
    if (!kbForm.kbName.trim()) {
      kbError.value = '请输入 KB 名称'
      return false
    }
    if (!kbForm.bizType) {
      kbError.value = '请选择业务类型'
      return false
    }
    if (!kbForm.bizKey.trim()) {
      kbError.value = '请输入业务键'
      return false
    }
    kbError.value = ''
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
        showPopup.success('Provider 新增成功')
      } else {
        await updateAiChatProviderConfig(providerForm.id, payload)
        showPopup.success('Provider 更新成功')
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
      const payload = {
        kbCode: kbForm.kbCode.trim(),
        kbName: kbForm.kbName.trim(),
        bizType: kbForm.bizType,
        bizKey: kbForm.bizKey.trim(),
        providerCode: kbForm.providerCode || null,
        providerKbId: kbForm.providerKbId.trim() || null,
        currentVersionNo: normalizeNumber(kbForm.currentVersionNo),
        status: kbForm.status,
        enabled: kbForm.enabled,
        remark: kbForm.remark.trim()
      }

      if (kbDialogMode.value === 'create') {
        await createAiKbStore(payload)
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

  async function toggleProviderStatus(row) {
    const nextValue = !row.enabled
    try {
      await editAiChatProviderConfig(row.id, { enabled: nextValue })
      row.enabled = nextValue
      showPopup.success(`Provider 已${nextValue ? '启用' : '停用'}`)
    } catch (error) {
      showPopup.error(error.message || 'Provider 状态更新失败')
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
    const nextValue = !row.enabled
    try {
      await editAiKbStore(row.id, { enabled: nextValue })
      row.enabled = nextValue
      if (!nextValue && row.status !== 'DISABLED') {
        row.status = 'DISABLED'
      }
      showPopup.success(`KB 已${nextValue ? '启用' : '停用'}`)
    } catch (error) {
      showPopup.error(error.message || 'KB 状态更新失败')
    }
  }

  async function confirmDeleteProvider(row) {
    if (!window.confirm(`确认删除 Provider「${row.providerName}」吗？`)) {
      return
    }
    try {
      await deleteAiChatProviderConfig(row.id)
      showPopup.success('Provider 已删除')
      await Promise.all([loadProviderPage(), ensureProviderOptions()])
    } catch (error) {
      showPopup.error(error.message || 'Provider 删除失败')
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

  function resetKbFilters() {
    kbFilters.keyword = ''
    kbFilters.providerCode = ''
    kbFilters.status = ''
    kbFilters.enabled = ''
    kbPagination.page = 1
    loadKbPage()
  }

  function handleSearch() {
    if (activeTab.value === 'provider') {
      providerPagination.page = 1
      loadProviderPage()
    } else if (activeTab.value === 'model') {
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
    if (activeTab.value === 'provider') {
      openProviderCreate()
    } else if (activeTab.value === 'model') {
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

  return {
    activeTab,
    loading,
    providerFilters,
    modelFilters,
    kbFilters,
    providerList,
    modelList,
    kbList,
    providerOptions,
    providerDialogVisible,
    providerDialogMode,
    providerError,
    providerForm,
    modelDialogVisible,
    modelDialogMode,
    modelError,
    modelForm,
    kbDialogVisible,
    kbDialogMode,
    kbError,
    kbForm,
    enabledOptions,
    kbBizTypeOptions,
    kbStatusOptions,
    pageSizeOptions,
    currentPage,
    currentSize,
    pageSummary,
    totalPages,
    openProviderEdit,
    openModelEdit,
    openKbEdit,
    submitProviderForm,
    submitModelForm,
    submitKbForm,
    toggleProviderStatus,
    toggleModelStatus,
    toggleKbStatus,
    confirmDeleteProvider,
    confirmDeleteModel,
    confirmDeleteKb,
    resetProviderFilters,
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
