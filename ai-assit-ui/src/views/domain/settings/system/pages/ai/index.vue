<script setup>
import { computed, reactive, ref } from 'vue'
import { Delete, EditPen, Plus, Search } from '@element-plus/icons-vue'

const aiTabs = [
  {
    key: 'provider',
    label: '提供商',
    summary: '基础地址、超时、启停',
    icon: 'P'
  },
  {
    key: 'model',
    label: '模型',
    summary: '能力标签、Token、优先级',
    icon: 'M'
  },
  {
    key: 'credential',
    label: '密钥',
    summary: '脱敏密钥、版本、过期时间',
    icon: 'C'
  }
]

const activeAiTab = ref('provider')
const editorMode = ref('create')
const editingId = ref(null)
const searchText = ref('')
const formError = ref('')

let nextId = 1

function createProviderRecord(overrides = {}) {
  return {
    id: nextId++,
    providerCode: '',
    providerName: '',
    baseUrl: '',
    connectTimeoutMs: 3000,
    readTimeoutMs: 12000,
    enabled: true,
    remark: '',
    ...overrides
  }
}

function createModelRecord(overrides = {}) {
  return {
    id: nextId++,
    modelCode: '',
    modelName: '',
    providerCode: '',
    apiModel: '',
    capabilityTags: '',
    maxContextTokens: 32000,
    maxOutputTokens: 4096,
    temperatureEnabled: 1,
    enabled: true,
    priority: 1,
    remark: '',
    ...overrides
  }
}

function createCredentialRecord(overrides = {}) {
  return {
    id: nextId++,
    credentialCode: '',
    providerCode: '',
    modelCode: '',
    apiKeyCiphertext: '',
    apiKeyMasked: '',
    keyVersion: 1,
    enabled: true,
    expireAt: '',
    remark: '',
    ...overrides
  }
}

const aiRecords = reactive({
  provider: [
    createProviderRecord({
      providerCode: 'qwen',
      providerName: '通义千问',
      baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      connectTimeoutMs: 3000,
      readTimeoutMs: 12000,
      enabled: true,
      remark: '主生产接入'
    }),
    createProviderRecord({
      providerCode: 'deepseek',
      providerName: 'DeepSeek',
      baseUrl: 'https://api.deepseek.com',
      connectTimeoutMs: 2500,
      readTimeoutMs: 10000,
      enabled: true,
      remark: '推理备用'
    })
  ],
  model: [
    createModelRecord({
      modelCode: 'qwen-max',
      modelName: '通义千问 Max',
      providerCode: 'qwen',
      apiModel: 'qwen-max-latest',
      capabilityTags: 'chat,stream,tool-call',
      maxContextTokens: 131072,
      maxOutputTokens: 8192,
      temperatureEnabled: 1,
      enabled: true,
      priority: 1,
      remark: '主聊天模型'
    }),
    createModelRecord({
      modelCode: 'deepseek-r1',
      modelName: 'DeepSeek R1',
      providerCode: 'deepseek',
      apiModel: 'deepseek-reasoner',
      capabilityTags: 'chat,reasoning',
      maxContextTokens: 64000,
      maxOutputTokens: 8192,
      temperatureEnabled: 0,
      enabled: true,
      priority: 2,
      remark: '推理优先'
    })
  ],
  credential: [
    createCredentialRecord({
      credentialCode: 'qwen-prod-v1',
      providerCode: 'qwen',
      modelCode: 'qwen-max',
      apiKeyCiphertext: 'enc:***',
      apiKeyMasked: 'sk-qw****************f3',
      keyVersion: 1,
      enabled: true,
      expireAt: '2026-12-31T23:59',
      remark: '生产密钥'
    }),
    createCredentialRecord({
      credentialCode: 'deepseek-dr',
      providerCode: 'deepseek',
      modelCode: 'deepseek-r1',
      apiKeyCiphertext: 'enc:***',
      apiKeyMasked: 'sk-ds****************9a',
      keyVersion: 2,
      enabled: true,
      expireAt: '2026-09-30T23:59',
      remark: '容灾密钥'
    })
  ]
})

