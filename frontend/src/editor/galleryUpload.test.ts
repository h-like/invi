import test from 'node:test'
import assert from 'node:assert/strict'
import { setImmediate as nextTurn } from 'node:timers/promises'
import type { MediaAsset } from '../api/types.ts'
import type { PhotoUploadOptions } from './coverUpload.ts'
import { createGalleryUpload, type GallerySnapshot, type GalleryUploadOptions } from './galleryUpload.ts'
import type { PreparedPhoto } from './preparePhoto.ts'

const oldUrl = 'https://media.example.com/old.webp'
const file = (name: string) => new File(['source image'], name + '.heic', { type: 'image/heic' })
const prepared = (source: File): PreparedPhoto => ({
  file: new File(['optimized'], source.name.replace(/\.heic$/, '.webp'), { type: 'image/webp' }),
  width: 1707, height: 2560, originalBytes: source.size,
})
const asset = (output: File, sortOrder = 0): MediaAsset => ({
  id: output.name, url: 'https://media.example.com/' + output.name, type: 'GALLERY', sortOrder,
})
function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
type Queue = ReturnType<typeof createGalleryUpload>
type Dependencies = Pick<GalleryUploadOptions, 'prepare' | 'upload' | 'verify'>
function setup(deps: Partial<Dependencies> = {}, images: readonly string[] = []) {
  const changes: string[][] = []
  const pending: boolean[] = []
  const queue = createGalleryUpload({
    invitationId: 'invitation', images,
    prepare: async source => prepared(source),
    upload: async (_id, output, sortOrder) => asset(output, sortOrder),
    verify: async () => {}, ...deps,
    onChange: urls => { changes.push([...urls]) },
    onPendingChange: value => { pending.push(value) },
  })
  return { queue, changes, pending }
}
// Subscribe instead of polling; a missing transition fails with the final state.
function waitFor(queue: Queue, predicate: (state: GallerySnapshot) => boolean): Promise<GallerySnapshot> {
  const initial = queue.getSnapshot()
  if (predicate(initial)) return Promise.resolve(initial)
  return new Promise((resolve, reject) => {
    let unsubscribe = () => {}
    const timer = setTimeout(() => {
      unsubscribe()
      reject(new Error('Gallery transition timed out: ' + JSON.stringify(queue.getSnapshot())))
    }, 2000)
    const check = () => {
      const state = queue.getSnapshot()
      if (!predicate(state)) return
      clearTimeout(timer); unsubscribe(); resolve(state)
    }
    unsubscribe = queue.subscribe(check)
    check()
  })
}
const idle = (queue: Queue) => waitFor(queue, state => !state.working)
const urls = (queue: Queue) => queue.getSnapshot().items.flatMap(item => item.url ? [item.url] : [])

test('maximum 20 reserves both failed and queued rows, not just successful URLs', async () => {
  const gate = deferred<PreparedPhoto>()
  const existing = Array.from({ length: 17 }, (_, i) => 'https://media.example.com/old-' + i + '.webp')
  const { queue, changes } = setup({ prepare: async source => {
    if (source.name === 'failed.heic') throw new Error('decode failed')
    if (source.name === 'current.heic') return gate.promise
    return prepared(source)
  } }, existing)
  const current = file('current')
  queue.add([file('failed'), current, file('queued')])
  await waitFor(queue, state => state.items[17]?.status === 'failed' && state.items[18]?.status === 'preparing')
  assert.equal(queue.getSnapshot().items[19].status, 'queued')
  const before = queue.getSnapshot()
  assert.throws(() => queue.add([file('overflow')]), /최대 20장/)
  assert.throws(() => queue.addUrl('https://media.example.com/overflow.webp'), /최대 20장/)
  assert.equal(queue.getSnapshot(), before)
  assert.deepEqual(changes, [])
  gate.resolve(prepared(current))
  const done = await idle(queue)
  assert.equal(done.items.length, 20)
  assert.equal(done.items[17].status, 'failed')
  assert.equal(done.pending, true)
})

