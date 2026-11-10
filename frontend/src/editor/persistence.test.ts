import { test } from 'node:test'
import assert from 'node:assert/strict'
import type { Invitation, PageData } from '../api/types.ts'
import { createInvitationPersistence, STALE_VERSION, type PersistenceClient } from './persistence.ts'

function draft(name = '검증 신랑'): PageData {
  return {
    futureSetting: { keep: true },
    blocks: [{ id: 'hero-1', type: 'hero', order: 0, visible: true, style: {}, content: { groomName: name, brideName: '검증 신부' } }],
  }
}
function invitation(pageData = draft()): Invitation {
  return { id: 'test-id', slug: 'test-invitation', templateId: 'test-template', weddingDate: '2027-05-22', status: 'DRAFT', plan: 'FREE', pageData, langVariants: null, version: 3 }
}
/** Methods a test doesn't care about fail loudly, so an unexpected call shows up as a failure. */
function stub(client: Partial<PersistenceClient>): PersistenceClient {
  const unexpected = async (): Promise<Invitation> => { throw new Error('unexpected call') }
  return { updatePageData: unexpected, publishInvitation: unexpected, updateWeddingDate: unexpected, getInvitation: unexpected, ...client }
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
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => { throw new Error('저장 서버 오류 (503)') },
    publishInvitation: async () => { publishCalls++; return invitation() },
  }))
  const result = await controller.run(invitation(), page, false)
  assert.deepEqual(result, { kind: 'save-failed', detail: '저장 서버 오류 (503)' })
  assert.equal(JSON.stringify(page), original)
  assert.equal(publishCalls, 0)
  assert.equal(controller.isRunning(), false)
})

test('save failure while requesting publish stops the chain before publish', async () => {
  let publishCalls = 0
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => { throw new TypeError('Failed to fetch') },
    publishInvitation: async () => { publishCalls++; return invitation() },
  }))
  assert.equal((await controller.run(invitation(), draft(), true)).kind, 'save-failed')
  assert.equal(publishCalls, 0)
  assert.equal(controller.isRunning(), false)
})

test('publish failure returns saved data instead of reporting the whole save as failed', async () => {
  const page = draft()
  const saved = invitation(page)
  const events: string[] = []
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => { events.push('save'); return saved },
    publishInvitation: async () => { events.push('publish'); throw new Error('공개 서버 오류 (500)') },
  }))
  const result = await controller.run(invitation(), page, true)
  assert.deepEqual(events, ['save', 'publish'])
  assert.deepEqual(result, { kind: 'publish-failed', saved, detail: '공개 서버 오류 (500)' })
  assert.equal(controller.isRunning(), false)
})

test('a delayed save blocks same-tick repeated and mixed save/publish requests', async () => {
  const pending = deferred<Invitation>()
  let saves = 0, publishes = 0
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => { saves++; return pending.promise },
    publishInvitation: async () => { publishes++; return { ...invitation(), status: 'PUBLISHED' } },
  }))
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
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => { saves++; return invitation() },
    publishInvitation: async () => { publishes++; return pending.promise },
  }))
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
  const controller = createInvitationPersistence(stub({
    updatePageData: async (_id, page) => {
      pages.push(page)
      if (pages.length === 1) throw new Error('temporary error')
      return invitation(page)
    },
    publishInvitation: async () => { publishCalls++; return { ...invitation(pages[1]), status: 'PUBLISHED' } },
  }))
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
    updateWeddingDate: async () => latest,
    getInvitation: async () => latest,
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
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => saved,
    publishInvitation: async () => { publishCalls++; return saved },
  }))
  assert.deepEqual(await controller.run(invitation(), draft(), true), { kind: 'completed', invitation: saved, shared: true })
  assert.equal(publishCalls, 0)
})

