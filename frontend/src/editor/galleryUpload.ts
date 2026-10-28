import type { MediaAsset } from '../api/types.ts'
import type { PhotoUploadOptions } from './coverUpload.ts'
import type { PreparedPhoto } from './preparePhoto.ts'
import { isImageUrl, MAX_GALLERY_IMAGES } from './model.ts'
import { photoSizeLabel, validatePhoto } from './photoPolicy.ts'

export type GalleryStatus = 'ready' | 'queued' | 'preparing' | 'uploading' | 'verifying' | 'failed'
export interface GalleryItem {
  id: string
  name: string
  status: GalleryStatus
  url?: string
  progress?: number
  message: string
}
export interface GallerySnapshot {
  items: readonly GalleryItem[]
  pending: boolean
  working: boolean
}
export interface GalleryUploadOptions {
  invitationId: string
  images: readonly string[]
  prepare: (file: File, signal: AbortSignal, onStage: (stage: string) => void) => Promise<PreparedPhoto>
  upload: (invitationId: string, file: File, sortOrder: number, options: PhotoUploadOptions) => Promise<MediaAsset>
  verify: (url: string, signal: AbortSignal) => Promise<void>
  onChange: (images: string[]) => void
  onPendingChange: (pending: boolean) => void
}

/**
 * One conversion/upload at a time limits mobile decoding memory. Stable IDs keep
 * failed/retried slots in selection order; only verified URLs enter pageData.
 * MediaAsset.sortOrder is informational: content.images is the display order.
 */
export function createGalleryUpload(options: GalleryUploadOptions) {
  let sequence = 0
  const nextId = () => 'gallery-photo-' + (++sequence)
  let items: GalleryItem[] = options.images.map(url => ({ id: nextId(), name: '등록한 사진', status: 'ready', url, message: '등록된 사진' }))
  const files = new Map<string, File>()
  const listeners = new Set<() => void>()
  let current: { id: string; controller: AbortController } | null = null
  let pumping = false
  let active = true
  let lastPending = false
  let state: GallerySnapshot = { items, pending: false, working: false }

  function emit(changed = false) {
    if (!active) return
    const pending = current !== null || items.some(item => item.status !== 'ready')
    state = { items, pending, working: current !== null || items.some(item => item.status === 'queued') }
    if (changed) options.onChange(items.flatMap(item => item.status === 'ready' && item.url ? [item.url] : []))
    if (lastPending !== pending) { lastPending = pending; options.onPendingChange(pending) }
    listeners.forEach(listener => listener())
  }
  function patch(id: string, change: Partial<GalleryItem>, signal: AbortSignal) {
    if (!active || signal.aborted || !items.some(item => item.id === id)) return
    items = items.map(item => item.id === id ? { ...item, ...change } : item)
    emit()
  }
  async function pump() {
    if (pumping || !active) return
    pumping = true
    try {
      while (active) {
        const item = items.find(candidate => candidate.status === 'queued')
        if (!item) break
        const file = files.get(item.id)
        if (!file) break
        const controller = new AbortController()
        const { signal } = controller
        current = { id: item.id, controller }
        try {
          patch(item.id, { status: 'preparing', progress: undefined, message: '사진을 준비하고 있어요.' }, signal)
          validatePhoto(file)
          const prepared = await options.prepare(file, signal, message => patch(item.id, { message }, signal))
          signal.throwIfAborted()
          patch(item.id, { status: 'uploading', progress: 0, message: '업로드 중 · 0%' }, signal)
          const asset = await options.upload(options.invitationId, prepared.file, items.findIndex(candidate => candidate.id === item.id), {
            signal,
            onProgress: percent => {
              const progress = Math.max(0, Math.min(100, Math.round(percent)))
              patch(item.id, { progress, message: '업로드 중 · ' + progress + '%' }, signal)
            },
          })
          signal.throwIfAborted()
          if (!isImageUrl(asset.url)) throw new Error('사진의 공개 주소가 올바르지 않습니다.')
          patch(item.id, { status: 'verifying', progress: undefined, message: '업로드한 사진을 확인하고 있어요.' }, signal)
          await options.verify(asset.url, signal)
          signal.throwIfAborted()
          if (!active || !items.some(candidate => candidate.id === item.id)) continue
          items = items.map(candidate => candidate.id === item.id ? {
            ...candidate, status: 'ready', url: asset.url, progress: undefined,
            message: '완료 · ' + photoSizeLabel(prepared.originalBytes) + ' → ' + photoSizeLabel(prepared.file.size),
          } : candidate)
          files.delete(item.id)
          emit(true)
        } catch (error) {
          patch(item.id, { status: 'failed', progress: undefined, message: error instanceof Error ? error.message : '사진 업로드에 실패했습니다. 다시 시도해 주세요.' }, signal)
        } finally {
          current = null
          emit()
        }
      }
    } finally {
      pumping = false
    }
  }
  function capacity(count: number) {
    const remaining = Math.max(0, MAX_GALLERY_IMAGES - items.length)
    if (count > remaining) throw new Error('갤러리는 최대 20장입니다. 추가 가능한 ' + remaining + '장 이내로 다시 선택해 주세요. 선택한 사진은 추가되지 않았습니다.')
  }

  return {
    getSnapshot: () => state,
    subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener) } },
    activate() { active = true },
    // React StrictMode can reconnect this instance before the user selects files.
    deactivate() { active = false; current?.controller.abort(); files.clear() },
    add(selected: readonly File[]) {
      if (!active || selected.length === 0) return
      capacity(selected.length)
      const added = selected.map(file => {
        const id = nextId()
        files.set(id, file)
        return { id, name: file.name, status: 'queued' as const, message: '처리 대기 중' }
      })
      items = [...items, ...added]
      emit()
      void pump()
    },
    addUrl(url: string) {
      if (!active) return
      const value = url.trim()
      if (!isImageUrl(value)) throw new Error('http 또는 https 이미지 주소를 입력해 주세요.')
      capacity(1)
      items = [...items, { id: nextId(), name: '이미지 주소 사진', status: 'ready', url: value, message: '이미지 주소로 추가됨' }]
      emit(true)
    },
    retry(id?: string) {
      if (!active) return
      items = items.map(item => item.status === 'failed' && (id === undefined || item.id === id)
        ? { ...item, status: 'queued', message: '재시도 대기 중', progress: undefined } : item)
      emit()
      void pump()
    },
    move(id: string, offset: -1 | 1) {
      if (!active) return
      const from = items.findIndex(item => item.id === id), to = from + offset
      if (from < 0 || to < 0 || to >= items.length) return
      const next = [...items]
      const [moved] = next.splice(from, 1)
      next.splice(to, 0, moved)
      items = next
      emit(true)
    },
    remove(id: string) {
      if (!active) return
      if (current?.id === id) current.controller.abort()
      files.delete(id)
      items = items.filter(item => item.id !== id)
      emit(true)
    },
    cancelPending() {
      if (!active) return
      current?.controller.abort()
      items = items.filter(item => item.status === 'ready')
      files.clear()
      // Pending stays true until the aborted task settles, even if its row is gone.
      emit(true)
    },
  }
}
