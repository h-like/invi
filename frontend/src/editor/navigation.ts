export interface EditorChangeState {
  dirty: boolean
  busy: boolean
  /** Cover/gallery uploads that are still processing or failed; they are lost on leave. */
  photoPending?: boolean
}

export interface EditorLocation {
  pathname: string
  search: string
}

export type LeaveDialogMode = 'busy' | 'photos' | 'unsaved' | 'clean'

export function needsLeaveWarning(state: EditorChangeState): boolean {
  return state.dirty || state.busy || !!state.photoPending
}

export function leaveDialogMode(state: EditorChangeState): LeaveDialogMode {
  if (state.busy) return 'busy'
  if (state.photoPending) return 'photos'
  return state.dirty ? 'unsaved' : 'clean'
}

/** Saving is refused while photos are pending, so save-and-leave is offered only for plain unsaved edits. */
export function canSaveBeforeLeave(state: EditorChangeState): boolean {
  return state.dirty && !state.photoPending
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
