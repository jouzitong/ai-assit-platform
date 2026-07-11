<script setup lang="ts">
import { Connection, DataBoard, EditPen, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AppPagination } from '../../../../components'
import {
  createDbDataSource,
  searchDbDataSources,
  syncDbTableKnowledge,
  testDbDataSourceConnection,
  type DatabaseSourceConfig,
  type DbDataSourceItem,
  type DbDataSourceConfig,
  type HttpApiSourceConfig,
  updateDbDataSource,
} from '../../api/dataSources'
import { getEnumLabel, getEnumOptions, loadServiceEnums } from '../../../../stores/enums'
import { SERVICE_NAMES } from '../../../../config/services'

type DialogMode = 'create' | 'edit'

const router = useRouter()
const keyword = ref('')
const pageSize = ref(20)
const currentPage = ref(1)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const knowledgeSyncSubmitting = ref(false)
const errorMessage = ref('')
const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('create')
const editingId = ref<string | number | null>(null)
const dataSources = ref<DbDataSourceItem[]>([])

const pageSizeOptions = [5, 10, 20, 50, 100, 200, 500]
const form = reactive(createEmptyForm())
const sourceTypeOptions = computed(() => getEnumOptions('dbDataSourceType', SERVICE_NAMES.DB_ENGINE))
const syncModeOptions = computed(() => getEnumOptions('dbDataSourceSyncMode', SERVICE_NAMES.DB_ENGINE))
const authTypeOptions = computed(() => getEnumOptions('dbDataSourceAuthType', SERVICE_NAMES.DB_ENGINE))
const dbTypeOptions = computed(() => getEnumOptions('dbDataSourceDbType', SERVICE_NAMES.DB_ENGINE))
const sourceKind = computed(() => resolveSourceKind(form.sourceType))
const isDatabaseSource = computed(() => sourceKind.value === 'DATABASE')
const isHttpApiSource = computed(() => sourceKind.value === 'HTTP_API')
const authKind = computed(() => resolveAuthKind(form.authType))

function resolveDefaultEnumValue(enumName: string, fallback: unknown = '') {
  const [firstOption] = getEnumOptions(enumName, SERVICE_NAMES.DB_ENGINE)
  return firstOption?.value ?? fallback
}

const sourceCards = computed(() => dataSources.value.map((item) => mapDataSourceCard(item)))

function createEmptyForm() {
  return {
    sourceKey: '',
    sourceName: '',
    sourceType: resolveDefaultEnumValue('dbDataSourceType', 1),
    ownerTeam: '',
    ownerUser: '',
    enabled: true,
    syncMode: resolveDefaultEnumValue('dbDataSourceSyncMode', 1),
    summary: '',
    remark: '',
    dbType: '',
    connectionMode: 'HOST_PORT',
    jdbcUrl: '',
    host: '',
    port: '',
    databaseName: '',
    schemaName: '',
    baseUrl: '',
    connectTimeoutMs: '',
    readTimeoutMs: '',
    writeTimeoutMs: '',
    authType: resolveDefaultEnumValue('dbDataSourceAuthType', 0),
    username: '',
    passwordCiphertext: '',
    tokenCiphertext: '',
    accessKey: '',
    secretKeyCiphertext: '',
    credentialRef: '',
    driverPropertiesText: '',
    httpAttributesText: '',
  }
}

function resetForm() {
  Object.assign(form, createEmptyForm())
}

function normalizeEnumValue(value?: unknown) {
  return String(value || '').trim().replace(/-/g, '_').toUpperCase()
}

function resolveSourceKind(value?: unknown) {
  const normalized = normalizeEnumValue(value)
  if (normalized === '1' || normalized === 'DATABASE') {
    return 'DATABASE'
  }
  if (normalized === '2' || normalized === 'HTTP_API') {
    return 'HTTP_API'
  }
  return normalized
}

function resolveAuthKind(value?: unknown) {
  const normalized = normalizeEnumValue(value)
  const authKindMap: Record<string, string> = {
    '0': 'NONE',
    '1': 'BASIC',
    '2': 'BEARER',
    '3': 'AK_SK',
    '4': 'API_KEY',
  }
  return authKindMap[normalized] || normalized
}

function resolveTotal(payloadTotal?: number) {
  const numericTotal = Number(payloadTotal)
  return Number.isFinite(numericTotal) ? numericTotal : dataSources.value.length
}

