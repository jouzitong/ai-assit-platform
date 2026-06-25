function normalizeComponent(filter) {
  const rawComponent = String(filter?.component || filter?.type || 'input').toLowerCase()
  return rawComponent === 'text' ? 'input' : rawComponent
}

function normalizeOptionMasks(options = {}) {
  const source = Array.isArray(options?.masks)
    ? options.masks
    : Array.isArray(options?.options)
      ? options.options
      : Array.isArray(options?.list)
        ? options.list
        : []

  return source.map((item) => {
    if (item && item.label !== undefined && item.value !== undefined) {
      return item
    }
    return {
      label: item?.name ?? item?.label ?? item?.code ?? item,
      value: item?.value ?? item?.code ?? item?.id ?? item
    }
  })
}

function normalizeFilterOptions(filter, component) {
  const source = filter?.options || filter?.type_config || filter?.typeConfig || {}
  const options = {
    ...source,
    multiple: source?.multiple === true
  }

  if (component === 'select') {
    options.masks = normalizeOptionMasks(source)
    options.model = source?.model || ''
    options.labelField = source?.labelField || 'name'
    options.valueField = source?.valueField || 'id'
    options.filterDict = source?.filter_dict || source?.filterDict || {}
    options.filterExpr = source?.filterExpr || null
    options.relations = Array.isArray(source?.relations) ? source.relations : []
    options.sorts = Array.isArray(source?.sorts) ? source.sorts : []
    options.pageSize = source?.pageSize || source?.page_size || 100
    options.searchField = source?.searchField || source?.search_field || options.labelField
  }

  return options
}

function normalizeFilter(filter) {
  const key = String(filter?.key || '')
  const component = normalizeComponent(filter)
  const options = normalizeFilterOptions(filter, component)
  return {
    key,
    label: filter?.label || key,
    component,
    type: component,
    field: filter?.field || filter?.dataField || key,
    dataField: filter?.dataField || filter?.field || key,
    queryOp: filter?.queryOp || filter?.op || options?.op || '',
    action: filter?.action || `${key}-change`,
    value: filter?.defaultValue ?? filter?.value ?? '',
    variant: filter?.variant || '',
    options,
    type_config: options
  }
}

export function resolveFilters(filters = []) {
  return Array.isArray(filters) ? filters.map(normalizeFilter) : []
}
