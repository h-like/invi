/**
 * The venue the couple picked from the place search, stored as map.content.place.
 * page_data is saved unvalidated, so everything read here is checked before it builds a URL.
 */
export interface MapPlace {
  /** Kakao place id: digits only. */
  id: string
  name: string
  /** Road address if the place has one, else the lot address; also mirrored to content.address. */
  address: string
  lat: number
  lng: number
}

/** What the place search API returns (backend PlaceDto). */
export interface PlaceSearchResult {
  id: string
  name: string
  category: string
  roadAddress: string | null
  address: string | null
  phone: string | null
  lat: number
  lng: number
  placeUrl: string | null
}

const PLACE_ID = /^\d{1,20}$/

export function readMapPlace(content: Record<string, unknown>): MapPlace | null {
  const raw = content.place
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null
  const p = raw as Record<string, unknown>
  if (typeof p.id !== 'string' || !PLACE_ID.test(p.id)) return null
  if (typeof p.name !== 'string' || !p.name.trim() || typeof p.address !== 'string' || !p.address.trim()) return null
  if (typeof p.lat !== 'number' || typeof p.lng !== 'number' || !Number.isFinite(p.lat) || !Number.isFinite(p.lng)) return null
  if (Math.abs(p.lat) > 90 || Math.abs(p.lng) > 180) return null
  return { id: p.id, name: p.name.trim(), address: p.address.trim(), lat: p.lat, lng: p.lng }
}

/** The patch the editor applies when a search result is chosen; the address is never typed by hand. */
export function placeContentPatch(result: PlaceSearchResult, currentVenueName: string, previous: MapPlace | null): Record<string, unknown> {
  const address = (result.roadAddress ?? result.address ?? '').trim()
  const place: MapPlace = { id: result.id, name: result.name, address, lat: result.lat, lng: result.lng }
  // Keep a venue name the couple wrote themselves (e.g. "… 3층 그랜드홀"), replace one that was only the old place's name.
  const venue = currentVenueName.trim()
  const keepVenueName = venue && venue !== previous?.name && venue.includes(result.name)
  return { place, address, venueName: keepVenueName ? currentVenueName : result.name }
}

/**
 * A typed address for a ceremony the place search cannot find. The picked place is dropped so the
 * guest links fall back to searching this text, and the editor keeps warning that it is unverified.
 */
export function manualAddressPatch(address: string): Record<string, unknown> {
  return { address, place: null }
}

/** True when the shown address came from the place search rather than being typed by hand. */
export function isVerifiedAddress(content: Record<string, unknown>): boolean {
  const place = readMapPlace(content)
  return !!place && typeof content.address === 'string' && content.address.trim() === place.address
}

export interface MapLink { name: string; url: string }

/** Kakao opens the exact place by id; Naver searches the verified address, which pins that spot. */
export function mapLinks(content: Record<string, unknown>, address: string): MapLink[] {
  const place = isVerifiedAddress(content) ? readMapPlace(content) : null
  return [
    { name: '네이버 지도', url: 'https://map.naver.com/p/search/' + encodeURIComponent(address) },
    { name: '카카오맵', url: place ? 'https://map.kakao.com/link/map/' + place.id : 'https://map.kakao.com/link/search/' + encodeURIComponent(address) },
  ]
}

export function kakaoPlaceUrl(place: MapPlace): string {
  return 'https://place.map.kakao.com/' + place.id
}