function asDatabaseConfig(config?: DbDataSourceConfig): DatabaseSourceConfig | undefined {
  if (!config) {
    return undefined
  }
  if (('configType' in config && config.configType === 'DATABASE') || 'connection' in config) {
    return config as DatabaseSourceConfig
  }
  const legacy = config as Extract<DbDataSourceConfig, { endpoint?: string }>
  const database = legacy.database
  const jdbcUrl = database?.jdbcUrl || (legacy.endpoint?.startsWith('jdbc:') ? legacy.endpoint : undefined)
  return {
    configType: 'DATABASE',
    configVersion: 1,
    dbType: legacy.dbType || database?.dbType,
    connection: {
      mode: jdbcUrl ? 'JDBC_URL' : 'HOST_PORT',
      jdbcUrl,
      host: database?.host,
      port: database?.port,
      databaseName: database?.databaseName,
      schemaName: database?.schemaName,
    },
    credential: legacy.auth,
    network: legacy.network,
    driverProperties: legacy.attributes,
  }
}

function asHttpApiConfig(config?: DbDataSourceConfig): HttpApiSourceConfig | undefined {
  return config && 'configType' in config && config.configType === 'HTTP_API' ? config as HttpApiSourceConfig : undefined
}

function resolveAddress(item: DbDataSourceItem) {
  if (resolveSourceKind(item.sourceType) === 'HTTP_API') {
    return asHttpApiConfig(item.config)?.baseUrl || '-'
  }
  const connection = asDatabaseConfig(item.config)?.connection
  if (connection?.mode === 'JDBC_URL') {
    return connection.jdbcUrl || '-'
  }
  if (connection?.host) {
    return connection.port ? `${connection.host}:${connection.port}` : connection.host
  }
  return '-'
}

function formatSourceType(value?: string | number) {
  return String(getEnumLabel('dbDataSourceType', value, SERVICE_NAMES.DB_ENGINE) || value || '-')
}

function formatSyncMode(value?: string | number) {
  return String(getEnumLabel('dbDataSourceSyncMode', value, SERVICE_NAMES.DB_ENGINE) || value || '-')
}

function formatDbType(value?: string | number) {
  return String(getEnumLabel('dbDataSourceDbType', value, SERVICE_NAMES.DB_ENGINE) || value || '-')
}

function formatOwner(item: DbDataSourceItem) {
  return [item.ownerTeam, item.ownerUser].filter(Boolean).join(' / ') || '-'
}

function mapDataSourceCard(item: DbDataSourceItem) {
  const databaseConfig = asDatabaseConfig(item.config)
  const isDatabase = resolveSourceKind(item.sourceType) === 'DATABASE'
  const dbType = databaseConfig?.dbType
  return {
    id: item.id,
    key: item.sourceKey || '-',
    name: item.sourceName || item.sourceKey || '未命名数据源',
    type: formatSourceType(item.sourceType),
    syncMode: formatSyncMode(item.syncMode),
    owner: formatOwner(item),
    endpoint: resolveAddress(item),
    dbType: isDatabase ? formatDbType(dbType) : 'HTTP API',
    isDatabase,
    enabled: item.enabled !== false,
    summary: item.summary || item.remark || '暂无说明',
    raw: item,
  }
}

async function loadDataSources() {
  loading.value = true
  errorMessage.value = ''
  try {
    const payload = await searchDbDataSources({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value.trim() || undefined,
    })
    dataSources.value = payload?.list ?? []
    total.value = resolveTotal(payload?.pageInfo?.total)
  }
  catch (error) {
    dataSources.value = []
    total.value = 0
    errorMessage.value = error instanceof Error ? error.message : '数据源列表加载失败'
  }
  finally {
    loading.value = false
  }
}

async function handleSearch() {
  currentPage.value = 1
  await loadDataSources()
}

async function handleRefresh() {
  await loadDataSources()
}

async function handleCurrentPageChange(page: number) {
  currentPage.value = page
  await loadDataSources()
}

async function handlePageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  await loadDataSources()
}