test('overflow rejects the whole selection without reserving or converting any files', async () => {
  let converted = 0
  const existing = Array.from({ length: 19 }, (_, i) => 'https://media.example.com/' + i + '.webp')
  const { queue, changes, pending } = setup({ prepare: async source => { converted++; return prepared(source) } }, existing)
  const before = queue.getSnapshot()
  assert.throws(() => queue.add([file('a'), file('b')]), /추가 가능한 1장/)
  assert.equal(queue.getSnapshot(), before)
  assert.equal(converted, 0)
  assert.deepEqual(changes, [])
  assert.deepEqual(pending, [])
  queue.add([file('accepted')])
  await idle(queue)
  assert.equal(converted, 1)
  assert.equal(queue.getSnapshot().items.length, 20)
  assert.deepEqual(urls(queue), [...existing, 'https://media.example.com/accepted.webp'])
})

test('existing galleries over 20 are preserved without truncation and reject new additions', () => {
  const existing = Array.from({ length: 23 }, (_, i) => 'https://media.example.com/' + i + '.webp')
  const { queue, changes, pending } = setup({}, existing)
  assert.deepEqual(urls(queue), existing)
  assert.equal(queue.getSnapshot().pending, false)
  assert.throws(() => queue.add([file('extra')]), /추가 가능한 0장/)
  assert.throws(() => queue.addUrl('https://media.example.com/extra.webp'), /최대 20장/)
  queue.add([])
  assert.deepEqual(urls(queue), existing)
  assert.deepEqual(changes, [])
  assert.deepEqual(pending, [])
})

test('decodes sequentially and does not start the next file until upload and verification settle', async () => {
  const sources = [file('a'), file('b'), file('c')]
  const conversions = sources.map(() => deferred<PreparedPhoto>())
  const uploads = sources.map(() => deferred<MediaAsset>())
  const verifications = sources.map(() => deferred<void>())
  const calls: string[] = []
  let decoding = 0, maximum = 0
  const { queue } = setup({
    prepare: async source => {
      const index = sources.indexOf(source)
      calls.push('prepare:' + index); maximum = Math.max(maximum, ++decoding)
      try { return await conversions[index].promise } finally { decoding-- }
    },
    upload: async (_id, output) => {
      const index = sources.findIndex(source => prepared(source).file.name === output.name)
      calls.push('upload:' + index); return uploads[index].promise
    },
    verify: async url => {
      const index = sources.findIndex(source => asset(prepared(source).file).url === url)
      calls.push('verify:' + index); return verifications[index].promise
    },
  })
  queue.add(sources)
  for (let i = 0; i < sources.length; i++) {
    await waitFor(queue, state => state.items[i]?.status === 'preparing')
    assert.equal(calls.filter(call => call.startsWith('prepare:')).length, i + 1)
    conversions[i].resolve(prepared(sources[i]))
    await waitFor(queue, state => state.items[i]?.status === 'uploading')
    assert.equal(calls.filter(call => call.startsWith('prepare:')).length, i + 1)
    uploads[i].resolve(asset(prepared(sources[i]).file, i))
    await waitFor(queue, state => state.items[i]?.status === 'verifying')
    assert.equal(calls.filter(call => call.startsWith('prepare:')).length, i + 1)
    verifications[i].resolve(undefined)
  }
  await idle(queue)
  assert.equal(maximum, 1)
  assert.deepEqual(calls, ['prepare:0', 'upload:0', 'verify:0', 'prepare:1', 'upload:1', 'verify:1', 'prepare:2', 'upload:2', 'verify:2'])
})

