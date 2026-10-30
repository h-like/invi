import { useState } from 'react'
import type { Block } from '../api/types'

// Search links need no map API key or SDK; the address is the most precise query.
const MAP_LINKS = [
  { name: '네이버 지도', url: (q: string) => 'https://map.naver.com/p/search/' + encodeURIComponent(q) },
  { name: '카카오맵', url: (q: string) => 'https://map.kakao.com/link/search/' + encodeURIComponent(q) },
]

export function MapBlock({ block, interactive }: { block: Block; interactive: boolean }) {
  const text = (key: string) => typeof block.content[key] === 'string' ? block.content[key] as string : ''
  const address = text('address').trim()
  const [copied, setCopied] = useState<boolean | null>(null)
  async function copyAddress() {
    try { await navigator.clipboard.writeText(address); setCopied(true) } catch { setCopied(false) }
    setTimeout(() => setCopied(null), 1800)
  }
  return (
    <section className="block map-block">
      <p className="invitation-eyebrow">LOCATION</p><h3>오시는 길</h3>
      <p className="venue-name">{text('venueName') || '식장 이름을 입력해 주세요'}</p>
      <p className="block-sub">{address || '주소를 입력해 주세요'}</p>
      {address && (
        <div className="map-links">
          {MAP_LINKS.map(link => interactive
            ? <a key={link.name} className="map-link" href={link.url(address)} target="_blank" rel="noopener noreferrer">{link.name}</a>
            : <span key={link.name} className="map-link">{link.name}</span>)}
          {interactive
            ? <button type="button" className="map-link" onClick={copyAddress}>{copied === null ? '주소 복사' : copied ? '복사됨' : '복사 안 됨'}</button>
            : <span className="map-link">주소 복사</span>}
        </div>
      )}
      {text('directions') && <p className="venue-directions">{text('directions')}</p>}
    </section>
  )
}
