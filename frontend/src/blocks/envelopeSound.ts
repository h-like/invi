// Seal crackle, snap and paper rustle, synthesized with Web Audio so no sound files ship.
// The context is created on the first seal touch, which satisfies autoplay rules.

export interface EnvelopeSound {
  unlock(): void
  setMuted(muted: boolean): void
  crackle(): void
  snap(): void
  rustle(delay: number, duration: number, gain: number): void
}

export function createEnvelopeSound(): EnvelopeSound {
  let ctx: AudioContext | null = null
  let noise: AudioBuffer | null = null
  let muted = false

  function context(): AudioContext | null {
    if (muted) return null
    if (!ctx) {
      const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
      if (!Ctor) return null
      try { ctx = new Ctor() } catch { return null }
    }
    if (ctx.state === 'suspended') void ctx.resume()
    return ctx
  }

  function noiseBuffer(a: AudioContext): AudioBuffer {
    if (!noise) {
      noise = a.createBuffer(1, a.sampleRate, a.sampleRate)
      const data = noise.getChannelData(0)
      for (let i = 0; i < data.length; i++) data[i] = Math.random() * 2 - 1
    }
    return noise
  }

  function burst(a: AudioContext, t: number, { dur, type = 'bandpass', freq, q = 1, gain }: { dur: number; type?: BiquadFilterType; freq: number; q?: number; gain: number }) {
    const source = a.createBufferSource()
    source.buffer = noiseBuffer(a)
    const filter = a.createBiquadFilter()
    filter.type = type
    filter.frequency.value = freq
    filter.Q.value = q
    const g = a.createGain()
    g.gain.setValueAtTime(gain, t)
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur)
    source.connect(filter).connect(g).connect(a.destination)
    source.start(t, Math.random() * 0.5)
    source.stop(t + dur + 0.02)
  }

  return {
    unlock() { context() },
    setMuted(value) { muted = value },
    crackle() {
      const a = context()
      if (a) burst(a, a.currentTime, { dur: 0.03, type: 'highpass', freq: 2200 + Math.random() * 1500, gain: 0.22 })
    },
    snap() {
      const a = context()
      if (!a) return
      const t = a.currentTime
      burst(a, t, { dur: 0.08, freq: 1900, q: 0.8, gain: 1 })
      burst(a, t + 0.004, { dur: 0.13, type: 'lowpass', freq: 900, gain: 0.5 })
      const osc = a.createOscillator()
      const g = a.createGain()
      osc.frequency.setValueAtTime(150, t)
      osc.frequency.exponentialRampToValueAtTime(55, t + 0.09)
      g.gain.setValueAtTime(0.45, t)
      g.gain.exponentialRampToValueAtTime(0.0001, t + 0.13)
      osc.connect(g).connect(a.destination)
      osc.start(t)
      osc.stop(t + 0.15)
      for (let i = 0; i < 6; i++) burst(a, t + 0.14 + Math.random() * 0.35, { dur: 0.02, type: 'highpass', freq: 3000 + Math.random() * 2500, gain: 0.08 + Math.random() * 0.1 })
    },
    rustle(delay, duration, gain) {
      const a = context()
      if (!a) return
      const t = a.currentTime + delay
      const source = a.createBufferSource()
      source.buffer = noiseBuffer(a)
      source.loop = true
      const filter = a.createBiquadFilter()
      filter.type = 'bandpass'
      filter.Q.value = 0.6
      filter.frequency.setValueAtTime(800, t)
      filter.frequency.linearRampToValueAtTime(1700, t + duration)
      const g = a.createGain()
      g.gain.setValueAtTime(0.0001, t)
      g.gain.linearRampToValueAtTime(gain, t + duration * 0.3)
      g.gain.exponentialRampToValueAtTime(0.0001, t + duration)
      source.connect(filter).connect(g).connect(a.destination)
      source.start(t)
      source.stop(t + duration + 0.05)
    },
  }
}
