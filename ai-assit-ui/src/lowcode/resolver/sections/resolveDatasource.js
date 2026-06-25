import { resolveCountDatasource } from './datasource/resolveCountDatasource'
import { resolveGetDatasource } from './datasource/resolveGetDatasource'
import { resolveListDatasource } from './datasource/resolveListDatasource'
import { resolveTreeDatasource } from './datasource/resolveTreeDatasource'

const datasourceResolvers = {
  list: resolveListDatasource,
  get: resolveGetDatasource,
  tree: resolveTreeDatasource,
  count: resolveCountDatasource
}

export function resolveDatasource(datasource = {}, listConfig = {}) {
  const type = datasource?.type || 'list'
  const resolver = datasourceResolvers[type] || resolveListDatasource
  return resolver(datasource, listConfig)
}
