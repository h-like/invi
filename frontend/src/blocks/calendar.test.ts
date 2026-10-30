import assert from 'node:assert/strict'
import test from 'node:test'
import { calendarWeeks, countdownParts, parseWeddingMoment } from './calendar.ts'

test('wedding moment uses the hero time when valid and rejects impossible dates', () => {
  const withTime = parseWeddingMoment('2026-12-12', '13:00')!
  assert.equal(withTime.at.getHours(), 13)
  assert.equal(withTime.hasTime, true)
  assert.equal(parseWeddingMoment('2026-12-12', '25:00')!.hasTime, false)
  assert.equal(parseWeddingMoment('2026-02-31', ''), null)
  assert.equal(parseWeddingMoment('', ''), null)
})

test('countdown splits the remaining time and counts calendar days', () => {
  const at = new Date(2026, 11, 12, 13, 0)
  const parts = countdownParts(at, new Date(2026, 11, 10, 12, 59, 30).getTime())
  assert.deepEqual(parts, { days: 2, hours: 0, minutes: 0, seconds: 30, daysLeft: 2 })
  assert.equal(countdownParts(at, new Date(2026, 11, 12, 9).getTime()).daysLeft, 0)
  const after = countdownParts(at, new Date(2026, 11, 13).getTime())
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
