export const SERVICE_NAMES = {
  AI_ENGINE: 'aiEngine',
  AI_CHAT: 'aiChat',
  DB_ENGINE: 'dbEngine',
  USER: 'user'
}

export const BACKEND_SERVICES = {
  [SERVICE_NAMES.AI_ENGINE]: {
    name: SERVICE_NAMES.AI_ENGINE,
    displayName: 'AI 引擎服务',
    gatewayPrefix: '/aiEngine',
    enumEndpoint: '/aiEngine/common/v1/system/enums'
  },
  [SERVICE_NAMES.AI_CHAT]: {
    name: SERVICE_NAMES.AI_CHAT,
    displayName: 'AI Chat 服务',
    gatewayPrefix: '/aiChat',
    enumEndpoint: '/aiChat/common/v1/system/enums'
  },
  [SERVICE_NAMES.DB_ENGINE]: {
    name: SERVICE_NAMES.DB_ENGINE,
    displayName: 'DB Engine 服务',
    gatewayPrefix: '/dbEngine',
    enumEndpoint: '/dbEngine/common/v1/system/enums'
  },
  [SERVICE_NAMES.USER]: {
    name: SERVICE_NAMES.USER,
    displayName: '用户服务',
    gatewayPrefix: '/user',
    enumEndpoint: '/user/common/v1/system/enums'
  }
}

export const DEFAULT_ENUM_SERVICE_NAMES = Object.keys(BACKEND_SERVICES).filter(
  serviceName => BACKEND_SERVICES[serviceName].enumEndpoint
)

export function getBackendService(serviceName) {
  return BACKEND_SERVICES[serviceName] || null
}

export function getServiceEnumEndpoint(serviceName) {
  return getBackendService(serviceName)?.enumEndpoint || ''
}
