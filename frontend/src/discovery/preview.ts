import type { PageData } from '../api/types.ts'

export const SAMPLE_WEDDING_DATE = '2027-06-12'

// Samples belong only to this public preview, never the creation request.
export function templatePreview(pageData: PageData): PageData {
  const sample = (value: unknown, fallback: string) => typeof value === 'string' && value.trim() ? value : fallback
  return {
    ...pageData,
    blocks: pageData.blocks.map(block => {
      if (block.type === 'hero') return { ...block, content: { ...block.content,
        groomName: sample(block.content.groomName, '하늘'), brideName: sample(block.content.brideName, '다온'),
        weddingDate: sample(block.content.weddingDate, SAMPLE_WEDDING_DATE),
      } }
      if (block.type === 'map') return { ...block, content: { ...block.content,
        venueName: sample(block.content.venueName, '초대의 정원'), address: sample(block.content.address, '예식장 주소가 들어가는 자리입니다.'),
      } }
      return { ...block, content: { ...block.content }, style: { ...block.style } }
    }),
  }
}
