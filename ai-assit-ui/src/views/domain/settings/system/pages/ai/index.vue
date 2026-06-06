<script setup>
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
} from '../../../../../../api/aiChat'

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

const enabledOptions = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 'true' },
  { label: '停用', value: 'false' }
]

const pageSizeOptions = [10, 20, 50]

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

function createProviderForm() {
  return {
    id: null,
    providerCode: '',
    providerName: '',
    baseUrl: '',
    connectTimeoutMs: 3000,
    readTimeoutMs: 30000,
    enabled: true,
    remark: ''
  }
}

function createModelForm() {
  return {
    id: null,
    modelCode: '',
    modelName: '',
    providerCode: '',
    apiModel: '',
    capabilityTags: '',
    maxContextTokens: '',
    maxOutputTokens: '',
    temperatureEnabled: 1,
    enabled: true,
    priority: 100,
    remark: '',
    credentialId: null,
    credentialCode: '',
    apiKeyInput: '',
    apiKeyMasked: '',
    keyVersion: 1,
    credentialEnabled: true,
    expireAt: '',
    credentialRemark: ''
  }
}

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
    const payload = unwrapPayload(await searchAiChatProviderConfigs({
      page: 1,
      size: 200
    }))
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
</script>

<template>
  <div class="ai-page">
    <header class="content-head">
      <div class="head-copy">
        <p class="crumb">系统设置 / AI 接入</p>
        <h1>AI 元数据维护</h1>
        <p class="desc">页面只维护真实的 Provider 与 Model 配置。Model 编辑同时管理内部凭证配置。</p>
      </div>

      <button class="create-pill" type="button" @click="openCreateByTab">
        {{ activeTab === 'provider' ? '新增 Provider' : '新增 Model' }}
      </button>
    </header>

    <section class="stats-row">
      <article v-for="item in currentStats" :key="item.label" class="stat-card">
        <strong>{{ item.value }}</strong>
        <span>{{ item.label }}</span>
      </article>
    </section>

    <div v-if="notice.text" :class="['notice-bar', notice.type === 'error' ? 'is-error' : 'is-success']">
      {{ notice.text }}
    </div>

    <section class="workspace-card">
      <div class="tab-strip">
        <button
          class="tab-pill"
          :class="{ active: activeTab === 'provider' }"
          type="button"
          @click="activeTab = 'provider'"
        >
          Provider 管理
        </button>
        <button
          class="tab-pill"
          :class="{ active: activeTab === 'model' }"
          type="button"
          @click="activeTab = 'model'"
        >
          Model 管理
        </button>
      </div>

      <div v-if="activeTab === 'provider'" class="panel-shell">
        <div class="toolbar-grid provider-toolbar">
          <input
            v-model="providerFilters.providerCode"
            class="field-control"
            type="text"
            placeholder="按 Provider 编码检索"
            @keyup.enter="handleSearch"
          />

          <select v-model="providerFilters.enabled" class="field-control">
            <option v-for="item in enabledOptions" :key="item.label" :value="item.value">{{ item.label }}</option>
          </select>

          <div class="toolbar-actions">
            <button class="action-btn primary" type="button" @click="handleSearch">查询</button>
            <button class="action-btn" type="button" @click="resetProviderFilters">重置</button>
          </div>
        </div>

        <div class="table-card">
          <div v-if="loading.provider" class="table-state">正在加载 Provider 列表...</div>

          <template v-else>
            <div class="table-scroll">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>Provider 编码</th>
                    <th>Provider 名称</th>
                    <th>基础地址</th>
                    <th>连接超时</th>
                    <th>读取超时</th>
                    <th>状态</th>
                    <th>更新时间</th>
                    <th>备注</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in providerList" :key="row.id">
                    <td>{{ row.providerCode }}</td>
                    <td>{{ row.providerName }}</td>
                    <td class="ellipsis">{{ row.baseUrl }}</td>
                    <td>{{ row.connectTimeoutMs }} ms</td>
                    <td>{{ row.readTimeoutMs }} ms</td>
                    <td>
                      <button
                        class="status-btn"
                        :class="row.enabled ? 'is-on' : 'is-off'"
                        type="button"
                        @click="toggleProviderStatus(row)"
                      >
                        {{ row.enabled ? '启用' : '停用' }}
                      </button>
                    </td>
                    <td>{{ formatDateTime(row.updateTime) }}</td>
                    <td class="ellipsis">{{ row.remark || '-' }}</td>
                    <td>
                      <div class="row-actions">
                        <button class="link-btn" type="button" @click="openProviderEdit(row)">编辑</button>
                        <button class="link-btn danger" type="button" @click="confirmDeleteProvider(row)">删除</button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!providerList.length">
                    <td colspan="9" class="empty-cell">暂无 Provider 数据</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </div>
      </div>

      <div v-else class="panel-shell">
        <div class="toolbar-grid model-toolbar">
          <input
            v-model="modelFilters.keyword"
            class="field-control"
            type="text"
            placeholder="搜索模型编码、模型名称、API Model、凭证编码"
            @keyup.enter="handleSearch"
          />

          <select v-model="modelFilters.providerCode" class="field-control">
            <option value="">全部 Provider</option>
            <option v-for="item in providerOptions" :key="item.id" :value="item.providerCode">
              {{ item.providerName }} ({{ item.providerCode }})
            </option>
          </select>

          <select v-model="modelFilters.enabled" class="field-control">
            <option v-for="item in enabledOptions" :key="item.label" :value="item.value">{{ item.label }}</option>
          </select>

          <div class="toolbar-actions">
            <button class="action-btn primary" type="button" @click="handleSearch">查询</button>
            <button class="action-btn" type="button" @click="resetModelFilters">重置</button>
          </div>
        </div>

        <div class="table-card">
          <div v-if="loading.model" class="table-state">正在加载 Model 列表...</div>

          <template v-else>
            <div class="table-scroll">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>模型编码</th>
                    <th>模型名称</th>
                    <th>Provider</th>
                    <th>API Model</th>
                    <th>能力标签</th>
                    <th>状态</th>
                    <th>优先级</th>
                    <th>凭证编码</th>
                    <th>脱敏 Key</th>
                    <th>凭证状态</th>
                    <th>更新时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in modelList" :key="row.id">
                    <td>{{ row.modelCode }}</td>
                    <td>{{ row.modelName }}</td>
                    <td>
                      <div class="provider-cell">
                        <strong>{{ row.providerName || row.providerCode }}</strong>
                        <span>{{ row.providerCode }}</span>
                      </div>
                    </td>
                    <td>{{ row.apiModel }}</td>
                    <td>
                      <div class="tag-list">
                        <span v-for="tag in tagList(row.capabilityTags)" :key="tag" class="soft-tag">{{ tag }}</span>
                        <span v-if="!tagList(row.capabilityTags).length">-</span>
                      </div>
                    </td>
                    <td>
                      <button
                        class="status-btn"
                        :class="row.enabled ? 'is-on' : 'is-off'"
                        type="button"
                        @click="toggleModelStatus(row)"
                      >
                        {{ row.enabled ? '启用' : '停用' }}
                      </button>
                    </td>
                    <td>{{ row.priority ?? '-' }}</td>
                    <td>{{ row.credentialCode || '-' }}</td>
                    <td>{{ row.apiKeyMasked || '-' }}</td>
                    <td>
                      <span class="state-chip" :class="row.credentialEnabled ? 'is-on' : 'is-off'">
                        {{ row.credentialEnabled ? '启用' : '停用' }}
                      </span>
                    </td>
                    <td>{{ formatDateTime(row.updateTime) }}</td>
                    <td>
                      <div class="row-actions">
                        <button class="link-btn" type="button" @click="openModelEdit(row)">编辑</button>
                        <button class="link-btn danger" type="button" @click="confirmDeleteModel(row)">删除</button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!modelList.length">
                    <td colspan="12" class="empty-cell">暂无 Model 数据</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
        </div>
      </div>

      <footer class="pagination-bar">
        <span class="page-summary">{{ pageSummary }}</span>
        <div class="page-controls">
          <select class="field-control page-size" :value="currentSize" @change="handlePageSizeChange">
            <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }} / 页</option>
          </select>

          <div class="pager">
            <button class="action-btn" type="button" :disabled="currentPage <= 1" @click="handlePageChange(currentPage - 1)">
              上一页
            </button>
            <span class="pager-indicator">{{ currentPage }} / {{ totalPages }}</span>
            <button
              class="action-btn"
              type="button"
              :disabled="currentPage >= totalPages"
              @click="handlePageChange(currentPage + 1)"
            >
              下一页
            </button>
          </div>
        </div>
      </footer>
    </section>

    <div v-if="providerDialogVisible" class="modal-mask" @click.self="providerDialogVisible = false">
      <div class="modal-card">
        <header class="modal-head">
          <h3>{{ providerDialogMode === 'create' ? '新增 Provider' : '编辑 Provider' }}</h3>
          <button class="close-btn" type="button" @click="providerDialogVisible = false">×</button>
        </header>

        <p v-if="providerError" class="error-banner">{{ providerError }}</p>

        <div class="form-grid two-column">
          <label class="field-block">
            <span>Provider 编码</span>
            <input v-model="providerForm.providerCode" class="field-control" type="text" :disabled="providerDialogMode === 'edit'" />
          </label>
          <label class="field-block">
            <span>Provider 名称</span>
            <input v-model="providerForm.providerName" class="field-control" type="text" />
          </label>
          <label class="field-block full-span">
            <span>基础地址</span>
            <input v-model="providerForm.baseUrl" class="field-control" type="text" placeholder="https://api.example.com/v1" />
          </label>
          <label class="field-block">
            <span>连接超时(ms)</span>
            <input v-model="providerForm.connectTimeoutMs" class="field-control" type="number" min="0" />
          </label>
          <label class="field-block">
            <span>读取超时(ms)</span>
            <input v-model="providerForm.readTimeoutMs" class="field-control" type="number" min="0" />
          </label>
          <label class="switch-block">
            <input v-model="providerForm.enabled" type="checkbox" />
            <span>启用 Provider</span>
          </label>
          <label class="field-block full-span">
            <span>备注</span>
            <textarea v-model="providerForm.remark" class="field-control textarea-control" rows="4" />
          </label>
        </div>

        <footer class="modal-actions">
          <button class="action-btn" type="button" @click="providerDialogVisible = false">取消</button>
          <button class="action-btn primary" type="button" :disabled="loading.providerSaving" @click="submitProviderForm">
            {{ loading.providerSaving ? '保存中...' : '保存' }}
          </button>
        </footer>
      </div>
    </div>

    <div v-if="modelDialogVisible" class="modal-mask" @click.self="modelDialogVisible = false">
      <div class="modal-card modal-large">
        <header class="modal-head">
          <h3>{{ modelDialogMode === 'create' ? '新增 Model' : '编辑 Model' }}</h3>
          <button class="close-btn" type="button" @click="modelDialogVisible = false">×</button>
        </header>

        <p v-if="modelError" class="error-banner">{{ modelError }}</p>

        <section class="dialog-section">
          <header class="section-head">
            <h4>模型基础配置</h4>
            <p>维护模型本体、Provider 绑定和调用参数。</p>
          </header>

          <div class="form-grid three-column">
            <label class="field-block">
              <span>模型编码</span>
              <input v-model="modelForm.modelCode" class="field-control" type="text" :disabled="modelDialogMode === 'edit'" />
            </label>
            <label class="field-block">
              <span>模型名称</span>
              <input v-model="modelForm.modelName" class="field-control" type="text" />
            </label>
            <label class="field-block">
              <span>所属 Provider</span>
              <select v-model="modelForm.providerCode" class="field-control">
                <option value="">请选择 Provider</option>
                <option v-for="item in providerOptions" :key="item.id" :value="item.providerCode">
                  {{ item.providerName }} ({{ item.providerCode }})
                </option>
              </select>
            </label>

            <label class="field-block">
              <span>Provider 模型标识</span>
              <input v-model="modelForm.apiModel" class="field-control" type="text" />
            </label>
            <label class="field-block">
              <span>能力标签</span>
              <input v-model="modelForm.capabilityTags" class="field-control" type="text" placeholder="chat,reasoning,vision" />
            </label>
            <label class="field-block">
              <span>优先级</span>
              <input v-model="modelForm.priority" class="field-control" type="number" min="0" />
            </label>

            <label class="field-block">
              <span>最大上下文 Token</span>
              <input v-model="modelForm.maxContextTokens" class="field-control" type="number" min="0" />
            </label>
            <label class="field-block">
              <span>最大输出 Token</span>
              <input v-model="modelForm.maxOutputTokens" class="field-control" type="number" min="0" />
            </label>
            <label class="field-block">
              <span>温度参数</span>
              <select v-model="modelForm.temperatureEnabled" class="field-control">
                <option :value="1">启用温度参数</option>
                <option :value="0">禁用温度参数</option>
              </select>
            </label>

            <label class="switch-block">
              <input v-model="modelForm.enabled" type="checkbox" />
              <span>启用 Model</span>
            </label>

            <label class="field-block full-span">
              <span>备注</span>
              <textarea v-model="modelForm.remark" class="field-control textarea-control" rows="3" />
            </label>
          </div>
        </section>

        <section class="dialog-section credential-section">
          <header class="section-head">
            <h4>内部凭证配置</h4>
            <p>凭证与 Model 同弹窗维护。编辑时留空 API Key 表示保持现值。</p>
          </header>

          <div class="form-grid three-column">
            <label class="field-block">
              <span>凭证编码</span>
              <input v-model="modelForm.credentialCode" class="field-control" type="text" />
            </label>
            <label class="field-block">
              <span>Key 版本</span>
              <input v-model="modelForm.keyVersion" class="field-control" type="number" min="1" />
            </label>
            <label class="switch-block">
              <input v-model="modelForm.credentialEnabled" type="checkbox" />
              <span>启用凭证</span>
            </label>

            <label class="field-block full-span">
              <span>API Key</span>
              <input
                v-model="modelForm.apiKeyInput"
                class="field-control"
                type="password"
                :placeholder="modelDialogMode === 'edit' ? '留空表示不修改现有 API Key' : '请输入 API Key'"
              />
            </label>
            <label class="field-block">
              <span>当前脱敏值</span>
              <input class="field-control" type="text" :value="modelForm.apiKeyMasked || '-'" disabled />
            </label>
            <label class="field-block">
              <span>过期时间</span>
              <input v-model="modelForm.expireAt" class="field-control" type="datetime-local" />
            </label>
            <label class="field-block full-span">
              <span>凭证备注</span>
              <textarea v-model="modelForm.credentialRemark" class="field-control textarea-control" rows="3" />
            </label>
          </div>
        </section>

        <footer class="modal-actions">
          <button class="action-btn" type="button" @click="modelDialogVisible = false">取消</button>
          <button class="action-btn primary" type="button" :disabled="loading.modelSaving" @click="submitModelForm">
            {{ loading.modelSaving ? '保存中...' : '保存' }}
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-page {
  min-height: max(100%, calc(100vh - 176px));
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 22px 18px;
  background:
    linear-gradient(180deg, rgba(248, 251, 255, 0.92) 0%, rgba(255, 255, 255, 0.98) 56%),
    radial-gradient(circle at top left, rgba(250, 204, 21, 0.22), transparent 34%),
    radial-gradient(circle at top right, rgba(59, 130, 246, 0.12), transparent 38%);
}

