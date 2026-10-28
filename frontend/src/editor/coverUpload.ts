import type { MediaAsset } from '../api/types.ts'
import type { PreparedPhoto } from './preparePhoto.ts'
import { validatePhoto } from './photoPolicy.ts'

export interface PhotoUploadOptions { signal?: AbortSignal; onProgress?: (percent: number) => void }
export interface CoverUploadDependencies {
  prepare: (file: File, signal: AbortSignal, onStage: (stage: string) => void) => Promise<PreparedPhoto>
  upload: (invitationId: string, file: File, options: PhotoUploadOptions) => Promise<MediaAsset>
  verify: (url: string, signal: AbortSignal) => Promise<void>
}

/** No draft mutation until conversion, PUT, attach and public-image loading ALL succeed. */
export function createCoverUpload(dependencies: CoverUploadDependencies) {
  let running = false
  return {
    async run(invitationId: string, file: File, signal: AbortSignal, onStage: (stage: string) => void) {
      if (running) throw new Error('이미 사진을 처리하고 있습니다.')
      running = true
      try {
        signal.throwIfAborted()
        validatePhoto(file)
        onStage('사진을 준비하고 있어요.')
        const prepared = await dependencies.prepare(file, signal, onStage)
        signal.throwIfAborted()
        onStage('사진을 업로드하고 있어요. 0%')
        const asset = await dependencies.upload(invitationId, prepared.file, { signal, onProgress: percent => onStage('사진을 업로드하고 있어요. ' + percent + '%') })
        signal.throwIfAborted()
        if (!/^https?:\/\//i.test(asset.url)) throw new Error('사진의 공개 주소가 올바르지 않습니다.')
        onStage('업로드한 사진을 확인하고 있어요.')
        await dependencies.verify(asset.url, signal)
        signal.throwIfAborted()
        return { asset, prepared }
      } finally { running = false }
    },
  }
}
