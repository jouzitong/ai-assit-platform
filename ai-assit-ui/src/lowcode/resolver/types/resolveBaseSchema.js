import { resolveActions } from '../sections/resolveActions'
import { resolveDatasource } from '../sections/resolveDatasource'
import { resolveFields } from '../sections/resolveFields'
import { resolveFilters } from '../sections/resolveFilters'
import { resolveHooks } from '../sections/resolveHooks'
import { resolveLayout } from '../sections/resolveLayout'
import { resolveListConfig } from '../sections/resolveListConfig'

export function resolveBaseSchema(rawSchema = {}, pageCode = '') {
  const listConfig = resolveListConfig(rawSchema)
  const layout = resolveLayout(rawSchema, pageCode)

  return {
    viewId: rawSchema.viewId || rawSchema.id || pageCode,
    version: rawSchema.version || '1.0.0',
    title: rawSchema.title || rawSchema.name || pageCode,
    layout,
    component: rawSchema.component || '',
    datasource: resolveDatasource(rawSchema.datasource, listConfig),
    filters: resolveFilters(rawSchema.filters),
    fields: resolveFields(rawSchema.fields),
    actions: resolveActions(rawSchema.actions),
    listConfig,
    hooks: resolveHooks(rawSchema.hooks),
    rawSchema,
    mockData: rawSchema.mockData || rawSchema.mock_data || {}
  }
}