test('save-only success preserves response data and does not open the sharing flow', async () => {
  const page = draft()
  const saved = invitation(page)
  let publishCalls = 0
  const controller = createInvitationPersistence(stub({
    updatePageData: async (id, received) => { assert.equal(id, saved.id); assert.equal(received, page); return saved },
    publishInvitation: async () => { publishCalls++; return saved },
  }))
  assert.deepEqual(await controller.run(saved, page, false), { kind: 'completed', invitation: saved, shared: false })
  assert.deepEqual(page.futureSetting, { keep: true })
  assert.equal(publishCalls, 0)
})

test('non-Error rejections receive a useful fallback and release the lock', async () => {
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => { throw null },
    publishInvitation: async () => invitation(),
  }))
  assert.deepEqual(await controller.run(invitation(), draft(), true), { kind: 'save-failed', detail: '서버와 연결하지 못했습니다.' })
  assert.equal(controller.isRunning(), false)
})

test('an HTTP-success response without PUBLISHED status must not trigger sharing', async () => {
  const saved = invitation()
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => saved,
    publishInvitation: async () => saved,
  }))
  assert.deepEqual(await controller.run(saved, draft(), true), { kind: 'publish-failed', saved, detail: '서버에서 공개 완료 상태를 확인하지 못했습니다.' })
  assert.equal(controller.isRunning(), false)
})

test('saves send the version the editor last received', async () => {
  const versions: number[] = []
  const controller = createInvitationPersistence(stub({
    updatePageData: async (_id, page, version) => { versions.push(version); return { ...invitation(page), version: version + 1 } },
    publishInvitation: async () => invitation(),
  }))
  const result = await controller.run(invitation(), draft(), false)
  assert.deepEqual(versions, [3])
  assert.equal(result.kind === 'completed' && result.invitation.version, 4)
})

test('a newer copy saved elsewhere is reported as stale, keeps the draft and never publishes', async () => {
  const page = draft()
  const original = JSON.stringify(page)
  let publishCalls = 0
  const conflict = Object.assign(new Error('다른 곳에서 먼저 저장된 내용이 있어 저장하지 않았습니다.'), { status: 409, code: STALE_VERSION })
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => { throw conflict },
    publishInvitation: async () => { publishCalls++; return invitation() },
  }))
  const result = await controller.run(invitation(), page, true)
  assert.deepEqual(result, { kind: 'stale', detail: '다른 곳에서 먼저 저장된 내용이 있어 저장하지 않았습니다.' })
  assert.equal(JSON.stringify(page), original)
  assert.equal(publishCalls, 0)
  assert.equal(controller.isRunning(), false)
})

test('other 409s, such as photos being cleaned up, stay ordinary save failures', async () => {
  const fenced = Object.assign(new Error('정리 중이거나 삭제된 사진이 포함되어 있습니다.'), { status: 409 })
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => { throw fenced },
    publishInvitation: async () => invitation(),
  }))
  assert.equal((await controller.run(invitation(), draft(), false)).kind, 'save-failed')
})

test('a publish applied despite a lost response is confirmed instead of left for a stale retry', async () => {
  const saved = { ...invitation(), version: 4 }
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => saved,
    publishInvitation: async () => { throw new TypeError('Failed to fetch') },
    getInvitation: async () => ({ ...saved, status: 'PUBLISHED', version: 5 }),
  }))
  const result = await controller.run(invitation(), draft(), true)
  assert.equal(result.kind, 'completed')
  if (result.kind === 'completed') { assert.equal(result.invitation.version, 5); assert.equal(result.shared, true) }
})

test('a publish that did not apply, or a page changed elsewhere, stays a publish failure', async () => {
  const saved = { ...invitation(), version: 4 }
  for (const current of [saved, { ...saved, status: 'PUBLISHED' as const, version: 6 }]) {
    const controller = createInvitationPersistence(stub({
      updatePageData: async () => saved,
      publishInvitation: async () => { throw new Error('공개 서버 오류 (500)') },
      getInvitation: async () => current,
    }))
    assert.deepEqual(await controller.run(invitation(), draft(), true), { kind: 'publish-failed', saved, detail: '공개 서버 오류 (500)' })
  }
})