.content-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.head-copy {
  display: grid;
  gap: 6px;
}

.crumb {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  color: #2563eb;
  letter-spacing: 0.08em;
}

.content-head h1 {
  margin: 0;
  font-size: 30px;
  line-height: 1.04;
  color: #111827;
}

.desc {
  margin: 0;
  max-width: 760px;
  font-size: 14px;
  line-height: 1.6;
  color: #64748b;
}

.create-pill,
.action-btn,
.tab-pill,
.status-btn,
.link-btn,
.close-btn {
  border: 0;
  cursor: pointer;
}

.create-pill {
  border-radius: 18px;
  padding: 14px 18px;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  box-shadow: 0 14px 32px rgba(37, 99, 235, 0.24);
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  display: grid;
  gap: 8px;
  padding: 18px 18px 16px;
  border: 1px solid rgba(226, 232, 240, 0.96);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.04);
}

.stat-card strong {
  font-size: 30px;
  line-height: 1;
  color: #0f172a;
}

.stat-card span {
  font-size: 13px;
  color: #64748b;
}

.notice-bar {
  display: flex;
  align-items: center;
  min-height: 42px;
  border-radius: 16px;
  padding: 0 14px;
  font-size: 13px;
  font-weight: 700;
}

.notice-bar.is-success {
  background: rgba(220, 252, 231, 0.9);
  color: #166534;
}

