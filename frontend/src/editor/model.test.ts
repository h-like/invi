import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createEmptyPage, DESIGN_PRESETS, getPageDesign, isImageUrl, moveBlock, patchContent, publishIssues, readableColor, contrastRatio, updateBlock } from './model.ts'

test('reordering normalizes order without losing design, hidden flags or future fields', () => {
  const original = { ...createEmptyPage(), design: DESIGN_PRESETS[2].design, futureSetting: { retained: true } }
  original.blocks[1].visible = false
  const moved = moveBlock(original, 'map-1', 'hero-1')
  assert.equal(moved.blocks[0].id, 'map-1')
  assert.deepEqual(moved.blocks.map(b => b.order), [0, 1, 2, 3, 4, 5, 6])
  assert.equal(moved.blocks.find(b => b.id === 'countdown-1')?.visible, false)
  assert.deepEqual(moved.design, original.design)
  assert.deepEqual(moved.futureSetting, { retained: true })
  assert.equal(original.blocks[0].id, 'hero-1')
  assert.equal(moveBlock(original, 'missing', 'hero-1'), original)
})

test('content edits and visibility preserve unrelated data and never mutate input', () => {
  const original = createEmptyPage()
  original.blocks[0].content = { groomName: '민수', future: 'keep' }
  const edited = patchContent(original, 'hero-1', { brideName: '지영' })
  const hidden = updateBlock(edited, 'gallery-1', { visible: false })
  assert.deepEqual(hidden.blocks[0].content, { groomName: '민수', brideName: '지영', future: 'keep' })
  assert.equal(original.blocks[0].content.brideName, undefined)
  assert.equal(original.blocks[2].visible, true)
  assert.equal(hidden.blocks[2].visible, false)
})

test('legacy templates inherit design; unsafe style tokens are not accepted', () => {
  const legacy = createEmptyPage()
  legacy.blocks[0].style = { bgColor: '#141414', accentColor: '#4fd1c5', fontFamily: 'pretendard' }
  assert.equal(getPageDesign(legacy).theme, 'modern')
  assert.equal(getPageDesign(legacy).fontFamily, 'sans')
  const unsafe = JSON.parse('{"blocks":[],"design":{"theme":"injected","backgroundColor":"url(evil)","accentColor":"red;display:none","fontFamily":"evil","fontSize":"1000px"}}')
  assert.deepEqual(getPageDesign(unsafe), DESIGN_PRESETS[0].design)
})

test('text colors maintain WCAG AA contrast for each preset and custom backgrounds', () => {
  for (const background of [...DESIGN_PRESETS.map(p => p.design.backgroundColor), '#777777', '#ffff00', '#663399']) {
    for (const foreground of ['#ffffff', '#000000', background]) {
      assert.ok(contrastRatio(readableColor(foreground, background), background) >= 4.5)
    }
  }
})

test('gallery and cover URLs reject executable/data schemes', () => {
  assert.equal(isImageUrl('https://example.com/photo.jpg'), true)
  assert.equal(isImageUrl('http://localhost:5173/photo.jpg'), true)
  for (const value of ['javascript:alert(1)', 'data:image/svg+xml,<svg/>', 'file:///secret', '//example.com/a.jpg', '', 42, null]) assert.equal(isImageUrl(value), false)
})

test('drafts can be incomplete; publishing checks only displayed required sections', () => {
  let page = createEmptyPage()
  assert.equal(publishIssues(page).length, 2)
  page = patchContent(page, 'hero-1', { groomName: '민수', brideName: '지영' })
  page = updateBlock(page, 'map-1', { visible: false })
  assert.deepEqual(publishIssues(page), [])
  page = updateBlock(page, 'hero-1', { visible: false })
  assert.equal(publishIssues(page).length, 1)
})

// Run with Node 22.18+ or 24+: node --test src/editor/model.test.ts
