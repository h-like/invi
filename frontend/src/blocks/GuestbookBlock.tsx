import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../api/client'
import type { GuestbookEntryItem } from '../api/types'
import { GUEST_NAME_MAX, GUESTBOOK_MESSAGE_MAX } from './guestLimits'

interface Props {
  invitationId: string
  interactive: boolean
}

export function GuestbookBlock({ invitationId, interactive }: Props) {
  const [entries, setEntries] = useState<GuestbookEntryItem[]>([])
  const [author, setAuthor] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!interactive) return
    api
      .listGuestbook(invitationId)
      .then(setEntries)
      .catch(() => {})
  }, [invitationId, interactive])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!interactive || busy) return
    setError(null)
    setBusy(true)
    try {
      const entry = await api.submitGuestbook(invitationId, { author, message })
      setEntries((prev) => [entry, ...prev])
      setAuthor('')
      setMessage('')
    } catch (err) {
      setError(err instanceof Error ? err.message : '작성에 실패했습니다')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="block guestbook-block">
      <p className="invitation-eyebrow">GUESTBOOK</p><h3>방명록</h3>
      {interactive && (
        <form onSubmit={handleSubmit}>
          <input placeholder="이름" value={author} onChange={(e) => setAuthor(e.target.value)} maxLength={GUEST_NAME_MAX} required />
          <textarea
            placeholder="축하 메시지"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            maxLength={GUESTBOOK_MESSAGE_MAX}
            required
          />
          <small className="guestbook-count" aria-live="polite">{message.length}/{GUESTBOOK_MESSAGE_MAX}</small>
          {error && <p className="block-error">{error}</p>}
          <button type="submit" disabled={busy}>{busy ? '남기는 중...' : '남기기'}</button>
        </form>
      )}
      <ul className="guestbook-list">
        {entries.map((entry) => (
          <li key={entry.id}>
            <strong>{entry.author}</strong>
            <p>{entry.message}</p>
          </li>
        ))}
        {interactive && entries.length === 0 && (
          <li className="block-placeholder">아직 작성된 방명록이 없습니다</li>
        )}
      </ul>
    </section>
  )
}
