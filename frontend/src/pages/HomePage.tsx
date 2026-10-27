import { Link } from 'react-router-dom'
import { SiteHeader } from '../discovery/SiteHeader'
import { TemplateGallery } from '../discovery/TemplateGallery'
import '../discovery/discovery.css'

export function HomePage() {
  return (
    <div className="discovery-shell">
      <a className="discovery-skip-link" href="#discovery-main">본문으로 건너뛰기</a>
      <SiteHeader />
      <main id="discovery-main">
        <section className="discovery-hero discovery-container" aria-labelledby="discovery-hero-title">
          <div className="discovery-hero-copy">
            <p className="discovery-eyebrow">A LITTLE INVITATION, A NEW BEGINNING</p>
            <h1 id="discovery-hero-title">우리다운 시작,<br />마음을 담은 초대.</h1>
            <p className="discovery-hero-description">오래 기억하고 싶은 날을 위한 모바일 청첩장.<br />두 사람의 분위기에 어울리는 디자인을 만나보세요.</p>
            <a className="discovery-button" href="#templates">우리의 디자인 찾기 <span aria-hidden="true">↓</span></a>
            <p className="discovery-hero-note">천천히 둘러보고, 마음에 드는 한 장부터.</p>
          </div>
          <div className="discovery-hero-scene" aria-hidden="true">
            <div className="discovery-hero-halo" />
            <div className="discovery-hero-envelope" />
            <div className="discovery-hero-paper discovery-hero-paper-back"><span>the beginning<br /><i>of us.</i></span></div>
            <div className="discovery-hero-paper discovery-hero-paper-front">
              <span className="discovery-paper-overline">TOGETHER WITH LOVE</span>
              <svg className="discovery-hero-botanical" viewBox="0 0 180 210" fill="none">
                <path d="M87 195C81 146 106 95 91 24M86 164C60 143 45 114 42 84M89 140C113 125 133 98 140 67" stroke="currentColor" strokeWidth="1.5" />
                <g fill="currentColor" opacity=".75">
                  <ellipse cx="90" cy="40" rx="8" ry="19" transform="rotate(-17 90 40)" /><ellipse cx="105" cy="68" rx="8" ry="19" transform="rotate(39 105 68)" />
                  <ellipse cx="85" cy="87" rx="9" ry="21" transform="rotate(-38 85 87)" /><ellipse cx="106" cy="115" rx="8" ry="19" transform="rotate(45 106 115)" />
                  <ellipse cx="54" cy="111" rx="8" ry="21" transform="rotate(-32 54 111)" /><ellipse cx="38" cy="83" rx="7" ry="17" transform="rotate(-23 38 83)" />
                  <ellipse cx="130" cy="90" rx="7" ry="18" transform="rotate(35 130 90)" /><ellipse cx="141" cy="63" rx="6" ry="15" transform="rotate(18 141 63)" />
                </g>
              </svg>
              <span className="discovery-paper-title">The Wedding<br /><i>of us</i></span>
              <span className="discovery-paper-line" />
              <span className="discovery-paper-bottom">소중한 당신을 초대합니다</span>
            </div>
            <span className="discovery-hero-caption">a moment, made yours.</span>
          </div>
        </section>
        <div className="discovery-gallery-band"><div className="discovery-container"><TemplateGallery showAllLink /></div></div>
      </main>
      <footer className="discovery-footer discovery-container">
        <Link className="discovery-wordmark" to="/" aria-label="invi 홈">invi<span>.</span></Link>
        <p>소중한 날, 당신다운 초대.</p>
        <Link to="/templates">모든 템플릿 둘러보기 <span aria-hidden="true">↗</span></Link>
      </footer>
    </div>
  )
}
