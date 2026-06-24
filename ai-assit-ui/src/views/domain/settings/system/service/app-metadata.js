import { computed, onMounted, reactive, ref } from 'vue'
import {
  createRenderPage,
  deleteRenderPage,
  listRenderPageCategoryTree,
  searchRenderPages,
  updateRenderPage
} from '../../../../../api/renderPage'
import { showPopup } from '../../../../../utils/popup'

const STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: '草稿', value: 1 },
  { label: '已发布', value: 2 },
  { label: '已停用', value: 3 }
]

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]

export function useAppMetadataPage() {
  const loading = ref(false)
  const saving = ref(false)
  const errorMessage = ref('')
  const dialogError = ref('')
  const dialogVisible = ref(false)
  const dialogMode = ref('create')
  const pageList = ref([])
  const categoryOptions = ref([])
  const keyword = ref('')
  const filters = reactive({
    categoryCode: '',
    status: ''
  })
  const pagination = reactive({
    page: 1,
    size: 10,
    total: 0
  })
  const form = reactive(createEmptyForm())

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
    loadCategoryOptions()
    loadPageList()
  })

  async function loadCategoryOptions() {
    try {
      const payload = unwrapPayload(await listRenderPageCategoryTree({
        enabled: true,
        size: 500
      }))
      categoryOptions.value = flattenCategoryTree(payload)
    } catch (error) {
      categoryOptions.value = []
      showPopup.warning(error.message || '页面分类加载失败')
    }
  }

  async function loadPageList() {
    loading.value = true
    errorMessage.value = ''
    try {
      const payload = unwrapPayload(await searchRenderPages({
        page: pagination.page,
        size: pagination.size,
        keyword: emptyToUndefined(keyword.value),
        categoryCode: filters.categoryCode || undefined,
        status: normalizeStatusFilter(filters.status)
      }))
      const nextList = (payload?.list ?? []).map(mapPageItem)
      pageList.value = nextList
      pagination.total = resolvePageTotal(payload?.pageInfo?.total, nextList.length)
    } catch (error) {
      errorMessage.value = error.message || '应用元数据列表加载失败'
      pageList.value = []
      pagination.total = 0
    } finally {
      loading.value = false
    }
  }

  function openCreateDialog() {
    dialogMode.value = 'create'
    dialogError.value = ''
    Object.assign(form, createEmptyForm())
    dialogVisible.value = true
  }

  function openEditDialog(item) {
    if (!item) {
      showPopup.warning('当前没有可编辑的页面')
      return
    }
    dialogMode.value = 'edit'
    dialogError.value = ''
    Object.assign(form, createEmptyForm(), {
      id: item.id,
      code: item.raw?.code || '',
      name: item.raw?.name || '',
      categoryCode: item.raw?.categoryCode || '',
      status: Number(item.raw?.status ?? 1),
      content: item.raw?.content || '{}'
    })
    dialogVisible.value = true
  }

  function closeDialog() {
    if (saving.value) {
      return
    }
    dialogVisible.value = false
    dialogError.value = ''
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
        code: form.code.trim(),
        name: form.name.trim(),
        categoryCode: emptyToUndefined(form.categoryCode),
        status: Number(form.status),
        content: normalizeContent(form.content)
      }
      if (dialogMode.value === 'create') {
        await createRenderPage(payload)
        showPopup.success('应用元数据新增成功')
      } else {
        await updateRenderPage(form.id, payload)
        showPopup.success('应用元数据更新成功')
      }
      dialogVisible.value = false
      await loadPageList()
    } catch (error) {
      dialogError.value = error.message || '应用元数据保存失败'
    } finally {
      saving.value = false
    }
  }

  async function confirmDelete(item) {
    if (!item) {
      showPopup.warning('当前没有可删除的页面')
      return
    }
    if (!window.confirm(`确认删除页面「${item.name}」吗？`)) {
      return
    }
    try {
      await deleteRenderPage(item.id)
      showPopup.success('应用元数据已删除')
      if (pageList.value.length === 1 && pagination.page > 1) {
        pagination.page -= 1
      }
      await loadPageList()
    } catch (error) {
      showPopup.error(error.message || '应用元数据删除失败')
    }
  }

  async function handleSearch() {
    pagination.page = 1
    await loadPageList()
  }

  async function resetFilters() {
    keyword.value = ''
    filters.categoryCode = ''
    filters.status = ''
    pagination.page = 1
    await loadPageList()
  }

  async function handlePageChange(page) {
    if (page < 1 || page > totalPages.value || page === pagination.page) {
      return
    }
    pagination.page = page
    await loadPageList()
  }

  async function handlePageSizeChange(event) {
    pagination.size = Number(event?.target?.value || pagination.size)
    pagination.page = 1
    await loadPageList()
  }

  function formatDateTime(value) {
    if (!value) {
      return '-'
    }
    const text = String(value).trim()
    return text ? text.replace('T', ' ').slice(0, 19) : '-'
  }

  function resolveCategoryLabel(categoryCode) {
    if (!categoryCode) {
      return '未分类'
    }
    return categoryOptions.value.find(item => item.value === categoryCode)?.label || categoryCode
  }

  function resolveStatusLabel(status) {
    return STATUS_OPTIONS.find(item => Number(item.value) === Number(status))?.label || `状态 ${status}`
  }

  function resolveStatusClass(status) {
    const code = Number(status)
    if (code === 2) {
      return 'is-published'
    }
    if (code === 3) {
      return 'is-disabled'
    }
    return 'is-draft'
  }

  function mapPageItem(item) {
    return {
      id: item.id,
      code: item.code || '-',
      name: item.name || item.code || '未命名页面',
      categoryCode: item.categoryCode || '',
      categoryLabel: resolveCategoryLabel(item.categoryCode),
      status: Number(item.status ?? 1),
      statusLabel: resolveStatusLabel(item.status),
      contentPreview: summarizeContent(item.content),
      updateTime: item.updateTime || item.createTime || '',
      raw: item
    }
  }

  function validateForm() {
    if (!form.code.trim()) {
      return '请输入页面编码'
    }
    if (!form.name.trim()) {
      return '请输入页面名称'
    }
    if (!form.status) {
      return '请选择页面状态'
    }
    const normalized = normalizeContent(form.content)
    if (!normalized) {
      return '请输入页面内容'
    }
    try {
      JSON.parse(normalized)
    } catch {
      return '页面内容必须是合法 JSON'
    }
    return ''
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
    pageList,
    categoryOptions,
    statusOptions: STATUS_OPTIONS,
    pageSizeOptions: PAGE_SIZE_OPTIONS,
    pagination,
    pageSummary,
    totalPages,
    loadPageList,
    handleSearch,
    resetFilters,
    handlePageChange,
    handlePageSizeChange,
    openCreateDialog,
    openEditDialog,
    closeDialog,
    submitForm,
    confirmDelete,
    formatDateTime,
    resolveStatusClass
  }
}

