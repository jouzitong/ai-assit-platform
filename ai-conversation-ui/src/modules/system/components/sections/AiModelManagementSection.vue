<script setup lang="ts">
import { CircleCheck, CircleClose, Connection, Delete, EditPen, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { AppPagination } from '../../../../components'
import {
  batchSaveAiModels,
  createAiClient,
  deleteAiClient,
  deleteAiModelManage,
  discoverAiClientModels,
  editAiModelManage,
  listAiClients,
  searchAiModelManages,
  updateAiClient,
  type AiClientConfigItem,
  type AiClientConfigUpsertPayload,
  type AiModelManageItem,
  type AiProviderModelItem,
} from '../../api/aiPlatform'

const clients = ref<AiClientConfigItem[]>([])
const models = ref<AiModelManageItem[]>([])
const loading = ref(false)
const saving = ref(false)
const discovering = ref(false)
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const activeStep = ref(0)
const editingClientId = ref<string | number | null>(null)
const remoteModels = ref<AiProviderModelItem[]>([])
const selectedModels = ref<string[]>([])
const remoteKeyword = ref('')
const selectedModelIdKeys = ref<Set<string>>(new Set())
const batchAction = ref<'enable' | 'disable' | 'delete' | null>(null)

const clientForm = reactive({
  clientCode: '',
  clientName: '',
  clientType: 1,
  baseUrl: '',
  apiKey: '',
  enabled: true,
  extJsonText: '',
})

const clientTypeOptions = [
  { value: 1, label: 'OpenAI 兼容 / Spring AI' },
  { value: 2, label: 'AI Agent 客户端' },
]

const filteredRemoteModels = computed(() => {
  const search = remoteKeyword.value.trim().toLowerCase()
  if (!search) return remoteModels.value
  return remoteModels.value.filter(item => `${item.id} ${item.ownedBy || ''}`.toLowerCase().includes(search))
})
const selectedModelIds = computed(() => [...selectedModelIdKeys.value])
const allPageModelsSelected = computed(() => models.value.length > 0 && models.value.every(model => selectedModelIdKeys.value.has(String(model.id))))
const somePageModelsSelected = computed(() => models.value.some(model => selectedModelIdKeys.value.has(String(model.id))))

function clientTypeName(type?: number) {
  return clientTypeOptions.find(item => item.value === type)?.label || '未知客户端'
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function parseExtJson() {
  const text = clientForm.extJsonText.trim()
  if (!text) return null
  const value = JSON.parse(text)
  if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error('扩展配置必须是 JSON 对象')
  return value as Record<string, unknown>
}

function buildClientPayload(): AiClientConfigUpsertPayload {
  return {
    clientCode: clientForm.clientCode.trim(),
    clientName: clientForm.clientName.trim(),
    clientType: clientForm.clientType,
    baseUrl: clientForm.baseUrl.trim() || undefined,
    apiKey: clientForm.apiKey.trim() || undefined,
    enabled: clientForm.enabled,
    extJson: parseExtJson(),
  }
}

function validateClient() {
  if (!clientForm.clientCode.trim()) return '请输入客户端编码'
  if (!clientForm.clientName.trim()) return '请输入客户端名称'
  if (!clientForm.baseUrl.trim()) return '请输入 Base URL'
  if (dialogMode.value === 'create' && !clientForm.apiKey.trim()) return '新增客户端必须填写 API Key'
  return ''
}

function resetDialog() {
  Object.assign(clientForm, {
    clientCode: '', clientName: '', clientType: 1, baseUrl: '', apiKey: '', enabled: true, extJsonText: '',
  })
  editingClientId.value = null
  activeStep.value = 0
  remoteModels.value = []
  selectedModels.value = []
  remoteKeyword.value = ''
}

function openCreate() {
  resetDialog()
  dialogMode.value = 'create'
  dialogVisible.value = true
}

function openEdit(client: AiClientConfigItem) {
  resetDialog()
  dialogMode.value = 'edit'
  editingClientId.value = client.id
  clientForm.clientCode = client.clientCode || ''
  clientForm.clientName = client.clientName || ''
  clientForm.clientType = client.clientType || 1
  clientForm.baseUrl = client.baseUrl || ''
  clientForm.enabled = client.enabled !== false
  clientForm.extJsonText = client.extJson ? JSON.stringify(client.extJson, null, 2) : ''
  dialogVisible.value = true
}

async function saveClientAndDiscover() {
  const validation = validateClient()
  if (validation) return ElMessage.error(validation)
  saving.value = true
  try {
    const payload = buildClientPayload()
    const saved = editingClientId.value == null
      ? await createAiClient(payload)
      : await updateAiClient(editingClientId.value, payload)
    editingClientId.value = saved.id
    dialogMode.value = 'edit'
    activeStep.value = 1
    await loadRemoteModels(saved.id)
    await loadClients()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '客户端保存失败')
  }
  finally {
    saving.value = false
  }
}

async function loadRemoteModels(clientId: string | number) {
  discovering.value = true
  try {
    const [discovered, configured] = await Promise.all([
      discoverAiClientModels(clientId),
      searchAiModelManages({ page: 1, size: 500 }),
    ])
    remoteModels.value = discovered || []
    selectedModels.value = (configured?.list || [])
      .filter(item => String(item.clientId) === String(clientId) && item.enabled !== false)
      .map(item => item.apiModel || '')
      .filter(Boolean)
  }
  catch (error) {
    remoteModels.value = []
    ElMessage.error(error instanceof Error ? error.message : '标准模型列表获取失败')
  }
  finally {
    discovering.value = false
  }
}

async function submitModels() {
  if (editingClientId.value == null) return
  if (!selectedModels.value.length) return ElMessage.warning('至少勾选一个启用模型')
  saving.value = true
  try {
    await batchSaveAiModels(editingClientId.value, selectedModels.value)
    ElMessage.success(`已保存 ${selectedModels.value.length} 个启用模型`)
    dialogVisible.value = false
    await loadAll()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型保存失败')
  }
  finally {
    saving.value = false
  }
}

async function deleteClient(client: AiClientConfigItem) {
  try {
    await ElMessageBox.confirm(`确认删除客户端「${client.clientName || client.clientCode}」吗？`, '删除客户端', { type: 'warning' })
    await deleteAiClient(client.id)
    ElMessage.success('客户端已删除')
    await loadAll()
  }
  catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '客户端删除失败')
  }
}