function openCreateDialog() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(card: ReturnType<typeof mapDataSourceCard>) {
  const raw = card.raw ?? {}
  const databaseConfig = asDatabaseConfig(raw.config)
  const httpConfig = asHttpApiConfig(raw.config)
  const connection = databaseConfig?.connection
  const credential = databaseConfig?.credential || httpConfig?.credential || {}

  dialogMode.value = 'edit'
  editingId.value = raw.id ?? card.id
  Object.assign(form, {
    sourceKey: raw.sourceKey || card.key,
    sourceName: raw.sourceName || card.name,
    sourceType: raw.sourceType ?? resolveDefaultEnumValue('dbDataSourceType', 1),
    ownerTeam: raw.ownerTeam || '',
    ownerUser: raw.ownerUser || '',
    enabled: raw.enabled !== false,
    syncMode: raw.syncMode ?? resolveDefaultEnumValue('dbDataSourceSyncMode', 1),
    summary: raw.summary || '',
    remark: raw.remark || '',
    dbType: databaseConfig?.dbType ?? '',
    connectionMode: connection?.mode || 'HOST_PORT',
    jdbcUrl: connection?.jdbcUrl || '',
    host: connection?.host || '',
    port: connection?.port ?? '',
    databaseName: connection?.databaseName || '',
    schemaName: connection?.schemaName || '',
    baseUrl: httpConfig?.baseUrl || '',
    connectTimeoutMs: databaseConfig?.network?.connectTimeoutMs ?? httpConfig?.network?.connectTimeoutMs ?? '',
    readTimeoutMs: databaseConfig?.network?.readTimeoutMs ?? httpConfig?.network?.readTimeoutMs ?? '',
    writeTimeoutMs: databaseConfig?.network?.writeTimeoutMs ?? httpConfig?.network?.writeTimeoutMs ?? '',
    authType: credential.authType ?? resolveDefaultEnumValue('dbDataSourceAuthType', 0),
    username: credential.username || '',
    passwordCiphertext: credential.passwordCiphertext || '',
    tokenCiphertext: credential.tokenCiphertext || '',
    accessKey: credential.accessKey || '',
    secretKeyCiphertext: credential.secretKeyCiphertext || '',
    credentialRef: credential.credentialRef || '',
    driverPropertiesText: databaseConfig?.driverProperties ? JSON.stringify(databaseConfig.driverProperties, null, 2) : '',
    httpAttributesText: httpConfig?.attributes ? JSON.stringify(httpConfig.attributes, null, 2) : '',
  })
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
}

function emptyToUndefined(value: unknown) {
  if (value === null || value === undefined) {
    return undefined
  }
  if (typeof value === 'string') {
    return value.trim() ? value.trim() : undefined
  }
  return value
}

function hasSelectedEnumValue(value: unknown) {
  return value !== null && value !== undefined && String(value).trim().length > 0
}

function normalizeNumber(value: string | number) {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : undefined
}

function compactObject<T extends Record<string, unknown>>(value: T) {
  return Object.fromEntries(Object.entries(value).filter(([, entryValue]) => entryValue !== undefined))
}

function parseAttributesText(value: string) {
  if (!value.trim()) {
    return undefined
  }
  try {
    return JSON.parse(value)
  }
  catch {
    return value.trim()
  }
}

function validateJson(value: string, label: string) {
  if (!value.trim()) {
    return ''
  }
  try {
    JSON.parse(value)
    return ''
  }
  catch {
    return `${label}必须是合法 JSON`
  }
}

function validateForm() {
  if (!form.sourceKey.trim()) {
    return '请输入数据源 Key'
  }
  if (!form.sourceName.trim()) {
    return '请输入数据源名称'
  }
  if (!form.sourceType) {
    return '请选择数据源类型'
  }
  if (isDatabaseSource.value) {
    if (!hasSelectedEnumValue(form.dbType)) {
      return '请选择数据库类型'
    }
    if (form.connectionMode === 'JDBC_URL' && !form.jdbcUrl.trim()) {
      return '请输入 JDBC URL'
    }
    if (form.connectionMode === 'HOST_PORT' && (!form.host.trim() || !form.databaseName.trim())) {
      return 'HOST_PORT 模式必须填写主机地址和数据库名'
    }
    return validateJson(form.driverPropertiesText, '驱动属性')
  }
  if (isHttpApiSource.value) {
    if (!form.baseUrl.trim()) {
      return '请输入 HTTP API Base URL'
    }
    return validateJson(form.httpAttributesText, 'HTTP 属性')
  }
  return '当前仅支持数据库和 HTTP API 数据源配置'
}

function validateTestForm() {
  if (!isDatabaseSource.value) {
    return '当前测试连接仅支持数据库数据源'
  }
  return validateForm()
}

