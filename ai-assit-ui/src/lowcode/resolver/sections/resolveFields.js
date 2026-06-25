function resolveFieldPath(field, key) {
  if (Array.isArray(field?.field) && field.field.length) {
    const path = field.field
      .map((item) => String(item || '').trim())
      .filter(Boolean)
      .join('.')
    return path || key
  }

  if (typeof field?.field === 'string' && field.field.trim()) {
    return field.field.trim()
  }

  return key
}

function normalizeField(field) {
  const key = String(field?.key || field?.name || '')
  return {
    key,
    label: field?.label || field?.name || key,
    width: field?.options?.styles?.width || field?.width,
    className: field?.options?.className || field?.className || '',
    align: field?.options?.styles?.['text-align'] || field?.align || '',
    field: resolveFieldPath(field, key),
    visible: field?.visible
  }
}

export function resolveFields(fields = []) {
  return Array.isArray(fields) ? fields.map(normalizeField) : []
}
