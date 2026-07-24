import {
  executeRuntimeDataRequest,
  unwrapRuntimeApiResponse,
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
  LocalListDatasource,
  LocalListDataInput,
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

function isLocalDatasource(datasource: ListRendererSchema['datasource']): datasource is LocalListDatasource {
  return datasource?.type === 'local'
}

function isInlineDatasource(datasource: ListRendererSchema['datasource']): datasource is DirectJsonListDatasource | LocalListDatasource {
  return isDirectJsonDatasource(datasource) || isLocalDatasource(datasource)
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
  if (!datasource || isInlineDatasource(datasource) || !datasource.model) {
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

  if (isInlineDatasource(datasource)) {
    return {
      schema: options.schema,
      query,
      requestPlans: [{
        key: datasource.key,
        type: datasource.type,
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
  return parseListResponseData(response)
}

export function parseListRendererRequestResult(result: RuntimeDataRequestResult): ResolvedListRendererData {
  if (result.plan.type === 'direct-json') {
    const payload = result.raw as { data?: DirectJsonListDatasource['data']; summary?: Record<string, unknown> }
    return parseDirectJsonListData(payload.data, payload.summary || {})
  }

  if (result.plan.type === 'local') {
    return parseLocalListData(result.raw, result.plan.summary || {})
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
  return parseListResponseData(value, summary)
}

export function parseLocalListData(
  value: unknown,
  summary: Record<string, unknown> = {},
): ResolvedListRendererData {
  const data = unwrapRuntimeApiResponse<LocalListDataInput>(value)
  const records = Array.isArray(data?.list)
    ? data.list.filter(isRecord)
    : []
  const responseSummary = isRecord(data?.summary) ? data.summary : {}

  return {
    data: {
      records,
      total: Number(data?.pageInfo?.total ?? records.length),
    },
    summary: {
      ...summary,
      ...responseSummary,
    },
  }
}

function filterLocalRecords(
  records: Record<string, unknown>[],
  filters: RendererFilter[] = [],
  queryFilters?: Record<string, unknown>,
) {
  const activeFilters = compactFilterDict(queryFilters)
  if (!Object.keys(activeFilters).length) {
    return records
  }

  const filterMap = new Map(filters.map(filter => [filter.key, filter]))
  return records.filter(record => Object.entries(activeFilters).every(([key, expected]) => {
    const filter = filterMap.get(key)
    const field = filter?.query?.field || key
    const actual = getRecordValue(record, field)
    const condition = isFilterConditionValue(expected) ? expected : undefined
    const value = condition ? condition.value : expected
    const operation = condition?.op || filter?.query?.op || 'eq'
    return matchesLocalFilter(actual, value, operation)
  }))
}

function getRecordValue(record: Record<string, unknown>, field: string) {
  return field.split('.').reduce<unknown>((current, segment) => (
    isRecord(current) ? current[segment] : undefined
  ), record)
}

function matchesLocalFilter(actual: unknown, expected: unknown, operation: string) {
  if (operation === 'is_null') return actual == null
  if (operation === 'is_not_null') return actual != null

  if (operation === 'in' || operation === 'not_in') {
    const values = Array.isArray(expected) ? expected : [expected]
    const matched = values.some(value => isSameFilterValue(actual, value))
    return operation === 'in' ? matched : !matched
  }

  if (operation === 'like') {
    return String(actual ?? '').toLowerCase().includes(String(expected ?? '').toLowerCase())
  }

  if (operation === 'ne') return !isSameFilterValue(actual, expected)
  if (operation === 'gt') return compareFilterValues(actual, expected) > 0
  if (operation === 'gte') return compareFilterValues(actual, expected) >= 0
  if (operation === 'lt') return compareFilterValues(actual, expected) < 0
  if (operation === 'lte') return compareFilterValues(actual, expected) <= 0
  return isSameFilterValue(actual, expected)
}

function isSameFilterValue(actual: unknown, expected: unknown) {
  if (Array.isArray(actual)) {
    return actual.some(value => isSameFilterValue(value, expected))
  }
  return String(actual ?? '').toLowerCase() === String(expected ?? '').toLowerCase()
}

function compareFilterValues(actual: unknown, expected: unknown) {
  const actualNumber = Number(actual)
  const expectedNumber = Number(expected)
  if (Number.isFinite(actualNumber) && Number.isFinite(expectedNumber)) {
    return actualNumber - expectedNumber
  }
  return String(actual ?? '').localeCompare(String(expected ?? ''))
}

function parseListResponseData(
  value: unknown,
  summary: Record<string, unknown> = {},
): ResolvedListRendererData {
  const root = isRecord(value) ? value : {}
  const firstData = root.data
  const firstBody = isRecord(firstData) ? firstData : undefined
  const secondData = firstBody?.data
  const body = isRecord(secondData)
    ? secondData
    : firstBody || root
  const recordsValue = Array.isArray(firstData)
    ? firstData
    : body.records ?? body.list ?? body.data
  const records = Array.isArray(recordsValue)
    ? recordsValue.filter(isRecord)
    : []
  const pageInfo = isRecord(root.pageInfo)
    ? root.pageInfo
    : isRecord(firstBody?.pageInfo)
      ? firstBody.pageInfo
      : isRecord(body.pageInfo)
        ? body.pageInfo
        : undefined
  const totalValue = root.total
    ?? firstBody?.total
    ?? body.total
    ?? pageInfo?.total
  const total = Number.isFinite(Number(totalValue))
    ? Number(totalValue)
    : records.length
  const treeDataValue = root.treeData
    ?? firstBody?.treeData
    ?? body.treeData
  const responseSummary = isRecord(root.summary)
    ? root.summary
    : isRecord(firstBody?.summary)
      ? firstBody.summary
      : isRecord(body.summary)
        ? body.summary
        : {}

  return {
    data: {
      records,
      total,
      treeData: Array.isArray(treeDataValue)
        ? treeDataValue as ListRendererData['treeData']
        : undefined,
    },
    summary: {
      ...summary,
      ...responseSummary,
    },
  }
}

export function resolveDirectJsonListData(options: ResolveListRendererDataOptions) {
  const requestPlan = resolveListRendererStructure(options).requestPlans[0]
  if (!requestPlan || requestPlan.type !== 'direct-json') {
    return parseDirectJsonListData(undefined)
  }
  const resolved = parseDirectJsonListData(
    requestPlan.data as DirectJsonListDatasource['data'],
    requestPlan.summary || {},
  )
  const records = filterLocalRecords(
    resolved.data.records,
    options.schema.filters,
    options.query?.filters,
  )
  return {
    ...resolved,
    data: { ...resolved.data, records, total: records.length },
  }
}

export async function resolveLocalListData(options: ResolveListRendererDataOptions) {
  const structure = resolveListRendererStructure(options)
  const requestPlan = structure.requestPlans[0]
  if (!requestPlan || requestPlan.type !== 'local') {
    return parseLocalListData(undefined)
  }

  const result = await executeRuntimeDataRequest(requestPlan)
  const resolved = parseListRendererRequestResult(result)
  const records = filterLocalRecords(
    resolved.data.records,
    options.schema.filters,
    options.query?.filters,
  )
  return {
    ...resolved,
    data: { ...resolved.data, records, total: records.length },
    requestPlans: structure.requestPlans,
  } satisfies ResolvedListRendererData
}

export function resolveListRendererData(options: ResolveListRendererDataOptions) {
  if (isDirectJsonDatasource(options.schema.datasource)) {
    return resolveDirectJsonListData(options)
  }
  if (isLocalDatasource(options.schema.datasource)) {
    return resolveLocalListData(options)
  }
  return resolveDbQueryListData(options)
}
