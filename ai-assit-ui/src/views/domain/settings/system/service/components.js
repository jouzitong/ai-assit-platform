import { computed, onMounted, ref } from 'vue'
import {
  createRenderComponent,
  deleteRenderComponent,
  getRenderComponent,
  getRenderComponentSummary,
  listRenderComponentCategories,
  searchRenderComponents,
  updateRenderComponent,
  updateRenderComponentStatus
} from '../../../../../api/renderComponent'
import { showPopup } from '../../../../../utils/popup'

const STATUS = {
  DRAFT: 1,
  PUBLISHED: 2,
  DISABLED: 3
}

const STATUS_OPTIONS = [
  { code: '', name: '全部状态' },
  { code: STATUS.DRAFT, name: '草稿' },
  { code: STATUS.PUBLISHED, name: '已发布' },
  { code: STATUS.DISABLED, name: '已停用' }
]

export function useRenderComponentsPage() {
  const loading = ref(false)
  const actionLoading = ref(false)
  const errorMessage = ref('')
  const page = ref(1)
  const pageSize = ref(10)
  const total = ref(0)
  const selectedRowId = ref(null)
  const selectedCategory = ref('')
  const keyword = ref('')
  const status = ref('')
  const rows = ref([])
  const categories = ref([])
  const summary = ref({
    total: 0,
    published: 0,
    draft: 0,
    disabled: 0,
    categories: 0
  })
  const advancedVisible = ref(false)
  const sidebarCollapsed = ref(false)
  const sorts = ref([{ key: 'updateTimeLabel', type: 'desc' }])

  const filterSchema = computed(() => ([
    {
      key: 'keyword',
      label: '搜索',
      type: 'input',
      value: keyword.value,
      action: 'keyword-change',
      type_config: { placeholder: '搜索组件 key / 名称 / 分类', width: 240, clearable: true }
    },
    {
      key: 'status',
      label: '状态',
      type: 'select',
      value: status.value,
      action: 'status-change',
      type_config: { width: 150, clearable: true, options: STATUS_OPTIONS }
    },
    {
      key: 'search',
      label: '查询',
      type: 'button',
      action: 'search',
      type_config: { width: 88 }
    },
    {
      key: 'reset',
      label: '重置',
      type: 'button',
      action: 'reset',
      variant: 'ghost',
      type_config: { width: 88 }
    }
  ]))

  const actionItems = computed(() => {
    const selected = selectedRow.value
    return [
      { key: 'create', label: '新建组件', type: 'primary', action: 'create', disabled: actionLoading.value },
      { key: 'edit', label: '编辑', variant: 'ghost', action: 'edit', disabled: !selected || actionLoading.value },
      { key: 'publish', label: '发布', variant: 'ghost', action: 'publish', disabled: !selected || actionLoading.value },
      { key: 'disable', label: '停用', variant: 'ghost', action: 'disable', disabled: !selected || actionLoading.value },
      { key: 'remove', label: '删除', variant: 'danger', action: 'remove', disabled: !selected || actionLoading.value },
      { key: 'refresh', label: '刷新', variant: 'ghost', action: 'refresh', disabled: actionLoading.value }
    ]
  })

  const sidebarItems = computed(() => {
    const totalCount = Number(summary.value.total || 0)
    const categoryItems = categories.value.map(item => ({
      key: item.category || '',
      label: item.label || item.category || '未分类',
      count: Number(item.count || 0)
    }))
    return [
      { key: '', label: '全部组件', count: totalCount },
      ...categoryItems
    ]
  })

  const statsItems = computed(() => ([
    { key: 'total', label: '组件总数', value: summary.value.total || 0 },
    { key: 'published', label: '已发布', value: summary.value.published || 0 },
    { key: 'draft', label: '草稿', value: summary.value.draft || 0 },
    { key: 'disabled', label: '已停用', value: summary.value.disabled || 0 }
  ]))

  const tableColumns = [
    { key: 'name', label: '组件名', width: 20 },
    { key: 'key', label: '组件 Key', width: 18 },
    { key: 'categoryLabel', label: '组件分类', width: 16 },
    { key: 'statusLabel', label: '状态', width: 12, className: 'status-column' },
    { key: 'updateTimeLabel', label: '更新时间', width: 18 },
    { key: 'docPreview', label: '说明摘要', width: 36 }
  ]

  const listConfig = {
    striped: true,
    actionColumns: [
      { key: 'publish', label: '发布' },
      { key: 'disable', label: '停用' },
      { key: 'edit', label: '编辑' },
      { key: 'remove', label: '删除' }
    ],
    sorts_config: {
      header_enable: true,
      sorts: ['name', 'categoryLabel', 'statusLabel', 'updateTimeLabel']
    }
  }

  const summaryItems = computed(() => {
    const items = []
    if (keyword.value) {
      items.push({ key: '搜索', value: keyword.value })
    }
    if (status.value !== '' && status.value !== null && status.value !== undefined) {
      items.push({ key: '状态', value: formatStatusLabel(Number(status.value)) })
    }
    if (selectedCategory.value) {
      const target = categories.value.find(item => item.category === selectedCategory.value)
      items.push({ key: '分类', value: target?.label || selectedCategory.value })
    }
    if (items.length) {
      items.push({ key: '清空全部', value: '', ghost: true, action: 'clear' })
    }
    return items
  })

  const selectedRow = computed(() => rows.value.find(item => item.id === selectedRowId.value) || null)

  onMounted(() => {
    refreshAll()
  })

  async function refreshAll() {
    await Promise.all([loadPage(), loadCategories(), loadSummary()])
  }

  async function loadPage() {
    loading.value = true
    errorMessage.value = ''
    try {
      const payload = await searchRenderComponents({
        page: page.value,
        size: pageSize.value,
        keyword: normalizeText(keyword.value),
        category: normalizeText(selectedCategory.value),
        status: normalizeStatus(status.value)
      })
      const nextRows = Array.isArray(payload?.list) ? payload.list.map(mapRow) : []
      rows.value = nextRows
      total.value = Number(payload?.pageInfo?.total || nextRows.length || 0)
      if (!nextRows.some(item => item.id === selectedRowId.value)) {
        selectedRowId.value = nextRows[0]?.id ?? null
      }
    } catch (error) {
      errorMessage.value = error.message || '组件列表加载失败'
      rows.value = []
      total.value = 0
      selectedRowId.value = null
    } finally {
      loading.value = false
    }
  }

  async function loadCategories() {
    try {
      categories.value = await listRenderComponentCategories()
    } catch (error) {
      categories.value = []
      showPopup.warning(error.message || '组件分类加载失败')
    }
  }

  async function loadSummary() {
    try {
      summary.value = await getRenderComponentSummary()
    } catch (error) {
      summary.value = {
        total: 0,
        published: 0,
        draft: 0,
        disabled: 0,
        categories: 0
      }
      showPopup.warning(error.message || '组件统计加载失败')
    }
  }

  function handleFilterAction(payload) {
    if (payload?.key === 'keyword') {
      keyword.value = payload.value ?? ''
      return
    }
    if (payload?.key === 'status') {
      status.value = payload.value ?? ''
      return
    }
    if (payload?.action === 'search') {
      page.value = 1
      loadPage()
      return
    }
    if (payload?.action === 'reset') {
      resetFilters()
    }
  }

  async function resetFilters() {
    keyword.value = ''
    status.value = ''
    selectedCategory.value = ''
    page.value = 1
    await loadPage()
  }

  function handleSummaryAction(action) {
    if (action === 'clear') {
      resetFilters()
    }
  }

  async function handleSidebarSelect(item) {
    if (!item || item.key === selectedCategory.value) {
      return
    }
    selectedCategory.value = item.key
    page.value = 1
    await loadPage()
  }

  function handleRowClick({ row }) {
    selectedRowId.value = row?.id ?? null
  }

  async function handleActionBar(payload) {
    if (!payload?.action) {
      return
    }
    if (payload.action === 'refresh') {
      await refreshAll()
      return
    }
    if (payload.action === 'create') {
      await openCreateDialog()
      return
    }
    const target = selectedRow.value
    if (!target) {
      showPopup.warning('请先选择一个组件')
      return
    }
    await handleRowAction(target, payload.action)
  }

  async function handleTableAction({ row, actionItem }) {
    if (!row || !actionItem?.key) {
      return
    }
    selectedRowId.value = row.id
    await handleRowAction(row, actionItem.key)
  }

  async function handleRowAction(row, action) {
    if (actionLoading.value) {
      return
    }
    if (action === 'edit') {
      await openEditDialog(row)
      return
    }
    if (action === 'publish') {
      await updateStatus(row, STATUS.PUBLISHED, '发布')
      return
    }
    if (action === 'disable') {
      await updateStatus(row, STATUS.DISABLED, '停用')
      return
    }
    if (action === 'remove') {
      await removeRow(row)
    }
  }

  async function openCreateDialog() {
    const payload = promptComponentPayload({
      key: '',
      name: '',
      category: '',
      docMarkdown: '',
      exampleJson: '{}'
    }, 'create')
    if (!payload) {
      return
    }
    await withAction(async () => {
      await createRenderComponent(payload)
      showPopup.success('组件创建成功')
      page.value = 1
      await refreshAll()
    })
  }

  async function openEditDialog(row) {
    await withAction(async () => {
      const detail = await getRenderComponent(row.id)
      const payload = promptComponentPayload(detail, 'edit')
      if (!payload) {
        return
      }
      await updateRenderComponent(row.id, payload)
      showPopup.success('组件更新成功')
      await refreshAll()
    })
  }

  async function updateStatus(row, nextStatus, verb) {
    if (Number(row.status) === Number(nextStatus)) {
      showPopup.info(`组件当前已经是${verb}状态`)
      return
    }
    await withAction(async () => {
      await updateRenderComponentStatus(row.id, { status: nextStatus })
      showPopup.success(`组件已${verb}`)
      await refreshAll()
    })
  }

  async function removeRow(row) {
    if (!window.confirm(`确认删除组件「${row.name}」吗？`)) {
      return
    }
    await withAction(async () => {
      await deleteRenderComponent(row.id)
      showPopup.success('组件已删除')
      await refreshAll()
    })
  }

  async function withAction(task) {
    actionLoading.value = true
    try {
      await task()
    } catch (error) {
      showPopup.error(error.message || '组件操作失败')
    } finally {
      actionLoading.value = false
    }
  }

  async function handlePageChange(nextPage) {
    if (!nextPage || nextPage === page.value) {
      return
    }
    page.value = nextPage
    await loadPage()
  }

  async function handlePageSizeChange(nextPageSize) {
    if (!nextPageSize || nextPageSize === pageSize.value) {
      return
    }
    pageSize.value = nextPageSize
    page.value = 1
    await loadPage()
  }

  return {
    loading,
    actionLoading,
    errorMessage,
    sidebarCollapsed,
    advancedVisible,
    page,
    pageSize,
    total,
    selectedRowId,
    selectedCategory,
    rows,
    sorts,
    filterSchema,
    actionItems,
    sidebarItems,
    statsItems,
    tableColumns,
    listConfig,
    summaryItems,
    handleFilterAction,
    handleSummaryAction,
    handleSidebarSelect,
    handleRowClick,
    handleActionBar,
    handleTableAction,
    handlePageChange,
    handlePageSizeChange
  }
}

