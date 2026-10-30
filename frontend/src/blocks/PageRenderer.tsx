import { useEffect, type CSSProperties } from 'react'
import type { PageData } from '../api/types'
import { getPageDesign, readableColor } from '../editor/model'
import { AccountBlock } from './AccountBlock'
import { CountdownBlock } from './CountdownBlock'
import { FONT_STACKS, loadInvitationFont } from './fonts'
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
  useEffect(() => { void loadInvitationFont(design.fontFamily) }, [design.fontFamily])
  const accent = readableColor(design.accentColor, design.backgroundColor)
  const ink = readableColor('#36302b', design.backgroundColor)
  const soft = readableColor('#74695e', design.backgroundColor)
  const style = {
    '--invite-bg': design.backgroundColor, '--invite-accent': accent, '--invite-ink': ink,
    '--invite-soft': soft, '--invite-size': design.fontSize === 'larger' ? '20px' : design.fontSize === 'large' ? '18px' : '16px',
    '--invite-font': FONT_STACKS[design.fontFamily],
  } as CSSProperties
  const blocks = [...(pageData?.blocks ?? [])].filter(b => b.visible).sort((a, b) => a.order - b.order)
  return (
    <div className={'page-renderer invitation-theme-' + design.theme} style={style}>
      {blocks.map(original => {
        const block = { ...original, style: { ...original.style, bgColor: design.backgroundColor, accentColor: accent } }
        let rendered
        switch (block.type) {
          case 'hero': rendered = <HeroBlock block={block} weddingDate={weddingDate} layout={design.heroLayout} />; break
          case 'countdown': rendered = <CountdownBlock block={block} weddingDate={weddingDate} />; break
          case 'gallery': rendered = <GalleryBlock block={block} />; break
          case 'map': rendered = <MapBlock block={block} />; break
          case 'account': rendered = <AccountBlock block={block} />; break
          case 'rsvp': rendered = <RsvpBlock invitationId={invitationId} interactive={interactive} />; break
          case 'guestbook': rendered = <GuestbookBlock invitationId={invitationId} interactive={interactive} />; break
          default: return null
        }
        return <div key={block.id} data-block-id={block.id}>{rendered}</div>
      })}
      {blocks.length === 0 && <p className="block block-placeholder">표시할 섹션을 선택해 주세요.</p>}
    </div>
  )
}
