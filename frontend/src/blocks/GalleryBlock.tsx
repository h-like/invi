import type { Block } from '../api/types'

export function GalleryBlock({ block }: { block: Block }) {
  const content = block.content as { images?: string[] }
  const images = content.images ?? []

  if (images.length === 0) {
    return (
      <section className="block gallery-block">
        <p className="block-placeholder">사진을 아직 추가하지 않았습니다</p>
      </section>
    )
  }

  return (
    <section className="block gallery-block">
      <div className="gallery-grid">
        {images.map((src) => (
          <img key={src} src={src} alt="" />
        ))}
      </div>
    </section>
  )
}
