import { useEffect, useId, useState, useSyncExternalStore } from 'react'
import { api } from '../api/client'
import { createGalleryUpload } from './galleryUpload'
import { MAX_GALLERY_IMAGES } from './model'
import { PHOTO_ACCEPT } from './photoPolicy'
import { preparePhoto, verifyPublicPhoto } from './preparePhoto'

interface Props {
  invitationId: string
  images: string[]
  disabled: boolean
  onChange: (images: string[]) => void
  onPendingChange: (pending: boolean) => void
}

export function GalleryPhotoEditor({ invitationId, images, disabled, onChange, onPendingChange }: Props) {
  const fileId = useId()
  const policyId = useId()
  const [url, setUrl] = useState('')
  const [error, setError] = useState('')
  const [queue] = useState(() => createGalleryUpload({
    invitationId, images, onChange, onPendingChange,
    prepare: preparePhoto,
    upload: (id, file, order, options) => api.uploadImage(id, file, 'GALLERY', order, options),
    verify: verifyPublicPhoto,
  }))
  const state = useSyncExternalStore(queue.subscribe, queue.getSnapshot)
  const ready = state.items.filter(item => item.status === 'ready').length
  const failed = state.items.filter(item => item.status === 'failed').length
  const full = state.items.length >= MAX_GALLERY_IMAGES

  useEffect(() => {
    queue.activate()
    return () => queue.deactivate()
  }, [queue])

  function addFiles(selected: File[]) {
    if (disabled) return
    try { queue.add(selected); setError('') }
    catch (cause) { setError(cause instanceof Error ? cause.message : '사진을 추가하지 못했습니다.') }
  }
  function addUrl() {
    if (disabled) return
    try { queue.addUrl(url); setUrl(''); setError('') }
    catch (cause) { setError(cause instanceof Error ? cause.message : '이미지 주소를 추가하지 못했습니다.') }
  }

  return <div className="gallery-photo-editor">
    <label htmlFor={fileId}>갤러리 사진 여러 장 선택
      <input id={fileId} type="file" multiple accept={PHOTO_ACCEPT} disabled={disabled || full} aria-describedby={policyId} onChange={event => {
        const selected = Array.from(event.target.files ?? [])
        event.target.value = ''
        addFiles(selected)
      }} />
    </label>
    <p id={policyId} className="editor-section-hint">8~12장을 권장하며 최대 20장까지 추가할 수 있어요. JPG·JPEG·PNG·WebP·HEIC/HEIF, 한 장당 최대 30MB. 사진은 한 장씩 자동 최적화하며 자르기는 지원하지 않습니다.</p>
    <p className="gallery-upload-summary" role="status">{ready}장 준비 완료 · 전체 {state.items.length} / {MAX_GALLERY_IMAGES}장{failed > 0 ? ' · 실패 ' + failed + '장' : ''}{state.working ? ' · 처리 중' : ''}</p>
    {error && <p className="form-error" role="alert">{error}</p>}
    {state.pending && <div className="gallery-upload-notice">
      <p className="editor-section-hint">미완료·실패한 사진을 재시도하거나 삭제해야 저장·공개할 수 있어요. 완료된 사진과 다른 입력 내용은 유지됩니다.</p>
      <div className="photo-upload-actions">
        {failed > 0 && <button type="button" className="secondary-button" disabled={disabled} onClick={() => queue.retry()}>실패한 사진만 재시도</button>}
        <button type="button" className="secondary-button" disabled={disabled} onClick={() => { queue.cancelPending(); setError('') }}>미완료 사진 선택 취소</button>
      </div>
    </div>}
    <ol className="gallery-photo-list" aria-label="갤러리 사진 순서">
      {state.items.map((item, index) => <li key={item.id} className={'gallery-photo-row gallery-photo-' + item.status}>
        <div className="gallery-photo-content">
          {item.url ? <img src={item.url} alt={'갤러리 사진 ' + (index + 1)} className="gallery-photo-thumb" loading="lazy" /> : <span className="gallery-photo-placeholder" aria-hidden="true">{index + 1}</span>}
          <div className="gallery-photo-detail">
            <span className="gallery-photo-name">{index + 1}. {item.name}</span>
            <span className={item.status === 'failed' ? 'gallery-photo-error' : 'gallery-photo-stage'}>{item.message}</span>
            {['preparing', 'uploading', 'verifying'].includes(item.status) && <progress aria-label={'사진 ' + (index + 1) + ' 처리 진행'} max={100} value={item.progress} />}
          </div>
        </div>
        <div className="gallery-photo-actions">
          {item.status === 'failed' && <button type="button" className="secondary-button" disabled={disabled} onClick={() => queue.retry(item.id)} aria-label={'사진 ' + (index + 1) + ' 재시도'}>재시도</button>}
          <button type="button" className="secondary-button" disabled={disabled || index === 0} onClick={() => queue.move(item.id, -1)} aria-label={'사진 ' + (index + 1) + ' 위로'}>위로</button>
          <button type="button" className="secondary-button" disabled={disabled || index === state.items.length - 1} onClick={() => queue.move(item.id, 1)} aria-label={'사진 ' + (index + 1) + ' 아래로'}>아래로</button>
          <button type="button" className="secondary-button" disabled={disabled} onClick={() => { queue.remove(item.id); setError('') }} aria-label={'사진 ' + (index + 1) + ' 삭제'}>삭제</button>
        </div>
      </li>)}
    </ol>
    {ready > 0 && <p className="editor-section-hint">준비 완료는 업로드 완료 상태입니다. 저장해야 청첩장에 반영됩니다. 삭제도 저장 후 반영되며 R2 원본 파일은 즉시 지우지 않습니다.</p>}
    <details className="cover-photo-url"><summary>이미지 주소로 직접 추가</summary>
      <label>갤러리 사진 URL<input type="url" disabled={disabled || full} value={url} onChange={event => setUrl(event.target.value)} placeholder="https://.../photo.jpg" /></label>
      <button type="button" className="secondary-button" disabled={disabled || full} onClick={addUrl}>주소로 사진 추가</button>
    </details>
  </div>
}
