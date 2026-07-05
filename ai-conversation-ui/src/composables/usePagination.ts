import { reactive } from 'vue'

export function usePagination() {
  const pagination = reactive({
    page: 1,
    size: 10,
    total: 0,
  })

  const setPage = (page: number) => {
    pagination.page = page
  }

  const setTotal = (total: number) => {
    pagination.total = total
  }

  return {
    pagination,
    setPage,
    setTotal,
  }
}
