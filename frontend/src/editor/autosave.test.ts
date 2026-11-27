import { test } from 'node:test'
import assert from 'node:assert/strict'
import type { Invitation, PageData } from '../api/types.ts'
import { autosaveGate, createAutosave, retryDelay, type AutosaveAttempt, type AutosaveTimers } from './autosave.ts'
import { createInvitationPersistence, STALE_VERSION, type PersistenceClient } from './persistence.ts'

/** A manual clock: timers fire only from `advance`, in order, with microtasks drained between. */
function fakeClock() {
  let now = 0, seq = 0
  const tasks = new Map<number, { at: number; fn: () => void }>()
  const timers: AutosaveTimers = {
    set: (fn, ms) => { const id = ++seq; tasks.set(id, { at: now + ms, fn }); return id },
    clear: handle => { tasks.delete(handle as number) },
    now: () => now,
  }
  async function advance(ms: number) {
    const end = now + ms
    for (;;) {
      await drain()
      let next: [number, { at: number; fn: () => void }] | undefined
      for (const entry of tasks) if (entry[1].at <= end && (!next || entry[1].at < next[1].at)) next = entry
      if (!next) break
      tasks.delete(next[0])
      now = next[1].at
      next[1].fn()
    }
    now = end
    await drain()
  }
  return { timers, advance, now: () => now, pending: () => tasks.size }
}
const drain = () => new Promise<void>(resolve => setImmediate(resolve))

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(res => { resolve = res })
  return { promise, resolve }
}

/** A save that records when it ran and answers from a script (default 'saved'). */
function scriptedSave(clock: ReturnType<typeof fakeClock>, script: AutosaveAttempt[] = []) {
  const calls: number[] = []
  return { calls, save: async () => { calls.push(clock.now()); return script.shift() ?? 'saved' } }
}

const options = { delayMs: 2500, maxWaitMs: 10_000, busyRetryMs: 1000, retryBaseMs: 2000, retryMaxMs: 60_000 }

test('debounce: a burst of changes is saved once, delayMs after the last change', async () => {
  const clock = fakeClock()
  const { calls, save } = scriptedSave(clock)
  const autosave = createAutosave({ ...options, save, timers: clock.timers })
  autosave.schedule()
  await clock.advance(1000)
  autosave.schedule()
  await clock.advance(1000)
  autosave.schedule()
  await clock.advance(2499)
  assert.deepEqual(calls, [])
  assert.equal(autosave.status(), 'pending')
  await clock.advance(1)
  assert.deepEqual(calls, [4500])
  assert.equal(autosave.status(), 'idle')
  await clock.advance(60_000)
  assert.equal(calls.length, 1)
})

test('steady typing is still saved after maxWaitMs', async () => {
  const clock = fakeClock()
  const { calls, save } = scriptedSave(clock)
  const autosave = createAutosave({ ...options, save, timers: clock.timers })
  for (let i = 0; i < 12; i++) { autosave.schedule(); await clock.advance(1000) }
  assert.deepEqual(calls, [10_000])
})

test('serialized: changes during a save lead to exactly one more save after it', async () => {
  const clock = fakeClock()
  const first = deferred<AutosaveAttempt>()
  const calls: number[] = []
  let inFlight = 0, maxInFlight = 0
  const autosave = createAutosave({
    ...options,
    timers: clock.timers,
    save: async () => {
      calls.push(clock.now())
      maxInFlight = Math.max(maxInFlight, ++inFlight)
      try { return calls.length === 1 ? await first.promise : 'saved' } finally { inFlight-- }
    },
  })
  autosave.schedule()
  await clock.advance(2500)
  assert.equal(autosave.status(), 'saving')
  autosave.schedule()
  await clock.advance(500)
  autosave.schedule()
  autosave.flush() // a flush during the save only makes the follow-up immediate
  await clock.advance(10_000)
  assert.deepEqual(calls, [2500], 'nothing starts while the first save is in flight')
  first.resolve('saved')
  await clock.advance(0)
  assert.deepEqual(calls, [2500, 13_000])
  await clock.advance(60_000)
  assert.equal(calls.length, 2)
  assert.equal(maxInFlight, 1)
})

