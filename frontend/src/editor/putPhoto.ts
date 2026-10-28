import type { PhotoUploadOptions } from './coverUpload.ts'

/** Never send our API bearer token or cookies to the signed R2 endpoint. */
export function putPhoto(url: string, file: File, { signal, onProgress }: PhotoUploadOptions = {}, makeRequest: () => XMLHttpRequest = () => new XMLHttpRequest()): Promise<void> {
  signal?.throwIfAborted()
  return new Promise((resolve, reject) => {
    const xhr = makeRequest()
    const cleanup = () => { signal?.removeEventListener('abort', abort); xhr.onload = null; xhr.onerror = null; xhr.ontimeout = null; xhr.onabort = null; xhr.upload.onprogress = null }
    const fail = (error: Error) => { cleanup(); reject(error) }
    const abortError = () => new DOMException('사진 업로드를 취소했습니다.', 'AbortError')
    const abort = () => { xhr.abort(); fail(abortError()) }
    xhr.open('PUT', url)
    xhr.timeout = 60_000
    xhr.withCredentials = false
    xhr.setRequestHeader('Content-Type', file.type)
    xhr.upload.onprogress = event => { if (event.lengthComputable) onProgress?.(Math.min(100, Math.round(event.loaded / event.total * 100))) }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) { cleanup(); resolve() }
      else fail(new Error('사진 업로드에 실패했습니다 (' + xhr.status + '). 다시 시도해 주세요.'))
    }
    xhr.onerror = () => fail(new Error('이미지 저장소에 연결하지 못했습니다. 네트워크 또는 R2 CORS 설정을 확인한 후 다시 시도해 주세요.'))
    xhr.ontimeout = () => fail(new Error('사진 업로드 시간이 초과되었습니다. 다시 시도해 주세요.'))
    xhr.onabort = () => fail(abortError())
    signal?.addEventListener('abort', abort, { once: true })
    try { xhr.send(file) } catch (error) { fail(error instanceof Error ? error : new Error('사진 업로드를 시작하지 못했습니다.')) }
  })
}
