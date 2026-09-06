export type BlockType =
  | 'hero'
  | 'gallery'
  | 'rsvp'
  | 'map'
  | 'account'
  | 'guestbook'
  | 'countdown'

export interface Block {
  id: string
  type: BlockType
  order: number
  visible: boolean
  content: Record<string, unknown>
  style: Record<string, unknown>
}

export interface PageData {
  blocks: Block[]
}

export type Plan = 'FREE' | 'PAID'
export type InvitationStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface Template {
  id: string
  name: string
  category: string
  thumbnailUrl: string | null
}

export interface Invitation {
  id: string
  slug: string
  templateId: string
  weddingDate: string
  plan: Plan
  status: InvitationStatus
  pageData: PageData | null
  langVariants: unknown
}

export interface GuestInvitation {
  id: string
  slug: string
  weddingDate: string
  pageData: PageData | null
  langVariants: unknown
}

export interface RsvpEntry {
  id: string
  guestName: string
  attending: boolean
  guestCount: number
  message: string | null
}

export interface GuestbookEntryItem {
  id: string
  author: string
  message: string
}

export interface Member {
  id: string
  email: string
  name: string
}

export interface AccountInfo {
  bank: string
  accountNumber: string
  holder: string
}
