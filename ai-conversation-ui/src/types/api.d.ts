import type { ApiResponse, PageResponse } from '../api/types'

declare global {
  type BaseApiResponse<T = unknown> = ApiResponse<T>
  type BasePageResponse<T = unknown> = PageResponse<T>
}

export {}
