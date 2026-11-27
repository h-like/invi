import { useRef, useState, type KeyboardEvent } from 'react'
import { api } from '../api/client'
import { isVerifiedAddress, kakaoPlaceUrl, readMapPlace, type PlaceSearchResult } from '../blocks/mapPlace'

type Scope = 'venue' | 'all'

/**
 * Finds the real venue so the address guests get is the map provider's, not a hand-typed one.
 * Search starts with wedding venues only (the server filters); for ceremonies held elsewhere the
 * couple can widen it to every place, or as a last resort type the address with a warning.
 */
export function VenueSearch({ content, onPick, onManualAddress }: {
  content: Record<string, unknown>
  onPick: (result: PlaceSearchResult) => void
  onManualAddress: (address: string) => void
}) {
  const place = readMapPlace(content)
  const verified = isVerifiedAddress(content)
  const address = typeof content.address === 'string' ? content.address : ''
  const [open, setOpen] = useState(!verified)
  const [manual, setManual] = useState(!verified && address.trim() !== '')
  const [query, setQuery] = useState('')
  const [scope, setScope] = useState<Scope>('venue')
  const [results, setResults] = useState<PlaceSearchResult[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const seq = useRef(0)

  async function search(nextScope: Scope) {
    const q = query.trim()
    if (q.length < 2) { setError('장소 이름을 두 글자 이상 입력해 주세요.'); return }
    const id = ++seq.current
    setLoading(true); setError(null); setScope(nextScope)
    try {
      const found = await api.searchPlaces(q, nextScope)
      if (id === seq.current) setResults(found)
    } catch (e) {
      if (id === seq.current) { setResults(null); setError(e instanceof Error ? e.message : '검색하지 못했습니다.') }
    } finally {
      if (id === seq.current) setLoading(false)
    }
  }
  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' && !e.nativeEvent.isComposing) { e.preventDefault(); void search('venue') }
  }
  function pick(result: PlaceSearchResult) {
    onPick(result)
    setOpen(false); setManual(false); setResults(null); setQuery(''); setError(null); setScope('venue')
  }
  function typeAddress() {
    onManualAddress(address)
    setManual(true); setOpen(true)
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
      {manual && !verified && (
        <div className="venue-manual">
          <label>주소 (직접 입력)<input value={address} maxLength={300} onChange={e => onManualAddress(e.target.value)} placeholder="도로명 주소를 정확히 입력해 주세요" /></label>
          <p className="venue-unverified" role="status">검색으로 확인하지 않은 주소예요. 하객의 지도 버튼은 이 글자로 검색하니 오타가 없는지 꼭 확인해 주세요. 가능하면 아래에서 장소를 검색해 선택해 주세요.</p>
        </div>
      )}
      {open && (
        <>
          <div className="venue-search-row">
            <input type="search" value={query} maxLength={50} onChange={e => setQuery(e.target.value)} onKeyDown={onKeyDown}
              placeholder="예식장·호텔 이름 (예: 더채플앳청담)" aria-label="예식장 검색" enterKeyHint="search" />
            <button type="button" className="secondary-button" disabled={loading} onClick={() => void search('venue')}>{loading ? '검색 중' : '검색'}</button>
            {verified && <button type="button" className="secondary-button" onClick={() => { setOpen(false); setResults(null); setError(null) }}>취소</button>}
          </div>
          <p className="editor-section-hint">{scope === 'venue' ? '웨딩홀·호텔·컨벤션 등 예식 장소를 먼저 찾아 드려요.' : '예식장이 아닌 장소까지 모두 찾았어요. 주소를 꼭 확인해 주세요.'}</p>
          {error && <p className="venue-search-error" role="alert">{error}</p>}
          {results && (results.length === 0
            ? <p className="editor-section-hint" role="status">{scope === 'venue' ? '검색된 예식 장소가 없어요.' : '검색된 장소가 없어요.'} 지점명이나 지역을 함께 입력해 보세요 (예: 롯데호텔 잠실).</p>
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
          {(results || error) && (
            <div className="venue-fallback">
              <span>{results ? '찾는 곳이 없나요?' : '검색이 안 되면'}</span>
              {results && scope === 'venue' && <button type="button" disabled={loading} onClick={() => void search('all')}>모든 장소에서 찾기 (성당·레스토랑 등)</button>}
              {!manual && <button type="button" onClick={typeAddress}>주소 직접 입력</button>}
            </div>
          )}
        </>
      )}
    </div>
  )
}
