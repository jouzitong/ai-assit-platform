<script setup lang="ts">
import { Connection, DataBoard, EditPen, Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  createDbDataSource,
  searchDbDataSources,
  syncDbTableKnowledge,
  type DbDataSourceItem,
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
const knowledgeSyncSubmitting = ref(false)
const errorMessage = ref('')
const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('create')
const editingId = ref<string | number | null>(null)
const dataSources = ref<DbDataSourceItem[]>([])

const pageSizeOptions = [10, 20, 50, 100, 200, 500]
const form = reactive(createEmptyForm())
const sourceTypeOptions = computed(() => getEnumOptions('dbDataSourceType', SERVICE_NAMES.DB_ENGINE))
const syncModeOptions = computed(() => getEnumOptions('dbDataSourceSyncMode', SERVICE_NAMES.DB_ENGINE))
const authTypeOptions = computed(() => getEnumOptions('dbDataSourceAuthType', SERVICE_NAMES.DB_ENGINE))
const dbTypeOptions = computed(() => getEnumOptions('dbDataSourceDbType', SERVICE_NAMES.DB_ENGINE))

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
    endpoint: '',
    summary: '',
    remark: '',
    dbType: '',
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
    attributesText: '',
  }
}

function resetForm() {
  Object.assign(form, createEmptyForm())
}

function normalizeEnumValue(value?: string) {
  return String(value || '').trim().replace(/-/g, '_').toUpperCase()
}

function resolveTotal(payloadTotal?: number) {
  const numericTotal = Number(payloadTotal)
  return Number.isFinite(numericTotal) ? numericTotal : dataSources.value.length
}

function resolveHost(item: DbDataSourceItem) {
  const config = item.config ?? {}
  const databaseConfig = config.database ?? {}
  if (config.endpoint) {
    return config.endpoint
  }
  if (databaseConfig.host) {
    return databaseConfig.port ? `${databaseConfig.host}:${databaseConfig.port}` : databaseConfig.host
  }
  if (databaseConfig.jdbcUrl) {
    return databaseConfig.jdbcUrl
  }
  return '-'
}

function formatSourceType(value?: string) {
  return String(getEnumLabel('dbDataSourceType', value, SERVICE_NAMES.DB_ENGINE) || value || '-')
}

function formatSyncMode(value?: string) {
  return String(getEnumLabel('dbDataSourceSyncMode', value, SERVICE_NAMES.DB_ENGINE) || value || '-')
}

function formatDbType(value?: string | number) {
  return String(getEnumLabel('dbDataSourceDbType', value, SERVICE_NAMES.DB_ENGINE) || value || '-')
}

function formatOwner(item: DbDataSourceItem) {
  return [item.ownerTeam, item.ownerUser].filter(Boolean).join(' / ') || '-'
}

function mapDataSourceCard(item: DbDataSourceItem) {
  const dbType = item.config?.dbType ?? item.config?.database?.dbType
  const rawEndpoint = item.config?.endpoint || item.config?.database?.jdbcUrl || resolveHost(item)
  return {
    id: item.id,
    key: item.sourceKey || '-',
    name: item.sourceName || item.sourceKey || '未命名数据源',
    type: formatSourceType(item.sourceType),
    syncMode: formatSyncMode(item.syncMode),
    owner: formatOwner(item),
    endpoint: rawEndpoint || '-',
    dbType: formatDbType(dbType),
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
  const config = raw.config ?? {}
  const databaseConfig = config.database ?? {}
  const authConfig = config.auth ?? {}

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
    endpoint: config.endpoint || databaseConfig.jdbcUrl || '',
    summary: raw.summary || '',
    remark: raw.remark || '',
    dbType: config.dbType ?? databaseConfig.dbType ?? '',
    connectTimeoutMs: config.network?.connectTimeoutMs ?? '',
    readTimeoutMs: config.network?.readTimeoutMs ?? '',
    writeTimeoutMs: config.network?.writeTimeoutMs ?? '',
    authType: authConfig.authType ?? resolveDefaultEnumValue('dbDataSourceAuthType', 0),
    username: authConfig.username || databaseConfig.username || '',
    passwordCiphertext: authConfig.passwordCiphertext || databaseConfig.password || '',
    tokenCiphertext: authConfig.tokenCiphertext || '',
    accessKey: authConfig.accessKey || '',
    secretKeyCiphertext: authConfig.secretKeyCiphertext || '',
    credentialRef: authConfig.credentialRef || '',
    attributesText: config.attributes ? JSON.stringify(config.attributes, null, 2) : '',
  })
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
}

function emptyToUndefined(value: string) {
  return value.trim() ? value.trim() : undefined
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
  if (form.attributesText.trim()) {
    try {
      JSON.parse(form.attributesText)
    }
    catch {
      return '扩展属性必须是合法 JSON'
    }
  }
  return ''
}

