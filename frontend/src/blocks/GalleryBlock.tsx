import { useEffect, useRef, useState } from 'react'
import type { Block } from '../api/types'
import { isImageUrl, MAX_GALLERY_IMAGES } from '../editor/model'

export function GalleryBlock({ block, interactive }: { block: Block; interactive: boolean }) {
  const images = Array.isArray(block.content.images) ? block.content.images.filter(isImageUrl).slice(0, MAX_GALLERY_IMAGES) : []
  const layout = ['grid-3', 'slider'].includes(String(block.content.layout)) ? String(block.content.layout) : 'grid-2'
  // The lightbox covers the whole window, so editor and template previews keep plain images.
  const [open, setOpen] = useState<number | null>(null)
  return (
    <section className="block gallery-block">
      <p className="invitation-eyebrow">OUR MOMENTS</p><h3>우리의 순간</h3>
      {images.length === 0 ? <p className="block-placeholder">사진을 아직 추가하지 않았습니다</p> : <>
        <div className={'gallery-grid gallery-' + layout} tabIndex={layout === 'slider' ? 0 : undefined} aria-label={layout === 'slider' ? '웨딩 사진, 좌우로 스크롤할 수 있습니다' : '웨딩 사진'}>
          {images.map((src, i) => interactive
            ? <button key={i + '-' + src} type="button" className="gallery-open" onClick={() => setOpen(i)} aria-label={`웨딩 사진 ${i + 1} 크게 보기`}><img src={src} alt="" loading="lazy" /></button>
            : <img key={i + '-' + src} src={src} alt={'웨딩 사진 ' + (i + 1)} loading="lazy" />)}
        </div>
        {layout === 'slider' && <p className="block-sub">좌우로 넘겨 사진을 감상해 주세요.</p>}
        {interactive && layout !== 'slider' && <p className="block-sub">사진을 누르면 크게 볼 수 있어요.</p>}
      </>}
      {open !== null && images.length > 0 && <Lightbox images={images} start={Math.min(open, images.length - 1)} onClose={() => setOpen(null)} />}
    </section>
  )
}

function Lightbox({ images, start, onClose }: { images: string[]; start: number; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [index, setIndex] = useState(start)
  const swipeStart = useRef<number | null>(null)
  const go = (step: number) => setIndex(i => (i + step + images.length) % images.length)

  useEffect(() => {
    const el = dialog.current
    // A modal <dialog> gives focus trapping, Escape and focus return for free.
    const opener = document.activeElement as HTMLElement | null
    if (el && !el.open) el.showModal()
    document.documentElement.classList.add('invite-modal-open')
    // No close() here: it queues a `close` event that would call onClose, so a StrictMode
    // remount would shut the viewer right after opening. Unmounting removes the dialog,
    // so focus goes back to the photo by hand.
    return () => {
      document.documentElement.classList.remove('invite-modal-open')
      opener?.focus({ preventScroll: true })
    }
  }, [])

  return (
    <dialog
      ref={dialog}
      className="gallery-lightbox"
      aria-label="사진 크게 보기"
      onClose={onClose}
      onKeyDown={e => { if (e.key === 'ArrowLeft') go(-1); if (e.key === 'ArrowRight') go(1) }}
      onPointerDown={e => { swipeStart.current = e.clientX }}
      onPointerUp={e => {
        if (swipeStart.current === null) return
        const dx = e.clientX - swipeStart.current
        swipeStart.current = null
        if (Math.abs(dx) > 40) go(dx < 0 ? 1 : -1)
      }}
    >
      <button type="button" className="lightbox-close" onClick={() => dialog.current?.close()} aria-label="닫기">✕</button>
      <img src={images[index]} alt={`웨딩 사진 ${index + 1}`} draggable={false} />
      {images.length > 1 && (
        <div className="lightbox-bar">
          <button type="button" onClick={() => go(-1)} aria-label="이전 사진">‹</button>
          <span aria-live="polite">{index + 1} / {images.length}</span>
          <button type="button" onClick={() => go(1)} aria-label="다음 사진">›</button>
        </div>
      )}
    </dialog>
  )
}