test('uploads each optimized file with invitation, gallery sort order, shared signal and isolated progress', async () => {
  const sources = [file('a'), file('b')]
  const outputs = sources.map(prepared)
  const gates = sources.map(() => deferred<MediaAsset>())
  const progress: PhotoUploadOptions[] = []
  const signals: AbortSignal[] = []
  const verified: string[] = []
  const { queue } = setup({
    prepare: async (source, signal, onStage) => {
      const index = sources.indexOf(source)
      signals[index] = signal; onStage('Converting ' + source.name)
      return outputs[index]
    },
    upload: async (id, output, sortOrder, options) => {
      const index = outputs.findIndex(result => result.file === output)
      assert.notEqual(index, -1)
      assert.equal(id, 'invitation'); assert.equal(sortOrder, index + 1)
      assert.equal(output.type, 'image/webp'); assert.notEqual(output, sources[index])
      assert.equal(options.signal, signals[index])
      progress[index] = options
      return gates[index].promise
    },
    verify: async (url, signal) => {
      const index = outputs.findIndex(result => asset(result.file).url === url)
      assert.equal(signal, signals[index]); verified.push(url)
    },
  }, [oldUrl])
  const messages: string[] = []
  const unsubscribe = queue.subscribe(() => { messages.push(...queue.getSnapshot().items.map(item => item.message)) })
  queue.add(sources)
  await waitFor(queue, state => state.items[1]?.status === 'uploading')
  progress[0].onProgress?.(42.6)
  assert.equal(queue.getSnapshot().items[1].progress, 43)
  assert.equal(queue.getSnapshot().items[1].message, '업로드 중 · 43%')
  assert.equal(queue.getSnapshot().items[2].progress, undefined)
  progress[0].onProgress?.(-5); assert.equal(queue.getSnapshot().items[1].progress, 0)
  progress[0].onProgress?.(200); assert.equal(queue.getSnapshot().items[1].progress, 100)
  gates[0].resolve(asset(outputs[0].file, 1))
  await waitFor(queue, state => state.items[2]?.status === 'uploading')
  progress[1].onProgress?.(17)
  assert.equal(queue.getSnapshot().items[2].progress, 17)
  assert.equal(queue.getSnapshot().items[1].progress, undefined)
  gates[1].resolve(asset(outputs[1].file, 2))
  await idle(queue); unsubscribe()
  assert.ok(messages.includes('Converting a.heic'))
  assert.ok(messages.includes('Converting b.heic'))
  assert.deepEqual(verified, outputs.map(result => asset(result.file).url))
  assert.ok(queue.getSnapshot().items.slice(1).every(item => item.status === 'ready' && item.message.startsWith('완료 · ')))
})

test('failures do not block later files and retry only failed slots in stable selection order', async () => {
  const attempts = new Map<string, number>()
  const uploaded: string[] = []
  const { queue, changes, pending } = setup({
    prepare: async source => {
      const count = (attempts.get(source.name) ?? 0) + 1
      attempts.set(source.name, count)
      if (source.name === 'a.heic' && count === 1) throw new Error('decode failed')
      return prepared(source)
    },
    upload: async (_id, output, sortOrder) => {
      uploaded.push(output.name)
      if (output.name === 'c.webp' && attempts.get('c.heic') === 1) throw new Error('network failed')
      return asset(output, sortOrder)
    },
  }, [oldUrl])
  queue.add([file('a'), file('b'), file('c'), file('d')])
  const first = await idle(queue)
  const ids = first.items.map(item => item.id)
  assert.deepEqual(first.items.map(item => item.status), ['ready', 'failed', 'ready', 'failed', 'ready'])
  assert.equal(first.pending, true)
  assert.deepEqual(urls(queue), [oldUrl, 'https://media.example.com/b.webp', 'https://media.example.com/d.webp'])
  queue.retry()
  await waitFor(queue, state => !state.pending)
  assert.deepEqual(queue.getSnapshot().items.map(item => item.id), ids)
  assert.deepEqual([...attempts], [['a.heic', 2], ['b.heic', 1], ['c.heic', 2], ['d.heic', 1]])
  assert.deepEqual(uploaded, ['b.webp', 'c.webp', 'd.webp', 'a.webp', 'c.webp'])
  assert.deepEqual(changes.at(-1), [oldUrl, ...['a', 'b', 'c', 'd'].map(name => 'https://media.example.com/' + name + '.webp')])
  assert.equal(new Set(changes.at(-1)).size, 5)
  assert.deepEqual(pending, [true, false])
})

