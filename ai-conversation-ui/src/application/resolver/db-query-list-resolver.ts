import { request } from '../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../config/services'
import type {
  DirectJsonListDatasource,
  DbQueryListRequest,
  DbQueryListResponse,
  ListRendererData,
  ListRendererSchema,
  RendererQueryState,
} from '../schema'

const DB_QUERY_LIST_ENDPOINT = `${getBackendService(SERVICE_NAMES.DB_ENGINE).gatewayPrefix}/api/v1/query.list`

export interface ResolveListRendererDataOptions {
  schema: ListRendererSchema
  query?: Partial<RendererQueryState>
}

export interface ResolvedListRendererData {
  data: ListRendererData
  summary: Record<string, unknown>
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function isDirectJsonDatasource(datasource: ListRendererSchema['datasource']): datasource is DirectJsonListDatasource {
  return datasource?.type === 'direct-json'
}

export function buildDbQueryListRequest(
  schema: ListRendererSchema,
  query: Partial<RendererQueryState> = {},
): DbQueryListRequest | null {
  const datasource = schema.datasource
  if (!datasource?.model) {
    return null
  }

  const pagination = schema.list_config?.pagination
  const pageSize = query.pageSize || pagination?.pageSize || datasource.page_size || 10

  return {
    title: datasource.title || schema.title,
    model: datasource.model,
    filter_dict: {
      ...(datasource.filter_dict || {}),
      ...(query.filters || {}),
    },
    filterExpr: datasource.filterExpr,
    ext: datasource.ext,
    page: query.page || datasource.page || 1,
    page_size: pageSize,
  }
}

export function parseDbQueryListResponse(response: DbQueryListResponse): ResolvedListRendererData {
  return {
    data: {
      records: response.records || [],
      total: response.total || 0,
    },
    summary: response.summary || {},
  }
}

export async function resolveDbQueryListData(options: ResolveListRendererDataOptions) {
  const requestBody = buildDbQueryListRequest(options.schema, options.query)
  if (!requestBody) {
    return {
      data: {
        records: [],
        total: 0,
      },
      summary: {},
    } satisfies ResolvedListRendererData
  }

  const response = await request<DbQueryListResponse>(DB_QUERY_LIST_ENDPOINT, {
    method: 'POST',
    body: JSON.stringify(requestBody),
  })

  return parseDbQueryListResponse(response)
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
  const datasource = options.schema.datasource
  if (!isDirectJsonDatasource(datasource)) {
    return parseDirectJsonListData(undefined)
  }
  return parseDirectJsonListData(datasource.data, datasource.summary || {})
}

export function resolveListRendererData(options: ResolveListRendererDataOptions) {
  if (isDirectJsonDatasource(options.schema.datasource)) {
    return resolveDirectJsonListData(options)
  }
  return resolveDbQueryListData(options)
}
