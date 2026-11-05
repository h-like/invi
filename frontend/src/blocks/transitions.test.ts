import { test } from 'node:test'
import assert from 'node:assert/strict'
import { longestTransitionMs } from './transitions.ts'

test('the slide timer covers every listed transition, delays included', () => {
  assert.equal(longestTransitionMs({ transitionDuration: '0.7s', transitionDelay: '0.15s' }), 850)
  // A later, longer transition is not cut short by reading only the first entry.
  assert.equal(longestTransitionMs({ transitionDuration: '0.7s, 1s', transitionDelay: '0s' }), 1000)
  // The shorter list repeats: delays .1s,.5s pair with .7s, .3s, .7s.
  assert.equal(longestTransitionMs({ transitionDuration: '0.7s, 0.3s, 0.7s', transitionDelay: '0.1s, 0.5s' }), 800)
  assert.equal(longestTransitionMs({ transitionDuration: '', transitionDelay: '' }), 0)
})
