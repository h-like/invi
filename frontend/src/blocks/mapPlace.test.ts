import { test } from 'node:test'
import assert from 'node:assert/strict'
import { isVerifiedAddress, mapLinks, placeContentPatch, readMapPlace, type PlaceSearchResult } from './mapPlace.ts'

const result: PlaceSearchResult = {
  id: '8130419', name: '롯데호텔 서울', category: '특급호텔', roadAddress: '서울 중구 을지로 30', address: '서울 중구 소공동 1',
  phone: null, lat: 37.565, lng: 126.981, placeUrl: null,
}

test('choosing a result stores the place and its road address, and names the venue', () => {
  const patch = placeContentPatch(result, '', null)
  assert.equal(patch.address, '서울 중구 을지로 30')
  assert.equal(patch.venueName, '롯데호텔 서울')
  assert.deepEqual(readMapPlace(patch), { id: '8130419', name: '롯데호텔 서울', address: '서울 중구 을지로 30', lat: 37.565, lng: 126.981 })
  assert.equal(placeContentPatch({ ...result, roadAddress: null }, '', null).address, '서울 중구 소공동 1')
})

test('a venue name with the hall added is kept; a name from an earlier pick or typed by hand is replaced', () => {
  assert.equal(placeContentPatch(result, '롯데호텔 서울 2층 크리스탈볼룸', null).venueName, '롯데호텔 서울 2층 크리스탈볼룸')
  const previous = readMapPlace(placeContentPatch({ ...result, id: '1', name: '다른 웨딩홀' }, '', null))
  assert.equal(placeContentPatch(result, '다른 웨딩홀', previous).venueName, '롯데호텔 서울')
  assert.equal(placeContentPatch(result, '롯데 호털', null).venueName, '롯데호텔 서울')
})

test('hand-typed or tampered places are not trusted for links', () => {
  const content = placeContentPatch(result, '', null)
  assert.equal(isVerifiedAddress(content), true)
  assert.equal(isVerifiedAddress({ ...content, address: '서울 중구 을지로 31' }), false)
  assert.equal(isVerifiedAddress({ address: '서울 중구 을지로 30' }), false)
  for (const id of ['abc', '1/../x', '', 12]) assert.equal(readMapPlace({ place: { ...(content.place as object), id } }), null)
  assert.equal(readMapPlace({ place: { ...(content.place as object), lat: Number.NaN } }), null)
})

test('kakao opens the exact place when verified, otherwise both maps search the address', () => {
  const content = placeContentPatch(result, '', null)
  assert.deepEqual(mapLinks(content, '서울 중구 을지로 30').map(l => l.url), [
    'https://map.naver.com/p/search/' + encodeURIComponent('서울 중구 을지로 30'),
    'https://map.kakao.com/link/map/8130419',
  ])
  assert.equal(mapLinks({ address: '어딘가' }, '어딘가')[1].url, 'https://map.kakao.com/link/search/' + encodeURIComponent('어딘가'))
})