test('without a flush, the follow-up save waits for the debounce after the in-flight save', async () => {
  const clock = fakeClock()
  const first = deferred<AutosaveAttempt>()
  const calls: number[] = []
  const autosave = createAutosave({ ...options, timers: clock.timers, save: async () => { calls.push(clock.now()); return calls.length === 1 ? first.promise : 'saved' } })
  autosave.schedule()
  await clock.advance(2500)
  autosave.schedule()
  autosave.schedule()
  await clock.advance(1000)
  first.resolve('saved')
  await clock.advance(2499)
  assert.equal(calls.length, 1)
  await clock.advance(1)
  assert.deepEqual(calls, [2500, 6000])
})

test('a stale refusal stops autosave until resume()', async () => {
  const clock = fakeClock()
  const { calls, save } = scriptedSave(clock, ['stale'])
  const statuses: string[] = []
  const autosave = createAutosave({ ...options, save, timers: clock.timers, onStatus: s => statuses.push(s) })
  autosave.schedule()
  await clock.advance(2500)
  assert.equal(autosave.status(), 'stopped')
  autosave.schedule()
  autosave.flush()
  autosave.retryNow()
  await clock.advance(120_000)
  assert.deepEqual(calls, [2500], 'no overwrite attempt after a stale refusal')
  autosave.resume()
  autosave.schedule()
  await clock.advance(2500)
  assert.deepEqual(calls, [2500, 125_000])
  assert.deepEqual(statuses, ['pending', 'saving', 'stopped', 'idle', 'pending', 'saving', 'idle'])
})

test('stop() from a manual stale save cancels the pending autosave', async () => {
  const clock = fakeClock()
  const { calls, save } = scriptedSave(clock)
  const autosave = createAutosave({ ...options, save, timers: clock.timers })
  autosave.schedule()
  autosave.stop()
  await clock.advance(60_000)
  assert.deepEqual(calls, [])
  assert.equal(clock.pending(), 0)
})

test('failures retry with exponential backoff; changes during backoff wait for the retry', async () => {
  const clock = fakeClock()
  const { calls, save } = scriptedSave(clock, ['failed', 'failed', 'failed', 'saved'])
  const autosave = createAutosave({ ...options, save, timers: clock.timers })
  autosave.schedule()
  await clock.advance(2500)
  assert.equal(autosave.status(), 'error')
  autosave.schedule() // typing during the backoff does not hammer a failing server
  await clock.advance(1000)
  autosave.schedule()
  await clock.advance(30_000)
  // 2500 → +2000 → +4000 → +8000
  assert.deepEqual(calls, [2500, 4500, 8500, 16_500])
  assert.equal(autosave.status(), 'idle')
  assert.deepEqual([1, 2, 3, 6, 7, 10].map(n => retryDelay(n, 2000, 60_000)), [2000, 4000, 8000, 60_000, 60_000, 60_000])
})

test('retryNow() skips the remaining backoff and resets it', async () => {
  const clock = fakeClock()
  const { calls, save } = scriptedSave(clock, ['failed', 'failed', 'failed'])
  const autosave = createAutosave({ ...options, save, timers: clock.timers })
  autosave.schedule()
  await clock.advance(2500) // fails, retry armed at +2000
  await clock.advance(2000) // fails, retry armed at +4000
  autosave.retryNow()
  await clock.advance(0) // fails again, but the count restarted: next retry at +2000
  await clock.advance(2000)
  assert.deepEqual(calls, [2500, 4500, 4500, 6500])
})

test('busy (another write from this tab holds the guard) retries shortly without counting as a failure', async () => {
  const clock = fakeClock()
  const { calls, save } = scriptedSave(clock, ['busy', 'busy', 'saved'])
  const autosave = createAutosave({ ...options, save, timers: clock.timers })
  autosave.schedule()
  await clock.advance(10_000)
  assert.deepEqual(calls, [2500, 3500, 4500])
  assert.equal(autosave.status(), 'idle')
})

test('blocked (photos pending) is not retried until the editor schedules again', async () => {
  const clock = fakeClock()
  const { calls, save } = scriptedSave(clock, ['blocked'])
  const autosave = createAutosave({ ...options, save, timers: clock.timers })
  autosave.schedule()
  await clock.advance(60_000)
  assert.deepEqual(calls, [2500])
  assert.equal(autosave.status(), 'idle')
  autosave.flush() // tab hides: nothing new to save, so nothing is sent
  await clock.advance(0)
  assert.equal(calls.length, 1)
})

test('autosaveGate skips published pages and pending photos, and unchanged drafts', () => {
  const base = { published: false, photosPending: false, draftJson: '{"a":2}', baselineJson: '{"a":1}' }
  assert.equal(autosaveGate(base), 'save')
  assert.equal(autosaveGate({ ...base, photosPending: true }), 'blocked')
  assert.equal(autosaveGate({ ...base, published: true }), 'blocked')
  assert.equal(autosaveGate({ ...base, draftJson: base.baselineJson }), 'clean')
})