async function toggleClient(client: AiClientConfigItem, enabled: boolean) {
  try {
    await updateAiClient(client.id, { enabled })
    client.enabled = enabled
    ElMessage.success(`客户端已${enabled ? '启用' : '停用'}`)
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '客户端状态更新失败')
  }
}

async function toggleModel(model: AiModelManageItem, enabled: boolean) {
  try {
    await editAiModelManage(model.id, { enabled })
    model.enabled = enabled
    ElMessage.success(`模型已${enabled ? '启用' : '停用'}`)
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型状态更新失败')
  }
}

function isModelSelected(model: AiModelManageItem) {
  return selectedModelIdKeys.value.has(String(model.id))
}

function setModelSelected(model: AiModelManageItem, selected: boolean) {
  const next = new Set(selectedModelIdKeys.value)
  if (selected) next.add(String(model.id))
  else next.delete(String(model.id))
  selectedModelIdKeys.value = next
}

function togglePageModels(selected: boolean) {
  const next = new Set(selectedModelIdKeys.value)
  models.value.forEach((model) => {
    if (selected) next.add(String(model.id))
    else next.delete(String(model.id))
  })
  selectedModelIdKeys.value = next
}

function clearModelSelection() {
  selectedModelIdKeys.value = new Set()
}

async function handleBatchAction(action: 'enable' | 'disable' | 'delete') {
  const ids = selectedModelIds.value
  if (!ids.length || batchAction.value) return

  const actionLabel = action === 'enable' ? '启用' : action === 'disable' ? '禁用' : '删除'
  try {
    await ElMessageBox.confirm(
      `确认批量${actionLabel}已选择的 ${ids.length} 个模型吗？${action === 'delete' ? '删除后不可恢复。' : ''}`,
      `批量${actionLabel}模型`,
      {
        type: action === 'delete' ? 'warning' : 'info',
        confirmButtonText: `确认${actionLabel}`,
        cancelButtonText: '取消',
      },
    )
  }
  catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : `批量${actionLabel}确认失败`)
    }
    return
  }

  batchAction.value = action
  try {
    const results = await Promise.allSettled(ids.map(async (id) => {
      if (action === 'delete') {
        const deleted = await deleteAiModelManage(id)
        if (!deleted) throw new Error(`模型 ${id} 删除失败`)
        return
      }
      await editAiModelManage(id, { enabled: action === 'enable' })
    }))
    const successfulIds = ids.filter((_, index) => results[index]?.status === 'fulfilled')
    const failedCount = ids.length - successfulIds.length
    const nextSelection = new Set(selectedModelIdKeys.value)
    successfulIds.forEach(id => nextSelection.delete(String(id)))
    selectedModelIdKeys.value = nextSelection

    if (action === 'delete' && successfulIds.length) {
      const remainingTotal = Math.max(0, total.value - successfulIds.length)
      const lastPage = Math.max(1, Math.ceil(remainingTotal / pageSize.value))
      currentPage.value = Math.min(currentPage.value, lastPage)
    }

    let refreshFailed = false
    try {
      await Promise.all([loadModels(), loadClients()])
    }
    catch {
      refreshFailed = true
    }
    if (refreshFailed) {
      ElMessage.warning(`批量${actionLabel}完成：成功 ${successfulIds.length} 个，失败 ${failedCount} 个；列表刷新失败，请手动刷新`)
    } else if (failedCount) {
      ElMessage.warning(`已${actionLabel} ${successfulIds.length} 个模型，${failedCount} 个操作失败；失败项已保留选中，可重试`)
    } else {
      ElMessage.success(`已批量${actionLabel} ${successfulIds.length} 个模型`)
    }
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `批量${actionLabel}失败`)
  }
  finally {
    batchAction.value = null
  }
}

