export interface EditorChangeState {
  dirty: boolean
  busy: boolean
}

export interface EditorLocation {
  pathname: string
  search: string
}

export function needsLeaveWarning(state: EditorChangeState): boolean {
  return state.dirty || state.busy
}

export function shouldBlockEditorNavigation(state: EditorChangeState, current: EditorLocation, next: EditorLocation): boolean {
  // Hash-only jumps and same-location state updates do not unmount this editor.
  return needsLeaveWarning(state) && (current.pathname !== next.pathname || current.search !== next.search)
}

export function preventEditorUnload(event: Pick<BeforeUnloadEvent, 'preventDefault' | 'returnValue'>, state: EditorChangeState): boolean {
  if (!needsLeaveWarning(state)) return false
  event.preventDefault()
  event.returnValue = ''
  return true
}

/** Saves may finish after the user cancels or chooses a different destination. */
export function createSaveBeforeLeave() {
  let version = 0
  let running = false
  return {
    cancel() { version++ },
    isRunning: () => running,
    async run(save: () => Promise<boolean>, stillBlocked: () => boolean, proceed: () => void): Promise<boolean> {
      if (running) return false
      running = true
      const attempt = ++version
      try {
        if (!await save() || attempt !== version || !stillBlocked()) return false
        proceed()
        return true
      } catch {
        // Never discard the editor when saving or navigation confirmation fails.
        return false
      } finally {
        running = false
      }
    },
  }
}
