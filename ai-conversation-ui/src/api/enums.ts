import { request } from './request'
import { getServiceEnumEndpoint, SERVICE_NAMES } from '../config/services'

export function listServiceEnums(serviceName: keyof typeof SERVICE_NAMES = SERVICE_NAMES.AI_ENGINE) {
  const endpoint = getServiceEnumEndpoint(serviceName)
  if (!endpoint) {
    return Promise.reject(new Error(`未配置服务枚举接口：${serviceName}`))
  }

  return request(endpoint, {
    method: 'GET',
  })
}