test('flush() (tab hidden) saves right away; with nothing unsaved it sends nothing', async () => {
  const clock = fakeClock()
  const { calls, save } = scriptedSave(clock)
  const autosave = createAutosave({ ...options, save, timers: clock.timers })
  autosave.flush()
  await clock.advance(0)
  assert.deepEqual(calls, [])
  await clock.advance(100)
  autosave.schedule()
  await clock.advance(300)
  autosave.flush()
  await clock.advance(0)
  assert.deepEqual(calls, [400])
  await clock.advance(10_000)
  assert.equal(calls.length, 1, 'the cancelled debounce does not fire again')
})

test('hold(): a manual write waits for the autosave in flight, and none starts until released', async () => {
  const clock = fakeClock()
  const first = deferred<AutosaveAttempt>()
  const calls: number[] = []
  const autosave = createAutosave({ ...options, timers: clock.timers, save: async () => { calls.push(clock.now()); return calls.length === 1 ? first.promise : 'saved' } })
  autosave.schedule()
  await clock.advance(2500)
  let held = false
  const holding = autosave.hold().then(release => { held = true; return release })
  await clock.advance(0)
  assert.equal(held, false, 'waits for the save in flight')
  first.resolve('saved')
  const release = await holding
  autosave.schedule()
  autosave.flush()
  await clock.advance(30_000)
  assert.deepEqual(calls, [2500], 'no autosave while a manual write holds the guard')
  autosave.reset() // the manual save covered everything
  release()
  await clock.advance(30_000)
  assert.deepEqual(calls, [2500])
  autosave.schedule()
  await clock.advance(2500)
  assert.deepEqual(calls, [2500, 65_000])
})

test('detach() (unmount) cancels the pending autosave', async () => {
  const clock = fakeClock()
  const { calls, save } = scriptedSave(clock)
  const autosave = createAutosave({ ...options, save, timers: clock.timers })
  autosave.schedule()
  autosave.detach()
  await clock.advance(60_000)
  assert.deepEqual(calls, [])
  autosave.attach() // StrictMode remount picks the pending change up again
  await clock.advance(2500)
  assert.equal(calls.length, 1)
})

// --- Through the real write guard -------------------------------------------------------------

function draft(name: string): PageData {
  return { blocks: [{ id: 'hero-1', type: 'hero', order: 0, visible: true, style: {}, content: { groomName: name } }] }
}
function invitation(version: number, pageData: PageData, extra: Partial<Invitation> = {}): Invitation {
  return { id: 'test-id', slug: 'test', templateId: 't', weddingDate: '2027-05-22', status: 'DRAFT', plan: 'FREE', pageData, langVariants: null, version, ...extra }
}
function staleError() { return Object.assign(new Error('다른 곳에서 먼저 저장했습니다.'), { status: 409, code: STALE_VERSION }) }

/** The editor's autosave step in miniature: latest copy in, adopted copy out. */
function editor(client: PersistenceClient, start: Invitation) {
  const persistence = createInvitationPersistence(client)
  const state = { invitation: start, page: start.pageData!, baseline: JSON.stringify(start.pageData) }
  async function save(): Promise<AutosaveAttempt> {
    const sent = JSON.stringify(state.page)
    const gate = autosaveGate({ published: state.invitation.status === 'PUBLISHED', photosPending: false, draftJson: sent, baselineJson: state.baseline })
    if (gate !== 'save') return gate
    const result = await persistence.run(state.invitation, state.page, false)
    if (result.kind === 'busy') return 'busy'
    if (result.kind === 'stale') return 'stale'
    if (result.kind === 'save-failed') return 'failed'
    state.invitation = result.kind === 'completed' ? result.invitation : result.saved
    state.baseline = sent
    return 'saved'
  }
  return { persistence, state, save }
}

