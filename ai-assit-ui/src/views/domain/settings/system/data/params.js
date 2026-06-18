export const valueTypeOptions = [
  { label: '全部类型', value: '' },
  { label: '字符串', value: 'STRING' },
  { label: '数字', value: 'NUMBER' },
  { label: '布尔', value: 'BOOLEAN' },
  { label: 'JSON', value: 'JSON' },
  { label: '密码', value: 'PASSWORD' }
]

export const enabledOptions = [
  { label: '全部状态', value: '' },
  { label: '已启用', value: 'true' },
  { label: '已停用', value: 'false' }
]

export const pageSizeOptions = [10, 20, 50, 100]

export function createSystemSettingForm() {
  return {
    id: null,
    settingKey: '',
    description: '',
    settingValue: '',
    valueType: 'STRING',
    enabled: true
  }
}
