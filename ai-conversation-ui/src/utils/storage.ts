export function setStorage(key: string, value: unknown) {
  window.localStorage.setItem(key, JSON.stringify(value))
}

export function getStorage<T>(key: string): T | null {
  const rawValue = window.localStorage.getItem(key)
  return rawValue ? (JSON.parse(rawValue) as T) : null
}
