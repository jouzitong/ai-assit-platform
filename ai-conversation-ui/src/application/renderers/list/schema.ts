import type {
  ListRendererSchema,
  RendererField,
  RendererFilter,
  RendererQueryState,
} from './types'

const DEFAULT_PAGE_SIZE = 10

export function normalizeSchema(schema: ListRendererSchema): ListRendererSchema {
  return {
    ...schema,
    title: schema.title || '未命名列表',
    component: schema.component || 'zg-common-list',
    filters: schema.filters || [],
    fields: schema.fields || [],
    actions: schema.actions || [],
    tab: {
      activeTab: schema.tab?.activeTab || schema.tab?.tabs?.[0]?.key || 'all',
      tabs: schema.tab?.tabs || [],
    },
    list_config: {
      itemType: normalizeItemType(schema.list_config?.itemType),
      cardItem: schema.list_config?.cardItem || {},
      item_operate: schema.list_config?.item_operate || {},
      actionColumns: schema.list_config?.actionColumns || [],
      pagination: {
        enabled: schema.list_config?.pagination?.enabled ?? true,
        pageSize: schema.list_config?.pagination?.pageSize || DEFAULT_PAGE_SIZE,
        pageSizeOptions: schema.list_config?.pagination?.pageSizeOptions || [10, 20, 30, 50],
      },
      className: schema.list_config?.className || '',
      events: schema.list_config?.events || [],
    },
  }
}

export function createDefaultQueryState(schema: ListRendererSchema): RendererQueryState {
  const normalized = normalizeSchema(schema)
  const filters = normalized.filters?.reduce<Record<string, unknown>>((acc, filter) => {
    acc[filter.key] = getDefaultFilterValue(filter)
    return acc
  }, {}) || {}

  return {
    activeTab: normalized.tab?.activeTab || 'all',
    filters,
    page: 1,
    pageSize: normalized.list_config?.pagination?.pageSize || DEFAULT_PAGE_SIZE,
    selectedTreeKey: null,
  }
}

export function getDefaultFilterValue(filter: RendererFilter) {
  if (filter.options?.multiple) {
    return []
  }

  if (filter.component === 'zg-selector-tree' && filter.options?.multiple) {
    return []
  }

  if (filter.type === 'daterange') {
    return []
  }

  return ''
}

export function shouldShowTree(schema: ListRendererSchema) {
  if (schema.tree === null) {
    return false
  }

  const normalized = normalizeSchema(schema)
  return normalized.component === 'zg-common-tree-list'
    || normalized.component === 'common-tree-list'
    || Boolean(normalized.tree?.component)
}

export function getFieldValue(row: Record<string, unknown>, field: RendererField) {
  const segments = field.field?.length ? field.field : [field.key]
  let current: unknown = row

  for (const segment of segments) {
    if (current == null || typeof current !== 'object') {
      return ''
    }
    current = (current as Record<string, unknown>)[segment]
  }

  if (Array.isArray(current)) {
    return current.join(' / ')
  }

  return current ?? ''
}

export function getColumnMinWidth(field: RendererField) {
  const width = field.options?.styles?.width
  if (typeof width === 'number') {
    return width <= 100 ? `${width}%` : `${width}px`
  }
  return typeof width === 'string' ? width : '160'
}

function normalizeItemType(itemType?: string) {
  if (itemType === 'card' || itemType === 'item' || itemType === 'table') {
    return itemType
  }
  return 'table'
}