test('a wedding-date save and a page save never run at the same time', async () => {
  const pending = deferred<Invitation>()
  let saves = 0
  const controller = createInvitationPersistence(stub({
    updateWeddingDate: async () => pending.promise,
    updatePageData: async () => { saves++; return invitation() },
  }))
  const date = controller.saveWeddingDate(invitation(), '2027-06-01')
  assert.equal(controller.isRunning(), true)
  assert.deepEqual(await controller.run(invitation(), draft(), false), { kind: 'busy' })
  assert.deepEqual(await controller.saveWeddingDate(invitation(), '2027-06-02'), { kind: 'busy' })
  assert.equal(saves, 0)
  pending.resolve({ ...invitation(), weddingDate: '2027-06-01', version: 4 })
  const result = await date
  assert.equal(result.kind === 'saved' && result.invitation.version, 4)
  assert.equal(controller.isRunning(), false)
})

test('a running page save turns a wedding-date save away, and a failed date save frees the guard', async () => {
  const pending = deferred<Invitation>()
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => pending.promise,
    updateWeddingDate: async () => { throw new Error('예식일 서버 오류 (500)') },
  }))
  const save = controller.run(invitation(), draft(), false)
  assert.deepEqual(await controller.saveWeddingDate(invitation(), '2027-06-01'), { kind: 'busy' })
  pending.resolve(invitation())
  await save
  assert.deepEqual(await controller.saveWeddingDate(invitation(), '2027-06-01'), { kind: 'failed', detail: '예식일 서버 오류 (500)' })
  assert.equal(controller.isRunning(), false)
})

const staleError = () => Object.assign(new Error('다른 곳에서 먼저 저장된 내용이 있어 저장하지 않았습니다.'), { status: 409, code: STALE_VERSION })
/** The same page as the server returns it: jsonb reorders object keys. */
function reordered(page: PageData): PageData {
  const [hero] = page.blocks
  return { blocks: [{ visible: hero.visible, type: hero.type, style: hero.style, order: hero.order, id: hero.id, content: { brideName: hero.content.brideName, groomName: hero.content.groomName } }], futureSetting: page.futureSetting } as PageData
}

test('a save that landed without a response is saved on top of, not reported as stale', async () => {
  const first = draft('처음 이름'), edited = draft('고친 이름')
  const sent: number[] = []
  let calls = 0
  const controller = createInvitationPersistence(stub({
    updatePageData: async (_id, page, version) => {
      sent.push(version)
      calls++
      if (calls === 1) throw new TypeError('Failed to fetch')
      if (version === 3) throw staleError()
      return { ...invitation(page), version: version + 1 }
    },
    getInvitation: async () => ({ ...invitation(reordered(first)), version: 4 }),
  }))
  assert.equal((await controller.run(invitation(), first, false)).kind, 'save-failed')
  const retry = await controller.run(invitation(), edited, false)
  assert.deepEqual(sent, [3, 3, 4])
  assert.equal(retry.kind === 'completed' && retry.invitation.version, 5)
})

test('after a lost save, a copy someone else changed is still stale', async () => {
  let calls = 0
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => { calls++; if (calls === 1) throw new TypeError('Failed to fetch'); throw staleError() },
    getInvitation: async () => ({ ...invitation(draft('다른 기기')), version: 4 }),
  }))
  await controller.run(invitation(), draft('처음 이름'), false)
  assert.equal((await controller.run(invitation(), draft('처음 이름'), false)).kind, 'stale')
  assert.equal(calls, 2)
})

