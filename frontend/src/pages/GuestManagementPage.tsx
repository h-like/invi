import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { GuestbookEntryItem, RsvpEntry } from '../api/types'
import { mealLabel, sideLabel, summarizeRsvps } from '../blocks/rsvpOptions'

export function GuestManagementPage() {
  const { id } = useParams<{ id: string }>()
  const [rsvps, setRsvps] = useState<RsvpEntry[] | null>(null)
  const [guestbook, setGuestbook] = useState<GuestbookEntryItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    Promise.all([api.listRsvps(id), api.listGuestbook(id)])
      .then(([rsvpList, guestbookList]) => {
        setRsvps(rsvpList)
        setGuestbook(guestbookList)
      })
      .catch((err) => setError(err instanceof Error ? err.message : '목록을 불러오지 못했습니다'))
  }, [id])

  async function handleDeleteGuestbookEntry(entryId: string) {
    if (!id) return
    try {
      await api.deleteGuestbookEntry(id, entryId)
      setGuestbook((prev) => prev?.filter((entry) => entry.id !== entryId) ?? null)
    } catch (err) {
      setError(err instanceof Error ? err.message : '삭제에 실패했습니다')
    }
  }

  const summary = rsvps ? summarizeRsvps(rsvps) : null

  return (
    <main className="page">
      <div className="page-header">
        <h1>응답 관리</h1>
        <Link to={`/e/${id}`} className="logout-button">
          에디터로 돌아가기
        </Link>
      </div>

      {error && <p className="form-error">{error}</p>}

      <section className="editor-section" style={{ borderTop: 'none', paddingTop: 0 }}>
        <h2>참석 응답{summary ? ` (${summary.total.responses}건, 참석 ${summary.total.guests}명)` : ''}</h2>
        {summary && summary.total.responses > 0 && (
          <dl className="rsvp-summary">
            <div><dt>신랑측</dt><dd>{summary.side.GROOM.responses}건 · 참석 {summary.side.GROOM.guests}명</dd></div>
            <div><dt>신부측</dt><dd>{summary.side.BRIDE.responses}건 · 참석 {summary.side.BRIDE.guests}명</dd></div>
            {summary.side.unknown.responses > 0 && <div><dt>구분 없음</dt><dd>{summary.side.unknown.responses}건 · 참석 {summary.side.unknown.guests}명</dd></div>}
            <div className="rsvp-summary-wide"><dt>식사 (참석 인원)</dt><dd>예정 {summary.meal.YES}명 · 안 함 {summary.meal.NO}명 · 미정 {summary.meal.UNDECIDED}명{summary.meal.unknown > 0 ? ` · 응답 없음 ${summary.meal.unknown}명` : ''}</dd></div>
          </dl>
        )}
        {!rsvps ? (
          <p className="guest-admin-empty">불러오는 중...</p>
        ) : rsvps.length === 0 ? (
          <p className="guest-admin-empty">아직 응답이 없습니다.</p>
        ) : (
          <ul className="guest-admin-list">
            {rsvps.map((rsvp) => (
              <li key={rsvp.id}>
                <strong>{rsvp.guestName}</strong>{rsvp.side && <span className="rsvp-tag">{sideLabel(rsvp.side)}</span>} — {rsvp.attending ? `참석 (${rsvp.guestCount}명)` : '불참'}
                {rsvp.attending && rsvp.meal && ` · ${mealLabel(rsvp.meal)}`}
                {rsvp.message && <p>{rsvp.message}</p>}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="editor-section">
        <h2>방명록{guestbook ? ` (${guestbook.length}건)` : ''}</h2>
        {!guestbook ? (
          <p className="guest-admin-empty">불러오는 중...</p>
        ) : guestbook.length === 0 ? (
          <p className="guest-admin-empty">아직 남겨진 방명록이 없습니다.</p>
        ) : (
          <ul className="guest-admin-list">
            {guestbook.map((entry) => (
              <li key={entry.id} className="guest-admin-row">
                <div>
                  <strong>{entry.author}</strong>
                  <p>{entry.message}</p>
                </div>
                <button type="button" onClick={() => handleDeleteGuestbookEntry(entry.id)}>
                  삭제
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}
