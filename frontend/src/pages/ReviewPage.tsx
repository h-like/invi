import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api/client'
import { EnvelopeIntro } from '../blocks/EnvelopeIntro'
import { PageRenderer } from '../blocks/PageRenderer'
import { scriptFamily } from '../blocks/fonts'
import { pageMonogram } from '../blocks/heroDetails'
import { envelopeColor, getPageDesign, pageLook, themeFeatures } from '../editor/model'
import type { GuestInvitation } from '../api/types'

/**
 * The owner's private review link: the saved page exactly as guests would see it, published or
 * not, for family to check before it goes out. Guest forms stay closed (the API refuses them for
 * drafts anyway) so a check never lands as a real RSVP or guestbook entry.
 */
export function ReviewPage() {
  const { token } = useParams<{ token: string }>()
  const [invitation, setInvitation] = useState<GuestInvitation | null>(null)
  // 'gone': the server says this link does not exist. 'failed': it may be fine, so offer a retry.
  const [failure, setFailure] = useState<'gone' | 'failed' | null>(null)
  const [attempt, setAttempt] = useState(0)

  // The link is a private key: keep this page out of search results even outside https-test.
  useEffect(() => {
    const meta = document.createElement('meta')
    meta.name = 'robots'
    meta.content = 'noindex, nofollow'
    document.head.appendChild(meta)
    return () => meta.remove()
  }, [])

  useEffect(() => {
    if (!token) return
    let active = true
    api.getByReviewToken(token)
      .then(value => { if (active) setInvitation(value) })
      .catch((err: unknown) => {
        if (active) setFailure((err as { status?: unknown } | null)?.status === 404 ? 'gone' : 'failed')
      })
    return () => { active = false }
  }, [token, attempt])

  if (failure === 'gone') return <main className="page"><p className="form-error" role="alert">확인 링크가 없거나 꺼져 있습니다.</p><p>링크가 꺼졌거나 주소가 바뀌었을 수 있어요. 보낸 분께 새 링크를 받아 주세요.</p></main>
  if (failure === 'failed') return <main className="page"><p className="form-error" role="alert">청첩장을 불러오지 못했습니다.</p><p>링크 문제가 아니라 일시적인 연결 오류일 수 있어요. 잠시 후 다시 시도해 주세요.</p><button type="button" onClick={() => { setFailure(null); setAttempt(n => n + 1) }}>다시 시도</button></main>
  if (!invitation) return <main className="page">불러오는 중...</main>

  const design = getPageDesign(invitation.pageData)
  const look = pageLook(design)
  const page = <PageRenderer pageData={invitation.pageData} weddingDate={invitation.weddingDate} invitationId={invitation.id} interactive guestWrites={false} />
  return (
    <div className="guest-backdrop">
      <p className="review-banner" role="note"><strong>확인용 미리보기</strong> 마지막으로 저장된 내용이에요. 참석 응답과 방명록은 여기서 받지 않아요.</p>
      <main className="guest-page">
        {themeFeatures(look).envelopeIntro
          ? <EnvelopeIntro paper={design.backgroundColor} wine={envelopeColor(design, look)} parts={look.parts} monogram={pageMonogram(invitation.pageData)} script={scriptFamily(look.fonts)} storageKey={'invi:review-envelope:' + invitation.id}>{page}</EnvelopeIntro>
          : page}
      </main>
    </div>
  )
}
