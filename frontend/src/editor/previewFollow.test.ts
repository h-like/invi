import { test } from 'node:test'
import assert from 'node:assert/strict'
import { FOLLOW_MARGIN, followPreviewBlock, followScrollTop, shouldFollow, type FollowContainer, type Span } from './previewFollow.ts'

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

type FakeBox = { getBoundingClientRect(): Span; dataset: Record<string, string>; querySelectorAll(selector: string): FakeBox[] }
function box(top: number, bottom: number, dataset: Record<string, string> = {}, children: FakeBox[] = []): FakeBox {
  return { getBoundingClientRect: () => ({ top, bottom }), dataset, querySelectorAll: () => children }
}
// Preview box showing y 100..700 with blocks laid out below it, scrolled to the top.
function preview(blocks: FakeBox[]) {
  const scrolls: { top: number; behavior: string }[] = []
  const container = { ...box(100, 700), scrollTop: 0, scrollHeight: 3000, clientHeight: 600, querySelectorAll: () => blocks, scrollTo: (o: { top: number; behavior: string }) => { scrolls.push(o) } }
  return { container: container as unknown as FollowContainer, scrolls }
}

test('scrolls to a rendered block and reports it as followed', () => {
  const { container, scrolls } = preview([box(100, 1500, { blockId: 'hero-1' }), box(1500, 1900, { blockId: 'map-1' })])
  assert.equal(followPreviewBlock(container, 'map-1', [], null), 'map-1')
  assert.deepEqual(scrolls.map(s => s.top), [1400 - FOLLOW_MARGIN])
})

test('a hidden (unrendered) block does not scroll and keeps the last target', () => {
  const { container, scrolls } = preview([box(100, 1500, { blockId: 'hero-1' })])
  assert.equal(followPreviewBlock(container, 'account-1', [], 'hero-1'), 'hero-1')
  assert.equal(scrolls.length, 0)
})

test('scrolls to the first rendered anchor inside a tall block', () => {
  const greeting = box(1200, 1350, { previewAnchor: 'greeting' })
  const { container, scrolls } = preview([box(100, 1500, { blockId: 'hero-1' }, [greeting])])
  // No parents line rendered, so the parents section falls back to the greeting.
  assert.equal(followPreviewBlock(container, 'hero-1', ['parents', 'greeting'], 'hero-1'), 'hero-1#greeting')
  assert.deepEqual(scrolls.map(s => s.top), [1100 - FOLLOW_MARGIN])
})

test('an anchor the block does not render falls back to the block itself', () => {
  const { container, scrolls } = preview([box(800, 1500, { blockId: 'hero-1' })])
  assert.equal(followPreviewBlock(container, 'hero-1', ['greeting'], null), 'hero-1')
  assert.deepEqual(scrolls.map(s => s.top), [700 - FOLLOW_MARGIN])
})