test('individual retry ignores successful rows and leaves other failures untouched', async () => {
  const calls: string[] = []
  const { queue } = setup({ prepare: async source => {
    calls.push(source.name)
    if (source.name !== 'ok.heic' && calls.filter(name => name === source.name).length === 1) throw new Error('temporary failure')
    return prepared(source)
  } })
  queue.add([file('a'), file('ok'), file('b')])
  await idle(queue)
  const [a, ok, b] = queue.getSnapshot().items
  queue.retry(ok.id); queue.retry('missing')
  assert.deepEqual(calls, ['a.heic', 'ok.heic', 'b.heic'])
  queue.retry(b.id)
  await idle(queue)
  assert.equal(queue.getSnapshot().items[0].id, a.id)
  assert.equal(queue.getSnapshot().items[0].status, 'failed')
  assert.equal(queue.getSnapshot().items[1].status, 'ready')
  assert.equal(queue.getSnapshot().items[2].status, 'ready')
  assert.deepEqual(calls, ['a.heic', 'ok.heic', 'b.heic', 'b.heic'])
  assert.equal(queue.getSnapshot().pending, true)
})

test('reordering during upload persists when completion arrives and queued upload uses its current position', async () => {
  const gate = deferred<MediaAsset>()
  const orders: [string, number][] = []
  const { queue, changes } = setup({ upload: async (_id, output, sortOrder) => {
    orders.push([output.name, sortOrder])
    return output.name === 'a.webp' ? gate.promise : asset(output, sortOrder)
  } }, [oldUrl])
  queue.add([file('a'), file('b')])
  await waitFor(queue, state => state.items[1]?.status === 'uploading')
  const [, a, b] = queue.getSnapshot().items
  queue.move(b.id, -1)
  assert.deepEqual(queue.getSnapshot().items.map(item => item.id), [queue.getSnapshot().items[0].id, b.id, a.id])
  gate.resolve(asset(prepared(file('a')).file, 1))
  await idle(queue)
  assert.deepEqual(orders, [['a.webp', 1], ['b.webp', 1]])
  assert.deepEqual(changes.at(-1), [oldUrl, 'https://media.example.com/b.webp', 'https://media.example.com/a.webp'])
})

test('moving a failed row and moving it again during retry preserves its identity and latest order', async () => {
  const gate = deferred<PreparedPhoto>()
  let attempts = 0
  const orders: [string, number][] = []
  const { queue, changes } = setup({
    prepare: async source => {
      if (source.name === 'a.heic') {
        if (++attempts === 1) throw new Error('decode failed')
        return gate.promise
      }
      return prepared(source)
    },
    upload: async (_id, output, sortOrder) => { orders.push([output.name, sortOrder]); return asset(output, sortOrder) },
  }, [oldUrl])
  queue.add([file('a'), file('b')])
  await idle(queue)
  const a = queue.getSnapshot().items[1]
  queue.move(a.id, 1)
  assert.equal(queue.getSnapshot().items[2].id, a.id)
  queue.retry(a.id)
  await waitFor(queue, state => state.items[2]?.status === 'preparing')
  queue.move(a.id, -1)
  gate.resolve(prepared(file('a')))
  await idle(queue)
  assert.equal(queue.getSnapshot().items[1].id, a.id)
  assert.deepEqual(orders, [['b.webp', 2], ['a.webp', 1]])
  assert.deepEqual(changes.at(-1), [oldUrl, 'https://media.example.com/a.webp', 'https://media.example.com/b.webp'])
})

