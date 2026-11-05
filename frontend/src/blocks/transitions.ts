/**
 * The longest duration + delay among an element's transitions. Computed values are
 * comma-separated lists in seconds, and a shorter list repeats to match the longer one.
 */
export function longestTransitionMs(style: Pick<CSSStyleDeclaration, 'transitionDuration' | 'transitionDelay'>): number {
  const durations = style.transitionDuration.split(',').map(parseFloat)
  const delays = style.transitionDelay.split(',').map(parseFloat)
  const count = Math.max(durations.length, delays.length)
  let longest = 0
  for (let i = 0; i < count; i++) {
    const total = Math.round((durations[i % durations.length] + delays[i % delays.length]) * 1000)
    if (total > longest) longest = total
  }
  return longest
}
