import { detectPhotoFormat, photoDimensions, PHOTO_TARGET_BYTES, validatePhoto } from './photoPolicy'

type WorkerScope = { onmessage: ((event: MessageEvent<File>) => void) | null; postMessage: (message: unknown) => void }
const scope = self as unknown as WorkerScope

scope.onmessage = async ({ data: file }) => {
  let bitmap: ImageBitmap | undefined
  let canvas: OffscreenCanvas | undefined
  try {
    validatePhoto(file)
    const format = detectPhotoFormat(new Uint8Array(await file.slice(0, 128).arrayBuffer()))
    scope.postMessage({ stage: format === 'heic' ? 'HEIC 사진을 변환하고 있어요.' : '모바일 감상에 맞게 사진을 조정하고 있어요.' })
    if (format === 'heic') {
      const { heicTo } = await import('heic-to/next')
      bitmap = await heicTo({ blob: file, type: 'bitmap' })
    } else {
      bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
    }
    const size = photoDimensions(bitmap.width, bitmap.height)
    canvas = new OffscreenCanvas(size.width, size.height)
    const context = canvas.getContext('2d')
    if (!context) throw new Error('사진을 처리할 수 없습니다. 최신 브라우저에서 다시 시도해 주세요.')
    context.imageSmoothingEnabled = true
    context.imageSmoothingQuality = 'high'
    context.drawImage(bitmap, 0, 0, size.width, size.height)
    // Keep a quality floor: 1MB is a target, not a reason to destroy fine details.
    let blob = await canvas.convertToBlob({ type: 'image/webp', quality: 0.88 })
    for (const quality of [0.84, 0.8]) {
      if (blob.size <= PHOTO_TARGET_BYTES) break
      blob = await canvas.convertToBlob({ type: 'image/webp', quality })
    }
    if (blob.type !== 'image/webp') blob = await canvas.convertToBlob({ type: 'image/jpeg', quality: 0.88 })
    if (!blob.size || !['image/webp', 'image/jpeg'].includes(blob.type)) throw new Error('사진 변환에 실패했습니다. 다시 시도해 주세요.')
    const ext = blob.type === 'image/webp' ? '.webp' : '.jpg'
    const output = new File([blob], file.name.replace(/\.[^.]+$/, '') + ext, { type: blob.type })
    validatePhoto(output)
    scope.postMessage({ result: { file: output, width: size.width, height: size.height, originalBytes: file.size } })
  } catch (error) {
    scope.postMessage({ error: error instanceof Error ? error.message : '사진 변환에 실패했습니다. 다른 사진을 선택하거나 다시 시도해 주세요.' })
  } finally {
    bitmap?.close()
    if (canvas) { canvas.width = 1; canvas.height = 1 }
  }
}
