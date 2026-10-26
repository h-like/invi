import { test } from 'node:test'
import assert from 'node:assert/strict'
import type { Invitation, PageData } from '../api/types.ts'
import { createInvitationPersistence, type PersistenceClient } from './persistence.ts'

function draft(name = '검증 신랑'): PageData {
  return {
    futureSetting: { keep: true },
    blocks: [{ id: 'hero-1', type: 'hero', order: 0, visible: true, style: {}, content: { groomName: name, brideName: '검증 신부' } }],
  }
}
function invitation(pageData = draft()): Invitation {
  return { id: 'test-id', slug: 'test-invitation', templateId: 'test-template', weddingDate: '2027-05-22', status: 'DRAFT', plan: 'FREE', pageData, langVariants: null }
}
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

test('save failure leaves the draft untouched and never calls publish', async () => {
  const page = draft()
  const original = JSON.stringify(page)
  let publishCalls = 0
  const controller = createInvitationPersistence({
    updatePageData: async () => { throw new Error('저장 서버 오류 (503)') },
    publishInvitation: async () => { publishCalls++; return invitation() },
  })
  const result = await controller.run(invitation(), page, false)
  assert.deepEqual(result, { kind: 'save-failed', detail: '저장 서버 오류 (503)' })
  assert.equal(JSON.stringify(page), original)
  assert.equal(publishCalls, 0)
  assert.equal(controller.isRunning(), false)
})

test('save failure while requesting publish stops the chain before publish', async () => {
  let publishCalls = 0
  const controller = createInvitationPersistence({
    updatePageData: async () => { throw new TypeError('Failed to fetch') },
    publishInvitation: async () => { publishCalls++; return invitation() },
  })
  assert.equal((await controller.run(invitation(), draft(), true)).kind, 'save-failed')
  assert.equal(publishCalls, 0)
  assert.equal(controller.isRunning(), false)
})

test('publish failure returns saved data instead of reporting the whole save as failed', async () => {
  const page = draft()
  const saved = invitation(page)
  const events: string[] = []
  const controller = createInvitationPersistence({
    updatePageData: async () => { events.push('save'); return saved },
    publishInvitation: async () => { events.push('publish'); throw new Error('공개 서버 오류 (500)') },
  })
  const result = await controller.run(invitation(), page, true)
  assert.deepEqual(events, ['save', 'publish'])
  assert.deepEqual(result, { kind: 'publish-failed', saved, detail: '공개 서버 오류 (500)' })
  assert.equal(controller.isRunning(), false)
})

test('a delayed save blocks same-tick repeated and mixed save/publish requests', async () => {
  const pending = deferred<Invitation>()
  let saves = 0, publishes = 0
  const controller = createInvitationPersistence({
    updatePageData: async () => { saves++; return pending.promise },
    publishInvitation: async () => { publishes++; return { ...invitation(), status: 'PUBLISHED' } },
  })
  const first = controller.run(invitation(), draft(), true)
  assert.equal(controller.isRunning(), true)
  assert.deepEqual(await controller.run(invitation(), draft(), true), { kind: 'busy' })
  assert.deepEqual(await controller.run(invitation(), draft(), false), { kind: 'busy' })
  assert.equal(saves, 1)
  assert.equal(publishes, 0)
  pending.resolve(invitation())
  assert.equal((await first).kind, 'completed')
  assert.equal(publishes, 1)
  assert.equal(controller.isRunning(), false)
})

test('the lock remains held until delayed publish finishes, not merely until save finishes', async () => {
  const pending = deferred<Invitation>()
  let saves = 0, publishes = 0
  const controller = createInvitationPersistence({
    updatePageData: async () => { saves++; return invitation() },
    publishInvitation: async () => { publishes++; return pending.promise },
  })
  const first = controller.run(invitation(), draft(), true)
  await Promise.resolve()
  assert.equal(publishes, 1)
  assert.equal(controller.isRunning(), true)
  assert.deepEqual(await controller.run(invitation(), draft(), false), { kind: 'busy' })
  assert.equal(saves, 1)
  pending.resolve({ ...invitation(), status: 'PUBLISHED' })
  const result = await first
  assert.equal(result.kind, 'completed')
  assert.equal(controller.isRunning(), false)
})

