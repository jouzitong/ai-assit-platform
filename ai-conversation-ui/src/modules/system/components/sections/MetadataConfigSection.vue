<script setup lang="ts">
import { MoreFilled, Plus, RefreshRight, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AppDialog, AppPagination } from '../../../../components'
import {
  searchVirtualEntities,
  type VirtualEntityItem,
} from '../../api/virtualData'
import {
  createRenderPage,
  createRenderPageCategory,
  deleteRenderPage,
  deleteRenderPageCategory,
  getRenderPageTree,
  searchRenderPages,
  type RenderPageCategoryTreeItem,
  type RenderPageCategoryUpsertPayload,
  type RenderPageContent,
  type RenderPageItem,
  type RenderPageStatus,
  type RenderPageTreeResult,
  type RenderPageUpsertPayload,
  updateRenderPage,
  updateRenderPageCategory,
} from '../../api/renderPages'

type MetadataTreeNode = {
  id?: string | number
  key: string
  label: string
  count: number
  path?: string
  sortNo?: number
  enabled?: boolean
  parentCode?: string
  children?: MetadataTreeNode[]
}

type RenderPreviewLayout = 'standard' | 'form' | 'dashboard' | 'report' | 'embedded'

const UNCLASSIFIED_CATEGORY_KEY = '__uncategorized__'
const ALL_CATEGORY_KEY = 'all'
const EFFECTIVE_STATUS_DRAFT = 1
const EFFECTIVE_STATUS_PUBLISHED = 2
const EFFECTIVE_STATUS_DISABLED = 3
const PREVIEW_MODEL_PAGE_SIZE = 500
const previewLayoutOptions: Array<{
  value: RenderPreviewLayout
  label: string
  description: string
}> = [
  { value: 'standard', label: '普通布局', description: '适合列表、表单和常规业务页面' },
  { value: 'form', label: '表单布局', description: '适合查看、新增和编辑业务表单' },
  { value: 'dashboard', label: '看板布局', description: '适合指标、图表和监控大屏' },
  { value: 'report', label: '报表布局', description: '适合报表阅读、汇总和打印场景' },
  { value: 'embedded', label: '嵌入布局', description: '适合嵌入其他页面或工作区' },
]

const router = useRouter()
const metadataKeyword = ref('')
const activeCategory = ref('all')
const pageSize = ref(20)
const currentPage = ref(1)
const total = ref(0)
const loading = ref(false)
const summaryLoading = ref(false)
const errorMessage = ref('')
const categoryError = ref('')
const metadataRecords = ref<RenderPageItem[]>([])
const uncategorizedRecords = ref<RenderPageItem[]>([])
const categoryTreeRecords = ref<MetadataTreeNode[]>([])
const selectedTreeNode = ref<MetadataTreeNode | null>(null)
const categoryDialogVisible = ref(false)
const categoryDialogMode = ref<'create-root' | 'create-child' | 'edit'>('create-root')
const categorySubmitting = ref(false)
const metadataDialogVisible = ref(false)
const metadataDialogMode = ref<'create' | 'edit'>('edit')
const metadataSubmitting = ref(false)
const editingMetadataId = ref<string | number | null>(null)
const previewDialogVisible = ref(false)
const previewModelsLoading = ref(false)
const previewModelsLoaded = ref(false)
const previewRecord = ref<RenderPageItem | null>(null)
const previewModels = ref<VirtualEntityItem[]>([])
const summary = ref({
  total: 0,
  published: 0,
  draft: 0,
  disabled: 0,
  categories: 0,
})

const pageSizeOptions = [5, 10, 20, 50, 100, 200, 500]
const treeProps = {
  children: 'children',
  label: 'label',
}
const categoryForm = reactive({
  code: '',
  name: '',
  sortNo: 0,
  enabled: true,
})
const metadataForm = reactive({
  code: '',
  name: '',
  categoryCode: '',
  status: EFFECTIVE_STATUS_DRAFT as RenderPageStatus,
  content: '',
})
const previewForm = reactive({
  model: '',
  layout: 'standard' as RenderPreviewLayout,
})

const previewModelOptions = computed(() => previewModels.value
  .filter(item => Boolean(item.entityCode?.trim()))
  .map(item => ({
    value: item.entityCode!.trim(),
    label: item.entityName?.trim()
      ? `${item.entityName.trim()}（${item.entityCode!.trim()}）`
      : item.entityCode!.trim(),
  }))
  .sort((left, right) => left.label.localeCompare(right.label, 'zh-CN')))

const categoryTreeData = computed<MetadataTreeNode[]>(() => [
  {
    key: ALL_CATEGORY_KEY,
    label: '全部元数据',
    count: summary.value.total,
    children: categoryTreeRecords.value,
  },
])
const metadataCategoryOptions = computed(() => {
  const build = (nodes: MetadataTreeNode[]): Array<{ label: string; value: string; children?: Array<{ label: string; value: string; children?: unknown[] }> }> => {
    return nodes
      .filter(node => ![ALL_CATEGORY_KEY, UNCLASSIFIED_CATEGORY_KEY].includes(node.key))
      .map(node => ({
        label: node.label,
        value: node.key,
        children: Array.isArray(node.children) && node.children.length ? build(node.children) : undefined,
      }))
  }

  return build(categoryTreeRecords.value)
})

