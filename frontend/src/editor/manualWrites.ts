import type { Autosave } from './autosave.ts'

/**
 * The editor's manual writes (save, publish, wedding date, unpublish, loading the latest copy):
 * one at a time, and each takes the write guard from autosave first.
 *
 * - `running` is set synchronously, so a second click before React re-renders is refused here
 *   instead of reaching the persistence guard and reporting "another save is in progress".
 * - `write` starts only after an autosave in flight has settled, and that autosave may have moved
 *   the version on. So `write` must read the latest copy (the editor's refs) inside, never from a
 *   value captured before `run` was called — otherwise this tab's own autosave makes it stale.
 * - No autosave starts while `write` runs; a pending one is cancelled and re-armed afterwards.
 */
export function createManualWrites(autosave: Pick<Autosave, 'hold'>) {
  let running = false
  return {
    isRunning: () => running,
    /** Returns `write`'s result, or `undefined` without calling it when another manual write runs. */
    async run<T>(write: () => Promise<T>): Promise<T | undefined> {
      if (running) return undefined
      running = true
      try {
        const release = await autosave.hold()
        try { return await write() } finally { release() }
      } finally {
        running = false
      }
    },
  }
}

export type ManualWrites = ReturnType<typeof createManualWrites>
