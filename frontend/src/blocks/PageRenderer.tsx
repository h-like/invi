import type { PageData } from '../api/types'
import { AccountBlock } from './AccountBlock'
import { CountdownBlock } from './CountdownBlock'
import { GalleryBlock } from './GalleryBlock'
import { GuestbookBlock } from './GuestbookBlock'
import { HeroBlock } from './HeroBlock'
import { MapBlock } from './MapBlock'
import { RsvpBlock } from './RsvpBlock'

interface Props {
  pageData: PageData | null
  weddingDate: string
  invitationId: string
  interactive: boolean
}

/**
 * Renders the same page_data block schema for both the editor's live preview
 * (interactive=false) and the public guest view (interactive=true) — see
 * CLAUDE.md's page_data section and 아키텍처 문서 03번.
 */
export function PageRenderer({ pageData, weddingDate, invitationId, interactive }: Props) {
  const blocks = [...(pageData?.blocks ?? [])].filter((b) => b.visible).sort((a, b) => a.order - b.order)

  return (
    <div className="page-renderer">
      {blocks.map((block) => {
        switch (block.type) {
          case 'hero':
            return <HeroBlock key={block.id} block={block} weddingDate={weddingDate} />
          case 'countdown':
            return <CountdownBlock key={block.id} block={block} weddingDate={weddingDate} />
          case 'gallery':
            return <GalleryBlock key={block.id} block={block} />
          case 'map':
            return <MapBlock key={block.id} block={block} />
          case 'account':
            return <AccountBlock key={block.id} block={block} />
          case 'rsvp':
            return <RsvpBlock key={block.id} invitationId={invitationId} interactive={interactive} />
          case 'guestbook':
            return <GuestbookBlock key={block.id} invitationId={invitationId} interactive={interactive} />
          default:
            return null
        }
      })}
    </div>
  )
}
