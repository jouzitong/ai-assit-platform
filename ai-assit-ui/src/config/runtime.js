const gatewayBaseUrl = import.meta.env.VITE_GATEWAY_BASE_URL || 'http://127.0.0.1:9764'

export const GATEWAY_BASE_URL = gatewayBaseUrl.replace(/\/+$/, '')
