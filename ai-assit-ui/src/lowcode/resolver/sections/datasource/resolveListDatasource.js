import { resolveBaseDatasource } from './resolveBaseDatasource'

export function resolveListDatasource(datasource = {}, listConfig = {}) {
  return {
    ...resolveBaseDatasource(datasource, listConfig),
    type: 'list'
  }
}
