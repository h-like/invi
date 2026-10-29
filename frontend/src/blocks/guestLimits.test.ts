import { test } from 'node:test'
import assert from 'node:assert/strict'
import { clampGuestCount, GUEST_COUNT_MAX } from './guestLimits.ts'

test('guest count stays within the range the server accepts', () => {
  assert.equal(clampGuestCount(3), 3)
  assert.equal(clampGuestCount(GUEST_COUNT_MAX + 5), GUEST_COUNT_MAX)
  assert.equal(clampGuestCount(-2), 0)
  assert.equal(clampGuestCount(2.7), 2)
  assert.equal(clampGuestCount(Number('')), 0)
  assert.equal(clampGuestCount(Number.NaN), 0)
})
