import { test } from 'node:test'
import assert from 'node:assert/strict'
import { loginPath, readReturnTo, rememberReturnTo, safeReturnTo, takeReturnTo } from '../auth/returnTo.ts'
import { SAMPLE_WEDDING_DATE, templatePreview } from './preview.ts'
import type { PageData } from '../api/types.ts'

test('selected template and editor destinations survive login encoding', () => {
  const target = '/create?template=template-123'
  assert.equal(safeReturnTo(target), target)
  assert.equal(new URLSearchParams(loginPath(target).split('?')[1]).get('returnTo'), target)
  assert.equal(safeReturnTo('/e/test/guests?tab=rsvp#top'), '/e/test/guests?tab=rsvp#top')
})

test('login return paths reject external destinations and auth loops', () => {
  for (const value of [null, '', 'https://evil.test', '//evil.test', '/\\evil.test', '/\nevil.test', '/login', '/LOGIN/', '/oauth/callback?token=sample', '/create/../login', '/%6cogin', 'javascript:alert(1)']) {
    assert.equal(safeReturnTo(value), '/invitations', String(value))
  }
})

test('social login destination can be read repeatedly before successful consumption', () => {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage')
  const values = new Map<string, string>()
  Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, value: { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value), removeItem: (key: string) => values.delete(key) } })
  try {
    rememberReturnTo('/create?template=chosen')
    assert.equal(readReturnTo(), '/create?template=chosen')
    assert.equal(readReturnTo(), '/create?template=chosen')
    assert.equal(takeReturnTo(), '/create?template=chosen')
    assert.equal(readReturnTo(), '/invitations')
    rememberReturnTo('//evil.test')
    assert.equal(takeReturnTo(), '/invitations')
  } finally {
    if (original) Object.defineProperty(globalThis, 'sessionStorage', original)
    else Reflect.deleteProperty(globalThis, 'sessionStorage')
  }
})

test('blocked browser storage does not break the login flow', () => {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage')
  Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, get() { throw new Error('disabled') } })
  try {
    assert.doesNotThrow(() => rememberReturnTo('/create?template=chosen'))
    assert.equal(readReturnTo(), '/invitations')
    assert.equal(takeReturnTo(), '/invitations')
  } finally {
    if (original) Object.defineProperty(globalThis, 'sessionStorage', original)
    else Reflect.deleteProperty(globalThis, 'sessionStorage')
  }
})

test('public template samples preserve presets and never modify source data', () => {
  const page: PageData = { future: { keep: true }, blocks: [
    { id: 'hero', type: 'hero', order: 2, visible: true, content: { groomName: '', brideName: '기존 이름', future: 42 }, style: { bgColor: '#faf7f2' } },
    { id: 'map', type: 'map', order: 4, visible: false, content: { address: '', venueName: '기존 장소' }, style: {} },
    { id: 'gallery', type: 'gallery', order: 0, visible: true, content: { images: [] }, style: {} },
  ] }
  const before = structuredClone(page)
  const preview = templatePreview(page)
  assert.deepEqual(page, before)
  assert.deepEqual(preview.future, page.future)
  assert.equal(preview.blocks[0].content.groomName, '하늘')
  assert.equal(preview.blocks[0].content.brideName, '기존 이름')
  assert.equal(preview.blocks[0].content.weddingDate, SAMPLE_WEDDING_DATE)
  assert.equal(preview.blocks[0].content.future, 42)
  assert.deepEqual(preview.blocks[0].style, page.blocks[0].style)
  assert.equal(preview.blocks[1].visible, false)
  assert.equal(preview.blocks[1].content.venueName, '기존 장소')
  assert.deepEqual(preview.blocks[2], page.blocks[2])
})
