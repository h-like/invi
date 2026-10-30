import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { createEnvelopeSound } from './envelopeSound'
import './envelope.css'

// Guest-page opening for themes with an envelope: push the wax seal sideways until it
// cracks, the flap swings open, then swipe the letter (the real invitation) up and out
// while the envelope drops away. Fixed layers sit around the in-flow letter, so once it
// is out the page is an ordinary scrolling invitation. Only the guest page uses this;
// editor previews never lock the window.

type Stage = 'sealed' | 'opening' | 'open' | 'pulling' | 'read'

const STAGE_HINTS: Partial<Record<Stage, string>> = {
  open: '봉투가 열렸습니다. 위로 쓸어올리거나 Enter 키를 눌러 청첩장을 꺼내 주세요.',
}

// Envelope art is drawn in a 340×236 box and scaled to the column.
const geometry = (() => {
  let blob = ''
  for (let i = 0; i <= 120; i++) {
    const t = i / 120 * Math.PI * 2
    const r = 35 + 2.2 * Math.sin(9 * t) + 1.3 * Math.sin(23 * t + 1) + 1.1 * Math.sin(5 * t + 2)
    blob += (i ? 'L' : 'M') + (r * Math.cos(t)).toFixed(2) + ' ' + (r * Math.sin(t)).toFixed(2)
  }
  // The seal splits along the flap's edge, with a jagged break.
  const slope = 153.4 / 170
  const edge: [number, number][] = []
  for (let i = 0, x = -46; x <= 46.01; x += 2.3, i++) {
    const jag = (i % 2 ? 1.5 : -1.1) * (0.6 + 0.4 * Math.abs(Math.sin(i * 12.9898)))
    edge.push([x, -slope * Math.abs(x) + 5 + (Math.abs(x) < 1 ? 0 : jag)])
  }
  const points = (list: [number, number][]) => list.map(p => p[0].toFixed(2) + ',' + p[1].toFixed(2)).join(' ')
  const path = (list: [number, number][]) => 'M' + list.map(p => p[0].toFixed(2) + ' ' + p[1].toFixed(2)).join('L')
  const inside = (p: [number, number]) => Math.hypot(p[0], p[1]) < 37
  const mid = edge.findIndex(p => p[0] >= 0)
  return {
    blob: blob + 'Z',
    top: points([...edge.map(p => [p[0], p[1] + 0.6] as [number, number]), [46, -60], [-46, -60]]),
    bottom: points([...edge, [46, 60], [-46, 60]]),
    crackLeft: path(edge.slice(0, mid + 1).reverse().filter(inside)),
    crackRight: path(edge.slice(mid).filter(inside)),
  }
})()

function readOnce(key: string): boolean {
  try { return sessionStorage.getItem(key) === '1' } catch { return false }
}

interface Props {
  paper: string
  wine: string
  /** Remembers per tab that the guest already opened this invitation, so a refresh skips the intro. */
  storageKey: string
  children: ReactNode
}

