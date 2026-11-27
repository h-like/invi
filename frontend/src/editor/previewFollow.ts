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

/** Scrolls `container` to the preview block `blockId` if it is rendered; returns whether it did. */
export function followPreviewBlock(container: HTMLElement, blockId: string, last: string | null): boolean {
  const target = container.querySelector<HTMLElement>(`[data-block-id="${CSS.escape(blockId)}"]`)
  if (!target) return false // hidden blocks are not rendered in the preview
  const r = target.getBoundingClientRect()
  const c = container.getBoundingClientRect()
  const block = { top: r.top, bottom: r.bottom }
  const view = { top: c.top, bottom: c.bottom }
  if (!shouldFollow(last, blockId, block, view)) return false
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  container.scrollTo({ top: followScrollTop(container.scrollTop, block, view, container.scrollHeight - container.clientHeight), behavior: reduce ? 'auto' : 'smooth' })
  return true
}
