import type { CatalogStatus, LogicalType, RelationResultMode, TransformMode } from '../../api/virtualData'
import type { RelationLineStyle } from './types'

export const relationLineStyleOptions: Array<{ label: string; value: RelationLineStyle }> = [
  { label: '曲线', value: 'curve' },
  { label: '折线', value: 'polyline' },
  { label: '直线', value: 'straight' },
]

export const relationResultModeOptions: Array<{
  label: string
  value: RelationResultMode
  cardinality: string
  description: string
}> = [
  {
    label: '返回单个对象',
    value: 0,
    cardinality: '1:1 / N:1',
    description: '从来源表访问目标表时，最多返回一个目标对象。',
  },
  {
    label: '返回对象集合',
    value: 1,
    cardinality: '1:N / N:N',
    description: '从来源表访问目标表时，返回零个或多个目标对象。',
  },
]

export const catalogStatusOptions: Array<{ label: string; value: CatalogStatus }> = [
  { label: '草稿', value: 0 },
  { label: '已发布', value: 1 },
  { label: '已停用', value: 2 },
]

export const logicalTypeOptions: Array<{ label: string; value: LogicalType }> = [
  { label: '字符串', value: 0 },
  { label: '布尔值', value: 1 },
  { label: '整数', value: 2 },
  { label: '长整数', value: 3 },
  { label: '小数', value: 4 },
  { label: '日期', value: 5 },
  { label: '时间戳', value: 6 },
  { label: 'JSON', value: 7 },
  { label: '二进制', value: 8 },
]

export const transformModeOptions: Array<{ label: string; value: TransformMode }> = [
  { label: '双向读写', value: 2 },
  { label: '只读转换', value: 0 },
  { label: '只写转换', value: 1 },
]

export function catalogStatusLabel(status?: CatalogStatus) {
  return catalogStatusOptions.find(item => item.value === status)?.label || status || '-'
}

export function catalogStatusType(status?: CatalogStatus) {
  if (status === 1) return 'success'
  if (status === 2) return 'info'
  return 'warning'
}

export function logicalTypeLabel(type?: LogicalType) {
  return logicalTypeOptions.find(item => item.value === type)?.label || '-'
}

export function transformModeLabel(mode?: TransformMode) {
  return transformModeOptions.find(item => item.value === mode)?.label || '-'
}

export function bindingRoleLabel(role?: 0 | 1) {
  return role === 1 ? '副本' : '主绑定'
}