function promptComponentPayload(initial, mode) {
  const key = window.prompt(mode === 'create' ? '请输入组件 Key' : '编辑组件 Key', initial?.key || '')
  if (key === null) {
    return null
  }
  const name = window.prompt(mode === 'create' ? '请输入组件名称' : '编辑组件名称', initial?.name || '')
  if (name === null) {
    return null
  }
  const category = window.prompt('请输入组件分类，留空则归入未分类', initial?.category || '')
  if (category === null) {
    return null
  }
  const docMarkdown = window.prompt('请输入组件说明 Markdown，可留空', initial?.docMarkdown || '')
  if (docMarkdown === null) {
    return null
  }
  const exampleJson = window.prompt('请输入示例 JSON', initial?.exampleJson || '{}')
  if (exampleJson === null) {
    return null
  }
  const normalizedKey = normalizeText(key)
  const normalizedName = normalizeText(name)
  if (!normalizedKey) {
    showPopup.warning('组件 Key 不能为空')
    return null
  }
  if (!normalizedName) {
    showPopup.warning('组件名称不能为空')
    return null
  }
  if (normalizeText(exampleJson)) {
    try {
      JSON.parse(exampleJson)
    } catch {
      showPopup.warning('示例 JSON 不是合法 JSON')
      return null
    }
  }
  return {
    key: normalizedKey,
    name: normalizedName,
    category: normalizeText(category),
    docMarkdown: docMarkdown ?? '',
    exampleJson: normalizeText(exampleJson) || '{}'
  }
}

function mapRow(item) {
  return {
    id: item.id,
    key: item.key || '',
    name: item.name || '',
    category: item.category || '',
    categoryLabel: item.category || '未分类',
    status: Number(item.status || STATUS.DRAFT),
    statusLabel: formatStatusLabel(Number(item.status || STATUS.DRAFT)),
    updateTime: item.updateTime || item.createTime || '',
    updateTimeLabel: formatDateTime(item.updateTime || item.createTime || ''),
    docPreview: formatPreview(item.docMarkdown),
    raw: item
  }
}

function formatStatusLabel(status) {
  if (status === STATUS.PUBLISHED) {
    return '已发布'
  }
  if (status === STATUS.DISABLED) {
    return '已停用'
  }
  return '草稿'
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

function formatPreview(value) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text) {
    return '暂无说明'
  }
  return text.length > 80 ? `${text.slice(0, 80)}...` : text
}

function normalizeStatus(value) {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : undefined
}

function normalizeText(value) {
  const text = String(value ?? '').trim()
  return text ? text : undefined
}