const filteredMetadataRecords = computed(() => {
  return metadataRecords.value.map((record) => ({
    id: record.id,
    code: record.code || '-',
    name: record.name || '未命名元数据',
    categoryCode: record.categoryCode || '未分类',
    status: formatStatus(record.status),
    statusType: resolveStatusType(record.status),
    updatedAt: formatDateTime(record.updateTime || record.createTime),
    updatedBy: record.updatedBy || record.createdBy || '-',
    contentPreview: formatPreview(record.content),
  }))
})

function formatDateTime(value?: string) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 19)
}

function formatPreview(value?: RenderPageContent) {
  if (!value) {
    return '暂无内容'
  }
  const normalized = JSON.stringify(value).replace(/\s+/g, ' ').trim()
  return normalized.length > 120 ? `${normalized.slice(0, 120)}...` : normalized
}

function formatStatus(status?: RenderPageStatus) {
  if (status === EFFECTIVE_STATUS_PUBLISHED || status === 'PUBLISHED') {
    return '已发布'
  }
  if (status === EFFECTIVE_STATUS_DRAFT || status === 'DRAFT') {
    return '草稿'
  }
  if (status === EFFECTIVE_STATUS_DISABLED || status === 'DISABLED') {
    return '已停用'
  }
  return status || '-'
}

function resolveStatusType(status?: RenderPageStatus) {
  if (status === EFFECTIVE_STATUS_PUBLISHED || status === 'PUBLISHED') {
    return 'primary'
  }
  if (status === EFFECTIVE_STATUS_DRAFT || status === 'DRAFT') {
    return 'warning'
  }
  if (status === EFFECTIVE_STATUS_DISABLED || status === 'DISABLED') {
    return 'info'
  }
  return ''
}

function flattenTreePages(tree: RenderPageTreeResult) {
  const records: RenderPageItem[] = []

  const visit = (nodes: RenderPageCategoryTreeItem[] = []) => {
    nodes.forEach((node) => {
      if (Array.isArray(node.pages)) {
        records.push(...node.pages)
      }
      if (Array.isArray(node.children) && node.children.length) {
        visit(node.children)
      }
    })
  }

  visit(tree.categories || [])
  if (Array.isArray(tree.uncategorizedPages)) {
    records.push(...tree.uncategorizedPages)
  }
  return records
}

function countNodePages(node: RenderPageCategoryTreeItem) {
  const ownCount = Array.isArray(node.pages) ? node.pages.length : 0
  const childCount = Array.isArray(node.children)
    ? node.children.reduce((sum, child) => sum + countNodePages(child), 0)
    : 0
  return ownCount + childCount
}

function countTreeNodes(nodes: RenderPageCategoryTreeItem[] = []) {
  return nodes.reduce((sum, node) => {
    const selfCount = node.code ? 1 : 0
    const childCount = Array.isArray(node.children) ? countTreeNodes(node.children) : 0
    return sum + selfCount + childCount
  }, 0)
}

function buildCategoryTree(nodes: RenderPageCategoryTreeItem[] = []) {
  return nodes
    .filter((node) => Boolean(node.code))
    .map((node) => ({
      id: node.id,
      key: node.code as string,
      label: node.name || node.code || '未命名分类',
      count: countNodePages(node),
      path: node.path,
      sortNo: node.sortNo,
      enabled: node.enabled,
      parentCode: node.parentCode,
      children: buildCategoryTree(node.children || []),
    }))
}

function resolveTotal(payloadTotal?: number) {
  const numericTotal = Number(payloadTotal)
  return Number.isFinite(numericTotal) ? numericTotal : metadataRecords.value.length
}

function paginateRecords(records: RenderPageItem[]) {
  const safePage = currentPage.value < 1 ? 1 : currentPage.value
  const safeSize = pageSize.value < 1 ? 20 : pageSize.value
  const fromIndex = Math.min((safePage - 1) * safeSize, records.length)
  const toIndex = Math.min(fromIndex + safeSize, records.length)
  return records.slice(fromIndex, toIndex)
}

async function loadSummaryAndCategories() {
  summaryLoading.value = true
  categoryError.value = ''
  try {
    const tree = await getRenderPageTree()
    const allPages = flattenTreePages(tree)
    const uncategorizedCount = Array.isArray(tree.uncategorizedPages) ? tree.uncategorizedPages.length : 0

    uncategorizedRecords.value = Array.isArray(tree.uncategorizedPages) ? tree.uncategorizedPages : []
    categoryTreeRecords.value = [
      ...buildCategoryTree(tree.categories || []),
      ...(uncategorizedCount > 0
        ? [{
            key: UNCLASSIFIED_CATEGORY_KEY,
            label: '未分类',
            count: uncategorizedCount,
          }]
        : []),
    ]
    summary.value = {
      total: allPages.length,
      published: allPages.filter((item) => item.status === EFFECTIVE_STATUS_PUBLISHED || item.status === 'PUBLISHED').length,
      draft: allPages.filter((item) => item.status === EFFECTIVE_STATUS_DRAFT || item.status === 'DRAFT').length,
      disabled: allPages.filter((item) => item.status === EFFECTIVE_STATUS_DISABLED || item.status === 'DISABLED').length,
      categories: countTreeNodes(tree.categories || []),
    }
  }
  catch (error) {
    uncategorizedRecords.value = []
    categoryTreeRecords.value = []
    summary.value = {
      total: 0,
      published: 0,
      draft: 0,
      disabled: 0,
      categories: 0,
    }
    categoryError.value = error instanceof Error ? error.message : '分类加载失败'
  }
  finally {
    summaryLoading.value = false
  }
}

