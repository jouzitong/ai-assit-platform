import { resolveBaseDatasource } from './resolveBaseDatasource'

export function resolveGetDatasource(datasource = {}, listConfig = {}) {
  return {
    ...resolveBaseDatasource(datasource, listConfig),
    type: 'get',
    id: datasource?.id ?? null,
    idField: datasource?.idField || datasource?.id_field || 'id'
  }
}