test('deleting current and queued rows aborts work, ignores late progress and never resurrects either row', async () => {
  const gate = deferred<MediaAsset>()
  let currentOptions!: PhotoUploadOptions
  const uploaded: string[] = []
  const verified: string[] = []
  const { queue, changes } = setup({
    upload: async (_id, output, sortOrder, options) => {
      uploaded.push(output.name)
      if (output.name === 'a.webp') { currentOptions = options; return gate.promise }
      return asset(output, sortOrder)
    },
    verify: async url => { verified.push(url) },
  }, [oldUrl])
  queue.add([file('a'), file('b'), file('c')])
  await waitFor(queue, state => state.items[1]?.status === 'uploading')
  const [, a, b] = queue.getSnapshot().items
  queue.remove(a.id); queue.remove(b.id)
  assert.equal(currentOptions.signal?.aborted, true)
  const before = queue.getSnapshot()
  currentOptions.onProgress?.(90)
  assert.equal(queue.getSnapshot(), before)
  gate.resolve(asset(prepared(file('a')).file))
  await idle(queue)
  assert.deepEqual(uploaded, ['a.webp', 'c.webp'])
  assert.deepEqual(verified, ['https://media.example.com/c.webp'])
  assert.ok(queue.getSnapshot().items.every(item => item.id !== a.id && item.id !== b.id))
  assert.deepEqual(changes.at(-1), [oldUrl, 'https://media.example.com/c.webp'])
  assert.ok(changes.every(change => !change.includes('https://media.example.com/a.webp')))
})

test('cancelPending keeps successes and pending stays true until the actual aborted task settles', async () => {
  const gate = deferred<PreparedPhoto>()
  let signal!: AbortSignal
  const converted: string[] = []
  const { queue, changes, pending } = setup({ prepare: async (source, currentSignal) => {
    converted.push(source.name)
    if (source.name === 'current.heic') { signal = currentSignal; return gate.promise }
    return prepared(source)
  } }, [oldUrl])
  queue.add([file('success'), file('current'), file('queued')])
  await waitFor(queue, state => state.items[2]?.status === 'preparing')
  queue.cancelPending()
  assert.equal(signal.aborted, true)
  assert.deepEqual(urls(queue), [oldUrl, 'https://media.example.com/success.webp'])
  assert.equal(queue.getSnapshot().items.length, 2)
  assert.equal(queue.getSnapshot().pending, true)
  assert.equal(queue.getSnapshot().working, true)
  assert.deepEqual(pending, [true])
  // Even a newly selected row must wait for the aborted decoder to release memory.
  queue.add([file('replacement')])
  assert.deepEqual(converted, ['success.heic', 'current.heic'])
  gate.resolve(prepared(file('current')))
  await waitFor(queue, state => !state.pending)
  assert.deepEqual(converted, ['success.heic', 'current.heic', 'replacement.heic'])
  assert.deepEqual(changes.at(-1), [oldUrl, 'https://media.example.com/success.webp', 'https://media.example.com/replacement.webp'])
  assert.deepEqual(pending, [true, false])
})

test('verification failure never commits an uploaded URL or replaces existing successes', async () => {
  const gate = deferred<void>()
  const { queue, changes } = setup({ verify: async () => gate.promise }, [oldUrl])
  queue.add([file('broken')])
  await waitFor(queue, state => state.items[1]?.status === 'verifying')
  assert.deepEqual(changes, [])
  assert.deepEqual(urls(queue), [oldUrl])
  gate.reject(new Error('public image blocked'))
  const done = await idle(queue)
  assert.equal(done.items[1].status, 'failed')
  assert.equal(done.items[1].message, 'public image blocked')
  assert.equal(done.pending, true)
  assert.deepEqual(changes, [])
  assert.deepEqual(urls(queue), [oldUrl])
})

