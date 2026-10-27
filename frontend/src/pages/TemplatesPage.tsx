import { SiteHeader } from '../discovery/SiteHeader'
import { TemplateGallery } from '../discovery/TemplateGallery'
import '../discovery/discovery.css'

export function TemplatesPage() {
  return (
    <div className="discovery-shell">
      <a className="discovery-skip-link" href="#discovery-main">본문으로 건너뛰기</a>
      <SiteHeader />
      <main id="discovery-main" className="discovery-container discovery-templates-page">
        <TemplateGallery headingLevel={1} title="두 사람을 닮은 한 장" description="서로 다른 분위기의 디자인 사이에서, 우리의 시작을 찾아보세요." />
      </main>
      <footer className="discovery-footer discovery-container"><p>invi. 소중한 날, 당신다운 초대.</p></footer>
    </div>
  )
}
