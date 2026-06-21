import { request } from '../utils/request'
import { getServiceEnumEndpoint } from '../config/services'

export function listServiceEnums(serviceName = 'aiEngine') {
  const endpoint = getServiceEnumEndpoint(serviceName)
  if (!endpoint) {
    return Promise.reject(new Error(`未配置服务枚举接口：${serviceName}`))
  }

  return request(endpoint, {
    method: 'GET'
  })
}
