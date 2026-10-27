import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createSaveBeforeLeave, needsLeaveWarning, preventEditorUnload, shouldBlockEditorNavigation } from './navigation.ts'

const editor = { pathname: '/e/test-id', search: '' }
const home = { pathname: '/', search: '' }
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(res => { resolve = res })
  return { promise, resolve }
}

test('clean, idle editors do not block internal navigation or warn on unload', () => {
  assert.equal(needsLeaveWarning({ dirty: false, busy: false }), false)
  assert.equal(shouldBlockEditorNavigation({ dirty: false, busy: false }, editor, home), false)
})

test('dirty editors block a different route or query but not the same location', () => {
  const state = { dirty: true, busy: false }
  assert.equal(shouldBlockEditorNavigation(state, editor, home), true)
  assert.equal(shouldBlockEditorNavigation(state, editor, { ...editor, search: '?mode=other' }), true)
  assert.equal(shouldBlockEditorNavigation(state, editor, { ...editor }), false)
})

test('pending save/publish blocks leaving even if the draft was previously saved', () => {
  assert.equal(shouldBlockEditorNavigation({ dirty: false, busy: true }, editor, home), true)
  assert.equal(needsLeaveWarning({ dirty: false, busy: true }), true)
})

test('clean unload leaves the browser event unchanged', () => {
  let prevented = false
  const event = { preventDefault() { prevented = true }, returnValue: 'unchanged' }
  assert.equal(preventEditorUnload(event, { dirty: false, busy: false }), false)
  assert.equal(prevented, false)
  assert.equal(event.returnValue, 'unchanged')
})

test('dirty and busy unloads request the browser native warning', () => {
  for (const state of [{ dirty: true, busy: false }, { dirty: false, busy: true }]) {
    let prevented = false
    const event = { preventDefault() { prevented = true }, returnValue: 'unchanged' }
    assert.equal(preventEditorUnload(event, state), true)
    assert.equal(prevented, true)
    assert.equal(event.returnValue, '')
  }
})

test('save-and-leave proceeds once, only after saving succeeds', async () => {
  const gate = createSaveBeforeLeave()
  const events: string[] = []
  assert.equal(await gate.run(async () => { events.push('save'); return true }, () => true, () => { events.push('leave') }), true)
  assert.deepEqual(events, ['save', 'leave'])
  assert.equal(gate.isRunning(), false)
})

test('save failure stays in the editor and permits a retry', async () => {
  const gate = createSaveBeforeLeave()
  let leaves = 0
  assert.equal(await gate.run(async () => false, () => true, () => { leaves++ }), false)
  assert.equal(leaves, 0)
  assert.equal(gate.isRunning(), false)
  assert.equal(await gate.run(async () => true, () => true, () => { leaves++ }), true)
  assert.equal(leaves, 1)
})

test('repeated save-and-leave clicks use one save', async () => {
  const pending = deferred<boolean>()
  const gate = createSaveBeforeLeave()
  let saves = 0, leaves = 0
  const save = () => { saves++; return pending.promise }
  const first = gate.run(save, () => true, () => { leaves++ })
  assert.equal(await gate.run(save, () => true, () => { leaves++ }), false)
  assert.equal(saves, 1)
  pending.resolve(true)
  assert.equal(await first, true)
  assert.equal(leaves, 1)
})

test('canceling navigation while saving prevents a late redirect', async () => {
  const pending = deferred<boolean>()
  const gate = createSaveBeforeLeave()
  let leaves = 0
  const first = gate.run(() => pending.promise, () => true, () => { leaves++ })
  gate.cancel()
  pending.resolve(true)
  assert.equal(await first, false)
  assert.equal(leaves, 0)
  assert.equal(gate.isRunning(), false)
})

test('a different pending destination is not used by an earlier save callback', async () => {
  const pending = deferred<boolean>()
  const gate = createSaveBeforeLeave()
  let sameDestination = true, leaves = 0
  const first = gate.run(() => pending.promise, () => sameDestination, () => { leaves++ })
  sameDestination = false
  pending.resolve(true)
  assert.equal(await first, false)
  assert.equal(leaves, 0)
})

test('unexpected save rejection never leaves or keeps the navigation lock stuck', async () => {
  const gate = createSaveBeforeLeave()
  let leaves = 0
  assert.equal(await gate.run(async () => { throw new Error('failed') }, () => true, () => { leaves++ }), false)
  assert.equal(gate.isRunning(), false)
  assert.equal(leaves, 0)
})
