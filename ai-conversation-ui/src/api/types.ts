export interface ApiRequestConfig extends RequestInit {
  baseURL?: string
  query?: Record<string, string | number | boolean | undefined>
}

export interface ApiResponse<T = unknown> {
  code: number
  msg: string
  data: T
  message?: string
}

export interface PageResponse<T = unknown> {
  list: T[]
  total: number
  page: number
  size: number
}
