import { resolveBaseDatasource } from './resolveBaseDatasource'

export function resolveTreeDatasource(datasource = {}, listConfig = {}) {
  return {
    ...resolveBaseDatasource(datasource, listConfig),
    type: 'tree',
    metrics: Array.isArray(datasource?.metrics) ? datasource.metrics : [],
    having: datasource?.having || {},
    ext: datasource?.ext || {}
  }
}
