import { queryDbCount, queryDbGet, queryDbList, queryDbTree } from '../../../../api/dbQuery'
import { buildDbCountRequest, buildDbGetRequest, buildDbListRequest, buildDbTreeRequest } from './buildDbQueryRequest'

async function executeListDatasource(schema, state) {
  const payload = await queryDbList(buildDbListRequest(schema, state))
  return {
    list: Array.isArray(payload?.records) ? payload.records : [],
    total: Number(payload?.total || 0),
    summary: payload?.summary || {},
    page: payload?.page,
    pageSize: payload?.pageSize ?? payload?.page_size
  }
}

async function executeGetDatasource(schema, state) {
  const payload = await queryDbGet(buildDbGetRequest(schema, state))
  const record = payload?.record || null
  return {
    record,
    list: record ? [record] : [],
    total: record ? 1 : 0
  }
}

async function executeTreeDatasource(schema, state) {
  const payload = await queryDbTree(buildDbTreeRequest(schema, state))
  const records = Array.isArray(payload?.records) ? payload.records : []
  return {
    records,
    list: records,
    total: records.length,
    summary: payload?.summary || {}
  }
}

async function executeCountDatasource(schema, state) {
  const payload = await queryDbCount(buildDbCountRequest(schema, state))
  const records = Array.isArray(payload?.records) ? payload.records : []
  return {
    records,
    list: records,
    total: Number(payload?.total || records.length || 0),
    summary: payload?.summary || {}
  }
}

export async function executeDatasource(schema, state) {
  const datasourceType = schema.datasource.type || 'list'

  if (datasourceType === 'get') {
    return executeGetDatasource(schema, state)
  }
  if (datasourceType === 'tree') {
    return executeTreeDatasource(schema, state)
  }
  if (datasourceType === 'count') {
    return executeCountDatasource(schema, state)
  }

  return executeListDatasource(schema, state)
}
