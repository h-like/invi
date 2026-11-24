import { resolveContactEmail } from './api/config'

/** Operator contact for privacy questions and deletion requests (set at build time, not in git). */
export const CONTACT_EMAIL = resolveContactEmail(import.meta.env.VITE_CONTACT_EMAIL)

/** Effective date shown on the privacy policy and terms. Update it whenever either text changes. */
export const POLICY_EFFECTIVE_DATE = '2026년 12월 1일'