function buildPayload() {
  const credential = compactObject({
    authType: form.authType,
    username: authKind.value === 'BASIC' ? emptyToUndefined(form.username) : undefined,
    passwordCiphertext: authKind.value === 'BASIC' ? emptyToUndefined(form.passwordCiphertext) : undefined,
    tokenCiphertext: authKind.value === 'BEARER' ? emptyToUndefined(form.tokenCiphertext) : undefined,
    accessKey: authKind.value === 'AK_SK' || authKind.value === 'API_KEY' ? emptyToUndefined(form.accessKey) : undefined,
    secretKeyCiphertext: authKind.value === 'AK_SK' ? emptyToUndefined(form.secretKeyCiphertext) : undefined,
    credentialRef: authKind.value === 'NONE' ? undefined : emptyToUndefined(form.credentialRef),
  })
  const network = compactObject({
    connectTimeoutMs: normalizeNumber(form.connectTimeoutMs),
    readTimeoutMs: normalizeNumber(form.readTimeoutMs),
    writeTimeoutMs: normalizeNumber(form.writeTimeoutMs),
  })
  const config = isDatabaseSource.value
    ? {
        configType: 'DATABASE' as const,
        configVersion: 2,
        dbType: emptyToUndefined(form.dbType),
        connection: form.connectionMode === 'JDBC_URL'
          ? compactObject({ mode: 'JDBC_URL' as const, jdbcUrl: emptyToUndefined(form.jdbcUrl), schemaName: emptyToUndefined(form.schemaName) })
          : compactObject({
              mode: 'HOST_PORT' as const,
              host: emptyToUndefined(form.host),
              port: normalizeNumber(form.port),
              databaseName: emptyToUndefined(form.databaseName),
              schemaName: emptyToUndefined(form.schemaName),
            }),
        credential,
        network,
        driverProperties: parseAttributesText(form.driverPropertiesText),
      }
    : {
        configType: 'HTTP_API' as const,
        configVersion: 2,
        baseUrl: emptyToUndefined(form.baseUrl),
        credential,
        network,
        attributes: parseAttributesText(form.httpAttributesText),
      }
  return {
    sourceKey: form.sourceKey.trim(),
    sourceName: form.sourceName.trim(),
    sourceType: form.sourceType,
    ownerTeam: emptyToUndefined(form.ownerTeam),
    ownerUser: emptyToUndefined(form.ownerUser),
    enabled: form.enabled,
    syncMode: form.syncMode,
    summary: emptyToUndefined(form.summary),
    remark: emptyToUndefined(form.remark),
    config,
  }
}

async function handleSubmitForm() {
  const validationError = validateForm()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }

  saving.value = true
  try {
    const payload = buildPayload()
    if (dialogMode.value === 'create') {
      await createDbDataSource(payload)
      ElMessage.success('数据源新增成功')
      currentPage.value = 1
    }
    else if (editingId.value !== null) {
      await updateDbDataSource(editingId.value, payload)
      ElMessage.success('数据源更新成功')
    }
    dialogVisible.value = false
    await loadDataSources()
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '数据源保存失败')
  }
  finally {
    saving.value = false
  }
}

async function handleTestConnection() {
  const validationError = validateTestForm()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }

  testing.value = true
  try {
    const result = await testDbDataSourceConnection(buildPayload())
    ElMessage.success(result?.message || '连接测试成功')
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '连接测试失败')
  }
  finally {
    testing.value = false
  }
}

async function handleKnowledgeSync(card: ReturnType<typeof mapDataSourceCard>) {
  if (!card.key || card.key === '-') {
    ElMessage.error('当前数据源信息不完整，无法同步知识库')
    return
  }

  knowledgeSyncSubmitting.value = true
  try {
    const payload = await syncDbTableKnowledge({ sourceKey: card.key })
    const totalCount = Number(payload?.totalCount ?? 0)
    const createdCount = Number(payload?.createdCount ?? 0)
    const updatedCount = Number(payload?.updatedCount ?? 0)
    const unchangedCount = Number(payload?.unchangedCount ?? 0)
    ElMessage.success(`同步完成：共 ${totalCount} 张表，新增 ${createdCount}，更新 ${updatedCount}，未变更 ${unchangedCount}`)
  }
  catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识库同步失败')
  }
  finally {
    knowledgeSyncSubmitting.value = false
  }
}

async function openSourceDetail(card: ReturnType<typeof mapDataSourceCard>) {
  if (!card.isDatabase) {
    ElMessage.info('HTTP API 数据源暂不提供表元数据浏览')
    return
  }
  if (!card.key || card.key === '-') {
    return
  }
  await router.push(`/settings/system/data-source/${card.key}`)
}

onMounted(() => {
  void loadServiceEnums(SERVICE_NAMES.DB_ENGINE)
  void loadDataSources()
})
</script>

