import { test } from 'node:test'
import assert from 'node:assert/strict'
import { clampGuestCount, GUEST_COUNT_MAX, guestCountDraft } from './guestLimits.ts'

test('guest count field drops the leading zero mobile keyboards leave behind', () => {
  assert.equal(guestCountDraft('03'), '3')
  assert.equal(guestCountDraft(''), '')
  assert.equal(guestCountDraft('0'), '0')
  assert.equal(guestCountDraft('2명'), '2')
  assert.equal(guestCountDraft('99'), String(GUEST_COUNT_MAX))
})

test('guest count stays within the range the server accepts', () => {
  assert.equal(clampGuestCount(3), 3)
  assert.equal(clampGuestCount(GUEST_COUNT_MAX + 5), GUEST_COUNT_MAX)
  assert.equal(clampGuestCount(-2), 0)
  assert.equal(clampGuestCount(2.7), 2)
  assert.equal(clampGuestCount(Number('')), 0)
  assert.equal(clampGuestCount(Number.NaN), 0)
})
