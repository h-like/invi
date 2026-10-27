import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { Invitation, InvitationStatus } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { SiteHeader } from '../discovery/SiteHeader'
import '../discovery/flow.css'

const STATUS_LABELS: Record<InvitationStatus, string> = { DRAFT: '작성 중', PUBLISHED: '공개됨', ARCHIVED: '보관됨' }
function coupleLabel(invitation: Invitation): string {
  const hero = invitation.pageData?.blocks.find(block => block.type === 'hero')?.content
  const names = [hero?.groomName, hero?.brideName].filter((name): name is string => typeof name === 'string' && Boolean(name.trim()))
  return names.length ? names.join(' & ') : invitation.slug
}

export function MyInvitationsPage() {
  const { member } = useAuth()
  const [result, setResult] = useState<{ memberId: string; items: Invitation[] } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    if (!member) return
    let active = true
    api.listMyInvitations().then(items => { if (active) setResult({ memberId: member.id, items }) })
      .catch(err => { if (active) setError(err instanceof Error ? err.message : '내 청첩장을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [member, attempt])
  const invitations = result?.memberId === member?.id ? result?.items : null
  return (
    <div className="discovery-shell">
      <SiteHeader />
      <main className="discovery-container flow-main">
        <div className="flow-heading"><div><p className="flow-eyebrow">MY INVITATIONS</p><h1>내 청첩장</h1><p className="flow-muted">우리의 초대장을 이어서 완성해 보세요.</p></div><Link className="discovery-button" to="/templates">새 청첩장 만들기</Link></div>
        {error ? <div className="flow-state" role="alert"><p>{error}</p><button type="button" onClick={() => { setError(null); setAttempt(value => value + 1) }}>다시 불러오기</button></div> : !invitations ? <p className="flow-state" role="status">내 청첩장을 불러오고 있어요.</p> : invitations.length === 0 ? <section className="flow-state"><h2>첫 초대장을 만들어 볼까요?</h2><p>마음에 드는 템플릿을 고르는 것부터 시작해 보세요.</p><Link className="discovery-button" to="/templates">템플릿 둘러보기</Link></section> : <ul className="my-invitation-list">
          {invitations.map(invitation => <li className="my-invitation-card" key={invitation.id}>
            <div className="my-invitation-info"><strong>{coupleLabel(invitation)}</strong><span className="my-invitation-meta">{invitation.weddingDate} · /i/{invitation.slug}</span></div>
            <span className={'my-invitation-status status-' + invitation.status.toLowerCase()}>{STATUS_LABELS[invitation.status]}</span>
            <div className="my-invitation-actions"><Link to={'/e/' + invitation.id}>편집</Link>{invitation.status === 'PUBLISHED' && <Link to={'/i/' + invitation.slug} target="_blank" rel="noreferrer">청첩장 보기</Link>}</div>
          </li>)}
        </ul>}
      </main>
    </div>
  )
}
