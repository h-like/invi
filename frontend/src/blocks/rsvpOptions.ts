// RSVP side/meal choices. Mirrors backend RsvpSide / RsvpMeal; both are optional and
// responses saved before they existed come back as null.

export type RsvpSide = 'GROOM' | 'BRIDE'
export type RsvpMeal = 'YES' | 'NO' | 'UNDECIDED'

export const RSVP_SIDE_OPTIONS: { value: RsvpSide; label: string }[] = [
  { value: 'GROOM', label: '신랑측' },
  { value: 'BRIDE', label: '신부측' },
]
export const RSVP_MEAL_OPTIONS: { value: RsvpMeal; label: string }[] = [
  { value: 'YES', label: '식사 예정' },
  { value: 'NO', label: '식사 안 함' },
  { value: 'UNDECIDED', label: '미정' },
]

export interface RsvpLike { attending: boolean; guestCount: number; side?: RsvpSide | null; meal?: RsvpMeal | null }

/** Responses and attending guests for one group; "unknown" collects rows without an answer. */
export interface RsvpTally { responses: number; guests: number }
export interface RsvpSummary {
  total: RsvpTally
  side: Record<RsvpSide | 'unknown', RsvpTally>
  /** Attending guests only: a meal answer from someone not coming means nothing. */
  meal: Record<RsvpMeal | 'unknown', number>
}

const tally = (): RsvpTally => ({ responses: 0, guests: 0 })

export function summarizeRsvps(rsvps: RsvpLike[]): RsvpSummary {
  const summary: RsvpSummary = {
    total: tally(),
    side: { GROOM: tally(), BRIDE: tally(), unknown: tally() },
    meal: { YES: 0, NO: 0, UNDECIDED: 0, unknown: 0 },
  }
  for (const r of rsvps) {
    const guests = r.attending ? Math.max(0, r.guestCount) : 0
    const side = r.side === 'GROOM' || r.side === 'BRIDE' ? r.side : 'unknown'
    for (const t of [summary.total, summary.side[side]]) { t.responses++; t.guests += guests }
    if (r.attending) summary.meal[r.meal === 'YES' || r.meal === 'NO' || r.meal === 'UNDECIDED' ? r.meal : 'unknown'] += guests
  }
  return summary
}

export function sideLabel(side: RsvpSide | null | undefined): string {
  return RSVP_SIDE_OPTIONS.find(o => o.value === side)?.label ?? ''
}
export function mealLabel(meal: RsvpMeal | null | undefined): string {
  return RSVP_MEAL_OPTIONS.find(o => o.value === meal)?.label ?? ''
}
