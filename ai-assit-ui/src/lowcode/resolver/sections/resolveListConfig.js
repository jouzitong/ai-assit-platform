export function resolveListConfig(rawSchema = {}) {
  return rawSchema.list_config || rawSchema.listConfig || {}
}
