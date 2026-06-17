import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createDbDataSource, searchDbDataSources, updateDbDataSource } from '../../../../api/dbEngine'
import { showPopup } from '../../../../utils/popup'

export function useKnowledgePage() {
  const router = useRouter()
  const keyword = ref('')
  const activeTab = ref('current')
  const selectedSourceKey = ref('')
  const sourceList = ref([])
  const loading = ref(false)
  const errorMessage = ref('')
  const dialogVisible = ref(false)
  const dialogMode = ref('create')
  const dialogError = ref('')
  const saving = ref(false)
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
  const statusOptions = [
    { label: '运行中', value: 'ACTIVE' },
    { label: '待校验', value: 'PENDING' },
    { label: '已停用', value: 'INACTIVE' }
  ]
  const authTypeOptions = [
    { label: '无认证', value: 'NONE' },
    { label: 'Basic', value: 'BASIC' },
    { label: 'Bearer', value: 'BEARER' },
    { label: 'AK/SK', value: 'AK_SK' },
    { label: 'API Key', value: 'API_KEY' }
  ]

  const tabOptions = [
    { key: 'current', label: '当前' },
    { key: 'draft', label: '草稿' },
    { key: 'history', label: '历史' }
  ]

  const filteredSources = computed(() => {
    const normalized = keyword.value.trim().toLowerCase()
    const matchedByTab = sourceList.value.filter(item => matchTab(item, activeTab.value))
    if (!normalized) {
      return matchedByTab
    }

    return matchedByTab.filter(item =>
      [item.name, item.type, item.owner, item.host, item.database].some(value =>
        String(value).toLowerCase().includes(normalized)
      )
    )
  })

  const activeTabLabel = computed(() => {
    return tabOptions.find(item => item.key === activeTab.value)?.label || '当前'
  })

  onMounted(() => {
    loadDataSources()
  })

  function openSource(key) {
    selectedSourceKey.value = key
    router.push(`/knowledge/${key}`)
  }

  function selectTab(tabKey) {
    activeTab.value = tabKey
  }

  function statusClass(status) {
    return `is-${status}`
  }

  function triggerKnowledgeSync() {
    showPopup.warning('知识库同步功能建设中')
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

  async function loadDataSources(options = {}) {
    const { showLoadingPopup = false, showSuccessPopup = false } = options
    loading.value = true
    errorMessage.value = ''
    if (showLoadingPopup) {
      showPopup.info('知识库列表刷新中...', { title: 'Loading', duration: 1600 })
    }
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
      if (showSuccessPopup) {
        showPopup.success('知识库列表已刷新')
      }
    } catch (error) {
      errorMessage.value = error.message || '数据源列表加载失败'
      sourceList.value = []
      showPopup.error(error.message || '数据源列表加载失败')
    } finally {
      loading.value = false
    }
  }

  function unwrapPayload(response) {
    return response?.data ?? response
  }

  function mapDataSourceItem(item) {
    const databaseConfig = item?.config?.database ?? {}
    const host = resolveHost(item)
    const database = databaseConfig.databaseName || databaseConfig.schemaName || '-'
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

  function matchTab(item, tabKey) {
    if (tabKey === 'draft') {
      return item.status === 'warning'
    }
    if (tabKey === 'history') {
      return item.status === 'offline'
    }
    return item.status === 'online'
  }

  function resolveHost(item) {
    const databaseConfig = item?.config?.database ?? {}
    if (databaseConfig.host) {
      return databaseConfig.port ? `${databaseConfig.host}:${databaseConfig.port}` : databaseConfig.host
    }
    return item?.config?.endpoint || '-'
  }

  function resolveStatusMeta(item) {
    if (item?.enabled === false) {
      return { status: 'offline', label: '已停用' }
    }
    if (String(item?.status || '').toUpperCase() === 'ACTIVE') {
      return { status: 'online', label: '运行中' }
    }
    return { status: 'warning', label: item?.status || '待校验' }
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
    const validationError = validateForm()
    if (validationError) {
      dialogError.value = validationError
      return
    }

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
    if (!form.syncMode) {
      return '请选择同步模式'
    }
    if (!form.status) {
      return '请选择状态'
    }
    if (form.sourceType === 'DATABASE') {
      if (!form.dbType.trim()) {
        return '数据库类型不能为空'
      }
      if (!form.host.trim()) {
        return '数据库主机不能为空'
      }
      if (!form.databaseName.trim()) {
        return '数据库名称不能为空'
      }
    }
    return ''
  }

  function buildPayload() {
    const endpoint = form.endpoint.trim() || form.jdbcUrl.trim() || undefined
    return {
      sourceKey: form.sourceKey.trim(),
      sourceName: form.sourceName.trim(),
      sourceType: form.sourceType,
      ownerTeam: emptyToUndefined(form.ownerTeam),
      ownerUser: emptyToUndefined(form.ownerUser),
      status: form.status,
      enabled: form.enabled,
      syncMode: form.syncMode,
      summary: emptyToUndefined(form.summary),
      remark: emptyToUndefined(form.remark),
      config: {
        endpoint,
        network: {
          connectTimeoutMs: normalizeNumber(form.connectTimeoutMs),
          readTimeoutMs: normalizeNumber(form.readTimeoutMs),
          writeTimeoutMs: normalizeNumber(form.writeTimeoutMs)
        },
        auth: {
          authType: form.authType,
          username: emptyToUndefined(form.username),
          passwordCiphertext: emptyToUndefined(form.passwordCiphertext),
          tokenCiphertext: emptyToUndefined(form.tokenCiphertext),
          accessKey: emptyToUndefined(form.accessKey),
          secretKeyCiphertext: emptyToUndefined(form.secretKeyCiphertext),
          credentialRef: emptyToUndefined(form.credentialRef)
        },
        database: {
          dbType: emptyToUndefined(form.dbType),
          host: emptyToUndefined(form.host),
          port: normalizeNumber(form.port),
          databaseName: emptyToUndefined(form.databaseName),
          schemaName: emptyToUndefined(form.schemaName),
          jdbcUrl: emptyToUndefined(form.jdbcUrl)
        }
      }
    }
  }

  function createFormFromItem(item) {
    const raw = item?.raw ?? {}
    const config = raw.config ?? {}
    const auth = config.auth ?? {}
    const network = config.network ?? {}
    const database = config.database ?? {}
    return {
      id: raw.id ?? item?.id ?? null,
      sourceKey: raw.sourceKey ?? '',
      sourceName: raw.sourceName ?? '',
      sourceType: raw.sourceType ?? 'DATABASE',
      ownerTeam: raw.ownerTeam ?? '',
      ownerUser: raw.ownerUser ?? '',
      status: raw.status ?? 'ACTIVE',
      enabled: raw.enabled ?? true,
      syncMode: raw.syncMode ?? 'REALTIME',
      summary: raw.summary ?? '',
      remark: raw.remark ?? '',
      endpoint: config.endpoint ?? '',
      connectTimeoutMs: stringifyNumber(network.connectTimeoutMs),
      readTimeoutMs: stringifyNumber(network.readTimeoutMs),
      writeTimeoutMs: stringifyNumber(network.writeTimeoutMs),
      authType: auth.authType ?? 'BASIC',
      username: auth.username ?? '',
      passwordCiphertext: auth.passwordCiphertext ?? '',
      tokenCiphertext: auth.tokenCiphertext ?? '',
      accessKey: auth.accessKey ?? '',
      secretKeyCiphertext: auth.secretKeyCiphertext ?? '',
      credentialRef: auth.credentialRef ?? '',
      dbType: database.dbType ?? '',
      host: database.host ?? '',
      port: stringifyNumber(database.port),
      databaseName: database.databaseName ?? '',
      schemaName: database.schemaName ?? '',
      jdbcUrl: database.jdbcUrl ?? ''
    }
  }

  function emptyToUndefined(value) {
    const normalized = String(value ?? '').trim()
    return normalized || undefined
  }

  function normalizeNumber(value) {
    if (value === '' || value === null || value === undefined) {
      return null
    }
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }

  function stringifyNumber(value) {
    if (value === null || value === undefined || value === '') {
      return ''
    }
    return String(value)
  }

  return {
    activeTab,
    activeTabLabel,
    tabOptions,
    keyword,
    selectedSourceKey,
    sourceList,
    loading,
    errorMessage,
    dialogVisible,
    dialogMode,
    dialogError,
    saving,
    form,
    sourceTypeOptions,
    syncModeOptions,
    statusOptions,
    authTypeOptions,
    filteredSources,
    openSource,
    selectTab,
    statusClass,
    triggerKnowledgeSync,
    loadDataSources,
    openCreateDialog,
    openEditDialog,
    closeDialog,
    submitForm
  }
}

function createEmptyForm() {
  return {
    id: null,
    sourceKey: '',
    sourceName: '',
    sourceType: 'DATABASE',
    ownerTeam: '',
    ownerUser: '',
    status: 'ACTIVE',
    enabled: true,
    syncMode: 'REALTIME',
    summary: '',
    remark: '',
    endpoint: '',
    connectTimeoutMs: '3000',
    readTimeoutMs: '30000',
    writeTimeoutMs: '30000',
    authType: 'BASIC',
    username: '',
    passwordCiphertext: '',
    tokenCiphertext: '',
    accessKey: '',
    secretKeyCiphertext: '',
    credentialRef: '',
    dbType: 'mysql',
    host: '',
    port: '3306',
    databaseName: '',
    schemaName: '',
    jdbcUrl: ''
  }
}
