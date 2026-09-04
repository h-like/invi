import type {
  GuestInvitation,
  GuestbookEntryItem,
  Invitation,
  Member,
  PageData,
  RsvpEntry,
  Template,
} from './types'

const API_BASE = 'http://localhost:8080'

class ApiError extends Error {}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new ApiError(body?.message ?? `요청에 실패했습니다 (${res.status})`)
  }
  if (res.status === 204) {
    return undefined as T
  }
  return (await res.json()) as T
}

export const api = {
  getDevMember: () => request<Member>('/api/members/dev'),

  listTemplates: () => request<Template[]>('/api/templates'),

  createInvitation: (body: { memberId: string; templateId: string; slug: string; weddingDate: string }) =>
    request<Invitation>('/api/invitations', { method: 'POST', body: JSON.stringify(body) }),

  getInvitation: (id: string) => request<Invitation>(`/api/invitations/${id}`),

  updatePageData: (id: string, pageData: PageData) =>
    request<Invitation>(`/api/invitations/${id}/page-data`, {
      method: 'PUT',
      body: JSON.stringify({ pageData }),
    }),

  publishInvitation: (id: string) => request<Invitation>(`/api/invitations/${id}/publish`, { method: 'POST' }),

  getBySlug: (slug: string) => request<GuestInvitation>(`/api/invitations/slug/${slug}`),

  checkSlugAvailable: (slug: string) =>
    request<{ available: boolean }>(`/api/invitations/slug-available?slug=${encodeURIComponent(slug)}`),

  listRsvps: (invitationId: string) => request<RsvpEntry[]>(`/api/invitations/${invitationId}/rsvps`),

  submitRsvp: (
    invitationId: string,
    body: { guestName: string; attending: boolean; guestCount: number; message?: string },
  ) =>
    request<RsvpEntry>(`/api/invitations/${invitationId}/rsvps`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  listGuestbook: (invitationId: string) =>
    request<GuestbookEntryItem[]>(`/api/invitations/${invitationId}/guestbook`),

  submitGuestbook: (invitationId: string, body: { author: string; message: string }) =>
    request<GuestbookEntryItem>(`/api/invitations/${invitationId}/guestbook`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
}