test('a wedding-date save that landed without a response does not make the next page save stale', async () => {
  const sent: number[] = []
  const controller = createInvitationPersistence(stub({
    updateWeddingDate: async () => { throw new TypeError('Failed to fetch') },
    updatePageData: async (_id, page, version) => {
      sent.push(version)
      if (version === 3) throw staleError()
      return { ...invitation(page), weddingDate: '2027-06-01', version: version + 1 }
    },
    getInvitation: async () => ({ ...invitation(), weddingDate: '2027-06-01', version: 4 }),
  }))
  assert.equal((await controller.saveWeddingDate(invitation(), '2027-06-01')).kind, 'failed')
  const save = await controller.run(invitation(), draft('고친 이름'), false)
  assert.deepEqual(sent, [3, 4])
  assert.equal(save.kind === 'completed' && save.invitation.version, 5)
})

test('after a lost wedding-date save, a date changed by someone else is still stale', async () => {
  const controller = createInvitationPersistence(stub({
    updateWeddingDate: async () => { throw new TypeError('Failed to fetch') },
    updatePageData: async () => { throw staleError() },
    getInvitation: async () => ({ ...invitation(), weddingDate: '2027-09-09', version: 4 }),
  }))
  await controller.saveWeddingDate(invitation(), '2027-06-01')
  assert.equal((await controller.run(invitation(), draft(), false)).kind, 'stale')
})

test('a lost wedding-date save does not replace a lost page save from the same copy', async () => {
  const first = draft('처음 이름')
  let calls = 0
  const controller = createInvitationPersistence(stub({
    updateWeddingDate: async () => { throw new TypeError('Failed to fetch') },
    updatePageData: async (_id, page, version) => {
      calls++
      if (calls === 1) throw new TypeError('Failed to fetch')
      if (version === 3) throw staleError()
      return { ...invitation(page), version: version + 1 }
    },
    // Only the page save landed; the date did not.
    getInvitation: async () => ({ ...invitation(reordered(first)), version: 4 }),
  }))
  await controller.run(invitation(), first, false)
  await controller.saveWeddingDate(invitation(), '2027-06-01')
  assert.equal((await controller.run(invitation(), first, false)).kind, 'completed')
})

test('a new date saved over this tab\'s own lost date save is adopted, so the next page save is not stale', async () => {
  let dateCalls = 0
  const sent: number[] = []
  const controller = createInvitationPersistence(stub({
    updateWeddingDate: async (_id, date) => {
      dateCalls++
      if (dateCalls === 1) throw new TypeError('Failed to fetch')
      return { ...invitation(), weddingDate: date, version: 5 }
    },
    updatePageData: async (_id, page, version) => {
      sent.push(version)
      if (version !== 5) throw staleError()
      return { ...invitation(page), version: 6 }
    },
  }))
  await controller.saveWeddingDate(invitation(), '2027-06-01')
  const date = await controller.saveWeddingDate(invitation(), '2027-06-02')
  assert.equal(date.kind === 'saved' && date.own, true)
  const save = await controller.run({ ...invitation(), weddingDate: '2027-06-02', version: 5 }, draft('고친 이름'), false)
  assert.deepEqual(sent, [5])
  assert.equal(save.kind, 'completed')
})

test('a date saved over this tab\'s own lost page save is adopted', async () => {
  const first = draft('처음 이름')
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => { throw new TypeError('Failed to fetch') },
    updateWeddingDate: async (_id, date) => ({ ...invitation(reordered(first)), weddingDate: date, version: 5 }),
  }))
  await controller.run(invitation(), first, false)
  const date = await controller.saveWeddingDate(invitation(), '2027-06-01')
  assert.equal(date.kind === 'saved' && date.own, true)
})

