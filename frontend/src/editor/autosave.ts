/**
 * Debounced autosave scheduling. The controller only decides WHEN to save; the caller's `save`
 * decides WHAT and sends it through `createInvitationPersistence`, so autosaves share the one
 * write guard with manual saves, publishes, wedding-date saves and unpublishing, and keep its
 * lost-response rules (`ownWriteLanded`, `own`).
 *
 * - Changes are coalesced: a save starts `delayMs` after the last change, but no later than
 *   `maxWaitMs` after the first unsaved one, so steady typing still gets saved.
 * - One save at a time. Changes made while a save is in flight lead to exactly one more save
 *   after it, with the latest content.
 * - A stale refusal stops autosave until `resume()`: saving again would be refused again, and
 *   overwriting someone else's save is not offered.
 * - Failures retry with exponential backoff; changes during the backoff wait for that retry.
 * - A refusal that would repeat (expired login, a request the server rejects) is not retried
 *   on a timer: `retryNow()` or a manual save (`reset()`) starts again. `flush()` (tab hidden,
 *   back online) still tries once, since the tab may be gone before the user can press retry.
 * - `hold()` lets a manual write take the guard: it cancels the pending autosave, waits for one
 *   in flight, and keeps new ones from starting until released.
 */

/** What one `save` call did. */
export type AutosaveAttempt =
  /** Saved the latest content. */
  | 'saved'
  /** Nothing differed from the stored copy. */
  | 'clean'
  /** Saving is not allowed right now (photos pending, published, ...). Wait for the next `schedule()`. */
  | 'blocked'
  /** Another write from this tab holds the guard: try again shortly. */
  | 'busy'
  /** Network or server error: retry with backoff. */
  | 'failed'
  /** The server refused the request (4xx other than stale): retrying alone would be refused again. */
  | 'rejected'
  /** Someone else saved first: stop until `resume()`. */
  | 'stale'

export type AutosaveStatus = 'idle' | 'pending' | 'saving' | 'error' | 'stopped'

export interface AutosaveTimers {
  set: (fn: () => void, ms: number) => unknown
  clear: (handle: unknown) => void
  now: () => number
}

export interface AutosaveOptions {
  /** Can also be set or replaced later with `setSave` (e.g. from a React effect). */
  save?: () => Promise<AutosaveAttempt>
  onStatus?: (status: AutosaveStatus) => void
  delayMs?: number
  maxWaitMs?: number
  busyRetryMs?: number
  retryBaseMs?: number
  retryMaxMs?: number
  timers?: AutosaveTimers
}

/**
 * Whether the editor may autosave its draft now. Published pages are never autosaved: a save
 * goes live to guests at once, so half-typed text (an account number, say) would reach them.
 * Pending or failed photo selections cannot be saved, as with a manual save.
 */
export function autosaveGate(state: { published: boolean; photosPending: boolean; draftJson: string; baselineJson: string }): 'save' | 'clean' | 'blocked' {
  if (state.published || state.photosPending) return 'blocked'
  return state.draftJson === state.baselineJson ? 'clean' : 'save'
}

export const AUTOSAVE_DELAY_MS = 2500
export const AUTOSAVE_MAX_WAIT_MS = 10_000

const browserTimers: AutosaveTimers = {
  set: (fn, ms) => setTimeout(fn, ms),
  clear: handle => clearTimeout(handle as ReturnType<typeof setTimeout>),
  now: () => Date.now(),
}

/** Backoff after the n-th consecutive failure (1-based): base, 2×base, 4×base, ... up to max. */
export function retryDelay(failures: number, baseMs: number, maxMs: number): number {
  return Math.min(baseMs * 2 ** Math.max(0, failures - 1), maxMs)
}

