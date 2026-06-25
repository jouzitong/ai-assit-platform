import { resolveBaseSchema } from './resolveBaseSchema'

export function resolveFormSchema(rawSchema = {}, pageCode = '') {
  const schema = resolveBaseSchema(rawSchema, pageCode)
  return {
    ...schema,
    type: 'form'
  }
}
