export type DbQueryOperation =
  | 'eq'
  | 'ne'
  | 'gt'
  | 'gte'
  | 'lt'
  | 'lte'
  | 'like'
  | 'in'
  | 'not_in'
  | 'is_null'
  | 'is_not_null'
  | string

export interface DbQueryFilterCondition {
  op?: DbQueryOperation
  value?: unknown
}

export type DbQueryFilterValue = unknown | DbQueryFilterCondition

export interface DbQuerySort {
  field: string
  order?: 'asc' | 'desc' | string
}

export interface DbQueryRelation {
  key: string
  model: string
  type?: 'left' | 'inner' | 'right' | 'full' | string
  on?: Record<string, string>
  filter?: Record<string, DbQueryFilterValue>
}

export interface DbQueryExt {
  fields?: string[]
  relations?: DbQueryRelation[]
  sorts?: DbQuerySort[]
}

export interface DbQueryBaseRequest {
  title?: string
  model: string
  filter_dict?: Record<string, DbQueryFilterValue>
  filterExpr?: string
}

export interface DbQueryListRequest extends DbQueryBaseRequest {
  ext?: DbQueryExt
  page?: number
  page_size?: number
}

export interface DbQueryListResponse {
  list?: Record<string, unknown>[]
  pageInfo?: {
    total?: number
    size?: number
    page?: number
  }
  summary?: Record<string, unknown>
}

export interface DbQueryTreeExt {
  relations?: DbQueryRelation[]
  id_field?: string
  parent_field?: string
  label_field?: string
  children_field?: string
  root_value?: unknown
  max_depth?: number
}

export interface DbQueryTreeNode {
  id?: string | number
  parentId?: string | number | null
  label?: string
  data?: Record<string, unknown>
  children?: DbQueryTreeNode[]
}

export interface DbQueryTreeRequest extends DbQueryBaseRequest {
  fields?: string[]
  sorts?: DbQuerySort[]
  ext?: DbQueryTreeExt
}

export interface DbQueryTreeResponse {
  records?: DbQueryTreeNode[]
  summary?: Record<string, unknown>
}

export interface DbQueryListDatasource extends DbQueryListRequest {
  key: string
  type?: 'db-query-list' | string
}
