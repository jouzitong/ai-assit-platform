import { request } from '../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../config/services'
import type {
  DbQueryListRequest,
  DbQueryListResponse,
} from '../schema'

const DB_QUERY_LIST_ENDPOINT = `${getBackendService(SERVICE_NAMES.DB_ENGINE).gatewayPrefix}/api/v1/query.list`

export type RuntimeDataRequestPlan =
  | RuntimeDbQueryListRequestPlan
  | RuntimeDirectJsonRequestPlan
  | RuntimeLocalRequestPlan

export interface RuntimeDbQueryListRequestPlan {
  key: string
  type: 'db-query-list'
  request: DbQueryListRequest
}

export interface RuntimeDirectJsonRequestPlan {
  key: string
  type: 'direct-json'
  data?: unknown
  summary?: Record<string, unknown>
}

export interface RuntimeLocalRequestPlan {
  key: string
  type: 'local'
  data?: unknown
  summary?: Record<string, unknown>
}

export interface RuntimeApiResponse<TData = unknown> {
  code: number
  msg: string
  data: TData
}

export function unwrapRuntimeApiResponse<TData>(value: unknown): TData {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('本地模拟响应格式不正确')
  }

  const response = value as Partial<RuntimeApiResponse<TData>>
  if (Number(response.code) !== 0) {
    throw new Error(response.msg || '本地模拟请求失败')
  }

  return response.data as TData
}

export interface RuntimeDataRequestResult<TData = unknown> {
  plan: RuntimeDataRequestPlan
  raw: TData
}

export async function executeRuntimeDataRequest(plan: RuntimeDataRequestPlan) {
  if (plan.type === 'direct-json') {
    return {
      plan,
      raw: {
        data: plan.data,
        summary: plan.summary || {},
      },
      } satisfies RuntimeDataRequestResult
  }

  if (plan.type === 'local') {
    return {
      plan,
      raw: {
        code: 0,
        msg: 'success',
        data: plan.data,
      } satisfies RuntimeApiResponse,
    } satisfies RuntimeDataRequestResult
  }

  const response = await request<DbQueryListResponse>(DB_QUERY_LIST_ENDPOINT, {
    method: 'POST',
    body: JSON.stringify(plan.request),
  })

  return {
    plan,
    raw: response,
  } satisfies RuntimeDataRequestResult<DbQueryListResponse>
}

export async function executeRuntimeDataRequests(plans: RuntimeDataRequestPlan[]) {
  return Promise.all(plans.map((plan) => executeRuntimeDataRequest(plan)))
}
