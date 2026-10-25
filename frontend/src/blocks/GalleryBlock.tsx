import type { Block } from '../api/types'
import { isImageUrl, MAX_GALLERY_IMAGES } from '../editor/model'

export function GalleryBlock({ block }: { block: Block }) {
  const images = Array.isArray(block.content.images) ? block.content.images.filter(isImageUrl).slice(0, MAX_GALLERY_IMAGES) : []
  const layout = ['grid-3', 'slider'].includes(String(block.content.layout)) ? String(block.content.layout) : 'grid-2'
  return (
    <section className="block gallery-block">
      <p className="invitation-eyebrow">OUR MOMENTS</p><h3>우리의 순간</h3>
      {images.length === 0 ? <p className="block-placeholder">사진을 아직 추가하지 않았습니다</p> : <>
        <div className={'gallery-grid gallery-' + layout} tabIndex={layout === 'slider' ? 0 : undefined} aria-label={layout === 'slider' ? '웨딩 사진, 좌우로 스크롤할 수 있습니다' : '웨딩 사진'}>
          {images.map((src, i) => <img key={i + '-' + src} src={src} alt={'웨딩 사진 ' + (i + 1)} loading="lazy" />)}
        </div>
        {layout === 'slider' && <p className="block-sub">좌우로 넘겨 사진을 감상해 주세요.</p>}
      </>}
    </section>
  )
}
