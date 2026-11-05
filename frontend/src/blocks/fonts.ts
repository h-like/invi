import type { InvitationFont } from '../api/types.ts'

// Self-hosted (OFL-1.1) instead of a font CDN: no third-party request leaves a guest's
// phone, and the https-test no-referrer/privacy headers stay intact. Each stylesheet
// only declares @font-face rules split by unicode-range, so browsers download just the
// Korean subsets a page actually renders. Loaded on demand so pages that never show an
// invitation (landing, login) don't pay for them.
const loaders: Record<InvitationFont, () => Promise<unknown>> = {
  serif: () => Promise.all([import('@fontsource/noto-serif-kr/400.css'), import('@fontsource/noto-serif-kr/600.css')]),
  sans: () => import('pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css'),
}

export const FONT_STACKS: Record<InvitationFont, string> = {
  serif: '"Noto Serif KR", Georgia, "Batang", "바탕", serif',
  sans: '"Pretendard Variable", system-ui, "Malgun Gothic", sans-serif',
}

// A theme may bring its own serif body face and decorative Latin faces (themes.json
// `fonts`). They load only when a page uses them; "sans" stays the user's override.
// Latin-only faces use the latin subset since they never render Hangul.
export const FONT_FACES = {
  'gowun-batang': {
    family: '"Gowun Batang"', stack: '"Gowun Batang", "Noto Serif KR", "AppleMyungjo", "Batang", serif',
    load: () => Promise.all([import('@fontsource/gowun-batang/400.css'), import('@fontsource/gowun-batang/700.css')]),
  },
  'pinyon-script': { family: '"Pinyon Script"', stack: '"Pinyon Script", "Snell Roundhand", cursive', load: () => import('@fontsource/pinyon-script/latin-400.css') },
  'cormorant-garamond': { family: '"Cormorant Garamond"', stack: '"Cormorant Garamond", Georgia, serif', load: () => import('@fontsource/cormorant-garamond/latin-600.css') },
} satisfies Record<string, { family: string; stack: string; load: () => Promise<unknown> }>
export type FontFaceId = keyof typeof FONT_FACES

/** serif replaces the body serif; script and display feed --theme-script / --theme-display. */
export interface ThemeFonts { serif?: FontFaceId; script?: FontFaceId; display?: FontFaceId }

function face(id: string | undefined) {
  return id && Object.hasOwn(FONT_FACES, id) ? FONT_FACES[id as FontFaceId] : undefined
}

export function fontStack(fonts: ThemeFonts, font: InvitationFont): string {
  return font === 'serif' ? face(fonts.serif)?.stack ?? FONT_STACKS.serif : FONT_STACKS[font]
}

/** The script face the page loaded (pageLook picks one whenever the seal shows initials), quoted for CSS/SVG. */
export function scriptFamily(fonts: ThemeFonts): string {
  return face(fonts.script)?.family ?? FONT_FACES['pinyon-script'].family
}

/** Custom properties for the decorative faces; the CSS supplies fallbacks when unset. */
export function fontVars(fonts: ThemeFonts): Record<string, string> {
  const vars: Record<string, string> = {}
  const script = face(fonts.script), display = face(fonts.display)
  if (script) vars['--theme-script'] = script.family
  if (display) vars['--theme-display'] = display.family
  return vars
}

const pending = new Map<string, Promise<unknown>>()

function once(key: string, loader: () => Promise<unknown>): Promise<unknown> {
  let load = pending.get(key)
  if (!load) {
    // A failed chunk falls back to the system stack in FONT_STACKS; allow a later retry.
    load = loader().catch(() => pending.delete(key))
    pending.set(key, load)
  }
  return load
}

export function loadInvitationFont(font: InvitationFont, fonts: ThemeFonts = {}): Promise<unknown> {
  const ids = [fonts.script, fonts.display, font === 'serif' ? fonts.serif : undefined]
  const loads = ids.flatMap(id => { const f = face(id); return f ? [once(id!, f.load)] : [] })
  // The theme's own serif replaces the shared one, so skip downloading both.
  if (!(font === 'serif' && face(fonts.serif))) loads.push(once(font, loaders[font]))
  return Promise.all(loads)
}