.notice-bar.is-error {
  background: rgba(254, 226, 226, 0.9);
  color: #991b1b;
}

.workspace-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(226, 232, 240, 0.96);
  border-radius: 24px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 24px 48px rgba(15, 23, 42, 0.06);
}

.tab-strip {
  display: flex;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
}

.tab-pill {
  border-radius: 999px;
  padding: 10px 16px;
  background: rgba(241, 245, 249, 0.92);
  color: #475569;
  font-size: 13px;
  font-weight: 700;
}

.tab-pill.active {
  background: linear-gradient(135deg, #dbeafe, #eff6ff);
  color: #1d4ed8;
}

.panel-shell {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 12px;
}

.toolbar-grid {
  display: grid;
  gap: 10px;
  align-items: center;
}

.provider-toolbar {
  grid-template-columns: minmax(220px, 340px) 160px auto;
}

.model-toolbar {
  grid-template-columns: minmax(280px, 1.2fr) 220px 160px auto;
}

.toolbar-actions {
  display: inline-flex;
  justify-content: flex-end;
  gap: 10px;
}

.field-control {
  width: 100%;
  min-width: 0;
  height: 42px;
  border: 1px solid rgba(203, 213, 225, 0.96);
  border-radius: 14px;
  padding: 0 14px;
  background: rgba(255, 255, 255, 0.96);
  font-size: 14px;
  color: #0f172a;
  box-sizing: border-box;
}

select.field-control {
  appearance: none;
}

.textarea-control {
  height: auto;
  min-height: 96px;
  padding-top: 12px;
  padding-bottom: 12px;
  resize: vertical;
}

.action-btn {
  height: 40px;
  border-radius: 14px;
  padding: 0 16px;
  background: rgba(241, 245, 249, 0.96);
  color: #0f172a;
  font-weight: 700;
}

.action-btn.primary {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #fff;
}

.action-btn:disabled,
.status-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.table-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(226, 232, 240, 0.88);
  border-radius: 18px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.98);
}

