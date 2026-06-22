import { computed, onMounted, reactive, ref } from 'vue'
import {
  createSystemSetting,
  deleteSystemSetting,
  editSystemSetting,
  searchSystemSettings,
  updateSystemSetting
} from '../../../../../api/systemSettings'
import { showPopup } from '../../../../../utils/popup'
import { createSystemSettingForm, enabledOptions, pageSizeOptions, valueTypeOptions } from '../data/params'

export function useSystemParamsPage() {
  const loading = ref(false)
  const saving = ref(false)
  const errorMessage = ref('')
  const dialogError = ref('')
  const dialogVisible = ref(false)
  const dialogMode = ref('create')
  const keyword = ref('')
  const settingList = ref([])
  const filters = reactive({
    valueType: '',
    enabled: ''
  })
  const pagination = reactive({
    page: 1,
    size: 10,
    total: 0
  })
  const form = reactive(createSystemSettingForm())

  const pageSummary = computed(() => {
    if (!pagination.total) {
      return '第 0 - 0 条，共 0 条'
    }
    const start = (pagination.page - 1) * pagination.size + 1
    const end = Math.min(pagination.page * pagination.size, pagination.total)
    return `第 ${start} - ${end} 条，共 ${pagination.total} 条`
  })

  const totalPages = computed(() => Math.max(1, Math.ceil(pagination.total / pagination.size)))

  onMounted(() => {
    loadSystemSettings()
  })

  async function loadSystemSettings() {
    loading.value = true
    errorMessage.value = ''
    try {
      const payload = unwrapPayload(
        await searchSystemSettings({
          page: pagination.page,
          size: pagination.size,
          keyword: emptyToUndefined(keyword.value),
          valueType: filters.valueType || undefined,
          enabled: parseBooleanFilter(filters.enabled)
        })
      )
      const nextList = (payload?.list ?? []).map(mapSettingItem)
      settingList.value = nextList
      pagination.total = resolvePageTotal(payload?.pageInfo?.total, nextList.length)
    } catch (error) {
      errorMessage.value = error.message || '系统配置列表加载失败'
      settingList.value = []
    } finally {
      loading.value = false
    }
  }

  function mapSettingItem(item) {
    return {
      id: item.id,
      settingKey: item.settingKey || '',
      description: item.description || '暂无说明',
      settingValue: item.settingValue ?? '',
      valueType: item.valueType || 'STRING',
      valueTypeLabel: formatValueType(item.valueType),
      enabled: item.enabled !== false,
      statusLabel: item.enabled === false ? '已停用' : '已启用',
      previewValue: formatPreviewValue(item.settingValue, item.valueType),
      updateTime: item.updateTime || item.createTime || '',
      lastModifiedBy: item.lastModifiedBy || item.updatedBy || item.createdBy || '-',
      raw: item
    }
  }

  function openCreateDialog() {
    dialogMode.value = 'create'
    dialogError.value = ''
    Object.assign(form, createSystemSettingForm())
    dialogVisible.value = true
  }

  function openEditDialog(item) {
    if (!item) {
      showPopup.warning('当前没有可编辑的系统配置')
      return
    }
    dialogMode.value = 'edit'
    dialogError.value = ''
    Object.assign(form, createSystemSettingForm(), {
      id: item.id,
      settingKey: item.settingKey,
      description: item.raw?.description || '',
      settingValue: item.raw?.settingValue ?? '',
      valueType: item.raw?.valueType || 'STRING',
      enabled: item.raw?.enabled !== false
    })
    dialogVisible.value = true
  }

  function closeDialog() {
    dialogVisible.value = false
  }

  async function submitForm() {
    const validationError = validateForm()
    if (validationError) {
      dialogError.value = validationError
      return
    }

    dialogError.value = ''
    saving.value = true
    try {
      const payload = {
        settingKey: form.settingKey.trim(),
        description: emptyToUndefined(form.description),
        settingValue: form.settingValue,
        valueType: form.valueType,
        enabled: form.enabled
      }
      if (dialogMode.value === 'create') {
        await createSystemSetting(payload)
        showPopup.success('系统配置新增成功')
      } else {
        await updateSystemSetting(form.id, payload)
        showPopup.success('系统配置更新成功')
      }
      dialogVisible.value = false
      await loadSystemSettings()
    } catch (error) {
      dialogError.value = error.message || '系统配置保存失败'
    } finally {
      saving.value = false
    }
  }

  async function toggleSettingStatus(item) {
    if (!item) {
      showPopup.warning('当前没有可切换状态的配置')
      return
    }
    const nextValue = !item.enabled
    try {
      await editSystemSetting(item.id, { enabled: nextValue })
      item.enabled = nextValue
      item.statusLabel = nextValue ? '已启用' : '已停用'
      if (item.raw) {
        item.raw.enabled = nextValue
      }
      showPopup.success(`系统配置已${nextValue ? '启用' : '停用'}`)
    } catch (error) {
      showPopup.error(error.message || '系统配置状态更新失败')
    }
  }

  async function confirmDelete(item) {
    if (!item) {
      showPopup.warning('当前没有可删除的配置')
      return
    }
    if (!window.confirm(`确认删除系统配置「${item.settingKey}」吗？`)) {
      return
    }
    try {
      await deleteSystemSetting(item.id)
      showPopup.success('系统配置已删除')
      await loadSystemSettings()
    } catch (error) {
      showPopup.error(error.message || '系统配置删除失败')
    }
  }

  async function handleSearch() {
    pagination.page = 1
    await loadSystemSettings()
  }

  async function resetFilters() {
    keyword.value = ''
    filters.valueType = ''
    filters.enabled = ''
    pagination.page = 1
    await loadSystemSettings()
  }

  async function handlePageChange(page) {
    if (page < 1 || page > totalPages.value || page === pagination.page) {
      return
    }
    pagination.page = page
    await loadSystemSettings()
  }

  async function handlePageSizeChange(event) {
    pagination.size = Number(event?.target?.value || pagination.size)
    pagination.page = 1
    await loadSystemSettings()
  }

  function validateForm() {
    if (!form.settingKey.trim()) {
      return '请输入配置 Key'
    }
    if (!form.valueType) {
      return '请选择值类型'
    }
    if (form.valueType === 'JSON' && form.settingValue.trim()) {
      try {
        JSON.parse(form.settingValue)
      } catch {
        return 'JSON 类型的配置值必须是合法 JSON'
      }
    }
    if (form.valueType === 'BOOLEAN') {
      const normalized = form.settingValue.trim().toLowerCase()
      if (normalized && normalized !== 'true' && normalized !== 'false') {
        return 'BOOLEAN 类型的配置值只能是 true 或 false'
      }
    }
    return ''
  }

  function resolvePageTotal(total, fallback) {
    const numericTotal = Number(total)
    return Number.isFinite(numericTotal) ? numericTotal : fallback
  }

  function unwrapPayload(response) {
    return response?.data ?? response
  }

  function parseBooleanFilter(value) {
    if (value === '' || value === null || value === undefined) {
      return undefined
    }
    return value === 'true'
  }

  function emptyToUndefined(value) {
    const normalized = String(value ?? '').trim()
    return normalized ? normalized : undefined
  }

  function formatValueType(value) {
    return valueTypeOptions.find(item => item.value === value)?.label || value || '-'
  }

  function formatPreviewValue(value, type) {
    if (value === null || value === undefined || value === '') {
      return '未配置'
    }
    if (type === 'PASSWORD') {
      return '••••••••'
    }
    if (type === 'BOOLEAN') {
      return String(value).toLowerCase() === 'true' ? 'true / 开启' : 'false / 关闭'
    }
    const text = String(value)
    return text.length > 72 ? `${text.slice(0, 72)}...` : text
  }

  function formatDateTime(value) {
    if (!value) {
      return '-'
    }
    const text = String(value)
    return text.replace('T', ' ').slice(0, 19)
  }

  return {
    loading,
    saving,
    errorMessage,
    dialogError,
    dialogVisible,
    dialogMode,
    keyword,
    filters,
    form,
    settingList,
    valueTypeOptions,
    enabledOptions,
    pageSizeOptions,
    pagination,
    pageSummary,
    totalPages,
    openCreateDialog,
    openEditDialog,
    closeDialog,
    submitForm,
    toggleSettingStatus,
    confirmDelete,
    loadSystemSettings,
    handleSearch,
    resetFilters,
    handlePageChange,
    handlePageSizeChange,
    formatDateTime
  }
}
