import test from 'node:test'
import assert from 'node:assert/strict'
import { putPhoto } from './putPhoto.ts'

function mockXhr(status = 200) {
  const headers: Record<string, string> = {}
  const xhr = {
    status, timeout: 0, withCredentials: true,
    upload: { onprogress: null as ((event: ProgressEvent) => void) | null },
    onload: null as (() => void) | null, onerror: null as (() => void) | null,
    ontimeout: null as (() => void) | null, onabort: null as (() => void) | null,
    open(method: string, url: string) { assert.equal(method, 'PUT'); assert.equal(url, 'https://r2.example.com/signed') },
    setRequestHeader(key: string, value: string) { headers[key] = value },
    send() {}, abort() { this.onabort?.() },
  }
  return { xhr, headers, factory: () => xhr as unknown as XMLHttpRequest }
}
const file = new File(['bytes'], 'photo.webp', { type: 'image/webp' })
const url = 'https://r2.example.com/signed'
test('PUT reports progress, sends only content type and never app credentials', async () => {
  const mock = mockXhr(); const percentages: number[] = []
  const pending = putPhoto(url, file, { onProgress: n => percentages.push(n) }, mock.factory)
  mock.xhr.upload.onprogress?.({ lengthComputable: true, loaded: 5, total: 10 } as ProgressEvent)
  assert.equal(mock.xhr.withCredentials, false); assert.equal(mock.xhr.timeout, 60_000)
  assert.deepEqual(mock.headers, { 'Content-Type': 'image/webp' }); assert.deepEqual(percentages, [50])
  mock.xhr.onload?.(); await pending
})
test('non-2xx responses are upload failures', async () => {
  const mock = mockXhr(403); const pending = putPhoto(url, file, {}, mock.factory)
  mock.xhr.onload?.(); await assert.rejects(pending, /403/)
})
test('network/CORS and timeout failures have actionable messages', async () => {
  const network = mockXhr(); const first = putPhoto(url, file, {}, network.factory)
  network.xhr.onerror?.(); await assert.rejects(first, /CORS/)
  const timeout = mockXhr(); const second = putPhoto(url, file, {}, timeout.factory)
  timeout.xhr.ontimeout?.(); await assert.rejects(second, /시간이 초과/)
})
test('abort cancels the transfer and removes event handlers', async () => {
  const mock = mockXhr(); const controller = new AbortController()
  const pending = putPhoto(url, file, { signal: controller.signal }, mock.factory)
  controller.abort(); await assert.rejects(pending, { name: 'AbortError' })
  assert.equal(mock.xhr.onload, null); assert.equal(mock.xhr.upload.onprogress, null)
})
