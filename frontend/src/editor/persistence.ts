import type { Invitation, PageData } from '../api/types.ts'

export interface PersistenceClient {
  updatePageData: (id: string, pageData: PageData, version: number) => Promise<Invitation>
  publishInvitation: (id: string) => Promise<Invitation>
  updateWeddingDate: (id: string, weddingDate: string) => Promise<Invitation>
  getInvitation: (id: string) => Promise<Invitation>
}

export type PersistenceOutcome =
  | { kind: 'busy' }
  | { kind: 'save-failed'; detail: string }
  /** Someone saved a newer copy first. Nothing was written; retrying would be refused again. */
  | { kind: 'stale'; detail: string }
  | { kind: 'publish-failed'; saved: Invitation; detail: string }
  | { kind: 'completed'; invitation: Invitation; shared: boolean }

export type DateSaveOutcome =
  | { kind: 'busy' }
  | { kind: 'failed'; detail: string }
  | { kind: 'saved'; invitation: Invitation }

/** Error code the API sends with a 409 when the saved copy is newer than the editor's. */
export const STALE_VERSION = 'STALE_VERSION'

function isStaleVersion(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { code?: unknown }).code === STALE_VERSION
}

function errorDetail(error: unknown, fallback: string): string {
  return error instanceof Error && error.message.trim() ? error.message : fallback
}

/**
 * One single-flight guard spans BOTH save and publish, including the gap before
 * React disables buttons. Failures are values: the caller decides when to replace
 * the draft/baseline and when it is safe to display a share link.
 *
 * A failed save never calls publish. A failed publish returns the successfully
 * saved invitation so the UI can distinguish the partial success. Retries always
 * save the current draft again; they do not publish an earlier cached draft.
 *
 * Wedding-date saves share the same guard. Every write bumps the stored version, so two
 * writes from this tab in flight at once would make the second look stale (or leave the
 * screen on an older version); one at a time, each response's version is this tab's own.
 *
 * A save or publish whose response is lost may still have landed. If the retry is then
 * refused as stale, the stored copy is read once: when this tab's own write is the only
 * newer one, the save is sent again on top of it instead of reporting someone else's save.
 */
export function createInvitationPersistence(client: PersistenceClient) {
  let running = false
  // This tab's last write whose response never arrived. The server may still have applied
  // it, so a retry from the same copy could be refused as stale by this tab's own write.
  let unconfirmed: UnconfirmedWrite | null = null

  return {
    isRunning: () => running,
    async run(invitation: Invitation, pageData: PageData, publish: boolean): Promise<PersistenceOutcome> {
      if (running) return { kind: 'busy' }
      running = true
      try {
        let saved: Invitation
        let version = invitation.version
        try {
          try {
            saved = await client.updatePageData(invitation.id, pageData, version)
          } catch (error) {
            const landed = isStaleVersion(error) ? await ownWriteLanded(invitation) : null
            if (landed === null) throw error
            // Only our own lost write is newer: save again on top of it.
            version = landed
            saved = await client.updatePageData(invitation.id, pageData, version)
          }
        } catch (error) {
          if (isStaleVersion(error)) return { kind: 'stale', detail: errorDetail(error, '다른 곳에서 먼저 저장된 내용이 있습니다.') }
          const sent = canonicalJson(pageData)
          unconfirmed = { id: invitation.id, from: invitation.version, version, applied: current => canonicalJson(current.pageData) === sent }
          return { kind: 'save-failed', detail: errorDetail(error, '서버와 연결하지 못했습니다.') }
        }
        unconfirmed = null

        if (!publish || saved.status === 'PUBLISHED') {
          return { kind: 'completed', invitation: saved, shared: publish }
        }

        try {
          const published = await client.publishInvitation(saved.id)
          if (published.status !== 'PUBLISHED') {
            return { kind: 'publish-failed', saved, detail: '서버에서 공개 완료 상태를 확인하지 못했습니다.' }
          }
          return { kind: 'completed', invitation: published, shared: true }
        } catch (error) {
          const confirmed = await publishedAnyway(saved)
          if (confirmed) return { kind: 'completed', invitation: confirmed, shared: true }
          // The caller adopts `saved`, so a retry starts from saved.version.
          unconfirmed = { id: saved.id, from: saved.version, version: saved.version, applied: current => current.status === 'PUBLISHED' }
          return { kind: 'publish-failed', saved, detail: errorDetail(error, '공개 요청 결과를 확인하지 못했습니다.') }
        }
      } finally {
        running = false
      }
    },

    async saveWeddingDate(invitation: Invitation, weddingDate: string): Promise<DateSaveOutcome> {
      if (running) return { kind: 'busy' }
      running = true
      try {
        return { kind: 'saved', invitation: await client.updateWeddingDate(invitation.id, weddingDate) }
      } catch (error) {
        return { kind: 'failed', detail: errorDetail(error, '예식일을 변경하지 못했습니다.') }
      } finally {
        running = false
      }
    },
  }

  /**
   * A publish whose response was lost may still have been applied, bumping the version past
   * `saved`; keeping `saved` would make the retry look stale. Exactly one step past `saved`
   * and PUBLISHED can only be that publish (any other save or a save-and-publish elsewhere
   * moves it further), so only then is the stored copy adopted. Anything else stays a failure.
   */
  async function publishedAnyway(saved: Invitation): Promise<Invitation | null> {
    try {
      const current = await client.getInvitation(saved.id)
      return current.status === 'PUBLISHED' && current.version === saved.version + 1 ? current : null
    } catch {
      return null
    }
  }

  /**
   * On a stale refusal: was the only newer write this tab's own unconfirmed one? True when
   * the retry starts from the copy that write was sent from, and the stored copy is exactly
   * one step past it and shows that write. Returns the version to save on top of, else null.
   */
  async function ownWriteLanded(invitation: Invitation): Promise<number | null> {
    const write = unconfirmed
    if (!write || write.id !== invitation.id || write.from !== invitation.version) return null
    try {
      const current = await client.getInvitation(invitation.id)
      return current.version === write.version + 1 && write.applied(current) ? current.version : null
    } catch {
      return null
    }
  }
}

interface UnconfirmedWrite {
  id: string
  /** The version the caller's copy had when the write was sent; retries start from it. */
  from: number
  /** The version the write itself was sent at. */
  version: number
  /** Whether the stored copy shows this write. */
  applied: (current: Invitation) => boolean
}

/** JSON with sorted keys: the server stores page data as jsonb, which reorders keys. */
export function canonicalJson(value: unknown): string {
  return JSON.stringify(value, (_key, v: unknown) => v !== null && typeof v === 'object' && !Array.isArray(v)
    ? Object.fromEntries(Object.entries(v as Record<string, unknown>).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)))
    : v)
}