async function loadMetadata() {
  loading.value = true
  errorMessage.value = ''
  try {
    if (activeCategory.value === UNCLASSIFIED_CATEGORY_KEY) {
      const tree = await getRenderPageTree({
        keyword: metadataKeyword.value.trim() || undefined,
      })
      const records = Array.isArray(tree.uncategorizedPages) ? tree.uncategorizedPages : []
      total.value = records.length
      metadataRecords.value = paginateRecords(records)
      return
    }

    const payload = await searchRenderPages({
      page: currentPage.value,
      size: pageSize.value,
      keyword: metadataKeyword.value.trim() || undefined,
      categoryCode: activeCategory.value === 'all' ? undefined : activeCategory.value,
    })
    metadataRecords.value = payload?.list ?? []
    total.value = resolveTotal(payload?.pageInfo?.total)
  }
  catch (error) {
    metadataRecords.value = []
    total.value = 0
    errorMessage.value = error instanceof Error ? error.message : '元数据列表加载失败'
  }
  finally {
    loading.value = false
  }
}

async function loadPageData() {
  await Promise.all([loadSummaryAndCategories(), loadMetadata()])
}

async function handleSearch() {
  currentPage.value = 1
  await loadMetadata()
}

async function handleRefresh() {
  await loadPageData()
}

async function handleCurrentPageChange(page: number) {
  currentPage.value = page
  await loadMetadata()
}

async function handlePageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  await loadMetadata()
}

async function handleSelectCategory(categoryKey: string) {
  activeCategory.value = categoryKey
  currentPage.value = 1
  await loadMetadata()
}

async function handleTreeNodeClick(node: MetadataTreeNode) {
  selectedTreeNode.value = node
  await handleSelectCategory(node.key)
}

function canOperateNode(node: MetadataTreeNode) {
  return ![ALL_CATEGORY_KEY, UNCLASSIFIED_CATEGORY_KEY].includes(node.key)
}

function canEditOrDeleteNode(node: MetadataTreeNode) {
  return ![ALL_CATEGORY_KEY, UNCLASSIFIED_CATEGORY_KEY].includes(node.key)
}

async function handleTreeAction(command: string, node: MetadataTreeNode) {
  selectedTreeNode.value = node
  if (command === 'create') {
    if ([ALL_CATEGORY_KEY, UNCLASSIFIED_CATEGORY_KEY].includes(node.key)) {
      openCreateRootDialog()
    } else {
      openCreateChildDialog(node)
    }
    return
  }
  if (command === 'edit') {
    openEditDialog(node)
    return
  }
  if (command === 'delete') {
    await handleDeleteCategory(node)
  }
}

function handleCreateMetadata() {
  resetMetadataForm()
  metadataDialogMode.value = 'create'
  metadataDialogVisible.value = true
}

function resetMetadataForm() {
  editingMetadataId.value = null
  metadataForm.code = ''
  metadataForm.name = ''
  metadataForm.categoryCode = ''
  metadataForm.status = EFFECTIVE_STATUS_DRAFT
  metadataForm.content = ''
}

function openEditMetadataDialog(record: {
  id: string | number
  code: string
  name: string
  categoryCode: string
  status: string
  contentPreview: string
}) {
  const raw = metadataRecords.value.find((item) => item.id === record.id)
  metadataDialogMode.value = 'edit'
  editingMetadataId.value = record.id
  metadataForm.code = record.code === '-' ? '' : record.code
  metadataForm.name = record.name
  metadataForm.categoryCode = record.categoryCode === '未分类' ? '' : record.categoryCode
  metadataForm.status = raw?.status ?? EFFECTIVE_STATUS_DRAFT
  metadataForm.content = raw?.content ? JSON.stringify(raw.content, null, 2) : ''
  metadataDialogVisible.value = true
}

async function loadPreviewModels() {
  if (previewModelsLoaded.value || previewModelsLoading.value) {
    return
  }
  previewModelsLoading.value = true
  try {
    const result = await searchVirtualEntities({
      page: 1,
      size: PREVIEW_MODEL_PAGE_SIZE,
      enabled: true,
    })
    previewModels.value = result?.list || []
    previewModelsLoaded.value = true
  }
  catch (error) {
    previewModels.value = []
    ElMessage.error(error instanceof Error ? error.message : '虚拟模型加载失败')
  }
  finally {
    previewModelsLoading.value = false
  }
}

