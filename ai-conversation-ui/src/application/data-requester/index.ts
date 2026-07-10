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
