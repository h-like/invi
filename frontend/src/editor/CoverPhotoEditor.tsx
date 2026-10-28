import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import { createCoverUpload } from './coverUpload'
import { PHOTO_ACCEPT, photoSizeLabel } from './photoPolicy'
import { preparePhoto, verifyPublicPhoto } from './preparePhoto'

interface Props {
  invitationId: string
  currentUrl: string
  disabled: boolean
  onChange: (url: string) => void
  onPendingChange: (pending: boolean) => void
}

export function CoverPhotoEditor({ invitationId, currentUrl, disabled, onChange, onPendingChange }: Props) {
  const [working, setWorking] = useState(false)
  const [stage, setStage] = useState('')
  const [error, setError] = useState('')
  const [summary, setSummary] = useState('')
  const [retryFile, setRetryFile] = useState<File | null>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const mountedRef = useRef(true)
  const [uploader] = useState(() => createCoverUpload({
    prepare: preparePhoto,
    upload: (id, file, options) => api.uploadImage(id, file, 'COVER', 0, options),
    verify: verifyPublicPhoto,
  }))

  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false; controllerRef.current?.abort() }
  }, [])

  async function start(file: File) {
    if (disabled || controllerRef.current) return
    const controller = new AbortController()
    controllerRef.current = controller
    setRetryFile(file)
    onPendingChange(true)
    setWorking(true)
    setError('')
    setSummary('')
    try {
      const { asset, prepared } = await uploader.run(invitationId, file, controller.signal, message => {
        if (mountedRef.current && !controller.signal.aborted) setStage(message)
      })
      if (!mountedRef.current || controller.signal.aborted) return
      onChange(asset.url)
      onPendingChange(false)
      setRetryFile(null)
      setSummary('업로드 완료 · ' + photoSizeLabel(prepared.originalBytes) + ' → ' + photoSizeLabel(prepared.file.size) + ' · ' + prepared.width + ' × ' + prepared.height + 'px. 저장하면 청첩장에 반영됩니다.')
    } catch (cause) {
      if (!mountedRef.current || controller.signal.aborted) return
      setError((cause instanceof Error ? cause.message : '사진 업로드에 실패했습니다.') + ' 기존 사진과 입력 내용은 유지됩니다.')
      // Keep save/publish blocked until retry succeeds or the user cancels this selection.
    } finally {
      if (controllerRef.current === controller) controllerRef.current = null
      if (mountedRef.current) { setWorking(false); setStage('') }
    }
  }

  function cancel() {
    controllerRef.current?.abort()
    setRetryFile(null)
    setError('')
    setSummary('새 사진 선택을 취소했습니다. 기존 사진은 그대로 유지됩니다.')
    setStage('')
    onPendingChange(false)
  }

  return <div className="cover-photo-editor" aria-busy={working}>
    <p className="cover-photo-label">대표 사진</p>
    {currentUrl && <img className="cover-photo-thumb" src={currentUrl} alt="현재 대표 사진" />}
    <label htmlFor="cover-photo-file">{currentUrl ? '대표 사진 교체' : '대표 사진 선택'}
      <input id="cover-photo-file" type="file" accept={PHOTO_ACCEPT} disabled={disabled || working} aria-describedby="cover-photo-policy" onChange={event => {
        const file = event.target.files?.[0]
        event.target.value = ''
        if (file) void start(file)
      }} />
    </label>
    <p id="cover-photo-policy" className="editor-section-hint">JPG·JPEG·PNG·WebP·HEIC · 한 장당 최대 30MB. 큰 사진도 직접 줄일 필요 없이, 모바일 감상에 맞게 해상도·용량을 자동 조정합니다.</p>
    {working && <div role="status" className="photo-upload-status"><progress aria-label="대표 사진 처리 중" /><span>{stage || '사진을 준비하고 있어요.'}</span></div>}
    {error && <p className="form-error" role="alert">{error}</p>}
    {(working || error) && <div className="photo-upload-actions">
      {!working && retryFile && <button type="button" className="secondary-button" disabled={disabled} onClick={() => void start(retryFile)}>사진 업로드 다시 시도</button>}
      <button type="button" className="secondary-button" disabled={disabled} onClick={cancel}>새 사진 선택 취소</button>
    </div>}
    {summary && <p className="editor-section-hint" role="status">{summary}</p>}
  </div>
}
