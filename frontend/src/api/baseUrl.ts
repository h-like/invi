import { resolveApiBaseUrl, resolveApiSameOrigin } from './config'

const sameOrigin = resolveApiSameOrigin(import.meta.env.VITE_API_SAME_ORIGIN)

export const API_BASE = resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL, {
  sameOrigin,
  currentOrigin: sameOrigin ? window.location.origin : undefined,
})
