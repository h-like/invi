import type { Block } from '../api/types'

export function MapBlock({ block }: { block: Block }) {
  const text = (key: string) => typeof block.content[key] === 'string' ? block.content[key] as string : ''
  return (
    <section className="block map-block">
      <p className="invitation-eyebrow">LOCATION</p><h3>오시는 길</h3>
      <p>{text('venueName') || '식장 이름을 입력해 주세요'}</p>
      <p className="block-sub">{text('address') || '주소를 입력해 주세요'}</p>
      {text('directions') && <p className="venue-directions">{text('directions')}</p>}
    </section>
  )
}
