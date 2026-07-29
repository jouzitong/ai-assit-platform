import type { RendererAction } from '../../schema/action'

export type FormRendererAction = RendererAction

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
  labelPosition?: 'left' | 'inner'
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
