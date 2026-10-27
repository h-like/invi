import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { TemplateDetail } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { PageRenderer } from '../blocks/PageRenderer'
import { SiteHeader } from '../discovery/SiteHeader'
import { SAMPLE_WEDDING_DATE, templatePreview } from '../discovery/preview'
import '../discovery/flow.css'

export function TemplateDetailPage() {
  const { id } = useParams<{ id: string }>()
  return <TemplateDetailContent key={id} id={id} />
}

function TemplateDetailContent({ id }: { id: string | undefined }) {
  const { member } = useAuth()
  const [template, setTemplate] = useState<TemplateDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let active = true
    if (!id) return
    api.getTemplate(id).then(value => {
      if (active) setTemplate(value)
    }).catch(err => { if (active) setError(err instanceof Error ? err.message : '템플릿을 불러오지 못했습니다.') })
    return () => { active = false }
  }, [id, attempt])
  const pageData = template?.defaultPageData
  return (
    <div className="discovery-shell">
      <SiteHeader />
      <main className="discovery-container flow-main">
        <Link className="flow-back" to="/templates">← 모든 템플릿</Link>
        {error ? <div className="flow-state" role="alert"><h1>템플릿을 불러오지 못했어요.</h1><p>{error}</p><button type="button" onClick={() => { setError(null); setAttempt(value => value + 1) }}>다시 불러오기</button></div> : !template ? <p className="flow-state" role="status">템플릿을 불러오고 있어요.</p> : <div className="template-detail-layout">
          <section className="template-detail-info">
            <p className="flow-eyebrow">A DESIGN FOR YOUR STORY</p><h1>{template.name}</h1>
            <p className="flow-muted">두 사람의 이야기를 담을 초대장.<br />구성을 천천히 살펴보고, 마음에 들면 시작해 보세요.</p>
            <Link className="discovery-button flow-full" to={'/create?template=' + encodeURIComponent(template.id)}>이 템플릿으로 만들기</Link>
            <p className="flow-caption">{member ? '다음 단계에서 예식 날짜와 청첩장 주소를 정해요.' : '구경은 로그인 없이. 제작을 시작할 때만 로그인해요.'}</p>
            <div className="template-detail-note"><h2>나만의 이야기로 바꿔 보세요.</h2><p>이름과 인사말, 사진을 채우고 색상과 섹션 구성을 편집할 수 있어요.</p><p>미리보기의 이름·날짜·장소는 예시이며, 참석 응답과 방명록 전송은 비활성화되어 있어요.</p></div>
          </section>
          <section className="template-detail-preview" aria-label="템플릿 실제 구성 미리보기">
            <p className="flow-eyebrow">TEMPLATE PREVIEW · SCROLL TO EXPLORE</p>
            <div className="template-preview-paper">{pageData && Array.isArray(pageData.blocks) ? <PageRenderer pageData={templatePreview(pageData)} weddingDate={SAMPLE_WEDDING_DATE} invitationId="template-preview" interactive={false} /> : <p className="flow-state" role="alert">상세 구성 데이터를 확인하지 못했습니다. 서버 업데이트 후 다시 불러와 주세요.</p>}</div>
          </section>
        </div>}
      </main>
    </div>
  )
}
