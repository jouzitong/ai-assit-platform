import { reactive, readonly } from 'vue'
import { listServiceEnums } from '../api/enums'
import { DEFAULT_ENUM_SERVICE_NAMES, SERVICE_NAMES } from '../config/services'

const enumStore = reactive({
  services: {},
  loading: {},
  errors: {},
  loadedAt: {}
})

const pendingRequests = new Map()

function normalizeEnumPayload(payload) {
  if (Array.isArray(payload)) {
    return Object.fromEntries(payload.map(item => [resolveEnumName(item), resolveEnumItems(item)]).filter(([key]) => key))
  }

  if (payload && typeof payload === 'object') {
    if (Array.isArray(payload.enums)) {
      return normalizeEnumPayload(payload.enums)
    }
    if (payload.enums && typeof payload.enums === 'object') {
      return payload.enums
    }
    return payload
  }

  return {}
}

function resolveEnumName(item) {
  return item?.enumName || item?.name || item?.code || item?.key || ''
}

function resolveEnumItems(item) {
  return item?.items || item?.values || item?.options || item?.children || []
}

function normalizeOption(item) {
  if (item === null || item === undefined) {
    return null
  }
  if (typeof item !== 'object') {
    return {
      label: String(item),
      value: item,
      raw: item
    }
  }

  const value = item.value ?? item.code ?? item.name ?? item.key
  return {
    label: item.label ?? item.desc ?? item.description ?? item.text ?? item.name ?? String(value ?? ''),
    value,
    raw: item
  }
}

function normalizeOptions(items) {
  if (Array.isArray(items)) {
    return items.map(normalizeOption).filter(Boolean)
  }

  if (items && typeof items === 'object') {
    return Object.entries(items).map(([value, label]) => ({
      label: typeof label === 'object' ? (label.label ?? label.desc ?? label.description ?? String(value)) : String(label),
      value,
      raw: label
    }))
  }

  return []
}

export async function loadServiceEnums(serviceName = SERVICE_NAMES.AI_ENGINE, options = {}) {
  if (!options.force && enumStore.services[serviceName]) {
    return enumStore.services[serviceName]
  }
  if (!options.force && pendingRequests.has(serviceName)) {
    return pendingRequests.get(serviceName)
  }

  enumStore.loading[serviceName] = true
  enumStore.errors[serviceName] = ''

  const requestPromise = listServiceEnums(serviceName)
    .then(payload => {
      const enums = normalizeEnumPayload(payload)
      enumStore.services[serviceName] = enums
      enumStore.loadedAt[serviceName] = Date.now()
      return enums
    })
    .catch(error => {
      enumStore.errors[serviceName] = error.message || '枚举加载失败'
      enumStore.services[serviceName] = enumStore.services[serviceName] || {}
      return enumStore.services[serviceName]
    })
    .finally(() => {
      enumStore.loading[serviceName] = false
      pendingRequests.delete(serviceName)
    })

  pendingRequests.set(serviceName, requestPromise)
  return requestPromise
}

export function preloadServiceEnums(serviceNames = DEFAULT_ENUM_SERVICE_NAMES) {
  return Promise.all(serviceNames.map(serviceName => loadServiceEnums(serviceName)))
}

export function getServiceEnums(serviceName = SERVICE_NAMES.AI_ENGINE) {
  return enumStore.services[serviceName] || {}
}

export function getEnum(enumName, serviceName = SERVICE_NAMES.AI_ENGINE) {
  return getServiceEnums(serviceName)[enumName] || []
}

export function getEnumOptions(enumName, serviceName = SERVICE_NAMES.AI_ENGINE) {
  return normalizeOptions(getEnum(enumName, serviceName))
}

export function getEnumLabel(enumName, value, serviceName = SERVICE_NAMES.AI_ENGINE) {
  const option = getEnumOptions(enumName, serviceName).find(item => String(item.value) === String(value))
  return option?.label ?? value
}

export function useEnumStore() {
  return {
    state: readonly(enumStore),
    loadServiceEnums,
    preloadServiceEnums,
    getServiceEnums,
    getEnum,
    getEnumOptions,
    getEnumLabel
  }
}
