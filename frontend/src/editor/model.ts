import type { Block, BlockType, HeroLayout, LookOverrides, PageData, PageDesign } from '../api/types.ts'
import type { FontFaceId, ThemeFonts } from '../blocks/fonts.ts'
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

// A theme's look is data: one choice per part slot, a few CSS tokens and its fonts.
// invitation.css styles parts (`part-<slot>-<value>`), never theme names, so any theme
// can reuse any part. The first option of each slot is the plain default.
export const PART_OPTIONS = {
  sheet: ['plain', 'letter'],
  texture: ['none', 'fibre'],
  heroFrame: ['none', 'double-rule', 'arch', 'top-bar', 'rounded-double', 'pressed-rules', 'letter'],
  heroMark: ['none', 'leaf', 'stamp'],
  photoFrame: ['none', 'print', 'arch'],
  typeset: ['plain', 'formal'],
  headingOrnament: ['none', 'diamond-rule'],
  surfaces: ['outlined', 'tinted'],
  closing: ['none', 'thank-you'],
  envelope: ['none', 'wax-seal'],
  motion: ['none', 'reveal'],
  // Envelope details; they only show while `envelope` is on.
  envelopeFlap: ['pointed', 'round', 'straight'],
  envelopeMaterial: ['smooth', 'linen', 'kraft', 'velvet'],
  envelopeLiner: ['none', 'dots', 'stripes', 'gingham'],
  sealColor: ['gold', 'wine', 'silver', 'forest', 'navy'],
  sealEmblem: ['monogram', 'heart', 'leaf', 'star'],
  envelopeDecor: ['none', 'ribbon', 'sprig', 'stamp'],
} as const
export type PartSlot = keyof typeof PART_OPTIONS
export type ThemeParts = { [S in PartSlot]: (typeof PART_OPTIONS)[S][number] }

// Token name -> the CSS custom property it sets on .page-renderer; null for tokens read
// elsewhere.
export const TOKEN_VARS = {
  line: '--inv-line',
  nameTracking: '--theme-name-tracking',
  numeralColor: '--theme-numeral',
  ground: '--theme-ground',
  paper2: '--theme-paper-2',
  // The envelope wraps the renderer and gets this through envelopeColor().
  envelopeColor: null,
} as const
export type TokenName = keyof typeof TOKEN_VARS
export type ThemeTokens = Partial<Record<TokenName, string>>

// What a couple may set per token. Presets are trusted CSS, but page overrides become
// inline custom properties, so they are limited to plain hex colours and fixed values
// (no url(), no third-party requests).
export const NAME_TRACKING_OPTIONS = [
  { value: '-.02em', label: '좁게' }, { value: '.03em', label: '보통' },
  { value: '.08em', label: '넓게' }, { value: '.14em', label: '아주 넓게' },
] as const
export const TOKEN_KINDS: Record<TokenName, 'color' | 'tracking'> = {
  ground: 'color', paper2: 'color', line: 'color', numeralColor: 'color', nameTracking: 'tracking', envelopeColor: 'color',
}
export interface ThemeLook { parts: ThemeParts; tokens: ThemeTokens; fonts: ThemeFonts }

export interface DesignPreset {
  code: string; name: string; description: string; design: PageDesign
  // Kept beside `design`, not in it: the seeder copies `design` into pages, and a look
  // read from the preset lets old pages pick up template improvements.
  parts?: Partial<ThemeParts>; tokens?: ThemeTokens; fonts?: ThemeFonts
}

// Single source for themes: the backend TemplateSeeder reads the same file, so a
// template's code, theme and defaults never drift from what the editor offers.
export const DESIGN_PRESETS: DesignPreset[] = themes as DesignPreset[]

const DEFAULT_PARTS = Object.fromEntries(Object.entries(PART_OPTIONS).map(([slot, values]) => [slot, values[0]])) as ThemeParts

function isPartValue(slot: PartSlot, value: unknown): boolean {
  return (PART_OPTIONS[slot] as readonly string[]).includes(value as string)
}

export function themeLook(theme: string): ThemeLook {
  const preset = DESIGN_PRESETS.find(p => p.design.theme === theme)
  const parts = { ...DEFAULT_PARTS }
  for (const slot of Object.keys(PART_OPTIONS) as PartSlot[]) {
    const value = preset?.parts?.[slot]
    if (isPartValue(slot, value)) (parts as Record<PartSlot, string>)[slot] = value as string
  }
  return { parts, tokens: { ...preset?.tokens }, fonts: { ...preset?.fonts } }
}

