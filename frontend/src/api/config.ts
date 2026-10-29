const DEFAULT_API_BASE = 'http://localhost:8080'

type ApiBaseUrlOptions = {
  sameOrigin?: boolean
  currentOrigin?: string
}

function resolvePublicBoolean(value: string | undefined, setting: string, fallback: boolean): boolean {
  const configured = value?.trim()
  if (!configured) return fallback
  if (configured === 'true') return true
  if (configured === 'false') return false
  throw new Error(`${setting}에는 true 또는 false만 입력해 주세요.`)
}

/** VITE_ flags are public build settings, not authorization controls. */
export function resolveApiSameOrigin(value?: string): boolean {
  return resolvePublicBoolean(value, 'VITE_API_SAME_ORIGIN', false)
}

export function resolveSocialLoginEnabled(mode: string, value?: string): boolean {
  const enabled = resolvePublicBoolean(value, 'VITE_SOCIAL_LOGIN_ENABLED', true)
  return mode !== 'mobile' && enabled
}

/** UI visibility only; the server must enforce registration restrictions. */
export function resolveRegistrationEnabled(value?: string): boolean {
  return resolvePublicBoolean(value, 'VITE_REGISTRATION_ENABLED', true)
}

function resolveCurrentOrigin(value?: string): string {
  try {
    const configured = value?.trim()
    // Check the original syntax too: URL normalizes dot paths, backslashes and
    // alternate IPv4 spellings, which must not widen the HTTP QA exception.
    const match = configured?.match(/^https?:\/\/([^/?#\\\s]+)\/?$/i)
    if (!configured || !match || match[1].includes('@')) throw new Error('Invalid origin')
    const url = new URL(configured)
    const loopback = /^(?:localhost|127\.0\.0\.1|\[::1\])(?::\d+)?$/i.test(match[1])
    if (url.username || url.password || url.pathname !== '/' || url.search || url.hash
      || (url.protocol !== 'https:' && !(url.protocol === 'http:' && loopback))) {
      throw new Error('Invalid origin')
    }
    return url.origin
  } catch {
    // Never echo a supplied URL: a misconfigured value could contain credentials.
    throw new Error('동일 출처 API 모드에는 유효한 HTTPS 출처가 필요합니다. HTTP는 로컬 테스트 주소만 허용됩니다.')
  }
}

/** VITE_ values are public. This setting is an API origin, never a credential. */
export function resolveApiBaseUrl(value?: string, options: ApiBaseUrlOptions = {}): string {
  const configured = value?.trim()
  if (options.sameOrigin) {
    if (configured) throw new Error('동일 출처 API 모드에서는 VITE_API_BASE_URL을 비워 주세요.')
    return resolveCurrentOrigin(options.currentOrigin)
  }
  if (!configured) return DEFAULT_API_BASE
  try {
    // Validate before WHATWG normalization can erase userinfo, paths or controls.
    // Keep ordinary HTTP origins (including LAN hosts) valid outside same-origin mode.
    if (!/^https?:\/\/[^/?#\\@\s\p{Cc}]+\/?$/iu.test(configured)) {
      throw new Error('Invalid API origin')
    }
    const url = new URL(configured)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
      throw new Error('Invalid API origin')
    }
    return url.origin
  } catch {
    throw new Error('VITE_API_BASE_URL에는 경로나 인증정보 없이 http 또는 https API 주소를 입력해 주세요.')
  }
}
