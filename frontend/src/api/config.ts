const DEFAULT_API_BASE = 'http://localhost:8080'

/** VITE_ values are public. This setting is an API origin, never a credential. */
export function resolveApiBaseUrl(value?: string): string {
  const configured = value?.trim()
  if (!configured) return DEFAULT_API_BASE
  try {
    const url = new URL(configured)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
      throw new Error('Invalid API origin')
    }
    return url.origin
  } catch {
    throw new Error('VITE_API_BASE_URL에는 경로나 인증정보 없이 http 또는 https API 주소를 입력해 주세요.')
  }
}