<template>
  <section class="data-source-page">
    <el-container class="data-source-layout">
      <el-header class="data-source-layout__header">
        <div class="data-source-layout__title">
          <h3>数据源</h3>
          <p>维护连接、同步模式和归属信息。</p>
        </div>
        <div class="data-source-layout__tools">
          <el-input v-model="keyword" placeholder="搜索名称 / 类型 / 负责人 / 库名" clearable @keyup.enter="handleSearch">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button plain @click="handleRefresh">
            <el-icon><RefreshRight /></el-icon>
            刷新
          </el-button>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新增数据源
          </el-button>
        </div>
      </el-header>

      <el-main class="data-source-layout__main">
        <div v-if="loading" class="data-source-state">数据源加载中...</div>
        <div v-else-if="errorMessage" class="data-source-state data-source-state--error">{{ errorMessage }}</div>
        <div v-else-if="!sourceCards.length" class="data-source-state">暂无数据源</div>
        <div
          v-for="item in sourceCards"
          v-else
          :key="item.id"
          class="data-source-card"
          role="button"
          tabindex="0"
          @click="openSourceDetail(item)"
          @keydown.enter.prevent="openSourceDetail(item)"
          @keydown.space.prevent="openSourceDetail(item)"
        >
          <div class="data-source-card__head">
            <div>
              <div class="data-source-card__name">{{ item.name }}</div>
              <div class="data-source-card__key">{{ item.key }}</div>
            </div>
            <el-switch :model-value="item.enabled" size="small" disabled />
          </div>

          <div class="data-source-card__summary">{{ item.summary }}</div>

          <div class="data-source-card__meta">
            <span><el-icon><Connection /></el-icon>{{ item.endpoint }}</span>
            <span><el-icon><DataBoard /></el-icon>{{ item.dbType }}</span>
            <span>{{ item.type }}</span>
            <span>{{ item.owner }}</span>
          </div>

          <div class="data-source-card__footer">
            <div class="data-source-card__tags">
              <el-tag size="small" effect="plain">{{ item.syncMode }}</el-tag>
              <el-tag size="small" effect="plain" :type="item.enabled ? 'primary' : 'info'">
                {{ item.enabled ? '已启用' : '已停用' }}
              </el-tag>
            </div>
            <div class="data-source-card__actions">
              <el-tooltip v-if="item.isDatabase" content="知识库同步" placement="top">
                <el-button circle plain @click.stop="handleKnowledgeSync(item)">
                  <el-icon><DataBoard /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="编辑" placement="top">
                <el-button circle plain type="primary" @click.stop="openEditDialog(item)">
                  <el-icon><EditPen /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
      </el-main>

      <el-footer class="data-source-layout__footer">
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

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增数据源' : '编辑数据源'"
      width="900"
      draggable
      overflow
      destroy-on-close
      @closed="resetForm"
    >
      <el-form label-position="top" class="data-source-dialog">
        <section class="data-source-dialog__section">
          <header class="data-source-dialog__section-head">
            <h4>基础信息</h4>
            <p>维护数据源标识、类型、归属和同步方式。</p>
          </header>
          <div class="data-source-dialog__grid">
            <el-form-item label="数据源 Key">
              <el-input v-model="form.sourceKey" :disabled="dialogMode === 'edit'" />
            </el-form-item>

            <el-form-item label="数据源名称">
              <el-input v-model="form.sourceName" />
            </el-form-item>

            <el-form-item label="数据源类型">
              <el-select v-model="form.sourceType">
                <el-option v-for="item in sourceTypeOptions" :key="String(item.value)" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>

            <el-form-item label="同步模式">
              <el-select v-model="form.syncMode">
                <el-option v-for="item in syncModeOptions" :key="String(item.value)" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>

            <el-form-item label="归属团队">
              <el-input v-model="form.ownerTeam" />
            </el-form-item>

            <el-form-item label="负责人">
              <el-input v-model="form.ownerUser" />
            </el-form-item>

            <el-form-item label="启用">
              <el-switch v-model="form.enabled" />
            </el-form-item>
          </div>
        </section>

        <section class="data-source-dialog__section">
          <header class="data-source-dialog__section-head">
            <h4>连接配置</h4>
            <p>按协议填写唯一的连接地址表达，避免 Endpoint、JDBC URL 和主机地址混用。</p>
          </header>
          <div v-if="isDatabaseSource" class="data-source-dialog__grid">
            <el-form-item label="数据库类型">
              <el-select v-model="form.dbType">
                <el-option v-for="item in dbTypeOptions" :key="String(item.value)" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>

            <el-form-item label="连接方式">
              <el-select v-model="form.connectionMode">
                <el-option label="主机 / 端口" value="HOST_PORT" />
                <el-option label="JDBC URL" value="JDBC_URL" />
              </el-select>
            </el-form-item>

            <template v-if="form.connectionMode === 'JDBC_URL'">
              <el-form-item label="JDBC URL" class="data-source-dialog__span-2">
                <el-input v-model="form.jdbcUrl" placeholder="jdbc:mysql://db.example.com:3306/app" />
              </el-form-item>
            </template>
            <template v-else>
              <el-form-item label="主机地址">
                <el-input v-model="form.host" placeholder="db.example.com" />
              </el-form-item>
              <el-form-item label="端口">
                <el-input v-model="form.port" placeholder="3306" />
              </el-form-item>
              <el-form-item label="数据库名称">
                <el-input v-model="form.databaseName" />
              </el-form-item>
              <el-form-item label="Schema">
                <el-input v-model="form.schemaName" />
              </el-form-item>
            </template>
          </div>
          <div v-else-if="isHttpApiSource" class="data-source-dialog__grid">
            <el-form-item label="HTTP API Base URL" class="data-source-dialog__span-2">
              <el-input v-model="form.baseUrl" placeholder="https://api.example.com" />
            </el-form-item>
          </div>

          <div v-if="isDatabaseSource || isHttpApiSource" class="data-source-dialog__grid data-source-dialog__grid--timeouts">
            <el-form-item label="连接超时(ms)">
              <el-input v-model="form.connectTimeoutMs" />
            </el-form-item>
            <el-form-item label="读取超时(ms)">
              <el-input v-model="form.readTimeoutMs" />
            </el-form-item>
            <el-form-item label="写入超时(ms)">
              <el-input v-model="form.writeTimeoutMs" />
            </el-form-item>
          </div>
        </section>

        <section class="data-source-dialog__section">
          <header class="data-source-dialog__section-head">
            <h4>认证配置</h4>
            <p>只展示当前认证方式需要填写的凭证字段。</p>
          </header>
          <div class="data-source-dialog__grid">
            <el-form-item label="认证类型">
              <el-select v-model="form.authType">
                <el-option v-for="item in authTypeOptions" :key="String(item.value)" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>

            <el-form-item v-if="authKind === 'BASIC'" label="用户名">
              <el-input v-model="form.username" />
            </el-form-item>

            <el-form-item v-if="authKind === 'BASIC'" label="密码密文">
              <el-input v-model="form.passwordCiphertext" type="password" show-password />
            </el-form-item>

            <el-form-item v-if="authKind === 'BEARER'" label="Bearer Token" class="data-source-dialog__span-2">
              <el-input v-model="form.tokenCiphertext" />
            </el-form-item>

            <el-form-item v-if="authKind === 'AK_SK'" label="Access Key">
              <el-input v-model="form.accessKey" />
            </el-form-item>

            <el-form-item v-if="authKind === 'AK_SK'" label="Secret Key 密文">
              <el-input v-model="form.secretKeyCiphertext" type="password" show-password />
            </el-form-item>

            <el-form-item v-if="authKind === 'API_KEY'" label="API Key" class="data-source-dialog__span-2">
              <el-input v-model="form.accessKey" type="password" show-password />
            </el-form-item>

            <el-form-item v-if="authKind !== 'NONE'" label="凭证引用" class="data-source-dialog__span-2">
              <el-input v-model="form.credentialRef" />
            </el-form-item>
          </div>
        </section>

        <section class="data-source-dialog__section">
          <header class="data-source-dialog__section-head">
            <h4>扩展信息</h4>
            <p>补充摘要、备注和扩展属性。</p>
          </header>
          <div class="data-source-dialog__grid">
            <el-form-item label="摘要说明" class="data-source-dialog__span-2">
              <el-input v-model="form.summary" type="textarea" :rows="1" />
            </el-form-item>

            <el-form-item label="备注" class="data-source-dialog__span-2">
              <el-input v-model="form.remark" type="textarea" :rows="1" />
            </el-form-item>

            <el-form-item v-if="isDatabaseSource" label="驱动属性(JSON)" class="data-source-dialog__span-2">
              <el-input v-model="form.driverPropertiesText" type="textarea" :rows="3" placeholder='例如：{"useSSL":true}' />
            </el-form-item>

            <el-form-item v-else-if="isHttpApiSource" label="HTTP 属性(JSON)" class="data-source-dialog__span-2">
              <el-input v-model="form.httpAttributesText" type="textarea" :rows="3" placeholder='例如：{"responseRecordsPath":"data.records"}' />
            </el-form-item>
          </div>
        </section>
      </el-form>

      <template #footer>
        <div class="data-source-dialog__footer">
          <el-button v-if="isDatabaseSource" :loading="testing" @click="handleTestConnection">测试连接</el-button>
          <div class="data-source-dialog__footer-actions">
            <el-button @click="closeDialog">取消</el-button>
            <el-button type="primary" :loading="saving" @click="handleSubmitForm">保存</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.data-source-page {
  display: flex;
  flex: 1;
  min-height: 0;
}