function buildPayload() {
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
    config: {
      dbType: emptyToUndefined(form.dbType),
      endpoint: emptyToUndefined(form.endpoint),
      network: compactObject({
        connectTimeoutMs: normalizeNumber(form.connectTimeoutMs),
        readTimeoutMs: normalizeNumber(form.readTimeoutMs),
        writeTimeoutMs: normalizeNumber(form.writeTimeoutMs),
      }),
      auth: compactObject({
        authType: form.authType,
        username: emptyToUndefined(form.username),
        passwordCiphertext: emptyToUndefined(form.passwordCiphertext),
        tokenCiphertext: emptyToUndefined(form.tokenCiphertext),
        accessKey: emptyToUndefined(form.accessKey),
        secretKeyCiphertext: emptyToUndefined(form.secretKeyCiphertext),
        credentialRef: emptyToUndefined(form.credentialRef),
      }),
      attributes: parseAttributesText(form.attributesText),
    },
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
              <el-tooltip content="知识库同步" placement="top">
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
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="pageSizeOptions"
          :pager-count="5"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @current-change="handleCurrentPageChange"
          @size-change="handlePageSizeChange"
        />
      </el-footer>
    </el-container>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增数据源' : '编辑数据源'"
      width="860"
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
            <p>维护连接地址、数据库类型和超时参数。</p>
          </header>
          <div class="data-source-dialog__grid">
            <el-form-item label="Endpoint" class="data-source-dialog__span-2">
              <el-input v-model="form.endpoint" placeholder="jdbc:mysql://host:3306/db" />
            </el-form-item>

            <el-form-item label="数据库类型">
              <el-select v-model="form.dbType" clearable>
                <el-option v-for="item in dbTypeOptions" :key="String(item.value)" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>

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
            <p>认证方式和凭证字段按接口枚举选择。</p>
          </header>
          <div class="data-source-dialog__grid">
            <el-form-item label="认证类型">
              <el-select v-model="form.authType">
                <el-option v-for="item in authTypeOptions" :key="String(item.value)" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>

            <el-form-item label="用户名">
              <el-input v-model="form.username" />
            </el-form-item>

            <el-form-item label="密码密文">
              <el-input v-model="form.passwordCiphertext" type="password" show-password />
            </el-form-item>

            <el-form-item label="Bearer Token">
              <el-input v-model="form.tokenCiphertext" />
            </el-form-item>

            <el-form-item label="Access Key">
              <el-input v-model="form.accessKey" />
            </el-form-item>

            <el-form-item label="Secret Key 密文">
              <el-input v-model="form.secretKeyCiphertext" type="password" show-password />
            </el-form-item>

            <el-form-item label="凭证引用" class="data-source-dialog__span-2">
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
              <el-input v-model="form.summary" type="textarea" :rows="2" />
            </el-form-item>

            <el-form-item label="备注" class="data-source-dialog__span-2">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>

            <el-form-item label="扩展属性(JSON)" class="data-source-dialog__span-2">
              <el-input v-model="form.attributesText" type="textarea" :rows="4" />
            </el-form-item>
          </div>
        </section>
      </el-form>

      <template #footer>
        <div class="data-source-dialog__footer">
          <el-button @click="closeDialog">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSubmitForm">确认</el-button>
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
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.data-source-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 66px;
  padding: 0 16px;
  border-bottom: 1px solid #eef2f7;
}

.data-source-layout__title h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
}

.data-source-layout__title p {
  margin: 3px 0 0;
  color: #6b7280;
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

.data-source-layout__main {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 380px));
  align-content: start;
  justify-content: start;
  gap: 14px;
  min-height: 0;
  padding: 14px 16px;
  background: #f8fafc;
  overflow-y: auto;
}

.data-source-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  padding: 24px;
  color: #6b7280;
  font-size: 13px;
}

.data-source-state--error {
  color: #dc2626;
}

.data-source-card {
  display: grid;
  gap: 12px;
  max-width: 380px;
  padding: 16px;
  border: 1px solid #e8edf3;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.04);
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.data-source-card:hover,
.data-source-card:focus-visible {
  border-color: #c7d7fe;
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.09);
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
  color: #111827;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
}

.data-source-card__key {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.data-source-card__summary {
  color: #6b7280;
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
  color: #0f172a;
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
  border-top: 1px solid #eef2f7;
  background: #fff;
}

.data-source-dialog :deep(.el-select) {
  width: 100%;
}

.data-source-dialog {
  display: grid;
  gap: 14px;
}

.data-source-dialog__section {
  padding: 14px 14px 8px;
  border: 1px solid #e8edf3;
  border-radius: 14px;
  background: #f8fafc;
}

.data-source-dialog__section-head {
  margin-bottom: 10px;
}

.data-source-dialog__section-head h4 {
  margin: 0;
  color: #111827;
  font-size: 14px;
}

.data-source-dialog__section-head p {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 12px;
}

.data-source-dialog__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
}

.data-source-dialog__span-2 {
  grid-column: span 2;
}

.data-source-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
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
