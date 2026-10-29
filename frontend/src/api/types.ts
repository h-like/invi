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

// Theme codes come from src/editor/themes.json, shared with the backend TemplateSeeder.
export type DesignTheme = string
export type InvitationFont = 'serif' | 'sans'
export type InvitationFontSize = 'normal' | 'large' | 'larger'

export interface PageDesign {
  theme: DesignTheme
  backgroundColor: string
  accentColor: string
  fontFamily: InvitationFont
  fontSize: InvitationFontSize
}

export interface PageData {
  blocks: Block[]
  design?: PageDesign
  [key: string]: unknown
}

export type Plan = 'FREE' | 'PAID'
export type InvitationStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface Template {
  id: string
  name: string
  category: string
  thumbnailUrl: string | null
}

export interface TemplateDetail extends Template {
  defaultPageData: PageData | null
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

export type MediaType = 'GALLERY' | 'OG' | 'COVER'

export interface MediaAsset {
  id: string
  url: string
  type: MediaType
  sortOrder: number
}

export interface Member {
  id: string
  email: string
  name: string
}

export interface AccountEntry {
  holder: string
  bank: string
  accountNumber: string
  message: string
}

export const ACCOUNT_ROLES = [
  'groom',
  'groomFather',
  'groomMother',
  'bride',
  'brideFather',
  'brideMother',
] as const

export type AccountRole = (typeof ACCOUNT_ROLES)[number]

export const ACCOUNT_ROLE_LABELS: Record<AccountRole, string> = {
  groom: '신랑',
  groomFather: '신랑측 아버님',
  groomMother: '신랑측 어머님',
  bride: '신부',
  brideFather: '신부측 아버님',
  brideMother: '신부측 어머님',
}

export type AccountContent = Record<AccountRole, AccountEntry>

export function emptyAccountEntry(): AccountEntry {
  return { holder: '', bank: '', accountNumber: '', message: '' }
}

export function emptyAccountContent(): AccountContent {
  return {
    groom: emptyAccountEntry(),
    groomFather: emptyAccountEntry(),
    groomMother: emptyAccountEntry(),
    bride: emptyAccountEntry(),
    brideFather: emptyAccountEntry(),
    brideMother: emptyAccountEntry(),
  }
}