.table-state,
.empty-cell {
  text-align: center;
  color: #64748b;
}

.table-state {
  padding: 42px 18px;
}

.table-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.data-table th,
.data-table td {
  border-bottom: 1px solid rgba(226, 232, 240, 0.88);
  padding: 14px 12px;
  text-align: left;
  vertical-align: top;
  font-size: 13px;
  color: #0f172a;
}

.data-table th {
  position: sticky;
  top: 0;
  background: #f8fafc;
  font-size: 12px;
  color: #64748b;
  z-index: 1;
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.provider-cell {
  display: grid;
  gap: 2px;
}

.provider-cell strong {
  color: #0f172a;
}

.provider-cell span {
  font-size: 12px;
  color: #64748b;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.soft-tag {
  border-radius: 999px;
  padding: 4px 10px;
  background: rgba(219, 234, 254, 0.76);
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.status-btn,
.state-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 56px;
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 700;
}

.status-btn.is-on,
.state-chip.is-on {
  background: rgba(220, 252, 231, 0.95);
  color: #15803d;
}

.status-btn.is-off,
.state-chip.is-off {
  background: rgba(254, 242, 242, 0.95);
  color: #b91c1c;
}

.row-actions {
  display: inline-flex;
  gap: 8px;
}

.link-btn {
  background: transparent;
  color: #2563eb;
  padding: 0;
  font-size: 13px;
  font-weight: 700;
}

.link-btn.danger {
  color: #dc2626;
}

.empty-cell {
  padding: 36px 12px;
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 4px 0;
}

.page-summary {
  font-size: 13px;
  color: #64748b;
}

.page-controls {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.page-size {
  width: 108px;
}

.pager {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.pager-indicator {
  font-size: 13px;
  color: #475569;
  font-weight: 700;
}

.modal-mask {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.36);
  z-index: 40;
}

.modal-card {
  width: min(720px, calc(100vw - 32px));
  max-height: calc(100vh - 48px);
  overflow: auto;
  border-radius: 24px;
  padding: 20px;
  background: #fff;
  box-shadow: 0 30px 80px rgba(15, 23, 42, 0.22);
}

.modal-large {
  width: min(980px, calc(100vw - 32px));
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.modal-head h3 {
  margin: 0;
  font-size: 22px;
  color: #111827;
}

.close-btn {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: rgba(241, 245, 249, 0.96);
  color: #475569;
  font-size: 22px;
}

.error-banner {
  margin: 0 0 14px;
  border-radius: 14px;
  padding: 10px 12px;
  background: rgba(254, 226, 226, 0.96);
  color: #991b1b;
  font-size: 13px;
  font-weight: 700;
}

.dialog-section + .dialog-section {
  margin-top: 18px;
}

.section-head {
  display: grid;
  gap: 4px;
  margin-bottom: 12px;
}

.section-head h4 {
  margin: 0;
  font-size: 16px;
  color: #111827;
}

.section-head p {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}

.credential-section {
  border-top: 1px solid rgba(226, 232, 240, 0.92);
  padding-top: 18px;
}

.form-grid {
  display: grid;
  gap: 12px 14px;
}

.two-column {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.three-column {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.field-block,
.switch-block {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.field-block span,
.switch-block span {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

.switch-block {
  align-content: end;
}

.switch-block input {
  width: 18px;
  height: 18px;
  margin: 0;
}

.full-span {
  grid-column: 1 / -1;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

@media (max-width: 1280px) {
  .stats-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .provider-toolbar,
  .model-toolbar {
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 900px) {
  .ai-page {
    padding: 16px;
  }

  .content-head {
    flex-direction: column;
  }

  .create-pill {
    width: 100%;
  }

  .stats-row,
  .two-column,
  .three-column {
    grid-template-columns: 1fr;
  }

  .pagination-bar,
  .page-controls {
    flex-direction: column;
    align-items: flex-start;
  }

  .modal-mask {
    padding: 12px;
  }
}
</style>
