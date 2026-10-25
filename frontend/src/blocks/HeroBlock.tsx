import type { Block } from '../api/types'
import { isImageUrl } from '../editor/model'

export function HeroBlock({ block, weddingDate }: { block: Block; weddingDate: string }) {
  const text = (key: string) => typeof block.content[key] === 'string' ? block.content[key] as string : ''
  const date = weddingDate ? new Date(weddingDate + 'T12:00:00') : null
  const dateLabel = date && !Number.isNaN(date.getTime()) ? new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(date) : '결혼식 날짜'
  const time = /^([01]\d|2[0-3]):[0-5]\d$/.test(text('weddingTime')) ? text('weddingTime') : ''
  return (
    <section className="block hero-block">
      <div className="hero-frame">
        <p className="invitation-eyebrow">WE ARE GETTING MARRIED</p>
        <p className="hero-date">{dateLabel}{time ? ' · ' + time : ''}</p>
        <h1 className="hero-names"><span>{text('groomName') || '신랑'}</span><span className="hero-ampersand">&amp;</span><span>{text('brideName') || '신부'}</span></h1>
        {isImageUrl(text('coverImage')) && <img className="hero-cover" src={text('coverImage')} alt="두 사람의 웨딩 사진" />}
        <p className="invitation-eyebrow hero-invitation-label">INVITATION</p>
        <p className="hero-greeting">{text('greeting') || '소중한 여러분을 저희의 결혼식에 초대합니다.'}</p>
      </div>
    </section>
  )
}
