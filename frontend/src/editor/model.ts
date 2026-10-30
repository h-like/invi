import type { Block, BlockType, HeroLayout, PageData, PageDesign } from '../api/types.ts'
import themes from './themes.json' with { type: 'json' }

export const BLOCK_LABELS: Record<BlockType, string> = {
  hero: '첫 화면 · 초대의 글', countdown: '예식일 카운트다운', gallery: '웨딩 갤러리',
  map: '오시는 길', account: '마음 전하실 곳', rsvp: '참석 확인', guestbook: '방명록',
}

export const HERO_LAYOUTS: { value: HeroLayout; label: string }[] = [
  { value: 'frame', label: '종이 프레임' },
  { value: 'photo-full', label: '사진 가득' },
  { value: 'typographic', label: '큰 이름 타이포' },
]

export interface DesignPreset { code: string; name: string; description: string; design: PageDesign }

// Single source for themes: the backend TemplateSeeder reads the same file, so a
// template's code, theme and defaults never drift from what the editor offers.
export const DESIGN_PRESETS: DesignPreset[] = themes as DesignPreset[]

// Behaviour a theme adds on top of its CSS. Kept here rather than in themes.json so the
// seeded design stays plain data and old pages never carry stale flags.
export interface ThemeFeatures { envelopeIntro: boolean; reveal: boolean }
const THEME_FEATURES: Record<string, ThemeFeatures> = {
  seal: { envelopeIntro: true, reveal: true },
}
export function themeFeatures(theme: string): ThemeFeatures {
  return THEME_FEATURES[theme] ?? { envelopeIntro: false, reveal: false }
}

// Original sample copy. No competitor text or artwork is reused.
export const GREETING_SAMPLES = [
  { name: '따뜻한 초대', text: '서로의 하루에 가장 편안한 사람이 되었습니다.\n이제 같은 계절을 함께 걸어가려 합니다.\n저희의 시작에 함께해 주시면 감사하겠습니다.' },
  { name: '짧고 담백하게', text: '함께 웃고, 함께 자라는 두 사람이\n부부라는 이름으로 새로운 시작을 합니다.\n소중한 여러분을 초대합니다.' },
  { name: '감사의 마음', text: '지금의 저희를 있게 해 주신 소중한 분들께\n감사한 마음을 담아 인사드립니다.\n따뜻한 축복 속에서 첫걸음을 내딛고 싶습니다.' },
]

export const MAX_GALLERY_IMAGES = 20
export type GalleryLayout = 'grid-2' | 'grid-3' | 'slider'

export function isImageUrl(value: unknown): value is string {
  if (typeof value !== 'string' || !value.trim()) return false
  try { return ['http:', 'https:'].includes(new URL(value).protocol) } catch { return false }
}

function color(value: unknown, fallback: string): string {
  return typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value) ? value : fallback
}

export function getPageDesign(page: PageData | null): PageDesign {
  const heroStyle = page?.blocks.find(b => b.type === 'hero')?.style ?? {}
  const inherited = DESIGN_PRESETS.find(p => p.design.backgroundColor === heroStyle.bgColor)?.design ?? DESIGN_PRESETS[0].design
  const raw = page?.design
  const theme = DESIGN_PRESETS.find(p => p.design.theme === raw?.theme)?.design.theme ?? inherited.theme
  return {
    theme,
    backgroundColor: color(raw?.backgroundColor, color(heroStyle.bgColor, inherited.backgroundColor)),
    accentColor: color(raw?.accentColor, color(heroStyle.accentColor, inherited.accentColor)),
    fontFamily: raw?.fontFamily === 'sans' || raw?.fontFamily === 'serif' ? raw.fontFamily : heroStyle.fontFamily === 'pretendard' ? 'sans' : inherited.fontFamily,
    fontSize: raw?.fontSize === 'large' || raw?.fontSize === 'larger' ? raw.fontSize : 'normal',
    // Pages saved before heroLayout existed keep the layout they were designed with.
    heroLayout: HERO_LAYOUTS.find(l => l.value === raw?.heroLayout)?.value ?? 'frame',
  }
}

function luminance(hex: string): number {
  const rgb = hex.match(/[0-9a-f]{2}/gi)!.map(c => parseInt(c, 16) / 255).map(c => c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4)
  return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722
}
export function contrastRatio(a: string, b: string): number {
  const x = luminance(a), y = luminance(b)
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05)
}
export function readableColor(foreground: string, background: string): string {
  if (contrastRatio(foreground, background) >= 4.5) return foreground
  return contrastRatio('#000000', background) >= contrastRatio('#ffffff', background) ? '#000000' : '#ffffff'
}

export function createEmptyPage(): PageData {
  return { blocks: (['hero', 'countdown', 'gallery', 'map', 'account', 'rsvp', 'guestbook'] as BlockType[]).map((type, order) => ({ id: type + '-1', type, order, visible: true, content: {}, style: {} })) }
}
export function updateBlock(page: PageData, id: string, patch: Partial<Block>): PageData {
  return { ...page, blocks: page.blocks.map(b => b.id === id ? { ...b, ...patch } : b) }
}
export function patchContent(page: PageData, id: string, patch: Record<string, unknown>): PageData {
  const block = page.blocks.find(b => b.id === id)
  return block ? updateBlock(page, id, { content: { ...block.content, ...patch } }) : page
}
export function moveBlock(page: PageData, sourceId: string, targetId: string): PageData {
  const blocks = [...page.blocks].sort((a, b) => a.order - b.order)
  const from = blocks.findIndex(b => b.id === sourceId), to = blocks.findIndex(b => b.id === targetId)
  if (from < 0 || to < 0 || from === to) return page
  const [moved] = blocks.splice(from, 1)
  blocks.splice(to, 0, moved)
  return { ...page, blocks: blocks.map((b, order) => ({ ...b, order })) }
}
export function publishIssues(page: PageData): string[] {
  const issues: string[] = []
  const hero = page.blocks.find(b => b.type === 'hero' && b.visible)
  if (!hero) issues.push('첫 화면을 표시해 주세요.')
  else if (![hero.content.groomName, hero.content.brideName].every(n => typeof n === 'string' && n.trim())) issues.push('신랑과 신부 이름을 입력해 주세요.')
  const map = page.blocks.find(b => b.type === 'map' && b.visible)
  if (map && ![map.content.venueName, map.content.address].every(n => typeof n === 'string' && n.trim())) issues.push('오시는 길의 식장 이름과 주소를 입력하거나 해당 섹션을 숨겨 주세요.')
  return issues
}
