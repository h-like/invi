import test from 'node:test'
import assert from 'node:assert/strict'
import { detectPhotoFormat, MAX_PHOTO_BYTES, photoDimensions, photoSizeLabel, validatePhoto } from './photoPolicy.ts'

const photo = { name: 'wedding.JPG', type: 'image/jpeg', size: 100 }
test('accepts JPEG, PNG, WebP and HEIC/HEIF including an empty mobile MIME', () => {
  for (const [name, type] of [['photo.jpg', 'image/jpeg'], ['photo.JPEG', 'image/jpeg'], ['photo.png', 'image/png'], ['photo.webp', 'image/webp'], ['photo.HEIC', 'image/heic'], ['photo.heif', ''], ['photo.heic', 'application/octet-stream']]) {
    assert.doesNotThrow(() => validatePhoto({ name, type, size: MAX_PHOTO_BYTES }))
  }
})
test('rejects empty, oversized, non-image and unsupported files', () => {
  for (const patch of [{ size: 0 }, { size: -1 }, { size: NaN }, { size: MAX_PHOTO_BYTES + 1 }, { name: 'photo.gif', type: 'image/gif' }, { name: 'photo.jpg', type: 'text/html' }, { name: 'photo.svg', type: 'image/svg+xml' }]) {
    assert.throws(() => validatePhoto({ ...photo, ...patch }))
  }
})
test('sniffs actual signatures instead of trusting filename or MIME', () => {
  assert.equal(detectPhotoFormat(Uint8Array.of(255, 216, 255, 224)), 'jpeg')
  assert.equal(detectPhotoFormat(Uint8Array.of(137, 80, 78, 71, 13, 10, 26, 10)), 'png')
  assert.equal(detectPhotoFormat(new TextEncoder().encode('RIFF0000WEBP')), 'webp')
  assert.equal(detectPhotoFormat(new TextEncoder().encode('0000ftypheic0000mif1')), 'heic')
  assert.throws(() => detectPhotoFormat(new TextEncoder().encode('<html>not a photo</html>')))
  assert.throws(() => detectPhotoFormat(new Uint8Array()))
})
test('resizes portrait and landscape without upscaling or changing aspect ratio', () => {
  assert.deepEqual(photoDimensions(6000, 4000), { width: 2560, height: 1707 })
  assert.deepEqual(photoDimensions(4000, 6000), { width: 1707, height: 2560 })
  assert.deepEqual(photoDimensions(800, 600), { width: 800, height: 600 })
  assert.deepEqual(photoDimensions(1, 9000), { width: 1, height: 2560 })
})
test('rejects invalid or excessive decoded dimensions', () => {
  for (const [w, h] of [[0, 20], [10, -1], [Infinity, 20], [20, NaN], [20000, 20000]]) assert.throws(() => photoDimensions(w, h))
})
test('formats optimized size without promising a fixed output size', () => {
  assert.equal(photoSizeLabel(512 * 1024), '512KB')
  assert.equal(photoSizeLabel(2 * 1024 * 1024), '2.0MB')
})
