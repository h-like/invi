import { validatePhoto } from './photoPolicy'

export interface PreparedPhoto { file: File; width: number; height: number; originalBytes: number }

export function preparePhoto(file: File, signal: AbortSignal, onStage: (stage: string) => void): Promise<PreparedPhoto> {
  validatePhoto(file)
  signal.throwIfAborted()
  return new Promise((resolve, reject) => {
    const worker = new Worker(new URL('./photo.worker.ts', import.meta.url), { type: 'module' })
    const cleanup = () => { clearTimeout(timer); signal.removeEventListener('abort', abort); worker.terminate() }
    const fail = (error: unknown) => { cleanup(); reject(error) }
    const abort = () => fail(new DOMException('사진 처리를 취소했습니다.', 'AbortError'))
    const timer = setTimeout(() => fail(new Error('사진 처리 시간이 길어지고 있습니다. 다시 시도하거나 다른 사진을 선택해 주세요.')), 90_000)
    signal.addEventListener('abort', abort, { once: true })
    worker.onmessage = ({ data }: MessageEvent<{ stage?: string; result?: PreparedPhoto; error?: string }>) => {
      if (data.stage) onStage(data.stage)
      else if (data.error) fail(new Error(data.error))
      else if (data.result) { cleanup(); resolve(data.result) }
    }
    worker.onerror = event => { event.preventDefault(); fail(new Error('사진을 처리하지 못했습니다. 최신 브라우저에서 다시 시도해 주세요.')) }
    worker.onmessageerror = () => fail(new Error('사진 처리 결과를 받지 못했습니다. 다시 시도해 주세요.'))
    worker.postMessage(file)
  })
}

export function verifyPublicPhoto(url: string, signal: AbortSignal): Promise<void> {
  signal.throwIfAborted()
  return new Promise((resolve, reject) => {
    const image = new Image()
    const cleanup = () => { clearTimeout(timer); signal.removeEventListener('abort', abort); image.onload = null; image.onerror = null }
    const fail = (error: unknown) => { cleanup(); image.src = ''; reject(error) }
    const abort = () => fail(new DOMException('사진 확인을 취소했습니다.', 'AbortError'))
    const timer = setTimeout(() => fail(new Error('업로드한 사진의 공개 조회를 확인하지 못했습니다. 다시 시도해 주세요.')), 20_000)
    signal.addEventListener('abort', abort, { once: true })
    image.onload = () => { cleanup(); resolve() }
    image.onerror = () => fail(new Error('업로드한 사진을 불러오지 못했습니다. 이미지 공개 주소 설정을 확인한 후 다시 시도해 주세요.'))
    image.src = url
  })
}