function openPreviewDialog(record: { id: string | number; code: string }) {
  const raw = metadataRecords.value.find(item => item.id === record.id)
  if (!raw?.code?.trim()) {
    ElMessage.warning('当前元数据缺少可预览的编码')
    return
  }
  previewRecord.value = raw
  previewForm.model = resolvePreviewModel(raw.content)
  previewForm.layout = resolvePreviewLayout(raw.content)
  previewDialogVisible.value = true
  void loadPreviewModels()
}

function resetPreviewForm() {
  previewRecord.value = null
  previewForm.model = ''
  previewForm.layout = 'standard'
}

function submitPreview() {
  const code = previewRecord.value?.code?.trim()
  if (!code) {
    ElMessage.warning('当前元数据缺少可预览的编码')
    return
  }
  if (!previewForm.model) {
    ElMessage.warning('请选择用于绑定 :model 的虚拟模型')
    return
  }

  const target = router.resolve({
    name: 'render-app',
    params: {
      mode: previewForm.layout,
      code,
    },
    query: {
      model: previewForm.model,
      preview: '1',
    },
  })
  window.open(target.href, '_blank', 'noopener,noreferrer')
  previewDialogVisible.value = false
}

function resolvePreviewLayout(content?: RenderPageContent): RenderPreviewLayout {
  const presentation = isRecord(content?.presentation)
    ? content.presentation
    : isRecord(content?.page) ? content.page : undefined
  const value = presentation?.defaultMode
  return previewLayoutOptions.some(option => option.value === value)
    ? value as RenderPreviewLayout
    : 'standard'
}

function resolvePreviewModel(content?: RenderPageContent) {
  if (!content) {
    return ''
  }
  const pending: unknown[] = [content]
  while (pending.length) {
    const current = pending.shift()
    if (Array.isArray(current)) {
      pending.push(...current)
      continue
    }
    if (!isRecord(current)) {
      continue
    }
    if (
      typeof current.type === 'string'
      && ['db-query-list', 'semantic-query'].includes(current.type)
      && typeof current.model === 'string'
      && current.model.trim()
      && current.model.trim() !== ':model'
    ) {
      return current.model.trim()
    }
    pending.push(...Object.values(current))
  }
  return ''
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

async function handleDeleteMetadata(record: { id: string | number; name: string }) {
  try {
    await ElMessageBox.confirm(`确定删除元数据“${record.name}”吗？`, '删除元数据', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteRenderPage(record.id)
    ElMessage.success('元数据已删除')
    await loadPageData()
  }
  catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '删除元数据失败')
  }
}

async function submitMetadataForm() {
  if (!metadataForm.code.trim()) {
    ElMessage.warning('请输入元数据编码')
    return
  }
  if (!metadataForm.name.trim()) {
    ElMessage.warning('请输入元数据名称')
    return
  }
  let content: RenderPageContent = {}
  if (metadataForm.content.trim()) {
    try {
      const parsed = JSON.parse(metadataForm.content) as unknown
      if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
        ElMessage.warning('内容必须是 JSON 对象')
        return
      }
      content = parsed as RenderPageContent
    }
    catch {
      ElMessage.warning('内容不是合法 JSON')
      return
    }
  }

  metadataSubmitting.value = true
  try {
    const payload: RenderPageUpsertPayload = {
      code: metadataForm.code.trim(),
      name: metadataForm.name.trim(),
      categoryCode: metadataForm.categoryCode.trim() || undefined,
      status: metadataForm.status,
      content,
    }
    if (metadataDialogMode.value === 'create') {
      await createRenderPage(payload)
      ElMessage.success('元数据已创建')
    } else {
      if (!editingMetadataId.value) {
        throw new Error('未找到可编辑的元数据')
      }
      await updateRenderPage(editingMetadataId.value, payload)
      ElMessage.success('元数据已更新')
    }
    metadataDialogVisible.value = false
    resetMetadataForm()
    await loadPageData()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '元数据更新失败')
  }
  finally {
    metadataSubmitting.value = false
  }
}

function resetCategoryForm() {
  categoryForm.code = ''
  categoryForm.name = ''
  categoryForm.sortNo = 0
  categoryForm.enabled = true
}

function buildCategoryPath(parentPath: string | undefined, code: string) {
  const normalizedCode = code.trim()
  if (!parentPath) {
    return normalizedCode
  }
  return `${parentPath}/${normalizedCode}`
}

function openCreateRootDialog() {
  categoryDialogMode.value = 'create-child'
  resetCategoryForm()
  categoryDialogVisible.value = true
}

function openCreateChildDialog(node = selectedTreeNode.value) {
  if (!node || [ALL_CATEGORY_KEY, UNCLASSIFIED_CATEGORY_KEY].includes(node.key)) {
    ElMessage.warning('请选择一个实际分类后再新增子分类')
    return
  }
  selectedTreeNode.value = node
  categoryDialogMode.value = 'create-child'
  resetCategoryForm()
  categoryDialogVisible.value = true
}

