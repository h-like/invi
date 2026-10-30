import { useEffect, useRef, type CSSProperties, type RefObject } from 'react'
import type { PageData } from '../api/types'
import { getPageDesign, readableColor, themeFeatures } from '../editor/model'
import { AccountBlock } from './AccountBlock'
import { CountdownBlock } from './CountdownBlock'
import { fontStack, loadInvitationFont } from './fonts'
import { GalleryBlock } from './GalleryBlock'
import { GuestbookBlock } from './GuestbookBlock'
import { HeroBlock } from './HeroBlock'
import { MapBlock } from './MapBlock'
import { RsvpBlock } from './RsvpBlock'
import './invitation.css'

interface Props {
  pageData: PageData | null
  weddingDate: string
  invitationId: string
  interactive: boolean
}

// One renderer for both the editor preview and the published invitation.
export function PageRenderer({ pageData, weddingDate, invitationId, interactive }: Props) {
  const design = getPageDesign(pageData)
  useEffect(() => { void loadInvitationFont(design.fontFamily, design.theme) }, [design.fontFamily, design.theme])
  const accent = readableColor(design.accentColor, design.backgroundColor)
  const ink = readableColor('#36302b', design.backgroundColor)
  const soft = readableColor('#74695e', design.backgroundColor)
  const style = {
    '--invite-bg': design.backgroundColor, '--invite-accent': accent, '--invite-ink': ink,
    '--invite-soft': soft, '--invite-size': design.fontSize === 'larger' ? '20px' : design.fontSize === 'large' ? '18px' : '16px',
    '--invite-font': fontStack(design.theme, design.fontFamily),
  } as CSSProperties
  const blocks = [...(pageData?.blocks ?? [])].filter(b => b.visible).sort((a, b) => a.order - b.order)
  const hero = blocks.find(b => b.type === 'hero')
  const weddingTime = typeof hero?.content.weddingTime === 'string' ? hero.content.weddingTime : ''
  // Editor previews re-render on every keystroke, so sections only fade in for guests.
  const reveal = interactive && themeFeatures(design.theme).reveal
  const sheet = useRef<HTMLDivElement>(null)
  useReveal(sheet, reveal)
  return (
    <div className={'page-renderer invitation-theme-' + design.theme + (reveal ? ' has-reveal' : '')} style={style}>
      <div className="page-sheet" ref={sheet}>
        {blocks.map(original => {
          const block = { ...original, style: { ...original.style, bgColor: design.backgroundColor, accentColor: accent } }
          let rendered
          switch (block.type) {
            case 'hero': rendered = <HeroBlock block={block} weddingDate={weddingDate} layout={design.heroLayout} />; break
            case 'countdown': rendered = <CountdownBlock block={block} weddingDate={weddingDate} weddingTime={weddingTime} />; break
            case 'gallery': rendered = <GalleryBlock block={block} interactive={interactive} />; break
            case 'map': rendered = <MapBlock block={block} interactive={interactive} />; break
            case 'account': rendered = <AccountBlock block={block} />; break
            case 'rsvp': rendered = <RsvpBlock invitationId={invitationId} interactive={interactive} />; break
            case 'guestbook': rendered = <GuestbookBlock invitationId={invitationId} interactive={interactive} />; break
            default: return null
          }
          return <div key={block.id} data-block-id={block.id} className={reveal && block.type !== 'hero' ? 'reveal-item' : undefined}>{rendered}</div>
        })}
        {blocks.length === 0 && <p className="block block-placeholder">표시할 섹션을 선택해 주세요.</p>}
      </div>
    </div>
  )
}

function useReveal(container: RefObject<HTMLDivElement | null>, enabled: boolean) {
  useEffect(() => {
    const root = container.current
    if (!enabled || !root) return
    const items = [...root.querySelectorAll<HTMLElement>('.reveal-item')]
    if (!('IntersectionObserver' in window)) { items.forEach(el => el.classList.add('is-revealed')); return }
    const observer = new IntersectionObserver(entries => entries.forEach(entry => {
      if (!entry.isIntersecting) return
      entry.target.classList.add('is-revealed')
      observer.unobserve(entry.target)
    }), { rootMargin: '0px 0px -12% 0px' })
    items.forEach(el => observer.observe(el))
    return () => observer.disconnect()
  }, [container, enabled])
}