function createEmptyForm() {
  return {
    id: null,
    code: '',
    name: '',
    categoryCode: '',
    status: 1,
    content: '{\n  \n}'
  }
}

function unwrapPayload(response) {
  return response?.data ?? response
}

function resolvePageTotal(total, fallback) {
  const numericTotal = Number(total)
  return Number.isFinite(numericTotal) ? numericTotal : fallback
}

function emptyToUndefined(value) {
  const normalized = String(value ?? '').trim()
  return normalized ? normalized : undefined
}

function normalizeStatusFilter(value) {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  return Number(value)
}

function normalizeContent(value) {
  return String(value ?? '').trim()
}

function summarizeContent(value) {
  const normalized = normalizeContent(value)
  if (!normalized) {
    return '{}'
  }
  return normalized.length > 120 ? `${normalized.slice(0, 120)}...` : normalized
}

function flattenCategoryTree(nodes, level = 0, result = []) {
  if (!Array.isArray(nodes)) {
    return result
  }

  nodes.forEach((node) => {
    if (!node?.code) {
      return
    }
    const indent = level > 0 ? `${'  '.repeat(level)}└ ` : ''
    result.push({
      value: node.code,
      label: `${indent}${node.name || node.code}`
    })
    flattenCategoryTree(node.children, level + 1, result)
  })
  return result
}
