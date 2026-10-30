import assert from 'node:assert/strict'
import test from 'node:test'
import { calendarWeeks, countdownParts, parseWeddingMoment } from './calendar.ts'

// Instants are written as KST wall-clock times so the tests pass in any machine timezone.
const kst = (y: number, m: number, d: number, h = 0, min = 0, s = 0) => Date.UTC(y, m - 1, d, h - 9, min, s)

test('wedding moment is the hero time in KST and rejects impossible dates', () => {
  const withTime = parseWeddingMoment('2026-12-12', '13:00')!
  assert.equal(withTime.at.getTime(), kst(2026, 12, 12, 13))
  assert.equal(withTime.at.toISOString(), '2026-12-12T04:00:00.000Z')
  assert.equal(withTime.hasTime, true)
  assert.equal(parseWeddingMoment('2026-12-12', '25:00')!.hasTime, false)
  assert.equal(parseWeddingMoment('2026-02-31', ''), null)
  assert.equal(parseWeddingMoment('', ''), null)
})

test('countdown splits the remaining time and counts Korean calendar days', () => {
  const at = parseWeddingMoment('2026-12-12', '13:00')!.at
  assert.deepEqual(countdownParts(at, kst(2026, 12, 10, 12, 59, 30)), { days: 2, hours: 0, minutes: 0, seconds: 30, daysLeft: 2 })
  assert.equal(countdownParts(at, kst(2026, 12, 12, 9)).daysLeft, 0)
  // 23:30 on the 11th in Korea is still "D-1", even though it is already the 12th in UTC+10.
  assert.equal(countdownParts(at, kst(2026, 12, 11, 23, 30)).daysLeft, 1)
  const after = countdownParts(at, kst(2026, 12, 13))
  assert.equal(after.daysLeft, -1)
  assert.equal(after.seconds, 0)
})

test('calendar starts on the right weekday and pads full weeks', () => {
  const weeks = calendarWeeks(2026, 12) // 2026-12-01 is a Tuesday
  assert.deepEqual(weeks[0], [null, null, 1, 2, 3, 4, 5])
  assert.ok(weeks.every(w => w.length === 7))
  assert.equal(weeks.flat().filter(Boolean).length, 31)
  assert.equal(calendarWeeks(2027, 2).flat().filter(Boolean).length, 28)
})
