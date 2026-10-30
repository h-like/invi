import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api/client'
import { EnvelopeIntro } from '../blocks/EnvelopeIntro'
import { PageRenderer } from '../blocks/PageRenderer'
import { getPageDesign, themeFeatures } from '../editor/model'
import type { GuestInvitation } from '../api/types'

export function GuestPage() {
  const { slug } = useParams<{ slug: string }>()
  const [invitation, setInvitation] = useState<GuestInvitation | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!slug) return
    api
      .getBySlug(slug)
      .then(setInvitation)
      .catch((err) => setError(err instanceof Error ? err.message : '청첩장을 찾을 수 없습니다'))
  }, [slug])

  if (error) {
    return (
      <main className="page">
        <p className="form-error">{error}</p>
      </main>
    )
  }
  if (!invitation) {
    return <main className="page">불러오는 중...</main>
  }

  const design = getPageDesign(invitation.pageData)
  const page = (
    <PageRenderer
      pageData={invitation.pageData}
      weddingDate={invitation.weddingDate}
      invitationId={invitation.id}
      interactive
    />
  )
  return (
    <div className="guest-backdrop">
      <main className="guest-page">
        {themeFeatures(design.theme).envelopeIntro
          ? <EnvelopeIntro paper={design.backgroundColor} wine={design.accentColor} monogram={heroMonogram(invitation)} storageKey={'invi:envelope:' + invitation.id}>{page}</EnvelopeIntro>
          : page}
      </main>
    </div>
  )
}

function heroMonogram(invitation: GuestInvitation): string {
  const value = invitation.pageData?.blocks.find(b => b.type === 'hero')?.content.monogram
  return typeof value === 'string' ? value : ''
}
