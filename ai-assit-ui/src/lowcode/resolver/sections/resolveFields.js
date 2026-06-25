function normalizeField(field) {
  const key = String(field?.key || field?.name || '')
  return {
    key,
    label: field?.label || field?.name || key,
    width: field?.options?.styles?.width || field?.width,
    className: field?.options?.className || field?.className || '',
    align: field?.options?.styles?.['text-align'] || field?.align || '',
    field: Array.isArray(field?.field) ? field.field : [key],
    visible: field?.visible
  }
}

export function resolveFields(fields = []) {
  return Array.isArray(fields) ? fields.map(normalizeField) : []
}
