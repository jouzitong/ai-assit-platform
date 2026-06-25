import { resolveBaseSchema } from './resolveBaseSchema'

export function resolveReportSchema(rawSchema = {}, pageCode = '') {
  const schema = resolveBaseSchema(rawSchema, pageCode)
  return {
    ...schema,
    type: 'report'
  }
}
