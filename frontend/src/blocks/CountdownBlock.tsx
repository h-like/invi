import { useEffect, useState } from 'react'
import type { Block } from '../api/types'

interface Props {
  block: Block
  weddingDate: string
}

export function CountdownBlock({ block, weddingDate }: Props) {
  const style = block.style as { accentColor?: string }
  const [daysLeft, setDaysLeft] = useState(() => calcDaysLeft(weddingDate))

  useEffect(() => {
    setDaysLeft(calcDaysLeft(weddingDate))
    const timer = setInterval(() => setDaysLeft(calcDaysLeft(weddingDate)), 60_000)
    return () => clearInterval(timer)
  }, [weddingDate])

  return (
    <section className="block countdown-block">
      <p style={{ color: style.accentColor }}>{formatDaysLeft(daysLeft)}</p>
    </section>
  )
}

function calcDaysLeft(weddingDate: string): number {
  if (!weddingDate) return NaN
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const target = new Date(weddingDate)
  target.setHours(0, 0, 0, 0)
  return Math.round((target.getTime() - today.getTime()) / 86_400_000)
}

function formatDaysLeft(daysLeft: number): string {
  if (Number.isNaN(daysLeft)) return '결혼식 날짜를 입력해주세요'
  if (daysLeft > 0) return `결혼식까지 D-${daysLeft}`
  if (daysLeft === 0) return '오늘이 결혼식입니다'
  return '결혼식을 마쳤습니다'
}