function openEditDialog(node = selectedTreeNode.value) {
  if (!node || [ALL_CATEGORY_KEY, UNCLASSIFIED_CATEGORY_KEY].includes(node.key)) {
    ElMessage.warning('请选择一个实际分类后再编辑')
    return
  }
  selectedTreeNode.value = node
  categoryDialogMode.value = 'edit'
  categoryForm.code = node.key
  categoryForm.name = node.label
  categoryForm.sortNo = node.sortNo ?? 0
  categoryForm.enabled = node.enabled ?? true
  categoryDialogVisible.value = true
}

async function handleDeleteCategory(node = selectedTreeNode.value) {
  if (!node || [ALL_CATEGORY_KEY, UNCLASSIFIED_CATEGORY_KEY].includes(node.key) || !node.id) {
    ElMessage.warning('请选择一个实际分类后再删除')
    return
  }
  selectedTreeNode.value = node
  try {
    await ElMessageBox.confirm(`确定删除分类“${node.label}”吗？`, '删除分类', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteRenderPageCategory(node.id)
    ElMessage.success('分类已删除')
    if (activeCategory.value === node.key) {
      activeCategory.value = ALL_CATEGORY_KEY
    }
    selectedTreeNode.value = null
    currentPage.value = 1
    await loadPageData()
  }
  catch (error) {
    if (error === 'cancel') {
      return
    }
    ElMessage.error(error instanceof Error ? error.message : '删除分类失败')
  }
}

const categoryDialogTitle = computed(() => {
  if (categoryDialogMode.value === 'edit') {
    return '编辑分类'
  }
  return '新增分类'
})

const categoryParentLabel = computed(() => {
  if (categoryDialogMode.value === 'create-root') {
    return '顶级分类'
  }
  return selectedTreeNode.value?.label || '-'
})

async function submitCategoryForm() {
  const name = categoryForm.name.trim()
  const code = categoryForm.code.trim()
  if (!name) {
    ElMessage.warning('请输入分类名称')
    return
  }
  if (!code) {
    ElMessage.warning('请输入分类编码')
    return
  }

  categorySubmitting.value = true
  try {
    if (categoryDialogMode.value === 'edit') {
      const node = selectedTreeNode.value
      if (!node?.id) {
        throw new Error('未找到可编辑的分类')
      }
      const payload: Partial<RenderPageCategoryUpsertPayload> = {
        code,
        name,
        parentCode: node.parentCode,
        path: node.path || buildCategoryPath(undefined, code),
        sortNo: Number.isFinite(Number(categoryForm.sortNo)) ? Number(categoryForm.sortNo) : 0,
        enabled: categoryForm.enabled,
      }
      await updateRenderPageCategory(node.id, payload)
      ElMessage.success('分类已更新')
    }
    else {
      const parentNode = categoryDialogMode.value === 'create-child' ? selectedTreeNode.value : null
      const payload: RenderPageCategoryUpsertPayload = {
        code,
        name,
        parentCode: parentNode?.key,
        path: buildCategoryPath(parentNode?.path, code),
        sortNo: Number.isFinite(Number(categoryForm.sortNo)) ? Number(categoryForm.sortNo) : 0,
        enabled: categoryForm.enabled,
      }
      await createRenderPageCategory(payload)
      ElMessage.success('分类已创建')
    }
    categoryDialogVisible.value = false
    currentPage.value = 1
    await loadPageData()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '分类保存失败')
  }
  finally {
    categorySubmitting.value = false
  }
}

onMounted(() => {
  void loadPageData()
})
</script>

