// Mirrors the backend CreateRsvpRequest / CreateGuestbookRequest limits. The server
// rejects anything longer with 400; these keep guests from typing past them first.
export const GUEST_NAME_MAX = 30
export const GUESTBOOK_MESSAGE_MAX = 500
export const RSVP_MESSAGE_MAX = 200
export const GUEST_COUNT_MAX = 20

export function clampGuestCount(value: number): number {
  return Number.isFinite(value) ? Math.min(GUEST_COUNT_MAX, Math.max(0, Math.trunc(value))) : 0
}
