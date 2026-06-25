import { reactive } from 'vue'
import { queryDbList } from '../../api/dbQuery'
import { handleRuntimeAction } from './executors/action/handleRuntimeAction'
import { createRuntimeDataLoader } from './executors/data-loader/createRuntimeDataLoader'
import { executeHook } from './executors/hook/executeHook'

function buildInitialQuery(schema) {
  return schema.filters.reduce((result, filter) => {
    result[filter.key] = filter.value ?? ''
    return result
  }, {})
}

function normalizeMaskOption(item) {
  if (item && item.label !== undefined && item.value !== undefined) {
    return item
  }
  return {
    label: item?.name ?? item?.label ?? item?.code ?? item,
    value: item?.value ?? item?.code ?? item?.id ?? item
  }
}

function createSelectFetchMethod(filter) {
  const options = filter.options || {}
  if (!options.model) {
    return null
  }

  return async (params = {}) => {
    const searchValue = params?.[options.searchField]
    const payload = {
      title: filter.label || filter.key,
      model: options.model,
      filter_dict: {
        ...(options.filterDict || {}),
        ...(searchValue
          ? {
              [options.searchField]: {
                op: 'like',
                value: searchValue
              }
            }
          : {})
      },
      filterExpr: options.filterExpr || null,
      page: 1,
      page_size: options.pageSize || 100,
      ext: {
        fields: [options.valueField || 'id', options.labelField || 'name'],
        relations: Array.isArray(options.relations) ? options.relations : [],
        sorts: Array.isArray(options.sorts) ? options.sorts : []
      }
    }
    const response = await queryDbList(payload)
    const records = Array.isArray(response?.records) ? response.records : []
    return records.map((item) => ({
      label: item?.[options.labelField || 'name'],
      value: item?.[options.valueField || 'id']
    }))
  }
}

function buildFilterOptions(filter) {
  const options = {
    ...(filter.options || {})
  }

  if (filter.component === 'select') {
    options.multiple = options.multiple === true
    options.masks = Array.isArray(options.masks) ? options.masks.map(normalizeMaskOption) : []
    const fetchMethod = createSelectFetchMethod(filter)
    if (fetchMethod) {
      options.fetchMethod = fetchMethod
      options.enableSearch = options.enableSearch !== false
      options.searchKey = options.searchField || options.labelField || 'name'
    }
  }

  return options
}

function buildSummaryItems(schema, query) {
  const items = schema.filters
    .filter((filter) => query[filter.key] !== '' && query[filter.key] !== null && query[filter.key] !== undefined)
    .filter((filter) => !(Array.isArray(query[filter.key]) && query[filter.key].length === 0))
    .map((filter) => {
      if (filter.component === 'select') {
        const masks = Array.isArray(filter.options?.masks) ? filter.options.masks : []
        const rawValue = query[filter.key]
        if (Array.isArray(rawValue)) {
          const labels = rawValue.map((value) => {
            const target = masks.find((item) => String(item?.value) === String(value))
            return target?.label || value
          })
          return {
            key: filter.label,
            value: labels.join(', ')
          }
        }
        const target = masks.find((item) => String(item?.value) === String(rawValue))
        return {
          key: filter.label,
          value: target?.label || rawValue
        }
      }
      return {
        key: filter.label,
        value: query[filter.key]
      }
    })

  if (items.length) {
    items.push({ key: '清空全部', value: '', ghost: true, action: 'clear' })
  }
  return items
}

function createHookContext(runtime) {
  return {
    schema: runtime.schema,
    state: runtime.state,
    setQueryValue: runtime.setQueryValue,
    setMeta: runtime.setMeta,
    notify: runtime.setFeedbackMessage
  }
}

export function createPageRuntime(schema) {
  const initialQuery = buildInitialQuery(schema)
  const state = reactive({
    loading: false,
    errorMessage: '',
    feedbackMessage: '',
    feedbackType: 'info',
    initialized: false,
    query: { ...initialQuery },
    rows: [],
    total: 0,
    page: 1,
    pageSize: schema.datasource.pagination.pageSize || 10,
    selectedRowId: null,
    meta: {
      statsItems: []
    }
  })

  const runtime = {
    schema,
    state,
    get title() {
      return schema.layout.title || schema.title || schema.viewId
    },
    get description() {
      return schema.layout.description || ''
    },
    get metaText() {
      return schema.layout.meta || schema.viewId
    },
    get loading() {
      return state.loading
    },
    get errorMessage() {
      return state.errorMessage
    },
    get feedbackMessage() {
      return state.feedbackMessage
    },
    get feedbackType() {
      return state.feedbackType
    },
    get rows() {
      return state.rows
    },
    get page() {
      return state.page
    },
    get pageSize() {
      return state.pageSize
    },
    get total() {
      return state.total
    },
    get rowKey() {
      return schema.datasource.primaryKey || 'id'
    },
    get listConfig() {
      return schema.listConfig
    },
    get filterSchema() {
      return schema.filters.map((filter) => ({
        ...filter,
        value: state.query[filter.key],
        options: buildFilterOptions(filter),
        type_config: buildFilterOptions(filter)
      }))
    },
    get tableColumns() {
      return schema.fields
    },
    get actionItems() {
      return schema.actions.map((action) => ({
        ...action,
        disabled: state.loading || action.disabled === true
      }))
    },
    get summaryItems() {
      return buildSummaryItems(schema, state.query)
    },
    get statsItems() {
      return state.meta.statsItems
    },
    get layoutType() {
      return schema.layout.variant === 'card' ? 'card' : 'table'
    },
    setQueryValue(key, value) {
      state.query[key] = value
    },
    resetQuery() {
      state.query = { ...initialQuery }
      state.feedbackMessage = ''
      state.feedbackType = 'info'
    },
    setPage(page) {
      state.page = page
    },
    setPageSize(pageSize) {
      state.pageSize = pageSize
    },
    setMeta(key, value) {
      state.meta[key] = value
    },
    setFeedbackMessage(message, type = 'info') {
      state.feedbackMessage = message || ''
      state.feedbackType = type
    },
    async init() {
      if (state.initialized) {
        return
      }
      await executeHook(schema.hooks.beforeLoad, createHookContext(runtime))
      await runtime.loadData()
      await executeHook(schema.hooks.afterLoad, createHookContext(runtime))
      state.initialized = true
    },
    async loadData() {
      await dataLoader.load()
    },
    async reload() {
      await runtime.loadData()
      await executeHook(schema.hooks.afterLoad, createHookContext(runtime))
    },
    async dispatchAction(payload) {
      await handleRuntimeAction(runtime, payload)
    },
    async handlePageChange(nextPage) {
      if (!nextPage || nextPage === state.page) {
        return
      }
      state.page = nextPage
      await runtime.reload()
    },
    async handlePageSizeChange(nextPageSize) {
      if (!nextPageSize || nextPageSize === state.pageSize) {
        return
      }
      state.pageSize = nextPageSize
      state.page = 1
      await runtime.reload()
    },
    handleRowClick({ row }) {
      state.selectedRowId = row?.[runtime.rowKey] ?? null
    },
    async handleTableAction(payload) {
      state.selectedRowId = payload?.row?.[runtime.rowKey] ?? null
      await runtime.dispatchAction({
        action: payload?.actionItem?.key,
        row: payload?.row,
        actionItem: payload?.actionItem
      })
    }
  }

  const dataLoader = createRuntimeDataLoader(runtime)

  return runtime
}
