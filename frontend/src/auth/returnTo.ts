const RETURN_KEY = 'invi.auth.returnTo'

/** Only same-origin app paths may be used after login. */
export function safeReturnTo(value: string | null | undefined): string {
  // Reject controls before URL parsing can silently normalize them.
  // eslint-disable-next-line no-control-regex
  if (!value || !value.startsWith('/') || value.startsWith('//') || /[\\\u0000-\u0020]/.test(value)) return '/invitations'
  try {
    const target = new URL(value, 'https://invi.invalid')
    const pathname = target.pathname
    if (target.origin !== 'https://invi.invalid' || !/^\/(?:$|(?:create|invitations)\/?$|(?:templates|e|i)(?:\/|$))/.test(pathname)) return '/invitations'
    return target.pathname + target.search + target.hash
  } catch { return '/invitations' }
}

export function loginPath(returnTo: string): string {
  return '/login?returnTo=' + encodeURIComponent(safeReturnTo(returnTo))
}

export function rememberReturnTo(returnTo: string): void {
  try { sessionStorage.setItem(RETURN_KEY, safeReturnTo(returnTo)) } catch { /* Storage may be disabled. Local login still uses the URL. */ }
}

export function readReturnTo(): string {
  try { return safeReturnTo(sessionStorage.getItem(RETURN_KEY)) } catch { return '/invitations' }
}

export function takeReturnTo(): string {
  try {
    const value = sessionStorage.getItem(RETURN_KEY)
    sessionStorage.removeItem(RETURN_KEY)
    return safeReturnTo(value)
  } catch { return '/invitations' }
}
