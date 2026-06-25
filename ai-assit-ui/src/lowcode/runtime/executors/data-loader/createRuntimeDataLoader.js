import {executeDatasource} from '../data-source/executeDatasource'

export function createRuntimeDataLoader(runtime) {
    async function load() {
        const {schema, state, rowKey} = runtime
        state.loading = true
        state.errorMessage = ''

        try {
            const result = await executeDatasource(schema, state)
            const nextRows = Array.isArray(result?.list) ? result.list : []
            state.rows = nextRows
            state.total = Number(result?.total || nextRows.length || 0)
            if (!nextRows.some((row) => row?.[rowKey] === state.selectedRowId)) {
                state.selectedRowId = nextRows[0]?.[rowKey] ?? null
            }
        } catch (error) {
            state.rows = []
            state.total = 0
            state.selectedRowId = null
            state.errorMessage = error instanceof Error ? error.message : '页面数据加载失败'
        } finally {
            state.loading = false
        }
    }

    return {
        load
    }
}
