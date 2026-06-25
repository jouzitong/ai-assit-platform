export function resolveBaseDatasource(datasource = {}, listConfig = {}) {
  const paginationConfig = listConfig?.pagination || {}

  return {
    key: datasource?.key || '',
    model: datasource?.model || '',
    type: datasource?.type || 'list',
    primaryKey: datasource?.primaryKey || 'id',
    filterDict: datasource?.filter_dict || datasource?.filterDict || {},
    filterExpr: datasource?.filterExpr || null,
    relations: Array.isArray(datasource?.relations) ? datasource.relations : [],
    sorts: Array.isArray(datasource?.sorts) ? datasource.sorts : [],
    pagination: {
      enabled: paginationConfig.enabled !== false,
      pageSize: paginationConfig.pageSize || 10,
      pageSizeOptions: Array.isArray(paginationConfig.pageSizeOptions) ? paginationConfig.pageSizeOptions : [10, 20, 30, 50]
    }
  }
}
