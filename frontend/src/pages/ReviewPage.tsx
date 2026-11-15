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
  const [error, setError] = useState<string | null>(null)

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
      .catch(err => { if (active) setError(err instanceof Error ? err.message : '확인 링크를 열지 못했습니다.') })
    return () => { active = false }
  }, [token])

  if (error) return <main className="page"><p className="form-error" role="alert">{error}</p><p>링크가 꺼졌거나 주소가 바뀌었을 수 있어요. 보낸 분께 새 링크를 받아 주세요.</p></main>
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
