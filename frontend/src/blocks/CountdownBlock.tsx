import { useEffect, useState } from 'react'
import type { Block } from '../api/types'
import { calendarWeeks, countdownParts, parseWeddingMoment, WEDDING_TIME_ZONE } from './calendar'

interface Props {
  block: Block
  weddingDate: string
  weddingTime: string
}

const MONTHS = ['JANUARY', 'FEBRUARY', 'MARCH', 'APRIL', 'MAY', 'JUNE', 'JULY', 'AUGUST', 'SEPTEMBER', 'OCTOBER', 'NOVEMBER', 'DECEMBER']
const WEEKDAYS = ['S', 'M', 'T', 'W', 'T', 'F', 'S']

export function CountdownBlock({ block, weddingDate, weddingTime }: Props) {
  const style = block.style as { accentColor?: string }
  const moment = parseWeddingMoment(weddingDate, weddingTime)
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  if (!moment) {
    return (
      <section className="block countdown-block">
        <p style={{ color: style.accentColor }}>결혼식 날짜를 입력해주세요</p>
      </section>
    )
  }

  const { year, month, day } = moment
  const parts = countdownParts(moment.at, now)
  const label = new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long', timeZone: WEDDING_TIME_ZONE }).format(moment.at)
  const time = moment.hasTime ? ' ' + new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit', timeZone: WEDDING_TIME_ZONE }).format(moment.at) : ''
  return (
    <section className="block countdown-block">
      <p className="invitation-eyebrow">WEDDING DAY</p><h3>예식 일시</h3>
      <p className="countdown-when">{label}{time}</p>
      <p className="calendar-title" aria-hidden="true">{MONTHS[month - 1]}</p>
      <table className="calendar" aria-label={`${year}년 ${month}월 달력, ${day}일 예식`}>
        <thead><tr>{WEEKDAYS.map((d, i) => <th key={i} scope="col" className={i === 0 ? 'is-sunday' : undefined}>{d}</th>)}</tr></thead>
        <tbody>
          {calendarWeeks(year, month).map((week, w) => (
            <tr key={w}>{week.map((d, i) => (
              <td key={i} className={[i === 0 && 'is-sunday', d === day && 'is-wedding-day'].filter(Boolean).join(' ') || undefined} aria-current={d === day ? 'date' : undefined}>
                {d && <span>{d}</span>}
              </td>
            ))}</tr>
          ))}
        </tbody>
      </table>
      {parts.daysLeft >= 0 && (
        <div className="countdown-clock" aria-hidden="true">
          {[['DAYS', parts.days], ['HOUR', parts.hours], ['MIN', parts.minutes], ['SEC', parts.seconds]].map(([unit, value]) => (
            <div key={unit}><b>{unit === 'DAYS' ? value : String(value).padStart(2, '0')}</b><small>{unit}</small></div>
          ))}
        </div>
      )}
      <p className="countdown-message" style={{ color: style.accentColor }}>{formatDaysLeft(parts.daysLeft)}</p>
    </section>
  )
}

function formatDaysLeft(daysLeft: number): string {
  if (daysLeft > 0) return `결혼식까지 D-${daysLeft}`
  if (daysLeft === 0) return '오늘이 결혼식입니다'
  return '결혼식을 마쳤습니다'
}