export function isHexColor(value: unknown): value is string {
  return typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value)
}

function isTokenValue(name: TokenName, value: unknown): value is string {
  return TOKEN_KINDS[name] === 'color' ? isHexColor(value) : NAME_TRACKING_OPTIONS.some(o => o.value === value)
}

/** Keeps only known slots/tokens with allowed values; undefined when nothing is left. */
export function sanitizeLook(raw: unknown): LookOverrides | undefined {
  if (!raw || typeof raw !== 'object') return undefined
  const { parts: rawParts, tokens: rawTokens } = raw as { parts?: Record<string, unknown>; tokens?: Record<string, unknown> }
  const parts: Record<string, string> = {}, tokens: Record<string, string> = {}
  for (const slot of Object.keys(PART_OPTIONS) as PartSlot[]) {
    const value = rawParts?.[slot]
    if (isPartValue(slot, value)) parts[slot] = value as string
  }
  for (const name of Object.keys(TOKEN_KINDS) as TokenName[]) {
    const value = rawTokens?.[name]
    if (isTokenValue(name, value)) tokens[name] = value
  }
  const look: LookOverrides = {}
  if (Object.keys(parts).length) look.parts = parts
  if (Object.keys(tokens).length) look.tokens = tokens
  return look.parts || look.tokens ? look : undefined
}

// Editor groups. A Record, so a new slot that names no group fails to compile instead of
// silently missing from the editor.
export const PART_GROUPS = [
  { id: 'paper', title: '종이와 첫 화면' },
  { id: 'type', title: '글자와 장식' },
  { id: 'staging', title: '연출' },
  // Shown, and drawn, only while `envelope` is on.
  { id: 'envelope', title: '봉투' },
] as const
export type PartGroup = (typeof PART_GROUPS)[number]['id']
export const PART_GROUP_OF: Record<PartSlot, PartGroup> = {
  sheet: 'paper', texture: 'paper', heroFrame: 'paper', heroMark: 'paper', photoFrame: 'paper',
  typeset: 'type', headingOrnament: 'type', surfaces: 'type', closing: 'type',
  envelope: 'staging', motion: 'staging',
  envelopeFlap: 'envelope', envelopeMaterial: 'envelope', envelopeLiner: 'envelope', sealColor: 'envelope', sealEmblem: 'envelope', envelopeDecor: 'envelope',
}

/** The group's slots in PART_OPTIONS order. */
export function slotsIn(group: PartGroup): PartSlot[] {
  return (Object.keys(PART_OPTIONS) as PartSlot[]).filter(slot => PART_GROUP_OF[slot] === group)
}

/** Whether the slot's choice shows at all: envelope details only count with the envelope on. */
export function partIsActive(parts: ThemeParts, slot: PartSlot): boolean {
  return PART_GROUP_OF[slot] !== 'envelope' || parts.envelope !== 'none'
}

// Decorative faces a part value draws with (invitation.css var(--theme-script|display),
// or the seal SVG's script initials). pageLook loads them only for active parts, so a new
// part that uses one must be listed here; model.test checks this against the CSS.
export type FaceRole = 'script' | 'display'
export const PART_FACES: { [S in PartSlot]?: Partial<Record<ThemeParts[S], readonly FaceRole[]>> } = {
  heroFrame: { letter: ['script', 'display'] },
  typeset: { formal: ['script', 'display'] },
  closing: { 'thank-you': ['script'] },
  sealEmblem: { monogram: ['script'] },
}

// A theme that doesn't name a face its parts use gets these.
const FALLBACK_FACES: Record<FaceRole, FontFaceId> = { script: 'pinyon-script', display: 'cormorant-garamond' }

/** The decorative faces the page's active parts draw with. */
export function facesUsed(parts: ThemeParts): Set<FaceRole> {
  const roles = new Set<FaceRole>()
  for (const slot of Object.keys(parts) as PartSlot[]) {
    if (!partIsActive(parts, slot)) continue
    const faces = (PART_FACES[slot] as Record<string, readonly FaceRole[]> | undefined)?.[parts[slot]]
    faces?.forEach(role => roles.add(role))
  }
  return roles
}