export function createAutosave(options: AutosaveOptions) {
  const {
    onStatus,
    delayMs = AUTOSAVE_DELAY_MS,
    maxWaitMs = AUTOSAVE_MAX_WAIT_MS,
    busyRetryMs = 1000,
    retryBaseMs = 2000,
    retryMaxMs = 60_000,
    timers = browserTimers,
  } = options
  let save = options.save ?? (async (): Promise<AutosaveAttempt> => 'clean')

  let status: AutosaveStatus = 'idle'
  let timer: unknown = null
  /** A change not yet included in a save that has started. */
  let dirty = false
  /** When the oldest change in `dirty` was made, for `maxWaitMs`. */
  let dirtySince: number | null = null
  let inFlight: Promise<void> | null = null
  /** `flush()` during a save: run the follow-up save right away instead of debounced. */
  let flushAfter = false
  let failures = 0
  let holds = 0
  let stopped = false
  let detached = false
  /** After a 'rejected' save: no automatic retries until `retryNow()` or `reset()`. */
  let refused = false

  function setStatus(next: AutosaveStatus) {
    if (next === status) return
    status = next
    onStatus?.(next)
  }

  function clearTimer() {
    if (timer !== null) timers.clear(timer)
    timer = null
  }

  function arm(ms: number) {
    clearTimer()
    timer = timers.set(() => { timer = null; start() }, ms)
  }

  /** Debounce: `delayMs` after now, capped so the oldest change waits at most `maxWaitMs`. */
  function armDebounce() {
    const waited = dirtySince === null ? 0 : timers.now() - dirtySince
    arm(Math.max(0, Math.min(delayMs, maxWaitMs - waited)))
    setStatus('pending')
  }

  function canStart() { return !stopped && !detached && !refused && holds === 0 }

  /** `afterRefusal`: one attempt despite a refusal (a flush), still not while stopped or held. */
  function start(afterRefusal = false) {
    if (inFlight || stopped || detached || holds > 0 || (refused && !afterRefusal)) return
    clearTimer()
    dirty = false
    dirtySince = null
    flushAfter = false
    setStatus('saving')
    inFlight = (async () => {
      let attempt: AutosaveAttempt
      try { attempt = await save() } catch { attempt = 'failed' }
      inFlight = null
      settle(attempt)
    })()
  }

  function settle(attempt: AutosaveAttempt) {
    if (attempt === 'stale') {
      stopped = true
      clearTimer()
      setStatus('stopped')
      return
    }
    if (attempt === 'failed') {
      failures++
      // The failed content is still unsaved; changes made meanwhile ride along on the retry.
      markDirty()
      setStatus('error')
      if (canStart()) arm(retryDelay(failures, retryBaseMs, retryMaxMs))
      return
    }
    if (attempt === 'rejected') {
      refused = true
      failures = 0
      markDirty()
      clearTimer()
      setStatus('error')
      return
    }
    if (attempt === 'busy') {
      markDirty()
      setStatus('pending')
      if (canStart()) arm(busyRetryMs)
      return
    }
    if (attempt === 'saved' || attempt === 'clean') { failures = 0; refused = false }
    // 'blocked' drops the request; the caller schedules again once saving is allowed.
    if (attempt === 'blocked' && !dirty) { setStatus('idle'); return }
    if (!dirty) { setStatus('idle'); return }
    if (!canStart()) { setStatus('pending'); return }
    if (flushAfter) start()
    else armDebounce()
  }

  function markDirty() {
    if (!dirty) dirtySince = timers.now()
    dirty = true
  }

  return {
    status: () => status,

    /** Replaces what a save does; takes effect from the next save. */
    setSave(next: () => Promise<AutosaveAttempt>) { save = next },

    /** The draft differs from the stored copy (call on every change while it does). */
    schedule() {
      if (stopped || detached) return
      markDirty()
      // In flight: the follow-up is armed when it settles. In backoff: wait for that retry.
      if (inFlight || status === 'error' || holds > 0) return
      armDebounce()
    },

    /** Save now if anything is unsaved (tab hidden, page hiding, back online). */
    flush() {
      if (stopped || detached || (!dirty && status !== 'error')) return
      if (inFlight) { flushAfter = true; return }
      if (holds > 0) return
      start(true)
    },

    /** The user asked to retry: skip the remaining backoff. */
    retryNow() {
      if (stopped || detached) return
      failures = 0
      refused = false
      markDirty()
      if (inFlight) { flushAfter = true; return }
      if (holds > 0) return
      start()
    },

    /**
     * For a manual write: cancels the pending autosave, waits for one in flight, and blocks new
     * ones until the returned release is called. A later `schedule()` re-arms as usual.
     */
    async hold(): Promise<() => void> {
      holds++
      clearTimer()
      while (inFlight) await inFlight
      let released = false
      return () => {
        if (released) return
        released = true
        holds--
        if (holds === 0 && dirty && canStart()) {
          if (status === 'error') arm(retryDelay(failures, retryBaseMs, retryMaxMs))
          else armDebounce()
        }
      }
    },

    /**
     * A manual save succeeded: forget failures and pending changes. The caller schedules again
     * if the draft still differs from what was saved.
     */
    reset() {
      failures = 0
      refused = false
      dirty = false
      dirtySince = null
      clearTimer()
      if (!stopped) setStatus(inFlight ? 'saving' : 'idle')
    },

    /** Stop after a stale refusal seen elsewhere (manual save); `resume()` after loading the latest copy. */
    stop() {
      stopped = true
      dirty = false
      dirtySince = null
      clearTimer()
      setStatus('stopped')
    },

    resume() {
      if (!stopped) return
      stopped = false
      failures = 0
      setStatus('idle')
    },

    /** Unmount: no timers fire afterwards. A save in flight still finishes. */
    detach() {
      detached = true
      clearTimer()
    },

    /** Mount (again, under React StrictMode). */
    attach() {
      detached = false
      if (dirty && !inFlight && holds === 0 && !stopped) armDebounce()
    },
  }
}

export type Autosave = ReturnType<typeof createAutosave>
