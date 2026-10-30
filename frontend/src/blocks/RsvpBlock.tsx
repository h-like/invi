import { useState, type FormEvent } from 'react'
import { api } from '../api/client'
import { clampGuestCount, GUEST_COUNT_MAX, GUEST_NAME_MAX, RSVP_MESSAGE_MAX } from './guestLimits'

interface Props {
  invitationId: string
  interactive: boolean
}

export function RsvpBlock({ invitationId, interactive }: Props) {
  const [guestName, setGuestName] = useState('')
  const [attending, setAttending] = useState(true)
  const [guestCount, setGuestCount] = useState(1)
  const [message, setMessage] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!interactive || busy) return
    setError(null)
    setBusy(true)
    try {
      await api.submitRsvp(invitationId, {
        guestName,
        attending,
        guestCount,
        message: message || undefined,
      })
      setSubmitted(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : '제출에 실패했습니다')
    } finally {
      setBusy(false)
    }
  }

  if (submitted) {
    return (
      <section className="block rsvp-block">
        <p className="block-success">참석 여부가 전달되었습니다. 감사합니다!</p>
      </section>
    )
  }

  return (
    <section className="block rsvp-block">
      <p className="invitation-eyebrow">R.S.V.P.</p><h3>참석 여부 전달</h3>
      <form onSubmit={handleSubmit}>
        <input
          placeholder="이름"
          value={guestName}
          onChange={(e) => setGuestName(e.target.value)}
          maxLength={GUEST_NAME_MAX}
          required
          disabled={!interactive}
        />
        <div className="rsvp-attending">
          <label>
            <input type="radio" checked={attending} onChange={() => setAttending(true)} disabled={!interactive} />
            참석
          </label>
          <label>
            <input type="radio" checked={!attending} onChange={() => setAttending(false)} disabled={!interactive} />
            불참
          </label>
        </div>
        <input
          type="number"
          min={0}
          max={GUEST_COUNT_MAX}
          placeholder="인원 수"
          value={guestCount}
          onChange={(e) => setGuestCount(clampGuestCount(Number(e.target.value)))}
          disabled={!interactive}
        />
        <textarea
          placeholder="전달할 말 (선택)"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          maxLength={RSVP_MESSAGE_MAX}
          disabled={!interactive}
        />
        {error && <p className="block-error">{error}</p>}
        <button type="submit" disabled={!interactive || busy}>
          {busy ? '전달하는 중...' : '전달하기'}
        </button>
      </form>
    </section>
  )
}
