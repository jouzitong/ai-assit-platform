function normalizeFilter(filter) {
  const key = String(filter?.key || '')
  const rawType = String(filter?.type || 'text').toLowerCase()
  const fieldType = rawType === 'text' ? 'input' : rawType
  return {
    key,
    label: filter?.label || key,
    type: fieldType,
    field: filter?.field || filter?.dataField || key,
    dataField: filter?.dataField || filter?.field || key,
    queryOp: filter?.queryOp || filter?.op || filter?.type_config?.op || '',
    action: filter?.action || `${key}-change`,
    value: filter?.defaultValue ?? filter?.value ?? '',
    variant: filter?.variant || '',
    type_config: filter?.type_config || filter?.typeConfig || {}
  }
}

export function resolveFilters(filters = []) {
  return Array.isArray(filters) ? filters.map(normalizeFilter) : []
}
