import type { DbQueryListDatasource } from './db-query'

export interface RendererAction {
  key: string
  name: string
  action: string
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | ''
  disabled?: boolean
}

export interface RendererTabItem {
  key: string
  label: string
}

export interface RendererTreeConfig {
  component?: 'common-tree' | 'group-list' | string
  title?: string
}

export interface RendererTabConfig {
  activeTab?: string
  tabs?: RendererTabItem[]
}

export type RendererDatasource = DbQueryListDatasource | DirectJsonListDatasource

export interface RendererFilter {
  key: string
  label: string
  type?: 'text' | 'select' | 'date' | 'daterange'
  component?: string
  placeholder?: string
  list?: Array<{ key: string; value: string | number | boolean; disabled?: boolean }>
  selector?: {
    model: string
    fieldKey: string
    fieldValue: string
  }
  enums?: string
  options?: {
    filterable?: boolean
    multiple?: boolean
    hidden?: boolean
    labelPosition?: 'left' | 'inner'
    clearable?: boolean
    disabled?: boolean
    collapseTags?: boolean
    teleported?: boolean
    checkStrictly?: boolean
    renderAfterExpand?: boolean
    styles?: Record<string, string | number>
    className?: string
    [key: string]: unknown
  }
  data?: RendererTreeNode[]
  componentProps?: Record<string, unknown>
}

export interface RendererFieldOptions {
  styles?: Record<string, string | number>
  className?: string
}

export interface RendererField {
  key: string
  name: string
  label: string
  field?: string[]
  options?: RendererFieldOptions
}

export interface RendererPaginationConfig {
  enabled?: boolean
  pageSize?: number
  pageSizeOptions?: number[]
}

export interface RendererSummaryCard {
  key: string
  label: string
  value: string | number
  accent?: string
  hint?: string
}

export interface RendererSummaryConfig {
  cards?: RendererSummaryCard[]
}

export interface RendererListConfig {
  variant?: 'default' | 'workbench'
  itemType?: 'table' | 'card' | 'item'
  cardItem?: Record<string, unknown>
  item_operate?: Record<string, unknown>
  actionColumns?: RendererAction[]
  pagination?: RendererPaginationConfig
  className?: string
  events?: string[]
}

export interface RendererHooks {
  beforeLoad?: string
  afterLoad?: string
}

export interface ListRendererSchema {
  id: string
  version?: string
  title?: string
  component?: 'zg-common-tree-list' | 'zg-common-list' | 'common-tree-list' | 'common-list' | string
  tree?: RendererTreeConfig
  tab?: RendererTabConfig
  datasource?: RendererDatasource
  filters?: RendererFilter[]
  fields?: RendererField[]
  actions?: RendererAction[]
  summary?: RendererSummaryConfig
  list_config?: RendererListConfig
  hooks?: RendererHooks
}

export interface RendererTreeNode {
  key: string | number
  label: string
  count?: number
  children?: RendererTreeNode[]
  [key: string]: unknown
}

export interface RendererQueryState {
  activeTab: string
  filters: Record<string, unknown>
  page: number
  pageSize: number
  selectedTreeKey: string | number | null
}

export interface ApplicationRendererState {
  loading?: boolean
  error?: unknown
  empty?: boolean
}

export interface ListRendererData {
  records: Record<string, unknown>[]
  total?: number
  treeData?: RendererTreeNode[]
}

export type DirectJsonListDataInput = Partial<ListRendererData> & {
  [key: string]: unknown
}

export interface DirectJsonListDatasource {
  key: string
  type: 'direct-json'
  data?: DirectJsonListDataInput
  summary?: Record<string, unknown>
}

export interface ListRendererRuntimeProps {
  schema: ListRendererSchema
  data?: Partial<ListRendererData>
  state?: ApplicationRendererState
}