test('save failure unlocks retry and retry submits the latest edited draft', async () => {
  const pages: PageData[] = []
  let publishCalls = 0
  const controller = createInvitationPersistence({
    updatePageData: async (_id, page) => {
      pages.push(page)
      if (pages.length === 1) throw new Error('temporary error')
      return invitation(page)
    },
    publishInvitation: async () => { publishCalls++; return { ...invitation(pages[1]), status: 'PUBLISHED' } },
  })
  const initial = draft('첫 입력')
  const edited = draft('실패 후 수정')
  assert.equal((await controller.run(invitation(), initial, true)).kind, 'save-failed')
  const result = await controller.run(invitation(), edited, true)
  assert.equal(result.kind, 'completed')
  assert.equal(pages.length, 2)
  assert.equal(pages[1], edited)
  assert.equal(publishCalls, 1)
  assert.equal(initial.blocks[0].content.groomName, '첫 입력')
})

test('retry after publish failure saves new edits before attempting publish again', async () => {
  const events: string[] = []
  let publishCalls = 0
  let latest = invitation()
  const client: PersistenceClient = {
    updatePageData: async (_id, page) => { events.push('save:' + page.blocks[0].content.groomName); latest = invitation(page); return latest },
    publishInvitation: async () => {
      events.push('publish')
      if (++publishCalls === 1) throw new Error('temporary publish failure')
      return { ...latest, status: 'PUBLISHED' }
    },
  }
  const controller = createInvitationPersistence(client)
  const first = await controller.run(invitation(), draft('원본'), true)
  assert.equal(first.kind, 'publish-failed')
  const next = await controller.run(invitation(), draft('수정본'), true)
  assert.equal(next.kind, 'completed')
  assert.deepEqual(events, ['save:원본', 'publish', 'save:수정본', 'publish'])
  if (next.kind === 'completed') assert.equal(next.invitation.pageData?.blocks[0].content.groomName, '수정본')
})

test('already-published saved responses skip a duplicate publish call', async () => {
  let publishCalls = 0
  const saved = { ...invitation(), status: 'PUBLISHED' as const }
  const controller = createInvitationPersistence({
    updatePageData: async () => saved,
    publishInvitation: async () => { publishCalls++; return saved },
  })
  assert.deepEqual(await controller.run(invitation(), draft(), true), { kind: 'completed', invitation: saved, shared: true })
  assert.equal(publishCalls, 0)
})

test('save-only success preserves response data and does not open the sharing flow', async () => {
  const page = draft()
  const saved = invitation(page)
  let publishCalls = 0
  const controller = createInvitationPersistence({
    updatePageData: async (id, received) => { assert.equal(id, saved.id); assert.equal(received, page); return saved },
    publishInvitation: async () => { publishCalls++; return saved },
  })
  assert.deepEqual(await controller.run(saved, page, false), { kind: 'completed', invitation: saved, shared: false })
  assert.deepEqual(page.futureSetting, { keep: true })
  assert.equal(publishCalls, 0)
})

test('non-Error rejections receive a useful fallback and release the lock', async () => {
  const controller = createInvitationPersistence({
    updatePageData: async () => { throw null },
    publishInvitation: async () => invitation(),
  })
  assert.deepEqual(await controller.run(invitation(), draft(), true), { kind: 'save-failed', detail: '서버와 연결하지 못했습니다.' })
  assert.equal(controller.isRunning(), false)
})

test('an HTTP-success response without PUBLISHED status must not trigger sharing', async () => {
  const saved = invitation()
  const controller = createInvitationPersistence({
    updatePageData: async () => saved,
    publishInvitation: async () => saved,
  })
  assert.deepEqual(await controller.run(saved, draft(), true), { kind: 'publish-failed', saved, detail: '서버에서 공개 완료 상태를 확인하지 못했습니다.' })
  assert.equal(controller.isRunning(), false)
})
