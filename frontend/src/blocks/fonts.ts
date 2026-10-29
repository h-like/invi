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

const pending = new Map<InvitationFont, Promise<unknown>>()

export function loadInvitationFont(font: InvitationFont): Promise<unknown> {
  let load = pending.get(font)
  if (!load) {
    // A failed chunk falls back to the system stack in FONT_STACKS; allow a later retry.
    load = loaders[font]().catch(() => pending.delete(font))
    pending.set(font, load)
  }
  return load
}