test('after a date save is adopted over a lost page save, the next page save goes out on its version', async () => {
  const first = draft('처음 이름')
  let pageCalls = 0
  const sent: number[] = []
  const controller = createInvitationPersistence(stub({
    updatePageData: async (_id, page, version) => {
      pageCalls++
      if (pageCalls === 1) throw new TypeError('Failed to fetch')
      sent.push(version)
      if (version !== 5) throw staleError()
      return { ...invitation(page), version: 6 }
    },
    updateWeddingDate: async (_id, date) => ({ ...invitation(reordered(first)), weddingDate: date, version: 5 }),
  }))
  await controller.run(invitation(), first, false)
  const date = await controller.saveWeddingDate(invitation(), '2027-06-01')
  assert.ok(date.kind === 'saved' && date.own)
  // The builder takes the adopted copy whole, as it does after a page save.
  const save = await controller.run(date.invitation, draft('고친 이름'), false)
  assert.deepEqual(sent, [5])
  assert.equal(save.kind, 'completed')
})

test('a date saved over this tab\'s own lost publish is adopted with the published status', async () => {
  const saved = { ...invitation(), version: 4 }
  const controller = createInvitationPersistence(stub({
    updatePageData: async () => saved,
    publishInvitation: async () => { throw new TypeError('Failed to fetch') },
    getInvitation: async () => { throw new TypeError('Failed to fetch') },
    updateWeddingDate: async (_id, date) => ({ ...saved, status: 'PUBLISHED', weddingDate: date, version: 6 }),
  }))
  assert.equal((await controller.run(invitation(), draft(), true)).kind, 'publish-failed')
  const date = await controller.saveWeddingDate(saved, '2027-06-01')
  assert.ok(date.kind === 'saved' && date.own)
  assert.equal(date.invitation.status, 'PUBLISHED')
})

test('two steps past the copy with someone else\'s page change is not adopted', async () => {
  let dateCalls = 0
  const controller = createInvitationPersistence(stub({
    updateWeddingDate: async (_id, date) => {
      dateCalls++
      if (dateCalls === 1) throw new TypeError('Failed to fetch')
      return { ...invitation(draft('다른 기기')), weddingDate: date, version: 5 }
    },
  }))
  await controller.saveWeddingDate(invitation(), '2027-06-01')
  const date = await controller.saveWeddingDate(invitation(), '2027-06-02')
  assert.equal(date.kind === 'saved' && date.own, false)
})

test('a refused date save (4xx) is not remembered as a write that may have landed', async () => {
  let dateCalls = 0
  const controller = createInvitationPersistence(stub({
    updateWeddingDate: async (_id, date) => {
      dateCalls++
      if (dateCalls === 1) throw Object.assign(new Error('예식일이 올바르지 않습니다.'), { status: 400 })
      return { ...invitation(), weddingDate: date, version: 5 }
    },
  }))
  await controller.saveWeddingDate(invitation(), '2020-01-01')
  // Nothing of this tab's landed, so a two-step gap is someone else's save.
  const date = await controller.saveWeddingDate(invitation(), '2027-06-02')
  assert.equal(date.kind === 'saved' && date.own, false)
})

test('a publish that landed while offline is picked up by the retry instead of a stale error', async () => {
  const saved = { ...invitation(), version: 4 }
  let reads = 0
  const controller = createInvitationPersistence(stub({
    updatePageData: async (_id, page, version) => {
      if (version === 4) throw staleError()
      // Saving on top of the landed publish keeps the page published.
      return { ...invitation(page), status: version === 5 ? 'PUBLISHED' : 'DRAFT', version: version + 1 }
    },
    publishInvitation: async () => { throw new TypeError('Failed to fetch') },
    getInvitation: async () => {
      reads++
      if (reads === 1) throw new TypeError('Failed to fetch')
      return { ...saved, status: 'PUBLISHED', version: 5 }
    },
  }))
  const first = await controller.run({ ...invitation(), version: 3 }, draft(), true)
  assert.equal(first.kind, 'publish-failed')
  // The caller adopts `saved` (version 4); the publish already took it to 5.
  const retry = await controller.run(saved, draft(), true)
  assert.equal(retry.kind, 'completed')
  if (retry.kind === 'completed') { assert.equal(retry.shared, true); assert.equal(retry.invitation.version, 6) }
})
