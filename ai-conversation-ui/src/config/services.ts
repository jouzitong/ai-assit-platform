export const SERVICE_NAMES = {
  USER: 'user',
  CHAT: 'chat',
} as const

export const BACKEND_SERVICES = {
  [SERVICE_NAMES.USER]: {
    name: SERVICE_NAMES.USER,
    displayName: '用户服务',
    gatewayPrefix: '/user',
  },
  [SERVICE_NAMES.CHAT]: {
    name: SERVICE_NAMES.CHAT,
    displayName: '聊天服务',
    gatewayPrefix: '/chat',
  },
} as const

export function getBackendService(serviceName: keyof typeof BACKEND_SERVICES) {
  return BACKEND_SERVICES[serviceName]
}
