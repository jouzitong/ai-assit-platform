import type { DbQueryListDatasource, DbQueryOperation } from './db-query'
import type { RendererAction } from './action'

export type { RendererAction, RendererActionOptions, RendererActionType } from './action'

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

export type RendererDatasource = DbQueryListDatasource | DirectJsonListDatasource | LocalListDatasource

export interface RendererFilterOption {
  key: string
  value: string | number | boolean
  disabled?: boolean
}

export interface RendererFilterOptions {
  type?: 'text' | 'select' | 'date' | 'daterange'
  placeholder?: string
  query?: {
    field?: string
    op?: DbQueryOperation
    submitOnChange?: boolean
    submitOnEnter?: boolean
  }
  list?: RendererFilterOption[]
  selector?: {
    model: string
    fieldKey: string
    fieldValue: string
  }
  enums?: string
  data?: RendererTreeNode[]
  componentProps?: Record<string, unknown>
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
  submitOnChange?: boolean
  submitOnEnter?: boolean
  operators?: Array<{ label: string; value: string }>
  defaultOperator?: string
  styles?: Record<string, string | number>
  className?: string
  [key: string]: unknown
}

interface RendererFilterBase {
  key: string
  component: string
  options?: RendererFilterOptions
}

export type RendererFilter = RendererFilterBase & (
  | { name: string; label?: string }
  | { label: string; name?: string }
)

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
  beforeEvent?: string
  afterEvent?: string
  onQueryChange?: string
  beforeLoad?: string
  afterLoad?: string
  beforeAction?: string
  afterAction?: string
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

/**
 * query.list 的 data 内层，用于 local 数据源模拟接口返回值。
 */
export interface LocalListDataInput {
  list?: Record<string, unknown>[]
  pageInfo?: {
    total?: number
    size?: number
    page?: number
  }
  summary?: Record<string, unknown>
}

/**
 * 本地模拟请求数据源：data 只保存接口响应的 data 内层，不发起 HTTP 请求。
 */
export interface LocalListDatasource {
  key: string
  type: 'local'
  data?: LocalListDataInput
  summary?: Record<string, unknown>
}

export interface ListRendererRuntimeProps {
  schema: ListRendererSchema
  data?: Partial<ListRendererData>
  state?: ApplicationRendererState
  developerMode?: boolean
  developerActions?: RendererAction[]
}
