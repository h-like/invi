import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { GuestbookEntryItem, RsvpEntry } from '../api/types'

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

  const attendingCount = rsvps
    ?.filter((r) => r.attending)
    .reduce((sum, r) => sum + r.guestCount, 0)

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
        <h2>참석 응답{rsvps ? ` (${rsvps.length}건, 참석 ${attendingCount}명)` : ''}</h2>
        {!rsvps ? (
          <p className="block-placeholder">불러오는 중...</p>
        ) : rsvps.length === 0 ? (
          <p className="block-placeholder">아직 응답이 없습니다.</p>
        ) : (
          <ul className="guestbook-list">
            {rsvps.map((rsvp) => (
              <li key={rsvp.id}>
                <strong>{rsvp.guestName}</strong> — {rsvp.attending ? `참석 (${rsvp.guestCount}명)` : '불참'}
                {rsvp.message && <p>{rsvp.message}</p>}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="editor-section">
        <h2>방명록{guestbook ? ` (${guestbook.length}건)` : ''}</h2>
        {!guestbook ? (
          <p className="block-placeholder">불러오는 중...</p>
        ) : guestbook.length === 0 ? (
          <p className="block-placeholder">아직 남겨진 방명록이 없습니다.</p>
        ) : (
          <ul className="guestbook-list">
            {guestbook.map((entry) => (
              <li key={entry.id} className="account-row">
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