.data-source-layout {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
  box-shadow: var(--system-shadow);
}

.data-source-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 66px;
  padding: 0 16px;
  border-bottom: 1px solid var(--system-border-subtle);
}

.data-source-layout__title h3 {
  margin: 0;
  color: var(--system-title);
  font-size: 16px;
}

.data-source-layout__title p {
  margin: 3px 0 0;
  color: var(--system-text-muted);
  font-size: 12px;
}

.data-source-layout__tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.data-source-layout__tools :deep(.el-input) {
  width: 280px;
}

.data-source-layout__tools :deep(.el-input__wrapper) {
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
}

.data-source-layout__tools :deep(.el-input__wrapper:hover) {
  border-color: var(--system-accent-border);
  background: var(--system-surface);
}

.data-source-layout__tools :deep(.el-input__wrapper.is-focus) {
  border-color: var(--system-accent-border);
  box-shadow: 0 0 0 1px var(--system-accent-border);
}

.data-source-layout__tools :deep(.el-input__inner),
.data-source-layout__tools :deep(.el-input__prefix-inner) {
  color: var(--system-text);
}

.data-source-layout__tools :deep(.el-input__inner::placeholder) {
  color: var(--system-text-faint);
}

.data-source-layout__tools :deep(.el-button) {
  border-radius: 10px;
}

