import type { Block } from '../api/types'

interface Props {
  block: Block
  weddingDate: string
}

export function HeroBlock({ block, weddingDate }: Props) {
  const content = block.content as { groomName?: string; brideName?: string }
  const style = block.style as { bgColor?: string; accentColor?: string }

  return (
    <section className="block hero-block" style={{ background: style.bgColor }}>
      <p className="hero-date">{weddingDate || '결혼식 날짜'}</p>
      <h1 className="hero-names" style={{ color: style.accentColor }}>
        {content.groomName || '신랑'} &amp; {content.brideName || '신부'}
      </h1>
    </section>
  )
}
