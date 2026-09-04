import type { Block } from '../api/types'

export function MapBlock({ block }: { block: Block }) {
  const content = block.content as { venueName?: string; address?: string }

  return (
    <section className="block map-block">
      <h3>오시는 길</h3>
      <p>{content.venueName || '식장 이름을 입력해주세요'}</p>
      <p className="block-sub">{content.address || '주소를 입력해주세요'}</p>
    </section>
  )
}
