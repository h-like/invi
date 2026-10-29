import type { Block, HeroLayout } from '../api/types'
import { isImageUrl } from '../editor/model'

export function HeroBlock({ block, weddingDate, layout }: { block: Block; weddingDate: string; layout: HeroLayout }) {
  const text = (key: string) => typeof block.content[key] === 'string' ? block.content[key] as string : ''
  const date = weddingDate ? new Date(weddingDate + 'T12:00:00') : null
  const validDate = date && !Number.isNaN(date.getTime()) ? date : null
  const dateLabel = validDate ? new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(validDate) : '결혼식 날짜'
  const time = /^([01]\d|2[0-3]):[0-5]\d$/.test(text('weddingTime')) ? text('weddingTime') : ''
  const groom = text('groomName') || '신랑'
  const bride = text('brideName') || '신부'
  const cover = isImageUrl(text('coverImage')) ? text('coverImage') : ''
  const greeting = <p className="hero-greeting">{text('greeting') || '소중한 여러분을 저희의 결혼식에 초대합니다.'}</p>

  if (layout === 'photo-full') {
    // Without a cover photo the overlay would float on nothing, so a tinted panel stands in.
    return (
      <section className="block hero-block hero-layout-photo-full">
        <div className={'hero-photo' + (cover ? '' : ' hero-photo-empty')}>
          {cover && <img className="hero-photo-image" src={cover} alt="두 사람의 웨딩 사진" />}
          <div className="hero-photo-caption">
            <p className="invitation-eyebrow">WE ARE GETTING MARRIED</p>
            <h1 className="hero-names"><span>{groom}</span><span className="hero-ampersand">&amp;</span><span>{bride}</span></h1>
            <p className="hero-date">{dateLabel}{time ? ' · ' + time : ''}</p>
          </div>
        </div>
        <div className="hero-photo-body">{greeting}</div>
      </section>
    )
  }

  if (layout === 'typographic') {
    const numeric = validDate ? [validDate.getFullYear(), validDate.getMonth() + 1, validDate.getDate()].map(n => String(n).padStart(2, '0')).join('.') : ''
    return (
      <section className="block hero-block hero-layout-typographic">
        <p className="invitation-eyebrow">WE ARE GETTING MARRIED</p>
        <h1 className="hero-names hero-names-stacked"><span>{groom}</span><span className="hero-ampersand">&amp;</span><span>{bride}</span></h1>
        <p className="hero-date">{numeric && <strong className="hero-date-numeric">{numeric}</strong>}<span>{dateLabel}{time ? ' · ' + time : ''}</span></p>
        {cover && <img className="hero-cover hero-cover-inset" src={cover} alt="두 사람의 웨딩 사진" />}
        {greeting}
      </section>
    )
  }

  return (
    <section className="block hero-block hero-layout-frame">
      <div className="hero-frame">
        <p className="invitation-eyebrow">WE ARE GETTING MARRIED</p>
        <p className="hero-date">{dateLabel}{time ? ' · ' + time : ''}</p>
        <h1 className="hero-names"><span>{groom}</span><span className="hero-ampersand">&amp;</span><span>{bride}</span></h1>
        {cover && <img className="hero-cover" src={cover} alt="두 사람의 웨딩 사진" />}
        <p className="invitation-eyebrow hero-invitation-label">INVITATION</p>
        {greeting}
      </div>
    </section>
  )
}
