const gatewayBaseUrl = import.meta.env.VITE_GATEWAY_BASE_URL || 'http://127.0.0.1:9764'
const frontendEnvironment = (import.meta.env.VITE_FRONTEND_ENV || 'dev').trim()

export const GATEWAY_BASE_URL = gatewayBaseUrl.replace(/\/+$/, '')
export const FRONTEND_ENVIRONMENT = frontendEnvironment || 'dev'
