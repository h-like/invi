// "미리보기 따라가기": focusing a field in an editor section scrolls the live preview to the
// block that section edits. Only the preview's own scroll box moves — never the window — so
// on narrow screens, where the preview sits below the form, the page does not jump away.

export type Span = { top: number; bottom: number }

/** Gap kept above the block so its edge is not flush with the preview frame. */
export const FOLLOW_MARGIN = 12

/**
 * Whether to scroll for this focus. A new block always scrolls; moving between fields of the
 * same block scrolls only once the block has left the view, so a reader who scrolled within a
 * tall block (the hero) is not yanked back to its top on every field.
 */
export function shouldFollow(last: string | null, next: string, block: Span, view: Span): boolean {
  if (next !== last) return true
  return block.bottom <= view.top || block.top >= view.bottom
}

/** scrollTop that puts the block's top FOLLOW_MARGIN below the top of the view. */
export function followScrollTop(scrollTop: number, block: Span, view: Span, maxScrollTop: number): number {
  const target = scrollTop + (block.top - view.top) - FOLLOW_MARGIN
  return Math.round(Math.min(Math.max(target, 0), Math.max(maxScrollTop, 0)))
}

type Box = { getBoundingClientRect(): Span; dataset: { blockId?: string; previewAnchor?: string } }
type Elements<T> = ArrayLike<T> & Iterable<T>

/** The parts of the preview scroll box this module touches (an HTMLElement satisfies it). */
export type FollowContainer = Box & {
  scrollTop: number
  scrollHeight: number
  clientHeight: number
  querySelectorAll(selector: '[data-block-id]'): Elements<Box & { querySelectorAll(selector: '[data-preview-anchor]'): Elements<Box> }>
  scrollTo(options: { top: number; behavior: 'auto' | 'smooth' }): void
}

/**
 * Scrolls `container` to the preview block `blockId` — or, inside it, to the first of `anchors`
 * that is rendered (a section of a tall block, e.g. the hero's greeting). Returns what is now
 * followed, to pass back as `last`; an unrendered (hidden) block leaves `last` unchanged.
 */
export function followPreviewBlock(container: FollowContainer, blockId: string, anchors: readonly string[], last: string | null): string | null {
  const block = [...container.querySelectorAll('[data-block-id]')].find(el => el.dataset.blockId === blockId)
  if (!block) return last // hidden blocks are not rendered in the preview
  const marks = [...block.querySelectorAll('[data-preview-anchor]')]
  const anchor = anchors.find(a => marks.some(m => m.dataset.previewAnchor === a))
  const target = anchor ? marks.find(m => m.dataset.previewAnchor === anchor)! : block
  const key = anchor ? blockId + '#' + anchor : blockId
  const r = target.getBoundingClientRect()
  const c = container.getBoundingClientRect()
  const span = { top: r.top, bottom: r.bottom }
  const view = { top: c.top, bottom: c.bottom }
  if (!shouldFollow(last, key, span, view)) return key
  const reduce = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  container.scrollTo({ top: followScrollTop(container.scrollTop, span, view, container.scrollHeight - container.clientHeight), behavior: reduce ? 'auto' : 'smooth' })
  return key
}