<template>
  <section class="system-settings-component-page">
    <el-container class="component-manage-layout">
      <el-aside width="220px" class="component-manage-layout__aside">
        <div v-if="categoryError" class="component-manage-layout__aside-error">
          {{ categoryError }}
        </div>
        <div class="metadata-tree-panel">
          <div class="metadata-tree-panel__header">元数据分类</div>
          <el-tree
            :data="categoryTreeData"
            node-key="key"
            :props="treeProps"
            :current-node-key="activeCategory"
            :default-expanded-keys="[ALL_CATEGORY_KEY]"
            highlight-current
            class="metadata-tree"
            @node-click="handleTreeNodeClick"
          >
            <template #default="{ data }">
              <div class="metadata-tree-node">
                <span class="metadata-tree-node__label">{{ data.label }}</span>
                <div class="metadata-tree-node__tools">
                  <span class="metadata-tree-node__count">{{ data.count }}</span>
                  <el-dropdown
                    v-if="canOperateNode(data)"
                    trigger="click"
                    placement="bottom-end"
                    @command="(command) => handleTreeAction(String(command), data)"
                  >
                    <button class="metadata-tree-node__more" type="button" @click.stop>
                      <el-icon><MoreFilled /></el-icon>
                    </button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="create">新增</el-dropdown-item>
                        <el-dropdown-item v-if="canEditOrDeleteNode(data)" command="edit">编辑</el-dropdown-item>
                        <el-dropdown-item v-if="canEditOrDeleteNode(data)" command="delete">删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>
            </template>
          </el-tree>
        </div>
      </el-aside>

      <el-container class="component-manage-layout__body">
        <el-header class="component-manage-layout__header">
          <div class="component-manage-layout__title">
            <h3>元数据配置</h3>
          </div>
          <div class="component-manage-layout__tools">
            <el-input v-model="metadataKeyword" placeholder="搜索元数据名称 / Code / 分类" clearable @keyup.enter="handleSearch">
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button plain :loading="loading || summaryLoading" @click="handleRefresh">
              <el-icon><RefreshRight /></el-icon>
              刷新
            </el-button>
            <el-button type="primary" @click="handleCreateMetadata">
              <el-icon><Plus /></el-icon>
              新建元数据
            </el-button>
          </div>
        </el-header>

        <el-main class="component-manage-layout__main">
          <div v-if="errorMessage" class="component-manage-layout__state component-manage-layout__state--error">
            {{ errorMessage }}
          </div>
          <div v-else-if="loading" class="component-manage-layout__state">
            正在加载元数据列表...
          </div>
          <div v-else-if="!filteredMetadataRecords.length" class="component-manage-layout__state">
            当前筛选条件下没有元数据
          </div>
          <div v-else class="component-manage-layout__grid">
            <div
              v-for="record in filteredMetadataRecords"
              :key="record.id"
              class="component-manage-card"
            >
              <div class="component-manage-card__row">
                <div>
                  <div class="component-manage-card__name">{{ record.name }}</div>
                  <div class="component-manage-card__meta">{{ record.code }}</div>
                </div>
                <div class="component-manage-card__actions">
                  <el-tag size="small" effect="plain" :type="record.statusType">
                    {{ record.status }}
                  </el-tag>
                  <el-dropdown
                    trigger="click"
                    placement="bottom-end"
                    @command="(command) => command === 'edit' ? openEditMetadataDialog(record) : handleDeleteMetadata(record)"
                  >
                    <button class="component-manage-card__more" type="button" @click.stop>
                      <el-icon><MoreFilled /></el-icon>
                    </button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="edit">编辑</el-dropdown-item>
                        <el-dropdown-item command="delete">删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>
              <div class="component-manage-card__info">
                <span>{{ record.categoryCode }}</span>
                <span>{{ record.updatedBy }}</span>
                <span>{{ record.updatedAt }}</span>
              </div>
              <div class="component-manage-card__content">
                <div class="component-manage-card__section">
                  <label>内容预览</label>
                  <p>{{ record.contentPreview }}</p>
                </div>
              </div>
              <div class="component-manage-card__footer">
                <el-button link type="primary" @click="openPreviewDialog(record)">
                  <el-icon><View /></el-icon>
                  预览
                </el-button>
              </div>
            </div>
          </div>
        </el-main>

        <el-footer class="component-manage-layout__footer">
          <AppPagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="pageSizeOptions"
            :total="total"
            :pager-count="5"
            @current-change="handleCurrentPageChange"
            @size-change="handlePageSizeChange"
          />
        </el-footer>
      </el-container>
    </el-container>
  </section>

  <el-dialog v-model="categoryDialogVisible" :title="categoryDialogTitle" width="460px">
    <el-form label-width="88px">
      <el-form-item label="父级分类">
        <div class="metadata-category-parent">{{ categoryParentLabel }}</div>
      </el-form-item>
      <el-form-item label="分类编码">
        <el-input
          v-model="categoryForm.code"
          placeholder="请输入分类编码"
          :disabled="categoryDialogMode === 'edit'"
        />
      </el-form-item>
      <el-form-item label="分类名称">
        <el-input v-model="categoryForm.name" placeholder="请输入分类名称" />
      </el-form-item>
      <el-form-item label="排序号">
        <el-input v-model.number="categoryForm.sortNo" type="number" placeholder="请输入排序号" />
      </el-form-item>
      <el-form-item label="启用状态">
        <el-switch v-model="categoryForm.enabled" />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="metadata-category-dialog__footer">
        <el-button @click="categoryDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="categorySubmitting" @click="submitCategoryForm">保存</el-button>
      </div>
    </template>
  </el-dialog>

  <el-dialog v-model="metadataDialogVisible" :title="metadataDialogMode === 'create' ? '新建元数据' : '编辑元数据'" width="620px" @closed="resetMetadataForm">
    <el-form label-width="88px">
      <el-form-item label="元数据编码">
        <el-input v-model="metadataForm.code" placeholder="请输入元数据编码" />
      </el-form-item>
      <el-form-item label="元数据名称">
        <el-input v-model="metadataForm.name" placeholder="请输入元数据名称" />
      </el-form-item>
      <el-form-item label="分类">
        <el-tree-select
          v-model="metadataForm.categoryCode"
          clearable
          check-strictly
          default-expand-all
          filterable
          node-key="value"
          placeholder="请选择分类"
          :data="metadataCategoryOptions"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="metadataForm.status" placeholder="请选择状态" style="width: 100%">
          <el-option label="草稿" :value="EFFECTIVE_STATUS_DRAFT" />
          <el-option label="已发布" :value="EFFECTIVE_STATUS_PUBLISHED" />
          <el-option label="已停用" :value="EFFECTIVE_STATUS_DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item label="内容">
        <el-input v-model="metadataForm.content" type="textarea" :rows="10" placeholder="请输入元数据内容" />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="metadata-category-dialog__footer">
        <el-button @click="metadataDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="metadataSubmitting" @click="submitMetadataForm">保存</el-button>
      </div>
    </template>
  </el-dialog>

  <AppDialog
    v-model="previewDialogVisible"
    title="预览元数据"
    :description="previewRecord ? `选择 ${previewRecord.name || previewRecord.code} 的模型与布局。` : ''"
    size="small"
    action-mode="confirm"
    confirm-text="新页签预览"
    :confirm-disabled="previewModelsLoading || !previewForm.model || !previewForm.layout"
    @confirm="submitPreview"
    @closed="resetPreviewForm"
  >
    <el-form label-position="top" class="metadata-preview-form">
      <el-form-item label=":model" required>
        <el-select
          v-model="previewForm.model"
          filterable
          :loading="previewModelsLoading"
          placeholder="请选择虚拟模型"
        >
          <el-option
            v-for="option in previewModelOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <div class="metadata-preview-form__hint">
          仅用于本次预览，绑定 Render JSON 中的 :model，不会修改元数据原文。
        </div>
      </el-form-item>
      <el-form-item label="Layout 类型" required>
        <el-select v-model="previewForm.layout" placeholder="请选择布局类型">
          <el-option
            v-for="option in previewLayoutOptions"
            :key="option.value"
            :label="`${option.label}（${option.value}）`"
            :value="option.value"
          >
            <div class="metadata-preview-layout-option">
              <span>{{ option.label }}</span>
              <small>{{ option.description }}</small>
            </div>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
  </AppDialog>
