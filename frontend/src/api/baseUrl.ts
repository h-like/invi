import { resolveApiBaseUrl } from './config'

export const API_BASE = resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL)
