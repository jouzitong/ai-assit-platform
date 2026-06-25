import { resolveBaseDatasource } from './resolveBaseDatasource'

export function resolveCountDatasource(datasource = {}, listConfig = {}) {
  return {
    ...resolveBaseDatasource(datasource, listConfig),
    type: 'count',
    dimensions: Array.isArray(datasource?.dimensions) ? datasource.dimensions : [],
    metrics: Array.isArray(datasource?.metrics) ? datasource.metrics : [],
    having: datasource?.having || {}
  }
}