</template>

<style scoped>
.system-settings-component-page {
  display: flex;
  flex: 1;
  min-height: 0;
}

.component-manage-layout {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
  box-shadow: var(--system-shadow);
}

.component-manage-layout__aside {
  min-height: 0;
  padding: 14px 12px;
  border-right: 1px solid var(--system-border-subtle);
  background: var(--system-surface-muted);
  overflow-y: auto;
}

.component-manage-layout__aside-title {
  margin-bottom: 8px;
  color: var(--system-text-muted);
  font-size: 12px;
  font-weight: 600;
}

.component-manage-layout__aside-summary {
  display: grid;
  gap: 4px;
  margin-bottom: 10px;
  color: var(--system-text-soft);
  font-size: 12px;
}

.component-manage-layout__aside-error {
  margin-bottom: 10px;
  color: var(--system-danger);
  font-size: 12px;
}

.metadata-tree-panel {
  padding: 10px 8px 4px;
  border: 1px solid var(--system-border);
  border-radius: 14px;
  background: var(--system-surface-gradient);
}

.metadata-tree-panel__header {
  margin-bottom: 8px;
  padding: 0 4px;
  color: var(--system-text-soft);
  font-size: 12px;
  font-weight: 600;
}

.metadata-tree {
  background: transparent;
}

.metadata-tree :deep(.el-tree-node__content) {
  height: 36px;
  border-radius: 10px;
}

.metadata-tree :deep(.el-tree-node__content:hover) {
  background: var(--system-accent-bg);
}

.metadata-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--system-accent-bg-strong);
  color: var(--system-accent-text);
}

.metadata-tree :deep(.el-tree-node__expand-icon) {
  color: var(--system-text-faint);
}

.metadata-tree-node {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  padding-right: 34px;
}

.metadata-tree-node__label {
  flex: 1;
  overflow: hidden;
  color: var(--system-title);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metadata-tree-node__tools {
  position: absolute;
  top: 50%;
  right: 0;
  transform: translateY(-50%);
  align-items: center;
  justify-content: flex-end;
  width: 26px;
  height: 22px;
}

.metadata-tree-node__count {
  position: absolute;
  top: 50%;
  right: 0;
  transform: translateY(-50%);
  min-width: 22px;
  padding: 2px 6px;
  border-radius: 999px;
  background: var(--system-accent-bg);
  color: var(--system-accent-text);
  font-size: 12px;
  line-height: 1.2;
  text-align: center;
  box-sizing: border-box;
  transition: opacity 0.2s ease;
}

.metadata-tree-node__more {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--system-text-faint);
  cursor: pointer;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s ease, background-color 0.2s ease, color 0.2s ease;
}

.metadata-tree-node__more:hover {
  background: var(--system-accent-bg-strong);
  color: var(--system-accent-text);
}

.metadata-tree :deep(.el-tree-node__content:hover) .metadata-tree-node__count,
.metadata-tree :deep(.el-tree-node.is-current > .el-tree-node__content) .metadata-tree-node__count {
  opacity: 0;
}

.metadata-tree :deep(.el-tree-node__content:hover) .metadata-tree-node__more,
.metadata-tree :deep(.el-tree-node.is-current > .el-tree-node__content) .metadata-tree-node__more,
.metadata-tree-node__more:focus-visible {
  opacity: 1;
  pointer-events: auto;
}

