// Optional hero extras stored in hero.content: parents' names (혼주) and the wax-seal
// monogram. page_data stays schema-free, so every reader tolerates missing or odd values.

export const PARENT_NAME_MAX = 20
export const RELATION_MAX = 10
export const MONOGRAM_MAX = 5

export const GROOM_RELATIONS = ['장남', '차남', '삼남', '아들']
export const BRIDE_RELATIONS = ['장녀', '차녀', '삼녀', '딸']

export type HeroSide = 'groom' | 'bride'
/** Shown when the relation is left blank; the editor uses it as the placeholder so both agree. */
export const DEFAULT_RELATION: Record<HeroSide, string> = { groom: '아들', bride: '딸' }
export const PARENT_FIELDS: Record<HeroSide, { father: string; mother: string; relation: string }> = {
  groom: { father: 'groomFather', mother: 'groomMother', relation: 'groomRelation' },
  bride: { father: 'brideFather', mother: 'brideMother', relation: 'brideRelation' },
}

export interface ParentLine { parents: string; relation: string; child: string }

function clean(value: unknown, max: number): string {
  return typeof value === 'string' ? value.trim().slice(0, max) : ''
}

/** "이정호 · 김미경" + "의 장남" + child, or null when neither parent is named. */
export function parentLine(content: Record<string, unknown>, side: HeroSide, child: string): ParentLine | null {
  const fields = PARENT_FIELDS[side]
  const parents = [clean(content[fields.father], PARENT_NAME_MAX), clean(content[fields.mother], PARENT_NAME_MAX)].filter(Boolean)
  if (!parents.length) return null
  const relation = clean(content[fields.relation], RELATION_MAX) || DEFAULT_RELATION[side]
  return { parents: parents.join(' · '), relation, child: child.trim() }
}

/** Latin letters, "&" and single spaces only, at most MONOGRAM_MAX characters. */
export function normalizeMonogram(value: unknown): string {
  if (typeof value !== 'string') return ''
  return value.replace(/[^A-Za-z& ]/g, '').replace(/ +/g, ' ').trimStart().slice(0, MONOGRAM_MAX)
}

/** What the seal shows: the couple's initials, or the plain ampersand when none are set. */
export function sealMonogram(value: unknown): string {
  return normalizeMonogram(value).trim() || '&'
}

/**
 * First-guess font size for the seal text inside its 25-radius pool: the inner ring leaves
 * about 36 units, and Pinyon Script capitals run near 0.8em with their swashes. The seal
 * measures the real width once the font has loaded and shrinks further if needed.
 */
export function monogramFontSize(text: string): number {
  const glyphs = Math.max(1, text.length)
  return Math.min(26, Math.round(36 / (glyphs * 0.8) * 10) / 10)
}
