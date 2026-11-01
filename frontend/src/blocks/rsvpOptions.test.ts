import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mealLabel, sideLabel, summarizeRsvps } from './rsvpOptions.ts'

test('summary counts responses and attending guests per side', () => {
  const s = summarizeRsvps([
    { attending: true, guestCount: 2, side: 'GROOM', meal: 'YES' },
    { attending: true, guestCount: 3, side: 'BRIDE', meal: 'UNDECIDED' },
    { attending: false, guestCount: 0, side: 'BRIDE', meal: null },
  ])
  assert.deepEqual(s.total, { responses: 3, guests: 5 })
  assert.deepEqual(s.side.GROOM, { responses: 1, guests: 2 })
  assert.deepEqual(s.side.BRIDE, { responses: 2, guests: 3 })
  assert.deepEqual(s.side.unknown, { responses: 0, guests: 0 })
  assert.deepEqual(s.meal, { YES: 2, NO: 0, UNDECIDED: 3, unknown: 0 })
})

test('responses saved before side and meal existed land in "unknown"', () => {
  const s = summarizeRsvps([
    { attending: true, guestCount: 4 },
    { attending: true, guestCount: 1, side: null, meal: null },
  ])
  assert.deepEqual(s.side.unknown, { responses: 2, guests: 5 })
  assert.equal(s.meal.unknown, 5)
})

test('meal answers from guests not attending are ignored', () => {
  const s = summarizeRsvps([{ attending: false, guestCount: 2, side: 'GROOM', meal: 'YES' }])
  assert.equal(s.meal.YES, 0)
  assert.equal(s.total.guests, 0)
})

test('labels are empty for missing answers', () => {
  assert.equal(sideLabel('GROOM'), '신랑측')
  assert.equal(sideLabel(null), '')
  assert.equal(mealLabel('UNDECIDED'), '미정')
  assert.equal(mealLabel(undefined), '')
})
