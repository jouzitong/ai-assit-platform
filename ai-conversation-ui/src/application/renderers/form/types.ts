import type { RendererAction } from '../../schema/action'

export type FormRendererAction = RendererAction
export type FormRendererMode = 'view' | 'edit' | 'add'
export type FormRendererLabelPosition = 'top' | 'inline' | 'left' | 'right'
export type FormRendererFieldSpan = 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12

export interface FormRendererSubmitConfig {
  /** 由页面 Runtime 注册并执行的稳定提交器 key。 */
  executor?: 'render-meta-data' | string
}

export interface FormRendererRelation {
  key: string
  model?: string
  type?: string
  foreign_key?: string
  local_key?: string
  filter_dict?: Record<string, unknown>
}

export interface FormRendererFieldOptions {
  hidden?: boolean
  readonly?: boolean
  required?: boolean
  multiple?: boolean
  clearable?: boolean
  filterable?: boolean
  disabled?: boolean
  /** label 布局：默认 left；inner 仅作为历史 inline 别名兼容。 */
  labelPosition?: FormRendererLabelPosition | 'inner'
  /** 12 栅格占位；普通字段默认 6，长文本和代码字段默认 12。 */
  span?: FormRendererFieldSpan
  rows?: number
  placeholder?: string
  className?: string
  styles?: Record<string, string | number>
  [key: string]: unknown
}

export interface FormRendererField {
  key: string
  name?: string
  label: string
  field?: string[]
  /** 隐藏字段；隐藏后不渲染、不占栅格，但仍保留在表单数据中。 */
  hide?: boolean
  component?: string
  type?: 'text' | 'textarea' | 'select' | 'date' | 'daterange' | 'time' | 'checkbox' | 'switch' | 'code' | 'display'
  list?: Array<{ key: string; value: string | number | boolean; disabled?: boolean }>
  options?: FormRendererFieldOptions
  componentProps?: Record<string, unknown>
}

export interface FormRendererGroup {
  key: string
  title?: string
  description?: string
  fields: string[]
  columns?: number
  collapsible?: boolean
  collapsed?: boolean
}

export interface FormRendererHooks {
  before_load?: string
  after_load?: string
}

export interface FormRendererConfig {
  variant?: 'default' | 'workbench'
  columns?: number
  labelWidth?: string | number
  actionsAlign?: 'left' | 'center' | 'right'
  description?: string
  className?: string
  events?: string[]
  defaultValues?: Record<string, unknown>
  submit?: FormRendererSubmitConfig
}

export interface FormRendererSubmitPayload {
  action: FormRendererAction
  values: Record<string, unknown>
}

export interface FormRendererResetPayload {
  values: Record<string, unknown>
}

export interface FormRendererSchema {
  id: string
  version?: string
  title?: string
  component?: 'zg-common-info' | 'zg-common-form' | string
  fields?: FormRendererField[]
  groups?: FormRendererGroup[]
  children?: unknown[]
  actions?: FormRendererAction[]
  form_relations?: FormRendererRelation[]
  life_cycle?: FormRendererHooks
  form_config?: FormRendererConfig
  data?: Record<string, unknown>
}

export interface NormalizedFormRendererGroup extends FormRendererGroup {
  title: string
  columns: number
  fields: string[]
}

export interface NormalizedFormRendererSchema extends FormRendererSchema {
  title: string
  component: string
  fields: FormRendererField[]
  groups: NormalizedFormRendererGroup[]
  actions: FormRendererAction[]
  children: unknown[]
  form_relations: FormRendererRelation[]
  life_cycle: FormRendererHooks
  form_config: FormRendererConfig
  data: Record<string, unknown>
}