async function deleteModel(model: AiModelManageItem) {
  try {
    await ElMessageBox.confirm(`确认删除模型「${model.modelName || model.modelCode}」吗？`, '删除模型', { type: 'warning' })
    await deleteAiModelManage(model.id)
    const nextSelection = new Set(selectedModelIdKeys.value)
    nextSelection.delete(String(model.id))
    selectedModelIdKeys.value = nextSelection
    ElMessage.success('模型已删除')
    await loadModels()
    await loadClients()
  }
  catch (error) {
    if (error !== 'cancel') ElMessage.error(error instanceof Error ? error.message : '模型删除失败')
  }
}

async function loadClients() {
  clients.value = await listAiClients() || []
}

async function loadModels() {
  const payload = await searchAiModelManages({ page: currentPage.value, size: pageSize.value, keyword: keyword.value.trim() || undefined })
  models.value = payload?.list || []
  total.value = Number(payload?.pageInfo?.total || models.value.length)
}

async function loadAll() {
  loading.value = true
  try { await Promise.all([loadClients(), loadModels()]) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '模型配置加载失败') }
  finally { loading.value = false }
}

async function searchModels() { currentPage.value = 1; await loadModels() }
async function changePage(page: number) { currentPage.value = page; await loadModels() }
async function changeSize(size: number) { pageSize.value = size; currentPage.value = 1; await loadModels() }

onMounted(loadAll)
</script>

