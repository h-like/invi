import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api/client'
import { PageRenderer } from '../blocks/PageRenderer'
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

  return (
    <main className="guest-page">
      <PageRenderer
        pageData={invitation.pageData}
        weddingDate={invitation.weddingDate}
        invitationId={invitation.id}
        interactive
      />
    </main>
  )
}