/** The theme's look with the couple's overrides applied; `design` comes from getPageDesign, which sanitized them. */
export function pageLook(design: PageDesign): ThemeLook {
  const base = themeLook(design.theme)
  const look = design.look
  const parts = { ...base.parts, ...look?.parts } as ThemeParts
  const fonts = { ...base.fonts }
  for (const role of facesUsed(parts)) fonts[role] ??= FALLBACK_FACES[role]
  return { parts, tokens: { ...base.tokens, ...look?.tokens }, fonts }
}

// Editor changes store only what differs from the template, so template updates still
// reach the page and "template default" is one choice away.
export function withPart(design: PageDesign, slot: PartSlot, value: string): LookOverrides | undefined {
  const parts = { ...design.look?.parts }
  if (value === themeLook(design.theme).parts[slot]) delete parts[slot]
  else parts[slot] = value
  return sanitizeLook({ ...design.look, parts })
}

/** Clears the token when unset or set back to what the template shows (its value or the picker's seed colour). */
export function withToken(design: PageDesign, name: TokenName, value?: string): LookOverrides | undefined {
  const tokens = { ...design.look?.tokens }
  const isDefault = !value || value === themeLook(design.theme).tokens[name]
    || (TOKEN_KINDS[name] === 'color' && value.toLowerCase() === tokenColorHint(name, design).toLowerCase())
  if (isDefault) delete tokens[name]
  else tokens[name] = value
  return sanitizeLook({ ...design.look, tokens })
}

// Only non-default parts get a class; the defaults are the unprefixed base styles.
export function partClassNames(parts: ThemeParts): string[] {
  return (Object.keys(parts) as PartSlot[]).filter(slot => parts[slot] !== DEFAULT_PARTS[slot])
    .map(slot => 'part-' + slot.replace(/[A-Z]/g, c => '-' + c.toLowerCase()) + '-' + parts[slot])
}

export function tokenStyle(tokens: ThemeTokens): Record<string, string> {
  return Object.fromEntries((Object.keys(TOKEN_VARS) as TokenName[]).flatMap(name => {
    const cssVar = TOKEN_VARS[name], value = tokens[name]
    return cssVar && value ? [[cssVar, value]] : []
  }))
}

// Behaviour a theme adds on top of its CSS, derived from its parts.
export interface ThemeFeatures { envelopeIntro: boolean; reveal: boolean }
export function themeFeatures({ parts }: ThemeLook): ThemeFeatures {
  return { envelopeIntro: parts.envelope !== 'none', reveal: parts.motion === 'reveal' }
}

/** The initials are pressed into the wax only with the envelope on and the monogram emblem. */
export function sealShowsInitials(look: ThemeLook): boolean {
  return themeFeatures(look).envelopeIntro && look.parts.sealEmblem === 'monogram'
}

// Editor labels. `layouts` marks options that only show with that hero layout.
export const PART_LABELS: { [S in PartSlot]: { label: string; options: Record<ThemeParts[S], string>; layouts?: Partial<Record<ThemeParts[S], HeroLayout>> } } = {
  sheet: { label: '종이', options: { plain: '기본', letter: '물결 테두리 편지지' } },
  texture: { label: '종이 질감', options: { none: '없음', fibre: '한지 섬유' } },
  heroFrame: {
    label: '첫 화면 장식',
    options: { none: '없음', 'double-rule': '이중 테두리', arch: '아치', 'top-bar': '굵은 윗선', 'rounded-double': '둥근 이중 테두리', 'pressed-rules': '두 줄 괘선', letter: '편지 구성' },
    layouts: { 'double-rule': 'frame', arch: 'frame', 'top-bar': 'frame', 'rounded-double': 'frame', letter: 'frame', 'pressed-rules': 'typographic' },
  },
  heroMark: { label: '첫 화면 마크', options: { none: '없음', leaf: '잎사귀 ❦', stamp: '囍 낙관' }, layouts: { leaf: 'frame' } },
  photoFrame: { label: '대표 사진 테두리', options: { none: '없음', print: '인화 사진', arch: '아치' }, layouts: { print: 'photo-full', arch: 'frame' } },
  typeset: { label: '글자 조판', options: { plain: '기본', formal: '격식 있게' } },
  headingOrnament: { label: '제목 장식', options: { none: '없음', 'diamond-rule': '마름모 장식선' } },
  surfaces: { label: '카드 표면', options: { outlined: '테두리', tinted: '색 면' } },
  closing: { label: '마무리', options: { none: '없음', 'thank-you': 'Thank you 서명' } },
  envelope: { label: '봉투 인트로', options: { none: '사용 안 함', 'wax-seal': '실링 봉투' } },
  motion: { label: '스크롤 효과', options: { none: '없음', reveal: '천천히 나타나기' } },
  envelopeFlap: { label: '덮개 모양', options: { pointed: '뾰족', round: '둥근', straight: '일자' } },
  envelopeMaterial: { label: '소재', options: { smooth: '매끈한 종이', linen: '린넨', kraft: '크라프트', velvet: '벨벳' } },
  envelopeLiner: { label: '안감', options: { none: '민무늬', dots: '물방울', stripes: '줄무늬', gingham: '깅엄 체크' } },
  sealColor: { label: '실링 색', options: { gold: '골드', wine: '와인', silver: '실버', forest: '포레스트', navy: '네이비' } },
  sealEmblem: { label: '실링 문양', options: { monogram: '이니셜', heart: '하트', leaf: '잎사귀', star: '별' } },
  envelopeDecor: { label: '장식', options: { none: '없음', ribbon: '리본', sprig: '마른 꽃', stamp: '우표' } },
}

