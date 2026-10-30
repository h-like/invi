import type { DesignTheme, InvitationFont } from '../api/types.ts'

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

// A theme may bring its own serif body face and decorative Latin faces. They load only
// with that theme; "sans" stays the user's override. Latin-only faces use the latin
// subset since they never render Hangul.
const themeLoaders: Record<string, () => Promise<unknown>> = {
  seal: () => Promise.all([
    import('@fontsource/gowun-batang/400.css'), import('@fontsource/gowun-batang/700.css'),
    import('@fontsource/pinyon-script/latin-400.css'), import('@fontsource/cormorant-garamond/latin-600.css'),
  ]),
}

const THEME_SERIF_STACKS: Record<string, string> = {
  seal: '"Gowun Batang", "Noto Serif KR", "AppleMyungjo", "Batang", serif',
}

export function fontStack(theme: DesignTheme, font: InvitationFont): string {
  return font === 'serif' ? THEME_SERIF_STACKS[theme] ?? FONT_STACKS.serif : FONT_STACKS[font]
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

export function loadInvitationFont(font: InvitationFont, theme?: DesignTheme): Promise<unknown> {
  const themeLoader = theme ? themeLoaders[theme] : undefined
  if (themeLoader) {
    const decorative = once('theme:' + theme, themeLoader)
    // The theme's own serif replaces the shared one, so skip downloading both.
    return font === 'serif' ? decorative : Promise.all([decorative, once(font, loaders[font])])
  }
  return once(font, loaders[font])
}
