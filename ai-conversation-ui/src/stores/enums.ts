import { reactive, readonly } from 'vue'
import { listServiceEnums } from '../api/enums'
import { DEFAULT_ENUM_SERVICE_NAMES, SERVICE_NAMES } from '../config/services'

type EnumOption = {
  label: string
  value: unknown
  raw: unknown
}

type ServiceEnumState = {
  services: Record<string, Record<string, unknown>>
  loading: Record<string, boolean>
  errors: Record<string, string>
  loadedAt: Record<string, number>
}

const enumStore = reactive<ServiceEnumState>({
  services: {},
  loading: {},
  errors: {},
  loadedAt: {},
})

const pendingRequests = new Map<string, Promise<Record<string, unknown>>>()

function normalizeEnumPayload(payload: unknown): Record<string, unknown> {
  if (Array.isArray(payload)) {
    return Object.fromEntries(
      payload
        .map(item => [resolveEnumName(item), resolveEnumItems(item)] as const)
        .filter(([key]) => key),
    )
  }

  if (payload && typeof payload === 'object') {
    const normalizedPayload = payload as { enums?: unknown }
    if (Array.isArray(normalizedPayload.enums)) {
      return normalizeEnumPayload(normalizedPayload.enums)
    }
    if (normalizedPayload.enums && typeof normalizedPayload.enums === 'object') {
      return normalizedPayload.enums as Record<string, unknown>
    }
    return payload as Record<string, unknown>
  }

  return {}
}

function resolveEnumName(item: unknown) {
  if (!item || typeof item !== 'object') {
    return ''
  }
  const normalizedItem = item as Record<string, unknown>
  return String(
    normalizedItem.enumName
    || normalizedItem.name
    || normalizedItem.code
    || normalizedItem.key
    || '',
  )
}

function resolveEnumItems(item: unknown) {
  if (!item || typeof item !== 'object') {
    return []
  }
  const normalizedItem = item as Record<string, unknown>
  return normalizedItem.items || normalizedItem.values || normalizedItem.options || normalizedItem.children || []
}

function normalizeOption(item: unknown): EnumOption | null {
  if (item === null || item === undefined) {
    return null
  }

  if (typeof item !== 'object') {
    return {
      label: String(item),
      value: item,
      raw: item,
    }
  }

  const normalizedItem = item as Record<string, unknown>
  const value = normalizedItem.value ?? normalizedItem.code ?? normalizedItem.name ?? normalizedItem.key
  return {
    label: String(
      normalizedItem.label
      ?? normalizedItem.desc
      ?? normalizedItem.description
      ?? normalizedItem.text
      ?? normalizedItem.name
      ?? value
      ?? '',
    ),
    value,
    raw: item,
  }
}

function normalizeOptions(items: unknown): EnumOption[] {
  if (Array.isArray(items)) {
    return items.map(normalizeOption).filter((item): item is EnumOption => Boolean(item))
  }

  if (items && typeof items === 'object') {
    return Object.entries(items).map(([value, label]) => ({
      label: typeof label === 'object' && label !== null
        ? String((label as Record<string, unknown>).label ?? (label as Record<string, unknown>).desc ?? (label as Record<string, unknown>).description ?? value)
        : String(label),
      value,
      raw: label,
    }))
  }

  return []
}

export async function loadServiceEnums(
  serviceName: keyof typeof SERVICE_NAMES = SERVICE_NAMES.AI_ENGINE,
  options: { force?: boolean } = {},
) {
  if (!options.force && enumStore.services[serviceName]) {
    return enumStore.services[serviceName]
  }

  if (!options.force && pendingRequests.has(serviceName)) {
    return pendingRequests.get(serviceName)!
  }

  enumStore.loading[serviceName] = true
  enumStore.errors[serviceName] = ''

  const requestPromise = listServiceEnums(serviceName)
    .then((payload) => {
      const enums = normalizeEnumPayload(payload)
      enumStore.services[serviceName] = enums
      enumStore.loadedAt[serviceName] = Date.now()
      return enums
    })
    .catch((error) => {
      enumStore.errors[serviceName] = error instanceof Error ? error.message : '枚举加载失败'
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

export function getServiceEnums(serviceName: keyof typeof SERVICE_NAMES = SERVICE_NAMES.AI_ENGINE) {
  return enumStore.services[serviceName] || {}
}

export function getEnum(enumName: string, serviceName: keyof typeof SERVICE_NAMES = SERVICE_NAMES.AI_ENGINE) {
  return getServiceEnums(serviceName)[enumName] || []
}

export function getEnumOptions(enumName: string, serviceName: keyof typeof SERVICE_NAMES = SERVICE_NAMES.AI_ENGINE) {
  return normalizeOptions(getEnum(enumName, serviceName))
}

export function getEnumLabel(
  enumName: string,
  value: unknown,
  serviceName: keyof typeof SERVICE_NAMES = SERVICE_NAMES.AI_ENGINE,
) {
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
    getEnumLabel,
  }
}