export const TOKEN_LABELS: Record<TokenName, string> = {
  ground: '바깥 배경 (편지지)', paper2: '카드 면 색', line: '구분선 색', numeralColor: '날짜 숫자 색', nameTracking: '이름 자간', envelopeColor: '봉투 색',
}

/** The envelope's colour: the couple's pick, else the accent colour as before. */
export function envelopeColor(design: PageDesign, look: ThemeLook): string {
  const value = look.tokens.envelopeColor
  return isHexColor(value) ? value : design.accentColor
}

function mixHex(a: string, b: string, weightA: number): string {
  const ca = a.match(/[0-9a-f]{2}/gi)!.map(c => parseInt(c, 16)), cb = b.match(/[0-9a-f]{2}/gi)!.map(c => parseInt(c, 16))
  return '#' + ca.map((v, i) => Math.round(v * weightA + cb[i] * (1 - weightA)).toString(16).padStart(2, '0')).join('')
}

/** A hex close to what a colour token shows when not overridden, to seed the picker. */
export function tokenColorHint(name: TokenName, design: PageDesign): string {
  const raw = themeLook(design.theme).tokens[name]
  const fromAccent = !!raw?.includes('--invite-accent')
  if (isHexColor(raw)) return raw
  const bg = design.backgroundColor, accent = readableColor(design.accentColor, bg), ink = readableColor('#36302b', bg)
  switch (name) {
    case 'ground': return mixHex(bg, '#5a4440', .85)
    case 'paper2': return mixHex(bg, '#6a4020', .93)
    case 'envelopeColor': return design.accentColor
    case 'line': return fromAccent ? mixHex(accent, bg, .16) : mixHex(ink, bg, .15)
    default: return fromAccent ? accent : ink
  }
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
  return isHexColor(value) ? value : fallback
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
    ...withLook(sanitizeLook(raw?.look)),
  }
}

// Fields this editor understands; anything else in design came from a newer editor and is kept.
const DESIGN_KEYS = new Set(['theme', 'backgroundColor', 'accentColor', 'fontFamily', 'fontSize', 'heroLayout', 'look'])

/**
 * Applies a design change. Known fields are normalized like getPageDesign; unknown ones are
 * carried over as they are, so an older open tab never wipes a newer editor's settings.
 */
export function withDesign(page: PageData, patch: Partial<PageDesign>): PageData {
  const raw = page.design !== null && typeof page.design === 'object' ? page.design as unknown as Record<string, unknown> : {}
  const unknown = Object.fromEntries(Object.entries(raw).filter(([key]) => !DESIGN_KEYS.has(key)))
  return { ...page, design: { ...unknown, ...getPageDesign(page), ...patch } }
}

/**
 * Gallery entries the editor can show (http(s) URLs) apart from anything else it cannot,
 * e.g. a future structured item. Editing the gallery keeps the rest instead of dropping it.
 */
export function splitGalleryImages(value: unknown): { images: string[]; kept: unknown[] } {
  const items = Array.isArray(value) ? value : []
  return { images: items.filter(isImageUrl), kept: items.filter(item => !isImageUrl(item)) }
}

/** The gallery after an edit: the edited URLs in their new order, then the untouched rest. */
export function withGalleryImages(value: unknown, images: string[]): unknown[] {
  return [...images, ...splitGalleryImages(value).kept]
}

function withLook(look: LookOverrides | undefined): { look?: LookOverrides } {
  return look ? { look } : {}
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
