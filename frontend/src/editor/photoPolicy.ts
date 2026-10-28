export const MAX_PHOTO_BYTES = 30 * 1024 * 1024
export const MAX_PHOTO_EDGE = 2560
export const PHOTO_TARGET_BYTES = 1024 * 1024
// Decoded-pixel ceiling protects against tiny files with extreme dimensions.
export const MAX_PHOTO_PIXELS = 100_000_000
export const PHOTO_ACCEPT = '.jpg,.jpeg,.png,.webp,.heic,.heif,image/jpeg,image/png,image/webp,image/heic,image/heif'
export type PhotoFormat = 'jpeg' | 'png' | 'webp' | 'heic'

export function validatePhoto(file: { name: string; type: string; size: number }): void {
  if (!Number.isFinite(file.size) || file.size <= 0) throw new Error('비어 있는 사진은 올릴 수 없습니다.')
  if (file.size > MAX_PHOTO_BYTES) throw new Error('사진은 한 장당 최대 30MB까지 올릴 수 있습니다.')
  if (!/\.(jpe?g|png|webp|heic|heif)$/i.test(file.name)) throw new Error('JPG·JPEG·PNG·WebP·HEIC 사진을 선택해 주세요.')
  if (file.type && !['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif', 'image/heic-sequence', 'image/heif-sequence', 'application/octet-stream'].includes(file.type.toLowerCase())) {
    throw new Error('지원하지 않는 사진 형식입니다. JPG·JPEG·PNG·WebP·HEIC 사진을 선택해 주세요.')
  }
}

export function detectPhotoFormat(bytes: Uint8Array): PhotoFormat {
  const ascii = (from: number, to: number) => String.fromCharCode(...bytes.subarray(from, to))
  if (bytes.length >= 3 && bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255) return 'jpeg'
  if ([137, 80, 78, 71, 13, 10, 26, 10].every((v, i) => bytes[i] === v)) return 'png'
  if (bytes.length >= 12 && ascii(0, 4) === 'RIFF' && ascii(8, 12) === 'WEBP') return 'webp'
  if (bytes.length >= 12 && ascii(4, 8) === 'ftyp' && /heic|heix|hevc|hevx|mif1|msf1/.test(ascii(8, 64))) return 'heic'
  throw new Error('사진 파일을 확인할 수 없습니다. 손상되지 않은 JPG·PNG·WebP·HEIC 사진을 선택해 주세요.')
}

export function photoDimensions(width: number, height: number): { width: number; height: number } {
  if (![width, height].every(n => Number.isFinite(n) && n > 0) || width * height > MAX_PHOTO_PIXELS) {
    throw new Error('사진의 해상도가 처리 가능한 범위를 넘거나 올바르지 않습니다. 다른 사진을 선택해 주세요.')
  }
  const scale = Math.min(1, MAX_PHOTO_EDGE / Math.max(width, height))
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) }
}

export function photoSizeLabel(bytes: number): string {
  return bytes < 1024 * 1024 ? Math.max(1, Math.round(bytes / 1024)) + 'KB' : (bytes / (1024 * 1024)).toFixed(1) + 'MB'
}
