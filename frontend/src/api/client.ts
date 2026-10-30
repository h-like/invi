import { getToken } from '../auth/token'
import { API_BASE } from './baseUrl'
import { putPhoto } from '../editor/putPhoto'
import type { PhotoUploadOptions } from '../editor/coverUpload'
import type {
  GuestInvitation,
  GuestbookEntryItem,
  Invitation,
  MediaAsset,
  MediaType,
  Member,
  PageData,
  RsvpEntry,
  Template,
  TemplateDetail,
} from './types'

class ApiError extends Error {}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getToken()
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
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
  register: (body: { email: string; password: string; name: string }) =>
    request<{ token: string }>('/api/auth/register', { method: 'POST', body: JSON.stringify(body) }),

  login: (body: { email: string; password: string }) =>
    request<{ token: string }>('/api/auth/login', { method: 'POST', body: JSON.stringify(body) }),

  getMe: () => request<Member>('/api/auth/me'),

  listTemplates: () => request<Template[]>('/api/templates'),

  getTemplate: (id: string) => request<TemplateDetail>(`/api/templates/${encodeURIComponent(id)}`),

  createInvitation: (body: { templateId: string; slug: string; weddingDate: string }) =>
    request<Invitation>('/api/invitations', { method: 'POST', body: JSON.stringify(body) }),

  listMyInvitations: () => request<Invitation[]>('/api/invitations'),

  getInvitation: (id: string) => request<Invitation>(`/api/invitations/${id}`),

  updatePageData: (id: string, pageData: PageData) =>
    request<Invitation>(`/api/invitations/${id}/page-data`, {
      method: 'PUT',
      body: JSON.stringify({ pageData }),
    }),

  updateWeddingDate: (id: string, weddingDate: string) =>
    request<Invitation>(`/api/invitations/${id}/wedding-date`, {
      method: 'PUT',
      body: JSON.stringify({ weddingDate }),
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

  deleteGuestbookEntry: (invitationId: string, entryId: string) =>
    request<void>(`/api/invitations/${invitationId}/guestbook/${entryId}`, { method: 'DELETE' }),

  presignMediaUpload: (invitationId: string, fileName: string, contentType: string, type: MediaType, contentLength: number, signal?: AbortSignal) =>
    request<{ uploadUrl: string; r2Key: string; publicUrl: string }>(
      `/api/invitations/${invitationId}/media/presign`,
      { method: 'POST', body: JSON.stringify({ fileName, contentType, type, contentLength }), signal },
    ),

  attachMedia: (invitationId: string, r2Key: string, type: MediaType, sortOrder: number, signal?: AbortSignal) =>
    request<MediaAsset>(`/api/invitations/${invitationId}/media`, {
      method: 'POST',
      body: JSON.stringify({ r2Key, type, sortOrder }),
      signal,
    }),

  // Uploads go straight from the browser to R2 via the presigned URL — never through our API.
  uploadImage: async (invitationId: string, file: File, type: MediaType, sortOrder: number, options: PhotoUploadOptions = {}): Promise<MediaAsset> => {
    options.signal?.throwIfAborted()
    const { uploadUrl, r2Key } = await api.presignMediaUpload(invitationId, file.name, file.type, type, file.size, options.signal)
    await putPhoto(uploadUrl, file, options)
    options.signal?.throwIfAborted()
    return api.attachMedia(invitationId, r2Key, type, sortOrder, options.signal)
  },
}