.data-source-layout__tools :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.data-source-layout__tools :deep(.el-button:not(.el-button--primary):hover) {
  border-color: var(--system-accent-border);
  background: var(--system-surface);
  color: var(--system-title);
}

.data-source-layout__tools :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-primary-button-bg);
  color: var(--system-primary-button-text);
}

.data-source-layout__tools :deep(.el-button--primary:hover) {
  filter: brightness(1.06);
}

.data-source-layout__main {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 380px));
  align-content: start;
  justify-content: start;
  gap: 14px;
  min-height: 0;
  padding: 14px 16px;
  background: var(--system-surface-muted);
  overflow-y: auto;
}

.data-source-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  padding: 24px;
  color: var(--system-text-muted);
  font-size: 13px;
}

.data-source-state--error {
  color: var(--system-danger);
}

.data-source-card {
  display: grid;
  gap: 12px;
  max-width: 380px;
  padding: 16px;
  border: 1px solid var(--system-border);
  border-radius: 14px;
  background: var(--system-surface-solid);
  box-shadow: var(--system-shadow);
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.data-source-card:hover,
.data-source-card:focus-visible {
  border-color: var(--system-accent-border);
  box-shadow: var(--system-accent-shadow);
  transform: translateY(-1px);
  outline: none;
}

.data-source-card__head,
.data-source-card__footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.data-source-card__name {
  color: var(--system-title);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
}

.data-source-card__key {
  margin-top: 2px;
  color: var(--system-text-soft);
  font-size: 12px;
}

.data-source-card__summary {
  color: var(--system-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.data-source-card__meta {
  display: grid;
  gap: 8px;
}

.data-source-card__meta span {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  color: var(--system-title);
  font-size: 12px;
  line-height: 1.6;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.data-source-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.data-source-card__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.data-source-card__actions :deep(.el-button) {
  width: 28px;
  height: 28px;
}

.data-source-layout__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 44px;
  padding: 0 16px;
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-solid);
}

.data-source-page :deep(.el-overlay-dialog) {
  display: flex;
  align-items: center;
  justify-content: center;
}

.data-source-page :deep(.el-dialog) {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 64px);
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--system-border);
  border-radius: 18px;
  background: var(--system-surface-strong);
  box-shadow: 0 24px 56px rgba(2, 6, 23, 0.28);
}

.data-source-page :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 14px 18px 12px;
  border-bottom: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.data-source-page :deep(.el-dialog__title) {
  color: var(--system-title);
  font-size: 16px;
  font-weight: 600;
}

.data-source-page :deep(.el-dialog__headerbtn) {
  top: 14px;
  right: 16px;
}

.data-source-page :deep(.el-dialog__close) {
  color: var(--system-text-muted);
}

.data-source-page :deep(.el-dialog__headerbtn:hover .el-dialog__close) {
  color: var(--system-title);
}

.data-source-page :deep(.el-dialog__body) {
  flex: 1;
  min-height: 0;
  padding: 12px 16px;
  background: var(--system-surface-strong);
  overflow-y: auto;
}

