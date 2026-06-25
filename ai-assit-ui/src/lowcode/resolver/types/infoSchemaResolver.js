import { resolveBaseSchema } from './resolveBaseSchema'

export function resolveInfoSchema(rawSchema = {}, pageCode = '') {
  const schema = resolveBaseSchema(rawSchema, pageCode)
  return {
    ...schema,
    type: 'info'
  }
}
