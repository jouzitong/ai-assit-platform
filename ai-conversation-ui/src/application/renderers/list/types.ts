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
  component?: string
}

export interface RendererTabConfig {
  activeTab?: string
  tabs?: RendererTabItem[]
}

export interface RendererRelation {
  key: string
  model: string
  type?: string
  foreign_key?: string
  local_key?: string
  filter_dict?: Record<string, unknown>
}

export interface RendererDatasource {
  key: string
  type?: string
  model?: string
  filter_dict?: Record<string, unknown>
  filterExpr?: string
  relations?: RendererRelation[]
}

export interface RendererFilter {
  key: string
  label: string
  type?: 'text' | 'select' | 'date' | 'daterange'
  placeholder?: string
  options?: Array<{ label: string; value: string | number | boolean }>
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

export interface RendererListConfig {
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
  component?: 'common-tree-list' | 'common-list' | string
  tree?: RendererTreeConfig
  tab?: RendererTabConfig
  datasource?: RendererDatasource
  filters?: RendererFilter[]
  fields?: RendererField[]
  actions?: RendererAction[]
  list_config?: RendererListConfig
  hooks?: RendererHooks
}

export interface RendererTreeNode {
  key: string | number
  label: string
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
