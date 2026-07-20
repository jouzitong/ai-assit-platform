import {
  executeRuntimeDataRequest,
  type RuntimeDataRequestPlan,
  type RuntimeDataRequestResult,
} from '../data-requester'
import type {
  DirectJsonListDatasource,
  DbQueryListDatasource,
  DbQueryListRequest,
  DbQueryFilterValue,
  DbQueryListResponse,
  ListRendererData,
  ListRendererSchema,
  RendererField,
  RendererFilter,
  RendererQueryState,
} from '../schema'

export interface ResolveListRendererDataOptions {
  schema: ListRendererSchema
  query?: Partial<RendererQueryState>
}

export interface ResolvedListRendererData {
  data: ListRendererData
  summary: Record<string, unknown>
  requestPlans?: RuntimeDataRequestPlan[]
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function isDirectJsonDatasource(datasource: ListRendererSchema['datasource']): datasource is DirectJsonListDatasource {
  return datasource?.type === 'direct-json'
}

function compactFilterDict(filters?: Record<string, unknown>) {
  if (!filters) {
    return {}
  }

  return Object.entries(filters).reduce<Record<string, unknown>>((acc, [key, value]) => {
    if (value == null) {
      return acc
    }

    if (typeof value === 'string' && value.trim() === '') {
      return acc
    }

    if (Array.isArray(value) && value.length === 0) {
      return acc
    }

    if (isFilterConditionValue(value) && !shouldKeepFilterCondition(value)) {
      return acc
    }

    acc[key] = value
    return acc
  }, {})
}

function hasFilterExprKey(filterExpr: string | undefined, key: string) {
  if (!filterExpr) {
    return false
  }
  return new RegExp(`(^|[^\\w])${key}([^\\w]|$)`).test(filterExpr)
}

function mergeFilterExpr(filterExpr: string | undefined, runtimeFilterKeys: string[]) {
  const nextKeys = runtimeFilterKeys.filter((key) => !hasFilterExprKey(filterExpr, key))
  if (!filterExpr || nextKeys.length === 0) {
    return filterExpr
  }
  return [filterExpr, ...nextKeys].join(' and ')
}

function buildRuntimeFilterDict(
  filters: RendererFilter[] = [],
  queryFilters?: Record<string, unknown>,
) {
  const rawFilters = compactFilterDict(queryFilters)
  const filterMap = new Map(filters.map((filter) => [filter.key, filter]))

  return Object.entries(rawFilters).reduce<Record<string, DbQueryFilterValue>>((acc, [key, value]) => {
    const filter = filterMap.get(key)
    const queryKey = filter?.query?.field || key
    const op = filter?.query?.op
    if (isFilterConditionValue(value)) {
      acc[queryKey] = normalizeFilterConditionValue(value)
    } else {
      acc[queryKey] = op ? { op, value } : value
    }
    return acc
  }, {})
}

function isFilterConditionValue(value: unknown): value is { op?: string; value?: unknown } {
  return isRecord(value) && typeof value.op === 'string'
}

function normalizeFilterConditionValue(value: { op?: string; value?: unknown }) {
  if (value.op === 'is_null' || value.op === 'is_not_null') {
    return { op: value.op }
  }
  return {
    op: value.op,
    value: value.value,
  }
}

function shouldKeepFilterCondition(value: { op?: string; value?: unknown }) {
  if (value.op === 'is_null' || value.op === 'is_not_null') {
    return true
  }
  if (value.value == null) {
    return false
  }
  if (typeof value.value === 'string' && value.value.trim() === '') {
    return false
  }
  if (Array.isArray(value.value) && value.value.length === 0) {
    return false
  }
  return true
}

function resolveRendererFieldPath(field: RendererField) {
  const segments = field.field
    ?.map(segment => segment.trim())
    .filter(Boolean)

  if (segments?.length) {
    return segments.join('.')
  }

  return field.key?.trim() || ''
}

function resolveRequestFields(
  datasource: DbQueryListDatasource,
  rendererFields: RendererField[] = [],
) {
  const result = new Set<string>()
  const datasourceFields = datasource.ext?.fields || []

  datasourceFields.forEach((field) => {
    const normalized = field?.trim()
    if (normalized) {
      result.add(normalized)
    }
  })

  rendererFields.forEach((field) => {
    const resolved = resolveRendererFieldPath(field)
    if (resolved) {
      result.add(resolved)
    }
  })

  return [...result]
}

export function buildDbQueryListRequest(
  schema: ListRendererSchema,
  query: Partial<RendererQueryState> = {},
): DbQueryListRequest | null {
  const datasource = schema.datasource
  if (!datasource || isDirectJsonDatasource(datasource) || !datasource.model) {
    return null
  }

  const pagination = schema.list_config?.pagination
  const pageSize = query.pageSize || pagination?.pageSize || datasource.page_size || 10
  const runtimeFilterDict = buildRuntimeFilterDict(schema.filters, query.filters)
  const runtimeFilterKeys = Object.keys(runtimeFilterDict)
  const requestFields = resolveRequestFields(datasource, schema.fields)
  const ext = datasource.ext || requestFields.length
    ? {
        ...datasource.ext,
        ...(requestFields.length ? { fields: requestFields } : {}),
      }
    : undefined

  return {
    title: datasource.title || schema.title,
    model: datasource.model,
    filter_dict: {
      ...compactFilterDict(datasource.filter_dict),
      ...runtimeFilterDict,
    },
    filterExpr: mergeFilterExpr(datasource.filterExpr, runtimeFilterKeys),
    ext,
    page: query.page || datasource.page || 1,
    page_size: pageSize,
  }
}

export interface ResolvedListRendererStructure {
  schema: ListRendererSchema
  query: Partial<RendererQueryState>
  requestPlans: RuntimeDataRequestPlan[]
}

export function resolveListRendererStructure(options: ResolveListRendererDataOptions): ResolvedListRendererStructure {
  const datasource = options.schema.datasource
  const query = options.query || {}

  if (isDirectJsonDatasource(datasource)) {
    return {
      schema: options.schema,
      query,
      requestPlans: [{
        key: datasource.key,
        type: 'direct-json',
        data: datasource.data,
        summary: datasource.summary || {},
      }],
    }
  }

  const requestBody = buildDbQueryListRequest(options.schema, query)
  return {
    schema: options.schema,
    query,
    requestPlans: requestBody
      ? [{
          key: datasource?.key || options.schema.id,
          type: 'db-query-list',
          request: requestBody,
        }]
      : [],
  }
}

export function parseDbQueryListResponse(response: DbQueryListResponse): ResolvedListRendererData {
  const records = Array.isArray(response.list) ? response.list : []
  const total = Number(response.pageInfo?.total ?? records.length)

  return {
    data: {
      records,
      total,
    },
    summary: response.summary || {},
  }
}

export function parseListRendererRequestResult(result: RuntimeDataRequestResult): ResolvedListRendererData {
  if (result.plan.type === 'direct-json') {
    const payload = result.raw as { data?: DirectJsonListDatasource['data']; summary?: Record<string, unknown> }
    return parseDirectJsonListData(payload.data, payload.summary || {})
  }

  return parseDbQueryListResponse(result.raw as DbQueryListResponse)
}

export async function resolveDbQueryListData(options: ResolveListRendererDataOptions) {
  const structure = resolveListRendererStructure(options)
  const requestPlan = structure.requestPlans[0]
  if (!requestPlan) {
    return {
      data: {
        records: [],
        total: 0,
      },
      summary: {},
      requestPlans: structure.requestPlans,
    } satisfies ResolvedListRendererData
  }

  const result = await executeRuntimeDataRequest(requestPlan)
  const resolved = parseListRendererRequestResult(result)
  return {
    ...resolved,
    requestPlans: structure.requestPlans,
  } satisfies ResolvedListRendererData
}

export function parseDirectJsonListData(
  value: DirectJsonListDatasource['data'],
  summary: Record<string, unknown> = {},
): ResolvedListRendererData {
  if (isRecord(value)) {
    const records = Array.isArray(value.records) ? value.records as Record<string, unknown>[] : []
    const treeData = Array.isArray(value.treeData) ? value.treeData as ListRendererData['treeData'] : undefined
    const total = typeof value.total === 'number' ? value.total : records.length

    return {
      data: {
        records,
        total,
        treeData,
      },
      summary,
    }
  }

  return {
    data: {
      records: [],
      total: 0,
    },
    summary,
  }
}

export function resolveDirectJsonListData(options: ResolveListRendererDataOptions) {
  const requestPlan = resolveListRendererStructure(options).requestPlans[0]
  if (!requestPlan || requestPlan.type !== 'direct-json') {
    return parseDirectJsonListData(undefined)
  }
  return parseDirectJsonListData(requestPlan.data as DirectJsonListDatasource['data'], requestPlan.summary || {})
}

export function resolveListRendererData(options: ResolveListRendererDataOptions) {
  if (isDirectJsonDatasource(options.schema.datasource)) {
    return resolveDirectJsonListData(options)
  }
  return resolveDbQueryListData(options)
}
