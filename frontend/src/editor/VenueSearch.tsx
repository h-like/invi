import { useRef, useState, type KeyboardEvent } from 'react'
import { api } from '../api/client'
import { isVerifiedAddress, kakaoPlaceUrl, readMapPlace, type PlaceSearchResult } from '../blocks/mapPlace'

/**
 * Finds the real venue (wedding hall, hotel, convention hall) so the address guests get is the
 * map provider's, not a hand-typed one. The server limits results to venues.
 */
export function VenueSearch({ content, onPick }: { content: Record<string, unknown>; onPick: (result: PlaceSearchResult) => void }) {
  const place = readMapPlace(content)
  const verified = isVerifiedAddress(content)
  const typedAddress = typeof content.address === 'string' ? content.address.trim() : ''
  const [open, setOpen] = useState(!verified)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<PlaceSearchResult[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const seq = useRef(0)

  async function search() {
    const q = query.trim()
    if (q.length < 2) { setError('예식장 이름을 두 글자 이상 입력해 주세요.'); return }
    const id = ++seq.current
    setLoading(true); setError(null)
    try {
      const found = await api.searchPlaces(q)
      if (id === seq.current) setResults(found)
    } catch (e) {
      if (id === seq.current) { setResults(null); setError(e instanceof Error ? e.message : '검색하지 못했습니다.') }
    } finally {
      if (id === seq.current) setLoading(false)
    }
  }
  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' && !e.nativeEvent.isComposing) { e.preventDefault(); void search() }
  }
  function pick(result: PlaceSearchResult) {
    onPick(result)
    setOpen(false); setResults(null); setQuery(''); setError(null)
  }

  return (
    <div className="venue-search">
      {verified && place && (
        <div className="venue-picked">
          <div>
            <strong>{place.name}</strong>
            <span>{place.address}</span>
          </div>
          <div className="venue-picked-actions">
            <a href={kakaoPlaceUrl(place)} target="_blank" rel="noopener noreferrer">지도에서 위치 확인</a>
            {!open && <button type="button" onClick={() => setOpen(true)}>다른 장소 찾기</button>}
          </div>
        </div>
      )}
      {!verified && typedAddress && (
        <p className="venue-unverified" role="status">직접 입력한 주소 “{typedAddress}”는 위치를 확인할 수 없어요. 아래에서 예식장을 검색해 선택해 주세요.</p>
      )}
      {open && (
        <>
          <div className="venue-search-row">
            <input type="search" value={query} maxLength={50} onChange={e => setQuery(e.target.value)} onKeyDown={onKeyDown}
              placeholder="예식장·호텔 이름 (예: 더채플앳청담)" aria-label="예식장 검색" enterKeyHint="search" />
            <button type="button" className="secondary-button" disabled={loading} onClick={() => void search()}>{loading ? '검색 중' : '검색'}</button>
            {verified && <button type="button" className="secondary-button" onClick={() => { setOpen(false); setResults(null); setError(null) }}>취소</button>}
          </div>
          <p className="editor-section-hint">웨딩홀·호텔·컨벤션 등 실제 예식 장소만 검색됩니다.</p>
          {error && <p className="venue-search-error" role="alert">{error}</p>}
          {results && (results.length === 0
            ? <p className="editor-section-hint" role="status">검색된 예식 장소가 없어요. 지점명이나 지역을 함께 입력해 보세요 (예: 롯데호텔 잠실).</p>
            : <ul className="venue-results" aria-label="검색 결과">
                {results.map(r => (
                  <li key={r.id}>
                    <button type="button" onClick={() => pick(r)}>
                      <strong>{r.name}</strong><small>{r.category}</small>
                      <span>{r.roadAddress ?? r.address}</span>
                    </button>
                  </li>
                ))}
              </ul>)}
        </>
      )}
    </div>
  )
}
