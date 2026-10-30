import { useId, useState, type FormEvent } from 'react'
import { api } from '../api/client'
import { clampGuestCount, GUEST_COUNT_MAX, GUEST_NAME_MAX, RSVP_MESSAGE_MAX } from './guestLimits'
import { RSVP_MEAL_OPTIONS, RSVP_SIDE_OPTIONS, type RsvpMeal, type RsvpSide } from './rsvpOptions'

interface Props {
  invitationId: string
  interactive: boolean
}

export function RsvpBlock({ invitationId, interactive }: Props) {
  const [guestName, setGuestName] = useState('')
  const [attending, setAttending] = useState(true)
  const [guestCount, setGuestCount] = useState(1)
  const [side, setSide] = useState<RsvpSide | null>(null)
  const [meal, setMeal] = useState<RsvpMeal | null>(null)
  const [message, setMessage] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!interactive || busy) return
    if (!side) { setError('신랑측·신부측 중 하나를 선택해 주세요.'); return }
    setError(null)
    setBusy(true)
    try {
      await api.submitRsvp(invitationId, {
        guestName,
        attending,
        guestCount,
        message: message || undefined,
        side,
        meal: attending && meal ? meal : undefined,
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
        <Segmented legend="어느 분의 하객이신가요?" name="side" options={RSVP_SIDE_OPTIONS} value={side} onChange={setSide} disabled={!interactive} />
        <Segmented legend="참석 여부" name="attending" options={ATTENDING_OPTIONS} value={attending ? 'yes' : 'no'} onChange={v => setAttending(v === 'yes')} disabled={!interactive} />
        {attending && <Segmented legend="식사 여부" name="meal" options={RSVP_MEAL_OPTIONS} value={meal} onChange={setMeal} disabled={!interactive} />}
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

const ATTENDING_OPTIONS = [{ value: 'yes', label: '참석' }, { value: 'no', label: '불참' }] as const

// Radio group drawn as one row of segments, so it stays keyboard and screen-reader friendly.
function Segmented<T extends string>({ legend, name, options, value, onChange, disabled }: {
  legend: string
  name: string
  options: readonly { value: T; label: string }[]
  value: T | null
  onChange: (value: T) => void
  disabled: boolean
}) {
  const id = useId()
  return (
    <fieldset className="rsvp-segment" disabled={disabled}>
      <legend>{legend}</legend>
      <div className="rsvp-segment-options">
        {options.map(option => (
          <label key={option.value} className={value === option.value ? 'is-selected' : ''}>
            <input type="radio" name={id + name} value={option.value} checked={value === option.value} onChange={() => onChange(option.value)} />
            {option.label}
          </label>
        ))}
      </div>
    </fieldset>
  )
}