const activeAiMeta = computed(() => {
  if (activeAiTab.value === 'model') {
    return {
      title: '模型配置',
      description: '定义模型编码、提供商映射、能力标签和最大 Token 上限。',
      columns: [
        { key: 'modelCode', label: '模型编码' },
        { key: 'modelName', label: '模型名称' },
        { key: 'providerCode', label: '提供商' },
        { key: 'capabilityTags', label: '能力标签' },
        { key: 'priority', label: '优先级' },
        { key: 'enabled', label: '状态' }
      ],
      fields: [
        { key: 'modelCode', label: '模型编码', type: 'text', required: true },
        { key: 'modelName', label: '模型名称', type: 'text', required: true },
        { key: 'providerCode', label: '提供商编码', type: 'text', required: true },
        { key: 'apiModel', label: 'API Model', type: 'text', required: true },
        { key: 'capabilityTags', label: '能力标签', type: 'text', required: true },
        { key: 'maxContextTokens', label: '最大上下文 Token', type: 'number', required: true },
        { key: 'maxOutputTokens', label: '最大输出 Token', type: 'number', required: true },
        { key: 'temperatureEnabled', label: '温度参数', type: 'select', required: true },
        { key: 'priority', label: '优先级', type: 'number', required: true },
        { key: 'enabled', label: '启用状态', type: 'boolean', required: true },
        { key: 'remark', label: '备注', type: 'textarea', required: false }
      ],
      hint: '模型配置承接 provider，决定可用能力和上下文上限。'
    }
  }

  if (activeAiTab.value === 'credential') {
    return {
      title: '密钥配置',
      description: '管理脱敏密钥、密钥版本和过期时间，前端不直接展示明文。',
      columns: [
        { key: 'credentialCode', label: '密钥编码' },
        { key: 'providerCode', label: '提供商' },
        { key: 'modelCode', label: '模型' },
        { key: 'apiKeyMasked', label: '脱敏值' },
        { key: 'expireAt', label: '过期时间' },
        { key: 'enabled', label: '状态' }
      ],
      fields: [
        { key: 'credentialCode', label: '密钥编码', type: 'text', required: true },
        { key: 'providerCode', label: '提供商编码', type: 'text', required: true },
        { key: 'modelCode', label: '模型编码', type: 'text', required: true },
        { key: 'apiKeyCiphertext', label: 'API Key 密文', type: 'textarea', required: true },
        { key: 'apiKeyMasked', label: 'API Key 脱敏值', type: 'text', required: true },
        { key: 'keyVersion', label: '密钥版本', type: 'number', required: true },
        { key: 'enabled', label: '启用状态', type: 'boolean', required: true },
        { key: 'expireAt', label: '过期时间', type: 'datetime-local', required: true },
        { key: 'remark', label: '备注', type: 'textarea', required: false }
      ],
      hint: '密钥配置只保留脱敏展示和版本管理，真实密文由后端托管。'
    }
  }

  return {
    title: '提供商配置',
    description: '管理模型接入的基础地址、超时和启停状态。',
    columns: [
      { key: 'providerCode', label: '提供商编码' },
      { key: 'providerName', label: '提供商名称' },
      { key: 'baseUrl', label: 'Base URL' },
      { key: 'connectTimeoutMs', label: '连接超时' },
      { key: 'readTimeoutMs', label: '读取超时' },
      { key: 'enabled', label: '状态' }
    ],
    fields: [
      { key: 'providerCode', label: '提供商编码', type: 'text', required: true },
      { key: 'providerName', label: '提供商名称', type: 'text', required: true },
      { key: 'baseUrl', label: 'Base URL', type: 'text', required: true },
      { key: 'connectTimeoutMs', label: '连接超时 (ms)', type: 'number', required: true },
      { key: 'readTimeoutMs', label: '读取超时 (ms)', type: 'number', required: true },
      { key: 'enabled', label: '启用状态', type: 'boolean', required: true },
      { key: 'remark', label: '备注', type: 'textarea', required: false }
    ],
    hint: 'providerCode 是模型和密钥的主关联键，建议稳定且不可随意修改。'
  }
})

const aiStats = computed(() => [
  { label: '提供商', value: aiRecords.provider.length },
  { label: '模型', value: aiRecords.model.length },
  { label: '密钥', value: aiRecords.credential.length }
])

const filteredAiRecords = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  const list = aiRecords[activeAiTab.value]
  if (!keyword) return list
  return list.filter((item) => JSON.stringify(item).toLowerCase().includes(keyword))
})

function switchAiTab(key) {
  activeAiTab.value = key
  openCreate()
}

function openCreate() {
  editorMode.value = 'create'
  editingId.value = null
  formError.value = ''
  Object.assign(draft(), createEmptyDraft())
}

function createEmptyDraft() {
  if (activeAiTab.value === 'model') return createModelRecord({ enabled: true })
  if (activeAiTab.value === 'credential') return createCredentialRecord({ enabled: true })
  return createProviderRecord({ enabled: true })
}

const draftState = reactive(createEmptyDraft())
function draft() {
  return draftState
}

function openEdit(row) {
  editorMode.value = 'edit'
  editingId.value = row.id
  formError.value = ''
  Object.assign(draftState, JSON.parse(JSON.stringify(row)))
}

function saveRecord() {
  const schema = activeAiMeta.value
  const missing = schema.fields.find((field) => field.required && isEmpty(draftState[field.key]))
  if (missing) {
    formError.value = `请补全「${missing.label}」`
    return
  }

  const cloned = JSON.parse(JSON.stringify(draftState))
  const list = aiRecords[activeAiTab.value]
  if (editorMode.value === 'edit' && editingId.value !== null) {
    const index = list.findIndex((item) => item.id === editingId.value)
    if (index !== -1) list[index] = cloned
  } else {
    list.unshift(cloned)
  }

  formError.value = ''
  editorMode.value = 'edit'
  editingId.value = cloned.id
}

