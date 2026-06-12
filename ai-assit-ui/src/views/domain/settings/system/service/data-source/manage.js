import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dataSources, fieldCatalog, pageSizeOptions, tableCatalog } from '../../data/data-source/manage'

export function useDataSourceManagePage() {
  const route = useRoute()
  const router = useRouter()

  const sourceList = dataSources
  const sourceKey = computed(() => String(route.params.sourceKey ?? ''))
  const currentSource = computed(() => {
    return sourceList.find(item => item.key === sourceKey.value) ?? sourceList[0]
  })

  const currentTables = computed(() => tableCatalog[currentSource.value.key] ?? [])
  const fieldWorkbenchVisible = ref(false)
  const selectedTableName = ref(currentTables.value[0]?.name ?? '')
  const pagination = reactive({
    page: 1,
    size: 4
  })

  const totalPages = computed(() => Math.max(1, Math.ceil(currentTables.value.length / pagination.size)))
  const pagedTables = computed(() => {
    const start = (pagination.page - 1) * pagination.size
    return currentTables.value.slice(start, start + pagination.size)
  })
  const pageSummary = computed(() => `共 ${currentTables.value.length} 条，${pagination.page} / ${totalPages.value} 页`)
  const selectedTable = computed(() => {
    return currentTables.value.find(item => item.name === selectedTableName.value) ?? currentTables.value[0] ?? null
  })
  const selectedFields = computed(() => fieldCatalog[selectedTable.value?.name ?? ''] ?? [])

  watch(currentSource, () => {
    pagination.page = 1
    fieldWorkbenchVisible.value = false
    selectedTableName.value = currentTables.value[0]?.name ?? ''
  })

  function statusClass(status) {
    return `is-${status}`
  }

  function handlePageChange(page) {
    pagination.page = Math.min(Math.max(page, 1), totalPages.value)
  }

  function handlePageSizeChange(event) {
    pagination.size = Number(event.target.value)
    pagination.page = 1
  }

  function handleSourceChange(event) {
    router.push(`/settings/system/data-source/${event.target.value}`)
  }

  function openFieldWorkbench(item) {
    selectedTableName.value = item.name
    fieldWorkbenchVisible.value = true
  }

  function selectTable(item) {
    selectedTableName.value = item.name
  }

  function formatEmpty(value) {
    return value?.trim ? (value.trim() || '无') : (value || '无')
  }

  function goBack() {
    router.push('/settings/system/data-source')
  }

  return {
    currentSource,
    currentSourceList: sourceList,
    currentTables,
    fieldWorkbenchVisible,
    pageSizeOptions,
    pagination,
    pageSummary,
    totalPages,
    selectedTableName,
    selectedTable,
    selectedFields,
    handleSourceChange,
    handlePageChange,
    handlePageSizeChange,
    openFieldWorkbench,
    selectTable,
    formatEmpty,
    goBack,
    statusClass,
    pagedTables
  }
}
