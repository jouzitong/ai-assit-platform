import { resolveLayout } from './sections/resolveLayout'
import { resolveFormSchema } from './types/formSchemaResolver'
import { resolveInfoSchema } from './types/infoSchemaResolver'
import { resolveListSchema } from './types/listSchemaResolver'
import { resolveReportSchema } from './types/reportSchemaResolver'

const schemaTypeResolvers = {
  list: resolveListSchema,
  form: resolveFormSchema,
  report: resolveReportSchema,
  info: resolveInfoSchema
}

export function resolveSchema(rawSchema = {}, pageCode = '') {
  const layout = resolveLayout(rawSchema, pageCode)
  const type = layout.type || 'info'
  const resolver = schemaTypeResolvers[type] || resolveInfoSchema
  return resolver(rawSchema, pageCode)
}
