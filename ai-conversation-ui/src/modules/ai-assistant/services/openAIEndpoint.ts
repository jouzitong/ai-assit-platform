export function normalizeOpenAIBaseUrl(baseUrl?: string) {
  const rawBaseUrl = baseUrl?.trim()
  if (!rawBaseUrl) return undefined

  try {
    const url = new URL(rawBaseUrl)
    const endpointPath = url.pathname
      .replace(/\/+$/, '')
      .replace(/\/(chat\/completions|responses)$/i, '')
    url.pathname = /(^|\/)v1$/i.test(endpointPath) ? endpointPath : `${endpointPath}/v1`
    url.search = ''
    url.hash = ''
    return url.toString().replace(/\/$/, '')
  }
  catch {
    const endpoint = rawBaseUrl
      .replace(/[?#].*$/, '')
      .replace(/\/+$/, '')
      .replace(/\/(chat\/completions|responses)$/i, '')
    return /(^|\/)v1$/i.test(endpoint) ? endpoint : `${endpoint}/v1`
  }
}