.metadata-category-parent {
  color: var(--system-text-soft);
  font-size: 13px;
}

.metadata-category-dialog__footer {
  display: flex;
  justify-content: flex-end;
}

.metadata-category-dialog__footer :deep(.el-button) {
  min-width: 76px;
  border-radius: 10px;
}

.metadata-category-dialog__footer :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.metadata-category-dialog__footer :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-primary-button-bg);
  color: var(--system-primary-button-text);
}

.component-manage-layout__body {
  min-width: 0;
}

.component-manage-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: auto;
  padding: 18px 20px 12px;
  border-bottom: 1px solid var(--system-border-subtle);
}

.component-manage-layout__title h3 {
  margin: 0;
  color: var(--system-title);
  font-size: 18px;
}

.component-manage-layout__title p {
  margin: 6px 0 0;
  color: var(--system-text-muted);
  font-size: 13px;
}

.component-manage-layout__tools {
  display: flex;
  align-items: center;
  gap: 12px;
}

.component-manage-layout__tools :deep(.el-input) {
  width: 320px;
}

.component-manage-layout__tools :deep(.el-input__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
}

.component-manage-layout__tools :deep(.el-input__inner),
.component-manage-layout__tools :deep(.el-input__prefix-inner) {
  color: var(--system-text);
}

.component-manage-layout__tools :deep(.el-input__inner::placeholder) {
  color: var(--system-text-faint);
}

.component-manage-layout__tools :deep(.el-button) {
  border-radius: 10px;
}

.component-manage-layout__tools :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.component-manage-layout__tools :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-primary-button-bg);
  color: var(--system-primary-button-text);
}

.component-manage-layout__main {
  min-height: 0;
  padding: 20px;
}

.component-manage-layout__state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  color: var(--system-text-soft);
  font-size: 14px;
}

.component-manage-layout__state--error {
  color: var(--system-danger);
}

.component-manage-layout__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.component-manage-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 220px;
  padding: 18px;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-gradient);
}

.component-manage-card__row,
.component-manage-card__info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.component-manage-card__name {
  color: var(--system-title);
  font-size: 16px;
  font-weight: 600;
}

.component-manage-card__meta,
.component-manage-card__info {
  color: var(--system-text-soft);
  font-size: 12px;
}

.component-manage-card__meta {
  margin-top: 4px;
}

.component-manage-card__actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.component-manage-card__more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: var(--system-text-faint);
  cursor: pointer;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s ease, background-color 0.2s ease, color 0.2s ease;
}

.component-manage-card__more:hover {
  background: var(--system-accent-bg-strong);
  color: var(--system-accent-text);
}

.component-manage-card:hover .component-manage-card__more,
.component-manage-card__more:focus-visible {
  opacity: 1;
  pointer-events: auto;
}

.component-manage-card__content {
  display: grid;
  flex: 1;
  gap: 12px;
}

.component-manage-card__footer {
  display: flex;
  justify-content: flex-end;
  padding-top: var(--app-space-1);
  border-top: 1px solid var(--system-border-subtle);
}

.component-manage-card__footer :deep(.el-button) {
  font-weight: 600;
}

.metadata-preview-form :deep(.el-select) {
  width: 100%;
}

.metadata-preview-form__hint {
  margin-top: var(--app-space-1);
  color: var(--app-text-muted);
  font-size: var(--app-font-size-caption);
  line-height: var(--app-line-height-body);
}

.metadata-preview-layout-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--app-space-3);
}

.metadata-preview-layout-option small {
  color: var(--app-text-muted);
}

.component-manage-card__section {
  display: grid;
  gap: 6px;
}

.component-manage-card__section label {
  color: var(--system-text-muted);
  font-size: 12px;
  font-weight: 600;
}

.component-manage-card__section p {
  margin: 0;
  color: var(--system-title);
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.component-manage-layout__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: auto;
  padding: 12px 20px 18px;
  border-top: 1px solid var(--system-border-subtle);
}

.system-settings-component-page :deep(.el-overlay-dialog) {
  display: flex;
  align-items: center;
  justify-content: center;
}

.system-settings-component-page :deep(.el-dialog) {
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
}

.system-settings-component-page :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.system-settings-component-page :deep(.el-dialog__body) {
  background: var(--system-surface-strong);
}

.system-settings-component-page :deep(.el-dialog__footer) {
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.system-settings-component-page :deep(.el-form-item__label) {
  color: var(--system-text-soft);
}

.system-settings-component-page :deep(.el-input__wrapper),
.system-settings-component-page :deep(.el-textarea__inner),
.system-settings-component-page :deep(.el-select__wrapper),
.system-settings-component-page :deep(.el-tree-select__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
  color: var(--system-text);
}

@media (max-width: 1100px) {
  .component-manage-layout__header {
    flex-direction: column;
    align-items: stretch;
  }

  .component-manage-layout__tools {
    flex-wrap: wrap;
  }

  .component-manage-layout__tools :deep(.el-input) {
    width: 100%;
  }

  .component-manage-layout__footer {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
