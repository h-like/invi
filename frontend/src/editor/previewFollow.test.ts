import { test } from 'node:test'
import assert from 'node:assert/strict'
import { FOLLOW_MARGIN, followScrollTop, shouldFollow } from './previewFollow.ts'

const view = { top: 100, bottom: 700 }

test('a different block always follows, even when already in view', () => {
  assert.equal(shouldFollow(null, 'map-1', { top: 200, bottom: 400 }, view), true)
  assert.equal(shouldFollow('hero-1', 'map-1', { top: 200, bottom: 400 }, view), true)
})

test('another field of the same block follows only once the block left the view', () => {
  assert.equal(shouldFollow('hero-1', 'hero-1', { top: -300, bottom: 900 }, view), false)
  assert.equal(shouldFollow('hero-1', 'hero-1', { top: 650, bottom: 900 }, view), false)
  assert.equal(shouldFollow('hero-1', 'hero-1', { top: -500, bottom: 100 }, view), true)
  assert.equal(shouldFollow('hero-1', 'hero-1', { top: 700, bottom: 900 }, view), true)
})

test('scroll target puts the block top just below the view top', () => {
  assert.equal(followScrollTop(0, { top: 900, bottom: 1200 }, view, 5000), 800 - FOLLOW_MARGIN)
  assert.equal(followScrollTop(1000, { top: -200, bottom: 100 }, view, 5000), 700 - FOLLOW_MARGIN)
})

test('scroll target is clamped to the scrollable range', () => {
  assert.equal(followScrollTop(0, { top: 105, bottom: 300 }, view, 5000), 0)
  assert.equal(followScrollTop(0, { top: 4000, bottom: 4200 }, view, 1200), 1200)
  assert.equal(followScrollTop(0, { top: 400, bottom: 500 }, view, -10), 0)
})
