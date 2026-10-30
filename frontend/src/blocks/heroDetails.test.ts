import { test } from 'node:test'
import assert from 'node:assert/strict'
import { MONOGRAM_MAX, monogramFontSize, normalizeMonogram, parentLine, sealMonogram } from './heroDetails.ts'

test('parent line joins both parents and keeps the chosen relation', () => {
  assert.deepEqual(parentLine({ groomFather: ' 이정호 ', groomMother: '김미경', groomRelation: '장남' }, 'groom', '도윤'),
    { parents: '이정호 · 김미경', relation: '장남', child: '도윤' })
})

test('parent line works with one parent and falls back to a neutral relation', () => {
  assert.deepEqual(parentLine({ brideMother: '박선영' }, 'bride', '서아'), { parents: '박선영', relation: '딸', child: '서아' })
  assert.equal(parentLine({ groomFather: '이정호' }, 'groom', '도윤')?.relation, '아들')
})

test('parent line leaves the child empty rather than inventing a name', () => {
  assert.equal(parentLine({ groomFather: '이정호' }, 'groom', '  ')?.child, '')
})

test('parent line is hidden without any parent name, whatever else is stored', () => {
  assert.equal(parentLine({}, 'groom', '도윤'), null)
  assert.equal(parentLine({ groomFather: '  ', groomMother: 3, groomRelation: '장남' }, 'groom', '도윤'), null)
})

test('monogram keeps only Latin letters, ampersand and single spaces', () => {
  assert.equal(normalizeMonogram('D&S'), 'D&S')
  assert.equal(normalizeMonogram('도D  &  S!'), 'D & S')
  assert.equal(normalizeMonogram('ABCDEFG'), 'ABCDE')
  assert.equal(normalizeMonogram(42), '')
  assert.ok(normalizeMonogram('a b c d e f').length <= MONOGRAM_MAX)
})

test('seal falls back to the ampersand when no usable initials are set', () => {
  assert.equal(sealMonogram(''), '&')
  assert.equal(sealMonogram('  '), '&')
  assert.equal(sealMonogram('가나'), '&')
  assert.equal(sealMonogram(undefined), '&')
  assert.equal(sealMonogram('d & s '), 'd & s')
})

test('monogram font shrinks with length and never exceeds the original size', () => {
  assert.equal(monogramFontSize('&'), 26)
  assert.ok(monogramFontSize('D&S') < 26)
  assert.ok(monogramFontSize('D & S') < monogramFontSize('D&S'))
  assert.ok(monogramFontSize('D & S') * 5 * 0.8 <= 36.01)
})
