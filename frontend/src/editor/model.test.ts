import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { FONT_FACES } from '../blocks/fonts.ts'
import { createEmptyPage, DESIGN_PRESETS, getPageDesign, isImageUrl, moveBlock, partClassNames, PART_OPTIONS, patchContent, publishIssues, readableColor, contrastRatio, themeFeatures, themeLook, TOKEN_VARS, tokenStyle, updateBlock, type PartSlot } from './model.ts'

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

test('theme registry is well-formed and every seeded template design round-trips', () => {
  const codes = DESIGN_PRESETS.map(p => p.code)
  assert.equal(new Set(codes).size, codes.length)
  for (const preset of DESIGN_PRESETS) {
    // The backend seeds template code === design.theme.
    assert.equal(preset.design.theme, preset.code)
    assert.match(preset.code, /^[a-z][a-z0-9-]*$/)
    assert.ok(preset.name && preset.description)
    assert.deepEqual(getPageDesign({ blocks: [], design: preset.design }), preset.design)
    // A typo in themes.json would silently drop a part, token or font, so reject unknowns.
    for (const [slot, value] of Object.entries(preset.parts ?? {})) {
      assert.ok((PART_OPTIONS[slot as PartSlot] as readonly string[] | undefined)?.includes(value), `${preset.code}.parts.${slot}=${value}`)
    }
    for (const name of Object.keys(preset.tokens ?? {})) assert.ok(name in TOKEN_VARS, `${preset.code}.tokens.${name}`)
    for (const id of Object.values(preset.fonts ?? {})) assert.ok(id in FONT_FACES, `${preset.code}.fonts ${id}`)
  }
})

test('theme look resolves parts to classes, tokens to custom properties and features', () => {
  const seal = themeLook('seal')
  assert.ok(partClassNames(seal.parts).includes('part-hero-frame-letter'))
  assert.ok(partClassNames(seal.parts).includes('part-heading-ornament-diamond-rule'))
  assert.deepEqual(themeFeatures('seal'), { envelopeIntro: true, reveal: true })
  // Plain defaults add no classes, so a theme without parts renders the base styles.
  assert.deepEqual(partClassNames(themeLook('minimal').parts), [])
  assert.deepEqual(themeFeatures('minimal'), { envelopeIntro: false, reveal: false })
  assert.deepEqual(themeFeatures('missing'), { envelopeIntro: false, reveal: false })
  assert.deepEqual(tokenStyle({ numeralColor: 'red', line: '' }), { '--theme-numeral': 'red' })
})

test('invitation CSS styles parts, never theme names', () => {
  const css = readFileSync(new URL('../blocks/invitation.css', import.meta.url), 'utf8')
  assert.doesNotMatch(css, /invitation-theme-/)
  for (const slot of Object.keys(PART_OPTIONS) as PartSlot[]) {
    for (const value of PART_OPTIONS[slot].slice(1)) {
      // envelope and motion are behaviour (EnvelopeIntro, reveal), not CSS.
      if (slot === 'envelope' || slot === 'motion') continue
      const name = partClassNames({ ...themeLook('minimal').parts, [slot]: value })[0]
      assert.ok(css.includes('.' + name + ' ') || css.includes('.' + name + ','), `no CSS for ${name}`)
    }
  }
})

test('hero layout falls back to frame for legacy or unknown values', () => {
  assert.equal(getPageDesign(createEmptyPage()).heroLayout, 'frame')
  assert.equal(getPageDesign({ blocks: [], design: { ...DESIGN_PRESETS[0].design, heroLayout: 'typographic' } }).heroLayout, 'typographic')
  assert.equal(getPageDesign(JSON.parse('{"blocks":[],"design":{"heroLayout":"<script>"}}')).heroLayout, 'frame')
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