test('an autosave never runs alongside a wedding-date save, and saves on top of its version', async () => {
  const clock = fakeClock()
  let stored = invitation(3, draft('처음'))
  const dateDone = deferred<void>()
  const sentVersions: number[] = []
  const client: PersistenceClient = {
    updatePageData: async (_id, page, version) => {
      sentVersions.push(version)
      if (version !== stored.version) throw staleError()
      stored = { ...stored, pageData: page, version: version + 1 }
      return stored
    },
    updateWeddingDate: async (_id, weddingDate) => { await dateDone.promise; stored = { ...stored, weddingDate, version: stored.version + 1 }; return stored },
    publishInvitation: async () => { throw new Error('unexpected') },
    unpublishInvitation: async () => { throw new Error('unexpected') },
    getInvitation: async () => stored,
  }
  const ed = editor(client, stored)
  const autosave = createAutosave({ ...options, save: ed.save, timers: clock.timers })

  const dateSave = ed.persistence.saveWeddingDate(ed.state.invitation, '2027-06-01')
  ed.state.page = draft('수정')
  autosave.schedule()
  await clock.advance(2500)
  assert.deepEqual(sentVersions, [], 'the date save holds the guard: autosave waits')
  dateDone.resolve()
  const date = await dateSave
  assert.equal(date.kind, 'saved')
  if (date.kind === 'saved' && date.own) ed.state.invitation = date.invitation
  await clock.advance(1000)
  assert.deepEqual(sentVersions, [4], 'sent on top of the date save, not refused as stale by it')
  assert.equal(stored.pageData?.blocks[0].content.groomName, '수정')
  assert.equal(stored.weddingDate, '2027-06-01')
})

test('a lost autosave response is retried on top of its own landed write, not reported stale', async () => {
  const clock = fakeClock()
  let stored = invitation(3, draft('처음'))
  const sentVersions: number[] = []
  let dropNext = true
  const client: PersistenceClient = {
    updatePageData: async (_id, page, version) => {
      sentVersions.push(version)
      if (version !== stored.version) throw staleError()
      stored = invitation(version + 1, page)
      if (dropNext) { dropNext = false; throw new TypeError('Failed to fetch') } // landed, response lost
      return stored
    },
    updateWeddingDate: async () => { throw new Error('unexpected') },
    publishInvitation: async () => { throw new Error('unexpected') },
    unpublishInvitation: async () => { throw new Error('unexpected') },
    getInvitation: async () => stored,
  }
  const ed = editor(client, stored)
  const autosave = createAutosave({ ...options, save: ed.save, timers: clock.timers })
  ed.state.page = draft('첫 입력')
  autosave.schedule()
  await clock.advance(2500)
  assert.equal(autosave.status(), 'error')
  ed.state.page = draft('이어서 입력') // typed on during the backoff
  autosave.schedule()
  await clock.advance(2000)
  assert.deepEqual(sentVersions, [3, 3, 4])
  assert.equal(autosave.status(), 'idle')
  assert.equal(stored.version, 5)
  assert.equal(stored.pageData?.blocks[0].content.groomName, '이어서 입력')
})

test('a save from another tab stops autosave without overwriting it', async () => {
  const clock = fakeClock()
  let stored = invitation(3, draft('처음'))
  let saves = 0
  const client: PersistenceClient = {
    updatePageData: async (_id, page, version) => {
      saves++
      if (version !== stored.version) throw staleError()
      stored = invitation(version + 1, page)
      return stored
    },
    updateWeddingDate: async () => { throw new Error('unexpected') },
    publishInvitation: async () => { throw new Error('unexpected') },
    unpublishInvitation: async () => { throw new Error('unexpected') },
    getInvitation: async () => stored,
  }
  const ed = editor(client, stored)
  const autosave = createAutosave({ ...options, save: ed.save, timers: clock.timers })
  stored = invitation(4, draft('다른 기기')) // another device saved first
  ed.state.page = draft('이 탭')
  autosave.schedule()
  await clock.advance(2500)
  assert.equal(autosave.status(), 'stopped')
  ed.state.page = draft('이 탭 계속')
  autosave.schedule()
  await clock.advance(120_000)
  assert.equal(saves, 1)
  assert.equal(stored.pageData?.blocks[0].content.groomName, '다른 기기')
})

test('a published invitation is never autosaved', async () => {
  const clock = fakeClock()
  let saves = 0
  const stored = invitation(3, draft('공개본'), { status: 'PUBLISHED' })
  const client: PersistenceClient = {
    updatePageData: async () => { saves++; return stored },
    updateWeddingDate: async () => stored,
    publishInvitation: async () => stored,
    unpublishInvitation: async () => stored,
    getInvitation: async () => stored,
  }
  const ed = editor(client, stored)
  const autosave = createAutosave({ ...options, save: ed.save, timers: clock.timers })
  ed.state.page = draft('반쯤 입력한 계좌')
  autosave.schedule()
  autosave.flush()
  await clock.advance(60_000)
  assert.equal(saves, 0)
})