.data-source-page :deep(.el-dialog__footer) {
  flex-shrink: 0;
  padding: 10px 16px 12px;
  border-top: 1px solid var(--system-border-subtle);
  background: var(--system-surface-gradient);
}

.data-source-dialog :deep(.el-select) {
  width: 100%;
}

.data-source-dialog {
  display: grid;
  gap: 10px;
}

.data-source-dialog :deep(.el-form-item__label) {
  min-height: 18px;
  margin-bottom: 4px;
  padding: 0;
  color: var(--system-text-soft);
  font-size: 12px;
  line-height: 18px;
}

.data-source-dialog :deep(.el-form-item) {
  margin-bottom: 0;
}

.data-source-dialog :deep(.el-input__wrapper),
.data-source-dialog :deep(.el-select__wrapper) {
  min-height: 32px;
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
  color: var(--system-text);
}

.data-source-dialog :deep(.el-textarea__inner) {
  min-height: 32px;
  padding: 6px 10px;
  background: var(--system-surface-muted);
  border: 1px solid var(--system-border);
  box-shadow: none;
  color: var(--system-text);
}

.data-source-dialog :deep(.el-input__wrapper:hover),
.data-source-dialog :deep(.el-textarea__inner:hover),
.data-source-dialog :deep(.el-select__wrapper:hover) {
  border-color: var(--system-accent-border);
  background: var(--system-surface);
}

.data-source-dialog :deep(.el-input__wrapper.is-focus),
.data-source-dialog :deep(.el-textarea__inner:focus),
.data-source-dialog :deep(.el-select__wrapper.is-focused) {
  border-color: var(--system-accent-border);
  box-shadow: 0 0 0 1px var(--system-accent-border);
}

.data-source-dialog :deep(.el-input__inner),
.data-source-dialog :deep(.el-textarea__inner),
.data-source-dialog :deep(.el-select__selected-item),
.data-source-dialog :deep(.el-select__placeholder),
.data-source-dialog :deep(.el-input__count) {
  color: var(--system-text);
}

.data-source-dialog :deep(.el-input__inner::placeholder),
.data-source-dialog :deep(.el-textarea__inner::placeholder) {
  color: var(--system-text-faint);
}

.data-source-dialog :deep(.el-switch__core) {
  background: var(--system-surface-muted);
  border-color: var(--system-border);
}

.data-source-dialog :deep(.el-switch.is-checked .el-switch__core) {
  background: var(--system-accent-text);
  border-color: var(--system-accent-text);
}

.data-source-dialog__section {
  padding: 10px 12px 12px;
  border: 1px solid var(--system-border);
  border-radius: 12px;
  background: var(--system-surface-muted);
}

.data-source-dialog__section-head {
  margin-bottom: 8px;
}

.data-source-dialog__section-head h4 {
  margin: 0;
  color: var(--system-title);
  font-size: 13px;
}

.data-source-dialog__section-head p {
  margin: 2px 0 0;
  color: var(--system-text-muted);
  font-size: 11px;
}

.data-source-dialog__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 12px;
}

.data-source-dialog__span-2 {
  grid-column: span 2;
}

.data-source-dialog__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.data-source-dialog__footer-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.data-source-dialog__footer :deep(.el-button) {
  min-width: 76px;
  border-radius: 10px;
}

.data-source-dialog__footer :deep(.el-button:not(.el-button--primary)) {
  border-color: var(--system-border);
  background: var(--system-surface-muted);
  color: var(--system-text);
}

.data-source-dialog__footer :deep(.el-button:not(.el-button--primary):hover) {
  border-color: var(--system-accent-border);
  background: var(--system-surface);
  color: var(--system-title);
}

.data-source-dialog__footer :deep(.el-button--primary) {
  border-color: var(--system-accent-border);
  background: var(--system-primary-button-bg);
  color: var(--system-primary-button-text);
}

.data-source-dialog__footer :deep(.el-button--primary:hover) {
  filter: brightness(1.06);
}

@media (max-width: 1024px) {
  .data-source-layout__header {
    height: auto;
    padding: 12px 16px;
    align-items: stretch;
    flex-direction: column;
  }

  .data-source-layout__tools {
    width: 100%;
    flex-wrap: wrap;
  }

  .data-source-layout__tools :deep(.el-input) {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .data-source-layout__main {
    grid-template-columns: minmax(0, 1fr);
  }

  .data-source-card {
    max-width: none;
  }

  .data-source-dialog__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .data-source-dialog__span-2 {
    grid-column: span 1;
  }
}
</style>
