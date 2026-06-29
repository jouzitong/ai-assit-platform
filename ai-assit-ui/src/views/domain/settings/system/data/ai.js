export const enabledOptions = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 'true' },
  { label: '停用', value: 'false' }
]

export const pageSizeOptions = [10, 20, 50]

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
    providerName: '',
    baseUrl: '',
    apiModel: '',
    enabled: true,
    apiKey: '',
    extJson: '{}'
  }
}

export function createKbForm() {
  return {
    id: null,
    kbCode: '',
    kbName: '',
    providerKbId: '',
    enabled: true,
    tags: '',
    url: '',
    extJson: ''
  }
}
