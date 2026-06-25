import { resolveBaseSchema } from './resolveBaseSchema'

export function resolveListSchema(rawSchema = {}, pageCode = '') {
  const schema = resolveBaseSchema(rawSchema, pageCode)
  return {
    ...schema,
    type: 'list'
  }
}
