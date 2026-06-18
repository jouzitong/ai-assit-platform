export const enabledOptions = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 'true' },
  { label: '停用', value: 'false' }
]

export const pageSizeOptions = [10, 20, 50]

export const kbBizTypeOptions = [{ label: '数据库数据源', value: 'DB_DATA_SOURCE' }]

export const kbStatusOptions = [
  { label: '全部状态', value: '' },
  { label: 'INIT', value: 'INIT' },
  { label: 'ACTIVE', value: 'ACTIVE' },
  { label: 'SYNCING', value: 'SYNCING' },
  { label: 'FAILED', value: 'FAILED' },
  { label: 'DISABLED', value: 'DISABLED' }
]

export function createProviderForm() {
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

export function createModelForm() {
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

export function createKbForm() {
  return {
    id: null,
    kbCode: '',
    kbName: '',
    bizType: 'DB_DATA_SOURCE',
    bizKey: '',
    providerCode: '',
    providerKbId: '',
    currentVersionNo: '',
    status: 'INIT',
    enabled: true,
    remark: ''
  }
}
