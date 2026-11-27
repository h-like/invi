import { test } from 'node:test'
import assert from 'node:assert/strict'
import type { Invitation, PageData } from '../api/types.ts'
import { createAutosave, type AutosaveAttempt, type AutosaveTimers } from './autosave.ts'
import { createManualWrites } from './manualWrites.ts'
import { createInvitationPersistence, STALE_VERSION, type PersistenceClient } from './persistence.ts'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(res => { resolve = res })
  return { promise, resolve }
}
const drain = () => new Promise<void>(resolve => setImmediate(resolve))

/** Timers that only fire when the test says so. */
function manualTimers() {
  const tasks: (() => void)[] = []
  const timers: AutosaveTimers = { set: fn => tasks.push(fn), clear: () => {}, now: () => 0 }
  return { timers, fireAll: () => { for (const fn of tasks.splice(0)) fn() } }
}

test('a second manual write while one runs is refused at once, without calling it', async () => {
  const writes = createManualWrites({ hold: async () => () => {} })
  const first = deferred<string>()
  let secondCalled = false
  const a = writes.run(() => first.promise)
  // Same tick as the first click, before React could disable the button.
  const b = await writes.run(async () => { secondCalled = true; return 'second' })
  assert.equal(b, undefined)
  assert.equal(secondCalled, false)
  assert.equal(writes.isRunning(), true)
  first.resolve('first')
  assert.equal(await a, 'first')
  assert.equal(writes.isRunning(), false)
})

test('the write starts only once the hold is granted, and the hold is released even if it throws', async () => {
  const granted = deferred<void>()
  let released = 0
  const writes = createManualWrites({ hold: async () => { await granted.promise; return () => { released++ } } })
  let started = false
  const run = writes.run(async () => { started = true; throw new Error('boom') })
  await drain()
  assert.equal(started, false, 'waits for the autosave in flight')
  granted.resolve()
  await assert.rejects(run, /boom/)
  assert.equal(started, true)
  assert.equal(released, 1)
  assert.equal(writes.isRunning(), false)
})

function page(name: string): PageData {
  return { blocks: [{ id: 'hero-1', type: 'hero', order: 0, visible: true, style: {}, content: { groomName: name } }] }
}

test('a manual save right after an autosave reads the version that autosave adopted, so it is not stale', async () => {
  // The editor in miniature: `latest` stands for invitationRef/draftRef.
  let stored: Invitation = { id: 'test-id', slug: 'test', templateId: 't', weddingDate: '2027-05-22', status: 'DRAFT', plan: 'FREE', pageData: page('처음'), langVariants: null, version: 3 }
  const autosaveResponse = deferred<void>()
  const sent: number[] = []
  const client: PersistenceClient = {
    updatePageData: async (_id, pageData, version) => {
      sent.push(version)
      if (sent.length === 1) await autosaveResponse.promise
      if (version !== stored.version) throw Object.assign(new Error('stale'), { status: 409, code: STALE_VERSION })
      stored = { ...stored, pageData, version: version + 1 }
      return stored
    },
    updateWeddingDate: async () => { throw new Error('unexpected') },
    publishInvitation: async () => { throw new Error('unexpected') },
    unpublishInvitation: async () => { throw new Error('unexpected') },
    getInvitation: async () => stored,
  }
  const persistence = createInvitationPersistence(client)
  const latest = { invitation: stored, page: page('자동 저장 중') }
  const { timers, fireAll } = manualTimers()
  const autosave = createAutosave({
    timers,
    save: async (): Promise<AutosaveAttempt> => {
      const result = await persistence.run(latest.invitation, latest.page, false)
      if (result.kind !== 'completed') return 'failed'
      latest.invitation = result.invitation
      return 'saved'
    },
  })
  const writes = createManualWrites(autosave)

  autosave.schedule()
  fireAll() // the autosave is now in flight at version 3
  latest.page = page('저장 버튼')
  // Clicked while it is in flight: the write reads `latest` inside, after the autosave settles.
  const manual = writes.run(() => persistence.run(latest.invitation, latest.page, false))
  await drain()
  assert.deepEqual(sent, [3], 'the manual save waits instead of colliding')
  autosaveResponse.resolve()
  const result = await manual
  assert.equal(result?.kind, 'completed')
  assert.deepEqual(sent, [3, 4])
  assert.equal(stored.pageData?.blocks[0].content.groomName, '저장 버튼')
})
