export function formatDate(input: string | number | Date) {
  return new Date(input).toLocaleString()
}
