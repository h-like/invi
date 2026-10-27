import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import type { TemplateDetail } from '../api/types'
import { SiteHeader } from '../discovery/SiteHeader'
import { TemplateArtwork } from '../discovery/TemplateArtwork'
import '../discovery/flow.css'

export function CreateInvitationPage() {
  const [params] = useSearchParams()
  const id = params.get('template')
  return <CreateInvitationForm key={id} templateId={id} />
}

function CreateInvitationForm({ templateId }: { templateId: string | null }) {
  const navigate = useNavigate()
  const [template, setTemplate] = useState<TemplateDetail | null>(null)
  const [slug, setSlug] = useState('')
  const [weddingDate, setWeddingDate] = useState('')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const pending = useRef(false)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    if (!templateId) return
    let active = true
    api.getTemplate(templateId).then(value => { if (active) setTemplate(value) })
      .catch(err => { if (active) setLoadError(err instanceof Error ? err.message : '템플릿을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [templateId, attempt])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!template || pending.current) return
    pending.current = true
    setSubmitting(true)
    setError(null)
    try {
      const invitation = await api.createInvitation({ templateId: template.id, slug, weddingDate })
      navigate('/e/' + invitation.id, { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '청첩장 생성에 실패했습니다. 입력 내용을 확인하고 다시 시도해 주세요.')
    } finally { pending.current = false; setSubmitting(false) }
  }

  return <div className="discovery-shell"><SiteHeader /><main className="discovery-container flow-main">
    {!templateId ? <section className="flow-state"><h1>먼저 템플릿을 골라 주세요.</h1><Link className="discovery-button" to="/templates">템플릿 둘러보기</Link></section> : loadError ? <section className="flow-state" role="alert"><p>{loadError}</p><button type="button" onClick={() => { setLoadError(null); setAttempt(value => value + 1) }}>다시 불러오기</button><Link to="/templates">다른 템플릿 보기</Link></section> : !template ? <p className="flow-state" role="status">선택한 템플릿을 불러오고 있어요.</p> : <>
      <Link className="flow-back" to={'/templates/' + template.id}>← 템플릿 다시 보기</Link>
      <div className="create-layout"><aside className="create-template"><TemplateArtwork template={template} compact /><p>{template.name} 템플릿</p><Link to="/templates">다른 템플릿 고르기</Link></aside>
        <section className="create-form-panel"><p className="flow-eyebrow">LET'S MAKE IT YOURS</p><h1>우리의 초대를 시작해요.</h1><p className="flow-muted">날짜와 주소를 정하면 선택한 템플릿으로 편집을 시작할 수 있어요.</p>
          <form className="flow-form" onSubmit={handleSubmit}>
            <fieldset disabled={submitting}>
              <label>결혼식 날짜<input type="date" value={weddingDate} onChange={event => setWeddingDate(event.target.value)} required /></label>
              <label>청첩장 주소<input value={slug} onChange={event => setSlug(event.target.value)} placeholder="haneul-daon" required pattern="[a-z0-9\-]{3,50}" minLength={3} maxLength={50} aria-describedby="slug-help" autoCapitalize="none" autoCorrect="off" spellCheck={false} /></label>
              <p id="slug-help" className="flow-caption">영문 소문자, 숫자, 하이픈으로 3~50자 입력해 주세요.<br />공개 주소: /i/{slug || 'haneul-daon'}</p>
              {error && <p className="form-error" role="alert">{error}</p>}
              <button type="submit" className="discovery-button flow-full">{submitting ? '만드는 중...' : '청첩장 만들기'}</button>
            </fieldset>
          </form><p className="flow-caption">생성 후 편집 화면으로 이동합니다. 공개는 편집 후 직접 선택해요.</p>
        </section>
      </div></>}
  </main></div>
}
