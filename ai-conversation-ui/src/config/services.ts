export const SERVICE_NAMES = {
  USER: 'user',
  CHAT: 'chat',
  DB_ENGINE: 'dbEngine',
  RENDER: 'render',
} as const

export const BACKEND_SERVICES = {
  [SERVICE_NAMES.USER]: {
    name: SERVICE_NAMES.USER,
    displayName: '用户服务',
    gatewayPrefix: '/user',
    enumEndpoint: '/user/common/v1/system/enums',
  },
  [SERVICE_NAMES.CHAT]: {
    name: SERVICE_NAMES.CHAT,
    displayName: '聊天服务',
    gatewayPrefix: '/chat',
    enumEndpoint: '/chat/common/v1/system/enums',
  },
  [SERVICE_NAMES.DB_ENGINE]: {
    name: SERVICE_NAMES.DB_ENGINE,
    displayName: '数据库引擎服务',
    gatewayPrefix: '/dbEngine',
    enumEndpoint: '/dbEngine/common/v1/system/enums',
  },
  [SERVICE_NAMES.RENDER]: {
    name: SERVICE_NAMES.RENDER,
    displayName: '渲染服务',
    gatewayPrefix: '/render',
  },
} as const

export function getBackendService(serviceName: keyof typeof BACKEND_SERVICES) {
  return BACKEND_SERVICES[serviceName]
}

export const DEFAULT_ENUM_SERVICE_NAMES = Object.keys(BACKEND_SERVICES).filter((serviceName) => {
  const service = BACKEND_SERVICES[serviceName as keyof typeof BACKEND_SERVICES]
  return Boolean(service?.enumEndpoint)
}) as Array<keyof typeof BACKEND_SERVICES>

export function getServiceEnumEndpoint(serviceName: keyof typeof BACKEND_SERVICES) {
  return getBackendService(serviceName)?.enumEndpoint || ''
}
