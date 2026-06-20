import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createDbDataSource, searchDbDataSources, syncDbTableKnowledge, updateDbDataSource } from '../../../../../api/dbEngine'
import { showPopup } from '../../../../../utils/popup'

export function useDataSourcePage() {
  const router = useRouter()
  const keyword = ref('')
  const selectedSourceKey = ref('')
  const sourceList = ref([])
  const loading = ref(false)
  const errorMessage = ref('')
  const dialogVisible = ref(false)
  const dialogMode = ref('create')
  const dialogError = ref('')
  const saving = ref(false)
  const knowledgeSyncSubmitting = ref(false)
  const form = reactive(createEmptyForm())
  const sourceTypeOptions = [
    { label: '数据库', value: 'DATABASE' },
    { label: 'HTTP API', value: 'HTTP_API' },
    { label: '服务接口', value: 'SERVICE_API' },
    { label: '文件', value: 'FILE' },
    { label: '流式数据', value: 'STREAM' }
  ]
  const syncModeOptions = [
    { label: '实时', value: 'REALTIME' },
    { label: '分钟级', value: 'MINUTE_LEVEL' },
    { label: '小时级', value: 'HOURLY' },
    { label: 'T+1', value: 'T_PLUS_1' },
    { label: '手动', value: 'MANUAL' }
  ]
  const authTypeOptions = [
    { label: '无认证', value: 'NONE' },
    { label: '用户名/密码', value: 'BASIC' },
    { label: 'Bearer', value: 'BEARER' },
    { label: 'AK/SK', value: 'AK_SK' },
    { label: 'API Key', value: 'API_KEY' }
  ]
  const dbTypeOptions = [
    { label: 'MySQL', value: 'MYSQL' },
    { label: 'PostgreSQL', value: 'POSTGRESQL' },
    { label: 'ClickHouse', value: 'CLICKHOUSE' },
    { label: 'Oracle', value: 'ORACLE' },
    { label: 'SQL Server', value: 'SQL_SERVER' },
    { label: 'Hive', value: 'HIVE' }
  ]

  const filteredSources = computed(() => {
    const normalized = keyword.value.trim().toLowerCase()
    if (!normalized) {
      return sourceList.value
    }

    return sourceList.value.filter(item =>
      [item.name, item.type, item.owner, item.host, item.database].some(value =>
        String(value).toLowerCase().includes(normalized)
      )
    )
  })

  const selectedSource = computed(() =>
    sourceList.value.find(item => item.key === selectedSourceKey.value) || sourceList.value[0] || null
  )

  onMounted(() => {
    loadDataSources()
  })

  function openSource(key) {
    selectedSourceKey.value = key
    router.push(`/settings/system/data-source/${key}`)
  }

  function statusClass(status) {
    return `is-${status}`
  }

  async function triggerKnowledgeSync() {
    const currentSource = selectedSource.value
    if (!currentSource?.key) {
      showPopup.error('当前没有可同步的数据源')
      return
    }
    const confirmMessage = `确认同步当前数据源「${currentSource.name}」下的全部数据表到知识库吗？`
    if (!window.confirm(confirmMessage)) {
      return
    }
    knowledgeSyncSubmitting.value = true
    try {
      const response = await syncDbTableKnowledge({
        sourceKey: currentSource.key
      })
      const payload = unwrapPayload(response) || {}
      const totalCount = Number(payload.totalCount ?? 0)
      const createdCount = Number(payload.createdCount ?? 0)
      const updatedCount = Number(payload.updatedCount ?? 0)
      const unchangedCount = Number(payload.unchangedCount ?? 0)
      showPopup.success(`知识库同步完成：共 ${totalCount} 张表，新增 ${createdCount}，更新 ${updatedCount}，未变更 ${unchangedCount}`)
    } catch (error) {
      showPopup.error(error.message || '知识库同步失败')
    } finally {
      knowledgeSyncSubmitting.value = false
    }
  }

  function openCreateDialog() {
    dialogMode.value = 'create'
    dialogError.value = ''
    Object.assign(form, createEmptyForm())
    dialogVisible.value = true
  }

  function openEditDialog(item) {
    dialogMode.value = 'edit'
    dialogError.value = ''
    Object.assign(form, createFormFromItem(item))
    dialogVisible.value = true
  }

  function closeDialog() {
    dialogVisible.value = false
  }

  async function loadDataSources() {
    loading.value = true
    errorMessage.value = ''
    try {
      const response = await searchDbDataSources({
        page: 1,
        size: 200
      })
      const payload = unwrapPayload(response)
      const nextSourceList = (payload?.list ?? []).map(mapDataSourceItem)
      sourceList.value = nextSourceList
      if (!selectedSourceKey.value && nextSourceList.length) {
        selectedSourceKey.value = nextSourceList[0].key
      }
    } catch (error) {
      errorMessage.value = error.message || '数据源列表加载失败'
      sourceList.value = []
    } finally {
      loading.value = false
    }
  }

  function unwrapPayload(response) {
    return response?.data ?? response
  }

  function mapDataSourceItem(item) {
    const config = item?.config ?? {}
    const host = resolveHost(item)
    const database = normalizeEnumValue(config.dbType || item?.config?.database?.dbType) || '-'
    const statusMeta = resolveStatusMeta(item)
    return {
      id: item.id,
      key: item.sourceKey || String(item.id ?? ''),
      name: item.sourceName || item.sourceKey || '未命名数据源',
      type: formatType(item.sourceType),
      owner: formatOwner(item),
      status: statusMeta.status,
      statusLabel: statusMeta.label,
      host,
      database,
      tables: '-',
      syncMode: formatSyncMode(item.syncMode),
      summary: item.summary || item.remark || '暂无说明。',
      raw: item
    }
  }

  function resolveHost(item) {
    const config = item?.config ?? {}
    const databaseConfig = config.database ?? {}
    if (config.endpoint) {
      return config.endpoint
    }
    if (databaseConfig.host) {
      return databaseConfig.port ? `${databaseConfig.host}:${databaseConfig.port}` : databaseConfig.host
    }
    return '-'
  }

  function resolveStatusMeta(item) {
    if (item?.enabled === false) {
      return { status: 'offline', label: '已停用' }
    }
    return { status: 'online', label: '运行中' }
  }

  function formatOwner(item) {
    return [item?.ownerTeam, item?.ownerUser].filter(Boolean).join(' / ') || '-'
  }

  function formatType(value) {
    const typeLabelMap = {
      DATABASE: '数据库',
      HTTP_API: 'HTTP API',
      SERVICE_API: '服务接口',
      FILE: '文件',
      STREAM: '流式数据'
    }
    return typeLabelMap[value] || value || '-'
  }

  function formatSyncMode(value) {
    const syncModeLabelMap = {
      REALTIME: '实时',
      MINUTE_LEVEL: '分钟级',
      HOURLY: '小时级',
      T_PLUS_1: 'T+1',
      MANUAL: '手动'
    }
    return syncModeLabelMap[value] || value || '-'
  }

  async function submitForm() {
    dialogError.value = ''
    saving.value = true
    try {
      const payload = buildPayload()
      if (dialogMode.value === 'create') {
        await createDbDataSource(payload)
        showPopup.success('数据源新增成功')
      } else {
        await updateDbDataSource(form.id, payload)
        showPopup.success('数据源更新成功')
      }
      dialogVisible.value = false
      await loadDataSources()
    } catch (error) {
      dialogError.value = error.message || '数据源保存失败'
    } finally {
      saving.value = false
    }
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
          writeTimeoutMs: normalizeNumber(form.writeTimeoutMs)
        }),
        auth: compactObject({
          authType: form.authType,
          username: emptyToUndefined(form.username),
          passwordCiphertext: emptyToUndefined(form.passwordCiphertext),
          tokenCiphertext: emptyToUndefined(form.tokenCiphertext),
          accessKey: emptyToUndefined(form.accessKey),
          secretKeyCiphertext: emptyToUndefined(form.secretKeyCiphertext),
          credentialRef: emptyToUndefined(form.credentialRef)
        }),
        attributes: parseAttributesText(form.attributesText)
      }
    }
  }

  function parseAttributesText(value) {
    if (!value.trim()) {
      return undefined
    }
    try {
      return JSON.parse(value)
    } catch {
      return value.trim()
    }
  }

  function emptyToUndefined(value) {
    return value.trim() ? value.trim() : undefined
  }

  function normalizeNumber(value) {
    if (value === '' || value === null || value === undefined) {
      return undefined
    }
    const numericValue = Number(value)
    return Number.isFinite(numericValue) ? numericValue : undefined
  }

  function compactObject(value) {
    return Object.fromEntries(Object.entries(value).filter(([, entryValue]) => entryValue !== undefined))
  }

  function createFormFromItem(item) {
    const raw = item?.raw ?? {}
    const config = raw?.config ?? {}
    const databaseConfig = raw?.config?.database ?? {}
    const authConfig = raw?.config?.auth ?? {}
    return {
      id: raw.id ?? item.id ?? null,
      sourceKey: raw.sourceKey || item.key || '',
      sourceName: raw.sourceName || item.name || '',
      sourceType: raw.sourceType || 'DATABASE',
      ownerTeam: raw.ownerTeam || '',
      ownerUser: raw.ownerUser || '',
      enabled: raw.enabled !== false,
      syncMode: raw.syncMode || 'REALTIME',
      endpoint: config.endpoint || databaseConfig.jdbcUrl || '',
      summary: raw.summary || '',
      remark: raw.remark || '',
      dbType: normalizeEnumValue(config.dbType || databaseConfig.dbType),
      connectTimeoutMs: config.network?.connectTimeoutMs ?? '',
      readTimeoutMs: config.network?.readTimeoutMs ?? '',
      writeTimeoutMs: config.network?.writeTimeoutMs ?? '',
      authType: authConfig.authType || 'NONE',
      username: authConfig.username || databaseConfig.username || '',
      passwordCiphertext: authConfig.passwordCiphertext || databaseConfig.password || '',
      tokenCiphertext: authConfig.tokenCiphertext || authConfig.token || '',
      accessKey: authConfig.accessKey || '',
      secretKeyCiphertext: authConfig.secretKeyCiphertext || '',
      credentialRef: authConfig.credentialRef || '',
      attributesText: config.attributes ? JSON.stringify(config.attributes, null, 2) : ''
    }
  }

  function normalizeEnumValue(value) {
    return String(value || '').trim().replace(/-/g, '_').toUpperCase()
  }

  function createEmptyForm() {
    return {
      id: null,
      sourceKey: '',
      sourceName: '',
      sourceType: 'DATABASE',
      ownerTeam: '',
      ownerUser: '',
      enabled: true,
      syncMode: 'REALTIME',
      endpoint: '',
      summary: '',
      remark: '',
      dbType: '',
      connectTimeoutMs: '',
      readTimeoutMs: '',
      writeTimeoutMs: '',
      authType: 'NONE',
      username: '',
      passwordCiphertext: '',
      tokenCiphertext: '',
      accessKey: '',
      secretKeyCiphertext: '',
      credentialRef: '',
      attributesText: ''
    }
  }

  return {
    keyword,
    selectedSourceKey,
    loading,
    errorMessage,
    dialogVisible,
    dialogMode,
    dialogError,
    saving,
    knowledgeSyncSubmitting,
    form,
    sourceTypeOptions,
    syncModeOptions,
    authTypeOptions,
    dbTypeOptions,
    filteredSources,
    openSource,
    statusClass,
    triggerKnowledgeSync,
    loadDataSources,
    openCreateDialog,
    openEditDialog,
    closeDialog,
    submitForm
  }
}
