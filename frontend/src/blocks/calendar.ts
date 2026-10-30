// Date math for the countdown block, kept pure so it runs under node --test.
// Times are the guest's local clock, matching how the date was always shown.

export interface WeddingMoment { year: number; month: number; day: number; at: Date; hasTime: boolean }

export function parseWeddingMoment(date: string, time: string): WeddingMoment | null {
  const d = /^(\d{4})-(\d{2})-(\d{2})/.exec(date ?? '')
  if (!d) return null
  const [year, month, day] = d.slice(1).map(Number)
  const t = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(time ?? '')
  const at = new Date(year, month - 1, day, t ? Number(t[1]) : 0, t ? Number(t[2]) : 0)
  // Rejects impossible dates such as 2026-02-31 that Date would roll over.
  if (at.getFullYear() !== year || at.getMonth() !== month - 1 || at.getDate() !== day) return null
  return { year, month, day, at, hasTime: Boolean(t) }
}

export function countdownParts(at: Date, now: number) {
  const diff = Math.max(0, at.getTime() - now)
  const today = new Date(now)
  today.setHours(0, 0, 0, 0)
  const target = new Date(at)
  target.setHours(0, 0, 0, 0)
  return {
    days: Math.floor(diff / 86_400_000),
    hours: Math.floor(diff / 3_600_000) % 24,
    minutes: Math.floor(diff / 60_000) % 60,
    seconds: Math.floor(diff / 1000) % 60,
    // Calendar days, so the wedding morning reads "today" rather than "D-0 hours".
    daysLeft: Math.round((target.getTime() - today.getTime()) / 86_400_000),
  }
}

/** Weeks of the month, Sunday first; blanks are null. */
export function calendarWeeks(year: number, month: number): (number | null)[][] {
  const first = new Date(year, month - 1, 1).getDay()
  const length = new Date(year, month, 0).getDate()
  const cells: (number | null)[] = [...Array(first).fill(null), ...Array.from({ length }, (_, i) => i + 1)]
  while (cells.length % 7) cells.push(null)
  return Array.from({ length: cells.length / 7 }, (_, w) => cells.slice(w * 7, w * 7 + 7))
}
