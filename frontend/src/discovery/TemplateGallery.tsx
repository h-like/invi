import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { Template } from '../api/types'
import { TemplateArtwork } from './TemplateArtwork'

export interface TemplateGalleryProps {
  title?: string
  description?: string
  headingLevel?: 1 | 2
  showAllLink?: boolean
}

const categoryLabels: Record<string, string> = {
  classic: '클래식', minimal: '미니멀', romantic: '로맨틱', modern: '모던',
  garden: '가든', hanji: '한지', film: '필름', letterpress: '레터프레스',
}

type GalleryState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; templates: Template[] }

export function TemplateGallery({
  title = '마음이 머무는 디자인',
  description = '한 장의 초대장에 담긴, 저마다의 분위기를 만나보세요.',
  headingLevel = 2,
  showAllLink = false,
}: TemplateGalleryProps) {
  const [state, setState] = useState<GalleryState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const Heading = headingLevel === 1 ? 'h1' : 'h2'

  useEffect(() => {
    let active = true
    api.listTemplates().then((templates) => {
      if (active) setState({ status: 'ready', templates })
    }).catch(() => {
      if (active) setState({ status: 'error' })
    })
    return () => { active = false }
  }, [attempt])

  function retry() {
    setState({ status: 'loading' })
    setAttempt((value) => value + 1)
  }

  return (
    <section id="templates" className="discovery-gallery" aria-labelledby="discovery-gallery-title">
      <div className="discovery-gallery-heading">
        <div>
          <p className="discovery-eyebrow">THE INVITATION COLLECTION</p>
          <Heading id="discovery-gallery-title">{title}</Heading>
          <p className="discovery-gallery-description">{description}</p>
        </div>
        {showAllLink && <Link className="discovery-text-link" to="/templates">모든 템플릿 <span aria-hidden="true">↗</span></Link>}
      </div>
      <p className="discovery-cover-note">커버는 디자인 예시입니다. 상세에서 실제 구성을 확인하세요.</p>
      <div aria-busy={state.status === 'loading'}>
        {state.status === 'loading' && (
          <>
            <p role="status" className="discovery-sr-only">템플릿을 불러오는 중입니다.</p>
            <div className="discovery-masonry discovery-skeletons" aria-hidden="true">
              {[0, 1, 2, 3].map((item) => <div className="discovery-skeleton" key={item}><div /><span /></div>)}
            </div>
          </>
        )}
        {state.status === 'error' && (
          <div className="discovery-gallery-state">
            <p role="alert">템플릿을 불러오지 못했어요.</p>
            <p>잠시 후 다시 시도해 주세요.</p>
            <button type="button" className="discovery-button discovery-button-secondary" onClick={retry}>다시 불러오기</button>
          </div>
        )}
        {state.status === 'ready' && (state.templates.length === 0 ? (
          <div className="discovery-gallery-state" role="status"><p>아직 준비된 템플릿이 없어요.</p><p>새로운 디자인을 준비하고 있습니다.</p></div>
        ) : (
          <ul className="discovery-masonry">
            {state.templates.map((template) => (
              <li className="discovery-template-card" key={template.id}>
                <Link className="discovery-template-link" to={`/templates/${encodeURIComponent(template.id)}`}>
                  <TemplateArtwork template={template} />
                  <div className="discovery-template-caption">
                    <div><span className="discovery-template-category">{categoryLabels[template.category.toLowerCase()] || template.category}</span><h3>{template.name}</h3></div>
                    <span className="discovery-card-arrow" aria-hidden="true">↗</span>
                  </div>
                  <span className="discovery-sr-only">템플릿 상세 보기</span>
                </Link>
              </li>
            ))}
          </ul>
        ))}
      </div>
    </section>
  )
}
