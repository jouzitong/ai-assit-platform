function applyFilterRule(row, filter, value) {
    if (value === '' || value === null || value === undefined) {
        return true
    }

    const normalizedType = String(filter?.type || '').toLowerCase()
    if (normalizedType === 'select') {
        return String(row?.[filter.key] ?? '') === String(value)
    }

    const keyword = String(value).trim().toLowerCase()
    if (!keyword) {
        return true
    }

    return Object.values(row || {}).some((fieldValue) => String(fieldValue ?? '').toLowerCase().includes(keyword))
}

function filterMockRows(schema, state) {
    const sourceRows = Array.isArray(schema.mockData?.list) ? schema.mockData.list : []
    return sourceRows.filter((row) => schema.filters.every((filter) => applyFilterRule(row, filter, state.query[filter.key])))
}

function executeListDatasource(schema, state) {
    const filteredRows = filterMockRows(schema, state)
    const total = filteredRows.length
    const pageSize = Number(state.pageSize || schema.datasource.pagination.pageSize || 10)
    const page = Number(state.page || 1)
    const fromIndex = Math.max(0, (page - 1) * pageSize)
    const toIndex = fromIndex + pageSize

    return {
        list: filteredRows.slice(fromIndex, toIndex),
        total
    }
}

function executeGetDatasource(schema, state) {
    const filteredRows = filterMockRows(schema, state)
    const idField = schema.datasource.idField || schema.datasource.primaryKey || 'id'
    const targetId = schema.datasource.id ?? state.query[idField]
    const record = targetId == null
        ? (filteredRows[0] || null)
        : (filteredRows.find((row) => String(row?.[idField]) === String(targetId)) || null)

    return {
        record,
        list: record ? [record] : [],
        total: record ? 1 : 0
    }
}

function buildTreeNodeMap(records, idField, parentField) {
    const nodeMap = new Map()
    records.forEach((record) => {
        const node = {...record, children: []}
        nodeMap.set(String(record?.[idField]), node)
    })
    return nodeMap
}

function executeTreeDatasource(schema, state) {
    const filteredRows = filterMockRows(schema, state)
    const ext = schema.datasource.ext || {}
    const idField = ext.idField || ext.id_field || schema.datasource.primaryKey || 'id'
    const parentField = ext.parentField || ext.parent_field || 'parentId'
    const childrenField = ext.childrenField || ext.children_field || 'children'
    const nodeMap = buildTreeNodeMap(filteredRows, idField, parentField)
    const roots = []

    filteredRows.forEach((record) => {
        const node = nodeMap.get(String(record?.[idField]))
        if (!node) {
            return
        }
        const parentId = record?.[parentField]
        const parentNode = parentId == null ? null : nodeMap.get(String(parentId))
        if (!parentNode) {
            roots.push(node)
            return
        }
        if (!Array.isArray(parentNode[childrenField])) {
            parentNode[childrenField] = []
        }
        parentNode[childrenField].push(node)
    })

    return {
        records: roots,
        list: roots,
        total: roots.length
    }
}

function executeCountDatasource(schema, state) {
    const filteredRows = filterMockRows(schema, state)
    return {
        records: [
            {
                total: filteredRows.length
            }
        ],
        list: [
            {
                total: filteredRows.length
            }
        ],
        total: 1,
        summary: {
            total: filteredRows.length
        }
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