export function EnvelopeIntro({ paper, wine, storageKey, children }: Props) {
  const [read, setRead] = useState(() => readOnce(storageKey))
  const [muted, setMuted] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const letter = useRef<HTMLDivElement>(null)
  const flap = useRef<HTMLDivElement>(null)
  const waxLayer = useRef<HTMLDivElement>(null)
  const waxTop = useRef<HTMLDivElement>(null)
  const waxBottom = useRef<HTMLDivElement>(null)
  const crackLeft = useRef<SVGPathElement>(null)
  const crackRight = useRef<SVGPathElement>(null)
  const hit = useRef<HTMLButtonElement>(null)
  const live = useRef<HTMLParagraphElement>(null)
  const sound = useRef(createEnvelopeSound())
  const skip = useRef<() => void>(() => {})
  const justOpened = useRef(false)

  useEffect(() => { sound.current.setMuted(muted) }, [muted])

  useLayoutEffect(() => {
    if (read) return
    const el = root.current, wrap = letter.current, flapLayer = flap.current, waxLay = waxLayer.current
    const top = waxTop.current, bottom = waxBottom.current, seal = hit.current
    if (!el || !wrap || !flapLayer || !waxLay || !top || !bottom || !seal || !crackLeft.current || !crackRight.current) return
    const html = document.documentElement
    const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches
    const audio = sound.current
    const timers: number[] = []
    const later = (fn: () => void, ms: number) => { timers.push(window.setTimeout(fn, ms)) }

    let stage: Stage = 'sealed'
    const setStage = (next: Stage) => {
      stage = next
      el.dataset.stage = next
      if (live.current) live.current.textContent = STAGE_HINTS[next] ?? ''
    }
    el.classList.remove('is-cracked', 'is-open', 'is-animating')
    flapLayer.classList.remove('is-behind')
    top.classList.remove('is-lifted')
    bottom.classList.remove('is-settled')
    setStage('sealed')
    html.classList.add('envelope-locked')
    // The letter is placed against the fixed envelope, so the page must sit at the top:
    // a restored scroll position after a refresh would push the letter out of the pocket.
    // Browsers keep this per history entry across reloads, so cleanup resets it to the
    // default rather than to whatever an interrupted earlier visit left behind.
    history.scrollRestoration = 'manual'
    window.scrollTo(0, 0)
    const pinTop = () => { if (stage !== 'read' && window.scrollY !== 0) window.scrollTo(0, 0) }
    window.addEventListener('scroll', pinTop)

    // ---- layout: envelope centred in the guest column, letter tucked into its pocket ----
    const setVar = (name: string, value: string) => el.style.setProperty(name, value)
    let width = -1, vh = 0, envTop = 0, scale = 1, travel = 0, pull = 0
    function update() {
      const y = pull * travel
      const start = travel * 0.3
      let e = travel - start > 0 ? Math.min(1, Math.max(0, (y - start) / (travel - start))) : pull
      e *= e
      setVar('--env-drop', e * (vh - envTop + 100) + 'px')
      wrap!.style.transform = y > 0 ? `translateY(${-y}px)` : ''
    }
    function layout(force: boolean) {
      const box = el!.getBoundingClientRect()
      // Mobile URL bars change the height constantly; only a width change moves things.
      if (!force && box.width === width) return
      width = box.width
      vh = window.innerHeight
      const w = width - 30
      scale = w / 340
      const h = 236 * scale
      envTop = Math.round(Math.max(h * 0.7 + 28, (vh - h) / 2 + h * 0.2))
      setVar('--env-s', String(scale))
      setVar('--env-l', box.left + 15 + 'px')
      setVar('--env-t', envTop + 'px')
      setVar('--env-h', h + 'px')
      setVar('--env-col-l', box.left + 'px')
      setVar('--env-col-w', width + 'px')
      wrap!.style.transform = ''
      wrap!.style.paddingTop = '0px'
      const sheet = wrap!.querySelector('.page-sheet') ?? wrap!
      const natural = sheet.getBoundingClientRect().top + window.scrollY
      travel = Math.max(0, envTop + 14 * scale - natural)
      wrap!.style.paddingTop = travel + 'px'
      update()
    }
    layout(true)
    const onResize = () => { if (stage === 'sealed' || stage === 'open') layout(false) }
    window.addEventListener('resize', onResize)

    // ---- seal: push sideways (or tap a few times) until it cracks ----
    const cracks = [crackLeft.current, crackRight.current].map(path => {
      const length = path.getTotalLength()
      path.style.strokeDasharray = String(length)
      path.style.strokeDashoffset = String(length)
      return { path, length }
    })
    let crack = 0, lastTick = 0
    let sealDrag: { x: number; base: number; moved: number } | null = null
    const setCrack = (c: number) => cracks.forEach(({ path, length }) => { path.style.strokeDashoffset = String(length * (1 - c)) })
    const setShift = (px: number) => { top.style.setProperty('--wx', px + 'px'); bottom.style.setProperty('--wx', px + 'px') }

    function chips(dir: number) {
      if (reduce || !waxLay!.animate) return
      for (let i = 0; i < 9; i++) {
        const chip = document.createElement('span')
        chip.className = 'env-chip'
        chip.style.left = 170 + (i - 4) * 6 + 'px'
        chip.style.top = 153 + Math.sin(i * 3.1) * 3 + 'px'
        chip.style.clipPath = `polygon(${i * 13 % 40}% 0,100% ${i * 29 % 50}%,${60 + i * 7 % 40}% 100%,0 ${30 + i * 11 % 60}%)`
        waxLay!.appendChild(chip)
        const size = 0.5 + (i * 37 % 10) / 12, vx = (i - 4) * 9 + dir * 14, up = 20 + (i * 17 % 25)
        chip.animate([
          { transform: `translate(0,0) rotate(0) scale(${size})`, opacity: 1 },
          { transform: `translate(${vx * 0.6}px,${-up}px) rotate(${dir * 160}deg) scale(${size})`, opacity: 1, offset: 0.35 },
          { transform: `translate(${vx}px,${70 + i * 6}px) rotate(${dir * 420}deg) scale(${size})`, opacity: 0 },
        ], { duration: 900 + i * 40, easing: 'cubic-bezier(.3,.5,.6,1)', fill: 'forwards' }).onfinish = () => chip.remove()
      }
    }
    function breakSeal(dir: number) {
      setStage('opening')
      el!.classList.add('is-cracked')
      setShift(0)
      top!.classList.add('is-lifted')
      bottom!.classList.add('is-settled')
      chips(dir)
      audio.snap()
      audio.rustle(reduce ? 0 : 0.45, 0.8, 0.16)
      later(() => {
        el!.classList.add('is-open')
        later(() => flapLayer!.classList.add('is-behind'), reduce ? 10 : 480)
        later(() => setStage('open'), reduce ? 50 : 950)
      }, reduce ? 0 : 420)
    }
    function advanceCrack(to: number, dir: number) {
      crack = Math.max(crack, Math.min(1, to))
      setCrack(crack)
      if (crack >= 1) { sealDrag = null; breakSeal(dir); return }
      if (crack - lastTick >= 0.12) { lastTick = crack; audio.crackle() }
    }
    const onSealDown = (e: PointerEvent) => {
      if (stage !== 'sealed') return
      audio.unlock()
      seal.setPointerCapture(e.pointerId)
      sealDrag = { x: e.clientX, base: crack, moved: 0 }
    }
    const onSealMove = (e: PointerEvent) => {
      if (!sealDrag) return
      const dx = (e.clientX - sealDrag.x) / scale
      sealDrag.moved = Math.max(sealDrag.moved, Math.abs(dx))
      setShift(Math.max(-4, Math.min(4, dx * 0.06)))
      advanceCrack(sealDrag.base + Math.abs(dx) / 90, Math.sign(dx) || 1)
    }
    const onSealUp = () => {
      if (!sealDrag) return
      // A plain tap also works: three taps break the seal.
      const tapped = sealDrag.moved < 6
      sealDrag = null
      setShift(0)
      if (tapped) { lastTick = -1; advanceCrack(crack + 0.34, 1) }
    }
    const onSealKey = (e: KeyboardEvent) => {
      if ((e.key === 'Enter' || e.key === ' ') && stage === 'sealed') { e.preventDefault(); audio.unlock(); advanceCrack(1, 1) }
    }
    seal.addEventListener('pointerdown', onSealDown)
    seal.addEventListener('pointermove', onSealMove)
    seal.addEventListener('pointerup', onSealUp)
    seal.addEventListener('pointercancel', onSealUp)
    seal.addEventListener('keydown', onSealKey)

    // ---- pull the letter up and out ----
    let pullDrag: { y: number; base: number } | null = null
    function finish() {
      timers.forEach(clearTimeout)
      wrap!.style.transform = ''
      wrap!.style.paddingTop = ''
      html.classList.remove('envelope-locked')
      window.scrollTo(0, 0)
      setStage('read')
      try { sessionStorage.setItem(storageKey, '1') } catch { /* private mode: the intro just plays again */ }
      justOpened.current = true
      setRead(true)
    }
    function settle(target: 0 | 1) {
      if (stage !== 'open') return
      el!.classList.add('is-animating')
      pull = target
      update()
      if (target === 1) {
        setStage('pulling')
        audio.rustle(0, 0.6, 0.12)
        later(finish, reduce ? 220 : 720)
      } else later(() => el!.classList.remove('is-animating'), 720)
    }
    const onDown = (e: PointerEvent) => {
      if (stage !== 'open' || (e.target as Element | null)?.closest?.('.envelope-tools')) return
      el.classList.remove('is-animating')
      pullDrag = { y: e.clientY, base: pull }
    }
    const onMove = (e: PointerEvent) => {
      if (!pullDrag) return
      pull = Math.min(1, Math.max(0, pullDrag.base + (pullDrag.y - e.clientY) / (vh * 0.5)))
      update()
    }
    const onUp = (e: PointerEvent) => {
      if (!pullDrag) return
      const tapped = Math.abs(pullDrag.y - e.clientY) < 6
      const moved = pull > pullDrag.base
      pullDrag = null
      settle(tapped || (moved && pull > 0.2) ? 1 : 0)
    }
    const onCancel = () => { if (pullDrag) { pullDrag = null; settle(0) } }
    const onWheel = (e: WheelEvent) => { if (stage === 'open' && e.deltaY > 0) settle(1) }
    const onKey = (e: KeyboardEvent) => {
      if (stage === 'open' && ['ArrowUp', 'ArrowDown', ' ', 'PageDown', 'Enter'].includes(e.key)) { e.preventDefault(); settle(1) }
    }
    window.addEventListener('pointerdown', onDown)
    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', onUp)
    window.addEventListener('pointercancel', onCancel)
    window.addEventListener('wheel', onWheel, { passive: true })
    window.addEventListener('keydown', onKey)
    skip.current = () => { if (stage !== 'read') finish() }

    return () => {
      timers.forEach(clearTimeout)
      html.classList.remove('envelope-locked')
      history.scrollRestoration = 'auto'
      window.removeEventListener('scroll', pinTop)
      window.removeEventListener('resize', onResize)
      seal.removeEventListener('pointerdown', onSealDown)
      seal.removeEventListener('pointermove', onSealMove)
      seal.removeEventListener('pointerup', onSealUp)
      seal.removeEventListener('pointercancel', onSealUp)
      seal.removeEventListener('keydown', onSealKey)
      window.removeEventListener('pointerdown', onDown)
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', onUp)
      window.removeEventListener('pointercancel', onCancel)
      window.removeEventListener('wheel', onWheel)
      window.removeEventListener('keydown', onKey)
    }
  }, [read, storageKey])

  // After the letter is out, move focus into it so keyboard and screen reader users land on the invitation.
  useEffect(() => {
    if (!read || !justOpened.current) return
    justOpened.current = false
    letter.current?.focus({ preventScroll: true })
  }, [read])

  const colors = { '--env-paper': paper, '--env-wine': wine } as CSSProperties
  return (
    <div ref={root} className="envelope" data-stage={read ? 'read' : 'sealed'} style={colors}>
      {!read && <>
        <div className="envelope-tools">
          <button type="button" className="envelope-pill" aria-pressed={muted} onClick={() => setMuted(m => !m)}>{muted ? '소리 꺼짐' : '소리 켜짐'}</button>
          <button type="button" className="envelope-pill" onClick={() => skip.current()}>바로 보기</button>
        </div>
        <p ref={live} className="envelope-live" aria-live="polite" />
        <svg className="env-defs" aria-hidden="true" focusable="false">
          <defs>
            <radialGradient id="env-wax" cx="38%" cy="32%" r="75%"><stop offset="0" stopColor="#f6e0a0" /><stop offset=".45" stopColor="#d1aa52" /><stop offset="1" stopColor="#8c6a22" /></radialGradient>
            <radialGradient id="env-pool" cx="60%" cy="65%" r="70%"><stop offset="0" stopColor="#b98f3a" /><stop offset="1" stopColor="#e2c27a" /></radialGradient>
            <linearGradient id="env-under" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#9c7a30" /><stop offset="1" stopColor="#6e5216" /></linearGradient>
            <path id="env-blob" d={geometry.blob} />
            <g id="env-seal-art">
              <use href="#env-blob" fill="url(#env-wax)" />
              <circle r="25" fill="url(#env-pool)" />
              <circle r="25" fill="none" stroke="#8a6721" strokeWidth="1.4" />
              <circle r="22.5" fill="none" stroke="#f3dc9c" strokeWidth=".6" opacity=".8" />
              <text y="9" x=".6" textAnchor="middle" fontFamily="Pinyon Script, cursive" fontSize="26" fill="#f7e6b4" opacity=".75">&amp;</text>
              <text y="8.3" textAnchor="middle" fontFamily="Pinyon Script, cursive" fontSize="26" fill="#7a5a1a">&amp;</text>
            </g>
            <clipPath id="env-cp-top"><polygon points={geometry.top} /></clipPath>
            <clipPath id="env-cp-bottom"><polygon points={geometry.bottom} /></clipPath>
          </defs>
        </svg>
        <div className="env-lay env-lay-back" aria-hidden="true"><div className="env-back" /></div>
        <div className="env-lay env-lay-flap" ref={flap} aria-hidden="true">
          <div className="env-flap">
            <div className="env-face env-flap-front" />
            <div className="env-face env-flap-inner" />
            <div className="env-wax env-wax-top" ref={waxTop}>
              <svg className="env-wax-front" viewBox="-40 -40 80 80"><use href="#env-seal-art" clipPath="url(#env-cp-top)" /></svg>
              <svg className="env-wax-under" viewBox="-40 -40 80 80"><g transform="scale(1,-1)"><use href="#env-blob" clipPath="url(#env-cp-top)" fill="url(#env-under)" /></g></svg>
            </div>
          </div>
        </div>
        <div className="env-mask" aria-hidden="true">
          <p className="env-hint env-hint-seal"><span>⟵ ⟶</span>실링을 옆으로 밀어 깨뜨려 주세요</p>
          <p className="env-hint env-hint-pull"><span>︿</span>위로 쓸어올려 편지를 꺼내 보세요</p>
        </div>
        <div className="env-lay env-lay-front" aria-hidden="true">
          <div className="env-shadow" />
          <div className="env-pocket">
            <div className="env-pocket-shade"><div className="env-pocket-side env-pocket-left" /></div>
            <div className="env-pocket-shade"><div className="env-pocket-side env-pocket-right" /></div>
            <div className="env-pocket-shade"><div className="env-pocket-bottom" /></div>
          </div>
          <div className="env-flap-shadow" />
        </div>
        <div className="env-lay env-lay-wax" ref={waxLayer}>
          <div className="env-wax env-wax-bottom" ref={waxBottom} aria-hidden="true">
            <svg viewBox="-40 -40 80 80"><use href="#env-seal-art" clipPath="url(#env-cp-bottom)" /></svg>
            <svg className="env-crack" viewBox="-40 -40 80 80"><path ref={crackLeft} d={geometry.crackLeft} /><path ref={crackRight} d={geometry.crackRight} /></svg>
          </div>
          <button ref={hit} type="button" className="env-seal-hit" aria-label="실링을 깨고 청첩장 열기" />
        </div>
      </>}
      <div ref={letter} className="envelope-letter" inert={!read} tabIndex={-1}>{children}</div>
    </div>
  )
}
