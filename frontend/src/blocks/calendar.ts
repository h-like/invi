// Date math for the countdown block, kept pure so it runs under node --test.
// Weddings happen in Korea, so the moment and day counts are in KST (UTC+9, no DST)
// whatever the guest's device timezone is; formatters must pass WEDDING_TIME_ZONE.

export const WEDDING_TIME_ZONE = 'Asia/Seoul'
const KST_OFFSET_MS = 9 * 3_600_000
const DAY_MS = 86_400_000

export interface WeddingMoment { year: number; month: number; day: number; at: Date; hasTime: boolean }

export function parseWeddingMoment(date: string, time: string): WeddingMoment | null {
  const d = /^(\d{4})-(\d{2})-(\d{2})/.exec(date ?? '')
  if (!d) return null
  const [year, month, day] = d.slice(1).map(Number)
  // Rejects impossible dates such as 2026-02-31 that Date would roll over.
  const check = new Date(Date.UTC(year, month - 1, day))
  if (check.getUTCFullYear() !== year || check.getUTCMonth() !== month - 1 || check.getUTCDate() !== day) return null
  const t = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(time ?? '')
  const at = new Date(Date.UTC(year, month - 1, day, t ? Number(t[1]) : 0, t ? Number(t[2]) : 0) - KST_OFFSET_MS)
  return { year, month, day, at, hasTime: Boolean(t) }
}

const kstDay = (ms: number) => Math.floor((ms + KST_OFFSET_MS) / DAY_MS)

export function countdownParts(at: Date, now: number) {
  const diff = Math.max(0, at.getTime() - now)
  return {
    days: Math.floor(diff / DAY_MS),
    hours: Math.floor(diff / 3_600_000) % 24,
    minutes: Math.floor(diff / 60_000) % 60,
    seconds: Math.floor(diff / 1000) % 60,
    // Calendar days in Korea, so the wedding morning reads "today" rather than "D-0 hours".
    daysLeft: kstDay(at.getTime()) - kstDay(now),
  }
}

/** Weeks of the month, Sunday first; blanks are null. */
export function calendarWeeks(year: number, month: number): (number | null)[][] {
  const first = new Date(Date.UTC(year, month - 1, 1)).getUTCDay()
  const length = new Date(Date.UTC(year, month, 0)).getUTCDate()
  const cells: (number | null)[] = [...Array(first).fill(null), ...Array.from({ length }, (_, i) => i + 1)]
  while (cells.length % 7) cells.push(null)
  return Array.from({ length: cells.length / 7 }, (_, w) => cells.slice(w * 7, w * 7 + 7))
}
