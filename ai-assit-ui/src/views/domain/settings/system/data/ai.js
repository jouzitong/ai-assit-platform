export const enabledOptions = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 'true' },
  { label: '停用', value: 'false' }
]

export const pageSizeOptions = [10, 20, 50]

export const kbBizTypeOptions = [
  { label: '数据库数据源', value: 'DB_DATA_SOURCE' },
  { label: '业务分析场景', value: 'BUSINESS_ANALYSIS_SCENE' },
  { label: '用户画像/偏好场景', value: 'USER_PROFILE_SCENE' },
  { label: 'Render JSON 渲染场景', value: 'RENDER_JSON_SCENE' },
  { label: '常见问题场景', value: 'FAQ_SCENE' }
]

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
    enabled: true,
    apiKeyInput: '',
    apiKeyMasked: '',
    extJson: `{\n  "capabilityTags": "chat,reasoning",\n  "maxContextTokens": 32000,\n  "maxOutputTokens": 4096,\n  "temperatureEnabled": 1,\n  "priority": 100\n}`
  }
}

export function createKbForm() {
  return {
    id: null,
    kbCode: '',
    kbName: '',
    bizType: 'DB_DATA_SOURCE',
    providerKbId: '',
    status: 'INIT',
    extJson: ''
  }
}
