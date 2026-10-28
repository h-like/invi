import test from 'node:test'
import assert from 'node:assert/strict'
import { createCoverUpload, type CoverUploadDependencies } from './coverUpload.ts'

const file = new File(['source image'], 'photo.heic', { type: 'image/heic' })
const prepared = { file: new File(['optimized'], 'photo.webp', { type: 'image/webp' }), width: 1707, height: 2560, originalBytes: file.size }
const asset = { id: 'media', url: 'https://media.example.com/new.webp', type: 'COVER' as const, sortOrder: 0 }
const defaults: CoverUploadDependencies = { prepare: async () => prepared, upload: async () => asset, verify: async () => {} }
const run = (deps: Partial<CoverUploadDependencies> = {}) => createCoverUpload({ ...defaults, ...deps }).run('invitation', file, new AbortController().signal, () => {})

test('converts before upload, uses optimized file, verifies public loading before returning', async () => {
  const calls: string[] = []
  const result = await run({ prepare: async () => { calls.push('prepare'); return prepared }, upload: async (id, output, options) => {
    calls.push('upload'); assert.equal(id, 'invitation'); assert.equal(output, prepared.file); options.onProgress?.(40); return asset
  }, verify: async url => { calls.push('verify'); assert.equal(url, asset.url) } })
  assert.deepEqual(calls, ['prepare', 'upload', 'verify'])
  assert.equal(result.asset.url, asset.url)
})
test('conversion failure never uploads and preserves the prior cover', async () => {
  let uploaded = false
  let current = 'https://media.example.com/old.webp'
  await assert.rejects(async () => { const result = await run({ prepare: async () => { throw new Error('decode failed') }, upload: async () => { uploaded = true; return asset } }); current = result.asset.url }, /decode failed/)
  assert.equal(uploaded, false); assert.equal(current, 'https://media.example.com/old.webp')
})
test('upload failure does not verify or replace the prior cover', async () => {
  let verified = false
  let current = 'old'
  await assert.rejects(async () => { const result = await run({ upload: async () => { throw new Error('network failed') }, verify: async () => { verified = true } }); current = result.asset.url }, /network failed/)
  assert.equal(verified, false); assert.equal(current, 'old')
})
test('a broken public image is not committed to the draft', async () => {
  let current = 'old'
  await assert.rejects(async () => { const result = await run({ verify: async () => { throw new Error('public image blocked') } }); current = result.asset.url }, /public image blocked/)
  assert.equal(current, 'old')
})
test('rejects relative or unsafe public URLs', async () => {
  for (const url of ['/invitations/photo.webp', 'javascript:alert(1)']) await assert.rejects(run({ upload: async () => ({ ...asset, url }) }), /공개 주소/)
})
test('allows a fresh retry after failure', async () => {
  let attempts = 0
  const uploader = createCoverUpload({ ...defaults, upload: async () => { if (++attempts === 1) throw new Error('temporary failure'); return asset } })
  await assert.rejects(uploader.run('i', file, new AbortController().signal, () => {}), /temporary failure/)
  assert.equal((await uploader.run('i', file, new AbortController().signal, () => {})).asset, asset)
  assert.equal(attempts, 2)
})
test('prevents concurrent uploads, then resets its single-flight guard', async () => {
  let release!: () => void
  const wait = new Promise<void>(resolve => { release = resolve })
  const uploader = createCoverUpload({ ...defaults, prepare: async () => { await wait; return prepared } })
  const first = uploader.run('i', file, new AbortController().signal, () => {})
  await assert.rejects(uploader.run('i', file, new AbortController().signal, () => {}), /이미 사진/)
  release(); await first
  await uploader.run('i', file, new AbortController().signal, () => {})
})
test('abort during conversion never uploads or replaces the cover', async () => {
  const controller = new AbortController()
  let uploaded = false
  const uploader = createCoverUpload({ ...defaults, prepare: async () => { controller.abort(); return prepared }, upload: async () => { uploaded = true; return asset } })
  await assert.rejects(uploader.run('i', file, controller.signal, () => {}), { name: 'AbortError' })
  assert.equal(uploaded, false)
})
test('oversized original fails before conversion', async () => {
  let converted = false
  const oversized = { name: 'photo.jpg', type: 'image/jpeg', size: 30 * 1024 * 1024 + 1 } as File
  const uploader = createCoverUpload({ ...defaults, prepare: async () => { converted = true; return prepared } })
  await assert.rejects(uploader.run('i', oversized, new AbortController().signal, () => {}), /30MB/)
  assert.equal(converted, false)
})
