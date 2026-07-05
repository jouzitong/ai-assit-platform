export function isRequired(value: string | null | undefined) {
  return Boolean(value && value.trim())
}
