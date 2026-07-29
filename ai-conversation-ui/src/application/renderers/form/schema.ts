import { normalizeRendererActions } from '../../schema/action'
import type {
  FormRendererField,
  FormRendererSchema,
  NormalizedFormRendererGroup,
  NormalizedFormRendererSchema,
} from './types'

const DEFAULT_COLUMNS = 2

export function normalizeSchema(schema: FormRendererSchema): NormalizedFormRendererSchema {
  const baseFields = schema.fields || []
  const syntheticFields = collectSyntheticFields(schema.groups || [], baseFields)
  const fields = [...baseFields, ...syntheticFields]

  return {
    ...schema,
    title: schema.title || '未命名表单',
    component: schema.component || 'zg-common-form',
    fields,
    groups: normalizeGroups(schema.groups || [], fields),
    actions: normalizeRendererActions(schema.actions),
    children: schema.children || [],
    form_relations: schema.form_relations || [],
    life_cycle: schema.life_cycle || {},
    form_config: {
      variant: schema.form_config?.variant || 'workbench',
      columns: schema.form_config?.columns || DEFAULT_COLUMNS,
      labelWidth: schema.form_config?.labelWidth || '96px',
      actionsAlign: schema.form_config?.actionsAlign || 'right',
      description: schema.form_config?.description || '',
      className: schema.form_config?.className || '',
      events: schema.form_config?.events || [],
    },
    data: schema.data || {},
  }
}

export function createFieldMap(schema: FormRendererSchema) {
  const normalized = normalizeSchema(schema)
  return normalized.fields.reduce<Record<string, FormRendererField>>((acc, field) => {
    acc[field.key] = field
    return acc
  }, {})
}

export function getFieldValue(record: Record<string, unknown>, field: FormRendererField) {
  const segments = field.field?.length ? field.field : [field.key]
  let current: unknown = record

  for (const segment of segments) {
    if (current == null || typeof current !== 'object') {
      return ''
    }
    current = (current as Record<string, unknown>)[segment]
  }

  return current ?? ''
}

export function formatFieldValue(value: unknown) {
  if (value == null || value === '') {
    return '—'
  }

  if (Array.isArray(value)) {
    return value.join(' / ')
  }

  if (typeof value === 'object') {
    return JSON.stringify(value)
  }

  return String(value)
}

function normalizeGroups(groups: NormalizedFormRendererGroup[] | FormRendererSchema['groups'], fields: FormRendererField[]) {
  if (!groups?.length) {
    return [
      {
        key: 'default',
        title: '基础信息',
        fields: fields.map((field) => field.key),
        columns: DEFAULT_COLUMNS,
      },
    ] as NormalizedFormRendererGroup[]
  }

  return groups.map((group) => ({
    ...group,
    title: group.title || group.key,
    columns: group.columns || DEFAULT_COLUMNS,
    fields: group.fields || [],
  }))
}

function collectSyntheticFields(groups: NonNullable<FormRendererSchema['groups']>, fields: FormRendererField[]) {
  const knownKeys = new Set(fields.map((field) => field.key))
  const syntheticKeys = new Set<string>()

  for (const group of groups) {
    for (const fieldKey of group.fields || []) {
      if (!knownKeys.has(fieldKey)) {
        syntheticKeys.add(fieldKey)
      }
    }
  }

  return [...syntheticKeys].map<FormRendererField>((key) => ({
    key,
    name: key,
    label: humanizeLabel(key),
    field: [key],
    type: 'display',
    options: {},
  }))
}

function humanizeLabel(key: string) {
  const normalized = key
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .trim()

  if (!normalized) {
    return key
  }

  return normalized.charAt(0).toUpperCase() + normalized.slice(1)
}
