import {
  executeRuntimeDataRequest,
  type RuntimeDataRequestPlan,
} from '../data-requester'
import {
  parseListRendererRequestResult,
  resolveListRendererStructure,
} from './db-query-list-resolver'
import type { ListRendererSchema, RendererQueryState } from '../schema'

export interface ChartRendererSchema extends Record<string, unknown> {
  id?: string
  title?: string
  datasource?: ListRendererSchema['datasource']
  filters?: ListRendererSchema['filters']
  fields?: ListRendererSchema['fields']
  bindings?: Record<string, unknown>
}

export interface ResolveChartRendererDataOptions {
  schema: ChartRendererSchema
  query?: Partial<RendererQueryState>
}

export interface ResolvedChartRendererData {
  data: Record<string, unknown>
  summary: Record<string, unknown>
  requestPlans: RuntimeDataRequestPlan[]
}

const CHART_DATA_KEYS = new Set([
  'categories',
  'series',
  'barSeries',
  'lineSeries',
  'indicators',
  'xCategories',
  'yCategories',
  'data',
  'value',
])

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function resolveBindingField(value: unknown) {
  if (typeof value === 'string' && value.trim()) return value.trim()
  if (!isRecord(value)) return ''
  if (typeof value.field === 'string' && value.field.trim()) return value.field.trim()
  if (typeof value.source === 'string' && value.source.trim()) return value.source.trim()
  return ''
}

function collectBindingFields(value: unknown, result = new Set<string>()): Set<string> {
  if (Array.isArray(value)) {
    value.forEach(item => collectBindingFields(item, result))
    return result
  }
  if (!isRecord(value)) return result

  Object.entries(value).forEach(([key, child]) => {
    if (key === 'field' || key === 'source' || ['category', 'x', 'y', 'value', 'group', 'seriesName'].includes(key)) {
      const field = resolveBindingField(child)
      if (field) result.add(field)
    }
    collectBindingFields(child, result)
  })
  return result
}

function createListSchema(schema: ChartRendererSchema): ListRendererSchema {
  const bindingFields = [...collectBindingFields(schema.bindings)]
  const existingFields = Array.isArray(schema.fields) ? schema.fields : []
  const existingKeys = new Set(existingFields.map(field => field.key))
  const fields = [
    ...existingFields,
    ...bindingFields
      .filter(field => !existingKeys.has(field))
      .map(field => ({ key: field, name: field, field: field.split('.') })),
  ]

  return {
    ...(schema as unknown as ListRendererSchema),
    id: schema.id || 'chart-renderer',
    fields,
  }
}

function hasChartPayload(value: Record<string, unknown>) {
  return Object.keys(value).some(key => CHART_DATA_KEYS.has(key))
}

function resolveInlineChartPayload(plan: RuntimeDataRequestPlan) {
  if ((plan.type === 'direct-json' || plan.type === 'local') && isRecord(plan.data) && hasChartPayload(plan.data)) {
    return plan.data
  }
  return undefined
}

export async function resolveChartRendererData(
  options: ResolveChartRendererDataOptions,
): Promise<ResolvedChartRendererData> {
  const schema = createListSchema(options.schema)
  const structure = resolveListRendererStructure({
    schema,
    query: options.query,
  })
  const requestPlans = structure.requestPlans
  const requestPlan = requestPlans[0]

  if (!requestPlan) {
    return { data: { records: [], total: 0 }, summary: {}, requestPlans }
  }

  const result = await executeRuntimeDataRequest(requestPlan)
  const resolved = parseListRendererRequestResult(result)
  const inlinePayload = resolveInlineChartPayload(requestPlan)

  return {
    data: {
      ...(inlinePayload || {}),
      records: resolved.data.records,
      total: resolved.data.total,
    },
    summary: resolved.summary,
    requestPlans,
  }
}
