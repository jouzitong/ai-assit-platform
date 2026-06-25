function unique(items) {
  return Array.from(new Set(items.filter(Boolean)))
}

function normalizeFieldPath(value) {
  if (Array.isArray(value)) {
    return value.filter(Boolean).join('.')
  }
  return String(value || '').trim()
}

function resolveSchemaFieldPaths(schema) {
  const source = Array.isArray(schema.fields) ? schema.fields : []
  return unique(source.map((field) => normalizeFieldPath(field?.field || field?.key)))
}

function resolveFilterField(filter) {
  return filter?.field || filter?.dataField || filter?.key || ''
}

function resolveFilterOp(filter) {
  if (filter?.queryOp || filter?.op || filter?.type_config?.op) {
    return filter?.queryOp || filter?.op || filter?.type_config?.op
  }
  if (filter?.component === 'select' || filter?.type === 'select') {
    return filter?.options?.multiple === true ? 'in' : 'eq'
  }
  return 'like'
}

function buildRuntimeFilterDict(schema, state) {
  const filters = Array.isArray(schema.filters) ? schema.filters : []
  return filters.reduce((result, filter) => {
    const value = state.query?.[filter.key]
    if (value === '' || value === null || value === undefined) {
      return result
    }
    if (Array.isArray(value) && value.length === 0) {
      return result
    }
    const field = resolveFilterField(filter)
    if (!field) {
      return result
    }
    result[field] = {
      op: resolveFilterOp(filter),
      value
    }
    return result
  }, {})
}

function buildFilterExpr(baseFilterExpr, baseFilterDict, runtimeFilterDict) {
  const runtimeKeys = Object.keys(runtimeFilterDict || {})
  if (!runtimeKeys.length) {
    return baseFilterExpr || null
  }

  if (!baseFilterExpr) {
    return null
  }

  const baseKeys = Object.keys(baseFilterDict || {})
  const appendedKeys = runtimeKeys.filter((key) => !baseKeys.includes(key))
  if (!appendedKeys.length) {
    return baseFilterExpr
  }

  return `(${baseFilterExpr}) and ${appendedKeys.join(' and ')}`
}

function buildBaseRequest(schema, state) {
  const baseFilterDict = schema.datasource.filterDict || {}
  const runtimeFilterDict = buildRuntimeFilterDict(schema, state)
  return {
    title: schema.title || schema.viewId || '',
    model: schema.datasource.model || '',
    filter_dict: {
      ...baseFilterDict,
      ...runtimeFilterDict
    },
    filterExpr: buildFilterExpr(schema.datasource.filterExpr, baseFilterDict, runtimeFilterDict)
  }
}

function buildRelations(schema) {
  return Array.isArray(schema.datasource.relations) ? schema.datasource.relations : []
}

function buildSorts(schema) {
  return Array.isArray(schema.datasource.sorts) ? schema.datasource.sorts : []
}

export function buildDbListRequest(schema, state) {
  return {
    ...buildBaseRequest(schema, state),
    page: state.page,
    page_size: state.pageSize,
    ext: {
      fields: resolveSchemaFieldPaths(schema),
      relations: buildRelations(schema),
      sorts: buildSorts(schema)
    }
  }
}

export function buildDbGetRequest(schema, state) {
  const idField = schema.datasource.idField || schema.datasource.primaryKey || 'id'
  const id = schema.datasource.id ?? state.query?.[idField] ?? null
  return {
    ...buildBaseRequest(schema, state),
    id,
    ext: {
      fields: resolveSchemaFieldPaths(schema),
      relations: buildRelations(schema),
      sorts: buildSorts(schema)
    }
  }
}

export function buildDbTreeRequest(schema, state) {
  const ext = schema.datasource.ext || {}
  const fieldPaths = resolveSchemaFieldPaths(schema)
  const idField = ext.idField || ext.id_field || schema.datasource.primaryKey || 'id'
  const parentField = ext.parentField || ext.parent_field || 'parentId'
  const labelField = ext.labelField || ext.label_field || fieldPaths[0] || idField

  return {
    ...buildBaseRequest(schema, state),
    fields: unique([...fieldPaths, idField, parentField, labelField]),
    metrics: Array.isArray(schema.datasource.metrics) ? schema.datasource.metrics : [],
    having: schema.datasource.having || {},
    sorts: buildSorts(schema),
    ext: {
      relations: buildRelations(schema),
      id_field: idField,
      parent_field: parentField,
      label_field: labelField,
      children_field: ext.childrenField || ext.children_field || 'children',
      root_value: ext.rootValue ?? ext.root_value,
      max_depth: ext.maxDepth ?? ext.max_depth
    }
  }
}

export function buildDbCountRequest(schema, state) {
  return {
    ...buildBaseRequest(schema, state),
    dimensions: Array.isArray(schema.datasource.dimensions) ? schema.datasource.dimensions : [],
    metrics: Array.isArray(schema.datasource.metrics) ? schema.datasource.metrics : [],
    having: schema.datasource.having || {},
    sorts: buildSorts(schema),
    page: state.page,
    page_size: state.pageSize,
    ext: {
      relations: buildRelations(schema),
      time_grain: schema.datasource.timeGrain || schema.datasource.time_grain,
      top_n: schema.datasource.topN || schema.datasource.top_n
    }
  }
}