<template>
  <section class="model-page" v-loading="loading">
    <div class="client-panel">
      <header class="section-head">
        <div><h2>AI 客户端</h2><p>连接信息和密钥只配置一次，一个客户端可启用多个远端模型。</p></div>
        <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新增客户端</el-button>
      </header>
      <div v-if="clients.length" class="client-grid">
        <article v-for="client in clients" :key="client.id" class="client-card">
          <div class="client-card__top">
            <div class="client-card__icon"><el-icon><Connection /></el-icon></div>
            <div class="client-card__identity"><strong>{{ client.clientName }}</strong><span>{{ client.clientCode }}</span></div>
            <el-switch :model-value="client.enabled !== false" @change="value => toggleClient(client, value)" />
          </div>
          <div class="client-card__url">{{ client.baseUrl || '-' }}</div>
          <div class="client-card__meta"><span>{{ clientTypeName(client.clientType) }}</span><span>{{ client.modelCount || 0 }} 个模型</span><span>{{ client.apiKeyMasked || '未配置密钥' }}</span></div>
          <div class="client-card__actions">
            <el-button plain @click="openEdit(client)"><el-icon><EditPen /></el-icon>配置与模型</el-button>
            <el-button plain type="danger" :disabled="Boolean(client.modelCount)" @click="deleteClient(client)"><el-icon><Delete /></el-icon></el-button>
          </div>
        </article>
      </div>
      <el-empty v-else description="先新增 AI 客户端，再从标准接口获取模型" :image-size="72" />
    </div>

    <div class="model-panel">
      <header class="section-head section-head--models">
        <div><h2>已配置模型</h2><p>模型来自客户端标准接口；这里只管理启用状态，不手工录入远端模型标识。</p></div>
        <div class="model-tools">
          <el-input v-model="keyword" clearable placeholder="搜索模型或客户端" @keyup.enter="searchModels"><template #prefix><el-icon><Search /></el-icon></template></el-input>
          <el-button plain @click="loadAll"><el-icon><RefreshRight /></el-icon>刷新</el-button>
        </div>
      </header>
      <div v-if="models.length || selectedModelIds.length" class="model-bulkbar">
        <div class="model-bulkbar__selection">
          <el-checkbox
            :model-value="allPageModelsSelected"
            :indeterminate="somePageModelsSelected && !allPageModelsSelected"
            :disabled="Boolean(batchAction) || !models.length"
            @change="value => togglePageModels(Boolean(value))"
          >
            全选本页
          </el-checkbox>
          <span>已选择 <strong>{{ selectedModelIds.length }}</strong> 个模型</span>
          <el-button text :disabled="Boolean(batchAction) || !selectedModelIds.length" @click="clearModelSelection">清空</el-button>
        </div>
        <div class="model-bulkbar__actions">
          <el-button
            type="success"
            plain
            :icon="CircleCheck"
            :loading="batchAction === 'enable'"
            :disabled="Boolean(batchAction) || !selectedModelIds.length"
            @click="handleBatchAction('enable')"
          >批量启用</el-button>
          <el-button
            plain
            :icon="CircleClose"
            :loading="batchAction === 'disable'"
            :disabled="Boolean(batchAction) || !selectedModelIds.length"
            @click="handleBatchAction('disable')"
          >批量禁用</el-button>
          <el-button
            type="danger"
            plain
            :icon="Delete"
            :loading="batchAction === 'delete'"
            :disabled="Boolean(batchAction) || !selectedModelIds.length"
            @click="handleBatchAction('delete')"
          >批量删除</el-button>
        </div>
      </div>
      <div v-if="models.length" class="model-grid">
        <article v-for="model in models" :key="model.id" :class="['model-card', { 'model-card--selected': isModelSelected(model) }]">
          <div class="model-card__head"><el-checkbox :model-value="isModelSelected(model)" :disabled="Boolean(batchAction)" :aria-label="`选择模型 ${model.modelName || model.apiModel || model.modelCode}`" @change="value => setModelSelected(model, Boolean(value))" /><div class="model-card__identity"><strong>{{ model.modelName || model.apiModel }}</strong><span>{{ model.modelCode }}</span></div><el-switch class="model-card__status-switch" size="small" :model-value="model.enabled !== false" :disabled="Boolean(batchAction)" inline-prompt active-text="启用" inactive-text="停用" @change="value => toggleModel(model, value)" /></div>
          <div class="model-card__remote">{{ model.apiModel }}</div>
          <div class="model-card__foot"><span>{{ model.clientName || clientTypeName(model.clientType) }}</span><span>{{ formatTime(model.updateTime) }}</span><el-button text type="danger" :disabled="Boolean(batchAction)" @click="deleteModel(model)"><el-icon><Delete /></el-icon></el-button></div>
        </article>
      </div>
      <el-empty v-else description="尚未启用任何模型" :image-size="72" />
      <AppPagination v-model:current-page="currentPage" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]" :total="total" @current-change="changePage" @size-change="changeSize" />
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增 AI 客户端' : '配置 AI 客户端与模型'" width="780px" destroy-on-close>
      <el-steps :active="activeStep" finish-status="success" align-center class="wizard-steps"><el-step title="客户端配置" description="连接地址与鉴权" /><el-step title="选择模型" description="标准接口发现并勾选" /></el-steps>
      <el-form v-if="activeStep === 0" label-position="top" class="client-form">
        <div class="form-grid"><el-form-item label="客户端名称" required><el-input v-model="clientForm.clientName" placeholder="例如：生产环境 OpenAI" /></el-form-item><el-form-item label="客户端编码" required><el-input v-model="clientForm.clientCode" :disabled="dialogMode === 'edit'" placeholder="例如：openai-prod" /></el-form-item></div>
        <el-form-item label="客户端类型" required><el-select v-model="clientForm.clientType"><el-option v-for="option in clientTypeOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
        <el-form-item label="Base URL" required><el-input v-model="clientForm.baseUrl" placeholder="https://api.openai.com/v1" /></el-form-item>
        <el-form-item :label="dialogMode === 'create' ? 'API Key' : 'API Key（留空保留原密钥）'" :required="dialogMode === 'create'"><el-input v-model="clientForm.apiKey" type="password" show-password autocomplete="new-password" /></el-form-item>
        <div class="form-grid"><el-form-item label="启用状态"><el-switch v-model="clientForm.enabled" inline-prompt active-text="启用" inactive-text="停用" /></el-form-item><el-form-item label="扩展配置"><el-input v-model="clientForm.extJsonText" placeholder='例如：{"modelListTimeoutMs": 30000}' /></el-form-item></div>
      </el-form>
      <div v-else class="model-picker">
        <el-alert title="模型列表来自该客户端的标准 GET /v1/models 接口。取消勾选已保存模型后，该模型会被停用。" type="info" :closable="false" show-icon />
        <div class="model-picker__toolbar"><el-input v-model="remoteKeyword" clearable placeholder="搜索模型 ID 或所有者"><template #prefix><el-icon><Search /></el-icon></template></el-input><el-button plain :loading="discovering" @click="editingClientId != null && loadRemoteModels(editingClientId)"><el-icon><RefreshRight /></el-icon>重新获取</el-button><span>已选 {{ selectedModels.length }} / {{ remoteModels.length }}</span></div>
        <div v-loading="discovering" class="model-picker__list">
          <el-checkbox-group v-model="selectedModels">
            <label v-for="model in filteredRemoteModels" :key="model.id" class="model-option"><el-checkbox :value="model.id" /><span class="model-option__id">{{ model.id }}</span><span>{{ model.ownedBy || '未声明所有者' }}</span></label>
          </el-checkbox-group>
          <el-empty v-if="!discovering && !filteredRemoteModels.length" description="标准接口未返回可用模型" :image-size="64" />
        </div>
      </div>
      <template #footer><div class="dialog-footer"><el-button @click="dialogVisible = false">取消</el-button><el-button v-if="activeStep === 1" @click="activeStep = 0">上一步</el-button><el-button v-if="activeStep === 0" type="primary" :loading="saving" @click="saveClientAndDiscover">保存客户端并获取模型</el-button><el-button v-else type="primary" :loading="saving" @click="submitModels">保存启用模型</el-button></div></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.model-page{display:grid;gap:16px;min-height:0;overflow:auto}.client-panel,.model-panel{border:1px solid var(--system-border);border-radius:16px;background:var(--system-surface-strong);box-shadow:var(--system-shadow)}.section-head{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px 18px;border-bottom:1px solid var(--system-border-subtle)}.section-head h2{margin:0;color:var(--system-title);font-size:16px}.section-head p{margin:5px 0 0;color:var(--system-text-muted);font-size:12px}.client-grid,.model-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(310px,1fr));gap:12px;padding:14px}.client-card,.model-card{display:grid;gap:11px;padding:14px;border:1px solid var(--system-border);border-radius:13px;background:var(--system-surface-solid)}.model-card--selected{border-color:var(--system-accent-border);box-shadow:var(--system-accent-shadow)}.client-card__top,.model-card__head,.client-card__actions,.model-card__foot,.client-card__meta,.model-picker__toolbar{display:flex;align-items:center;gap:10px}.client-card__icon{display:grid;place-items:center;width:36px;height:36px;border-radius:10px;background:var(--system-accent-bg-strong);color:var(--system-accent-text)}.client-card__identity,.model-card__identity{display:grid;gap:3px;min-width:0;flex:1}.model-card__status-switch{flex:0 0 auto}.client-card__identity strong,.model-card strong{color:var(--system-title)}.client-card__identity span,.model-card__head span,.client-card__url,.model-card__remote{color:var(--system-text-soft);font-size:12px;overflow-wrap:anywhere}.client-card__meta{flex-wrap:wrap;color:var(--system-text-muted);font-size:11px}.client-card__meta span{padding:3px 7px;border-radius:999px;background:var(--system-surface-muted)}.client-card__actions{justify-content:flex-end}.model-tools{display:flex;gap:8px}.model-tools :deep(.el-input){width:240px}.model-bulkbar{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 14px;border-bottom:1px solid var(--system-border-subtle);background:var(--system-surface-muted)}.model-bulkbar__selection,.model-bulkbar__actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.model-bulkbar__selection>span{color:var(--system-text-muted);font-size:12px}.model-bulkbar__selection strong{color:var(--system-accent-text)}.model-card__remote{padding:9px 10px;border-radius:9px;background:var(--system-surface-muted)}.model-card__foot{color:var(--system-text-muted);font-size:11px}.model-card__foot span:first-child{flex:1}.model-panel :deep(.app-pagination){padding:0 14px 14px}.wizard-steps{margin:4px 0 24px}.client-form :deep(.el-select){width:100%}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.model-picker{display:grid;gap:14px}.model-picker__toolbar :deep(.el-input){flex:1}.model-picker__toolbar>span{color:var(--system-text-muted);font-size:12px;white-space:nowrap}.model-picker__list{min-height:250px;max-height:380px;overflow:auto;border:1px solid var(--system-border);border-radius:12px}.model-option{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:10px;padding:10px 12px;border-bottom:1px solid var(--system-border-subtle);cursor:pointer}.model-option:hover{background:var(--system-surface-muted)}.model-option__id{color:var(--system-title);font-size:13px;overflow-wrap:anywhere}.model-option>span:last-child{color:var(--system-text-muted);font-size:11px}.dialog-footer{display:flex;justify-content:flex-end;gap:8px}@media(max-width:720px){.section-head,.section-head--models,.model-bulkbar{align-items:stretch;flex-direction:column}.model-tools,.form-grid{display:grid;grid-template-columns:1fr}.model-tools :deep(.el-input){width:100%}.client-grid,.model-grid{grid-template-columns:1fr}.model-bulkbar__actions :deep(.el-button){flex:1}}
</style>