function deleteRecord(row) {
  const list = aiRecords[activeAiTab.value]
  const index = list.findIndex((item) => item.id === row.id)
  if (index !== -1) list.splice(index, 1)
  if (editingId.value === row.id) openCreate()
}

function isEmpty(value) {
  return value === '' || value === null || value === undefined
}

function formatValue(row, key) {
  if (key === 'enabled') return row.enabled ? '启用' : '禁用'
  if (key === 'connectTimeoutMs') return `${row.connectTimeoutMs} ms`
  if (key === 'readTimeoutMs') return `${row.readTimeoutMs} ms`
  if (key === 'expireAt') return row.expireAt ? row.expireAt.replace('T', ' ') : '-'
  return row[key] || '-'
}

openCreate()
</script>

<template>
  <div class="content-head">
    <div>
      <p class="eyebrow">AI 接入</p>
      <h2>{{ activeAiMeta.title }}</h2>
      <p class="section-desc">{{ activeAiMeta.description }}</p>
    </div>
  </div>

  <div class="hero-stats">
    <article v-for="item in aiStats" :key="item.label" class="hero-stat">
      <strong>{{ item.value }}</strong>
      <span>{{ item.label }}</span>
    </article>
  </div>

  <div class="ai-layout">
    <aside class="ai-tabs">
      <button
        v-for="tab in aiTabs"
        :key="tab.key"
        type="button"
        class="ai-tab"
        :class="{ active: activeAiTab === tab.key }"
        @click="switchAiTab(tab.key)"
      >
        <span>{{ tab.icon }}</span>
        <div>
          <strong>{{ tab.label }}</strong>
          <small>{{ tab.summary }}</small>
        </div>
      </button>
    </aside>

    <div class="ai-workbench">
      <div class="toolbar">
        <label class="search-box">
          <Search :size="16" />
          <input v-model="searchText" type="search" placeholder="搜索当前配置" />
        </label>
        <button class="btn secondary" type="button" @click="openCreate">
          <Plus :size="16" />
          新增
        </button>
      </div>

      <div class="table-card">
        <table class="config-table">
          <thead>
            <tr>
              <th v-for="column in activeAiMeta.columns" :key="column.key">{{ column.label }}</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in filteredAiRecords" :key="row.id">
              <td v-for="column in activeAiMeta.columns" :key="column.key">
                {{ formatValue(row, column.key) }}
              </td>
              <td class="row-actions">
                <button class="icon-btn" type="button" @click="openEdit(row)">
                  <EditPen :size="15" />
                </button>
                <button class="icon-btn danger" type="button" @click="deleteRecord(row)">
                  <Delete :size="15" />
                </button>
              </td>
            </tr>
            <tr v-if="!filteredAiRecords.length">
              <td :colspan="activeAiMeta.columns.length + 1" class="empty-row">没有匹配的记录</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="editor-card">
        <div class="content-head compact">
          <div>
            <p class="eyebrow">编辑器</p>
            <h3>{{ editorMode === 'edit' ? '编辑记录' : '新增记录' }}</h3>
          </div>
          <button class="btn secondary" type="button" @click="openCreate">重置</button>
        </div>

        <p class="editor-hint">{{ activeAiMeta.hint }}</p>

        <div class="form-grid">
          <label v-for="field in activeAiMeta.fields" :key="field.key" class="form-field">
            <span>{{ field.label }}<em v-if="field.required">*</em></span>
            <template v-if="field.type === 'textarea'">
              <textarea v-model="draftState[field.key]" rows="3" />
            </template>
            <template v-else-if="field.type === 'boolean'">
              <select v-model="draftState[field.key]">
                <option :value="true">启用</option>
                <option :value="false">禁用</option>
              </select>
            </template>
            <template v-else-if="field.type === 'select'">
              <select v-model="draftState[field.key]">
                <option :value="1">启用</option>
                <option :value="0">禁用</option>
              </select>
            </template>
            <template v-else-if="field.type === 'datetime-local'">
              <input v-model="draftState[field.key]" type="datetime-local" />
            </template>
            <template v-else-if="field.type === 'number'">
              <input v-model.number="draftState[field.key]" type="number" />
            </template>
            <template v-else>
              <input v-model="draftState[field.key]" type="text" />
            </template>
          </label>
        </div>

        <p v-if="formError" class="form-error">{{ formError }}</p>

        <div class="editor-actions">
          <button class="btn secondary" type="button" @click="openCreate">新建空白</button>
          <button class="btn primary" type="button" @click="saveRecord">
            {{ editorMode === 'edit' ? '保存修改' : '保存新增' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
