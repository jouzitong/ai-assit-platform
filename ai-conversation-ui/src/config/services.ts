export const SERVICE_NAMES = {
  USER: 'user',
} as const

export const BACKEND_SERVICES = {
  [SERVICE_NAMES.USER]: {
    name: SERVICE_NAMES.USER,
    displayName: '用户服务',
    gatewayPrefix: '/user',
  },
} as const

export function getBackendService(serviceName: keyof typeof BACKEND_SERVICES) {
  return BACKEND_SERVICES[serviceName]
}