test('unsafe or relative uploaded URLs fail before verification and are never committed', async () => {
  for (const url of ['/photo.webp', 'javascript:alert(1)', 'data:image/png;base64,AA==', 'blob:https://example.com/photo']) {
    let verified = false
    const { queue, changes } = setup({
      upload: async (_id, output) => ({ ...asset(output), url }),
      verify: async () => { verified = true },
    }, [oldUrl])
    queue.add([file('unsafe')])
    const done = await idle(queue)
    assert.equal(done.items[1].status, 'failed', url)
    assert.match(done.items[1].message, /공개 주소/)
    assert.equal(verified, false, url)
    assert.deepEqual(changes, [])
    assert.deepEqual(urls(queue), [oldUrl])
  }
})

test('unmount during preparation aborts and suppresses late stages, queued work and callbacks', async () => {
  const gate = deferred<PreparedPhoto>()
  let signal!: AbortSignal, stage!: (message: string) => void
  let uploads = 0, notifications = 0
  const { queue, changes, pending } = setup({
    prepare: async (_source, currentSignal, onStage) => { signal = currentSignal; stage = onStage; return gate.promise },
    upload: async (_id, output) => { uploads++; return asset(output) },
  }, [oldUrl])
  const unsubscribe = queue.subscribe(() => { notifications++ })
  queue.add([file('current'), file('queued')])
  await waitFor(queue, state => state.items[1]?.status === 'preparing')
  queue.deactivate()
  const before = queue.getSnapshot(), count = notifications, pendingBefore = [...pending]
  assert.equal(signal.aborted, true)
  stage('late worker message'); gate.resolve(prepared(file('current')))
  await nextTurn()
  queue.add([file('ignored')]); queue.addUrl('https://media.example.com/ignored.webp')
  queue.retry(); queue.move(before.items[0].id, 1); queue.remove(before.items[0].id); queue.cancelPending()
  assert.equal(queue.getSnapshot(), before)
  assert.equal(uploads, 0)
  assert.equal(notifications, count)
  assert.deepEqual(changes, [])
  assert.deepEqual(pending, pendingBefore)
  unsubscribe()
})

test('unmount during upload ignores late progress and upload completion without verification', async () => {
  const gate = deferred<MediaAsset>()
  let options!: PhotoUploadOptions
  let verified = false, notifications = 0
  const { queue, changes, pending } = setup({
    upload: async (_id, _output, _order, currentOptions) => { options = currentOptions; return gate.promise },
    verify: async () => { verified = true },
  }, [oldUrl])
  const unsubscribe = queue.subscribe(() => { notifications++ })
  queue.add([file('current')])
  await waitFor(queue, state => state.items[1]?.status === 'uploading')
  queue.deactivate()
  const before = queue.getSnapshot(), count = notifications, pendingBefore = [...pending]
  assert.equal(options.signal?.aborted, true)
  options.onProgress?.(100); gate.resolve(asset(prepared(file('current')).file))
  await nextTurn()
  assert.equal(queue.getSnapshot(), before)
  assert.equal(verified, false)
  assert.equal(notifications, count)
  assert.deepEqual(changes, [])
  assert.deepEqual(pending, pendingBefore)
  unsubscribe()
})

test('unmount during verification aborts and a late verification result cannot commit', async () => {
  const gate = deferred<void>()
  let signal!: AbortSignal, notifications = 0
  const { queue, changes, pending } = setup({ verify: async (_url, currentSignal) => {
    signal = currentSignal; return gate.promise
  } }, [oldUrl])
  const unsubscribe = queue.subscribe(() => { notifications++ })
  queue.add([file('current')])
  await waitFor(queue, state => state.items[1]?.status === 'verifying')
  queue.deactivate()
  const before = queue.getSnapshot(), count = notifications, pendingBefore = [...pending]
  assert.equal(signal.aborted, true)
  gate.resolve(undefined)
  await nextTurn()
  assert.equal(queue.getSnapshot(), before)
  assert.equal(notifications, count)
  assert.deepEqual(changes, [])
  assert.deepEqual(urls(queue), [oldUrl])
  assert.deepEqual(pending, pendingBefore)
  unsubscribe()
})
