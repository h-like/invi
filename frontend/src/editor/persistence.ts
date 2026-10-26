import type { Invitation, PageData } from '../api/types.ts'

export interface PersistenceClient {
  updatePageData: (id: string, pageData: PageData) => Promise<Invitation>
  publishInvitation: (id: string) => Promise<Invitation>
}

export type PersistenceOutcome =
  | { kind: 'busy' }
  | { kind: 'save-failed'; detail: string }
  | { kind: 'publish-failed'; saved: Invitation; detail: string }
  | { kind: 'completed'; invitation: Invitation; shared: boolean }

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
 */
export function createInvitationPersistence(client: PersistenceClient) {
  let running = false

  return {
    isRunning: () => running,
    async run(invitation: Invitation, pageData: PageData, publish: boolean): Promise<PersistenceOutcome> {
      if (running) return { kind: 'busy' }
      running = true
      try {
        let saved: Invitation
        try {
          saved = await client.updatePageData(invitation.id, pageData)
        } catch (error) {
          return { kind: 'save-failed', detail: errorDetail(error, '서버와 연결하지 못했습니다.') }
        }

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
          return { kind: 'publish-failed', saved, detail: errorDetail(error, '공개 요청 결과를 확인하지 못했습니다.') }
        }
      } finally {
        running = false
      }
    },
  }
}
