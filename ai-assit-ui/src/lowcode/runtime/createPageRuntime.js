import { reactive } from 'vue'
import { handleRuntimeAction } from './executors/action/handleRuntimeAction'
import { createRuntimeDataLoader } from './executors/data-loader/createRuntimeDataLoader'
import { executeHook } from './executors/hook/executeHook'

function buildInitialQuery(schema) {
  return schema.filters.reduce((result, filter) => {
    result[filter.key] = filter.value ?? ''
    return result
  }, {})
}

function buildSummaryItems(schema, query) {
  const items = schema.filters
    .filter((filter) => query[filter.key] !== '' && query[filter.key] !== null && query[filter.key] !== undefined)
    .map((filter) => {
      if (filter.type === 'select') {
        const options = Array.isArray(filter.type_config?.options) ? filter.type_config.options : []
        const target = options.find((item) => String(item?.code ?? item?.value) === String(query[filter.key]))
        return {
          key: filter.label,
          value: target?.name || target?.label || query[filter.key]
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
        value: state.query[filter.key]
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
