import { createContext, useContext, useId, useLayoutEffect, useState, type ReactNode, type RefObject } from 'react'
import type { ThemeParts } from '../editor/model'
import { monogramFontSize, sealMonogram } from './heroDetails'

// Envelope art shared by the guest-page intro (EnvelopeIntro) and the editor's static
// preview (EnvelopePreview). Drawn in a 340×236 box; the flap tip and the seal centre sit
// at y=153. Which flap, material, liner, wax colour, emblem and decoration show comes from
// the page's envelope parts.

export type EnvelopeParts = Pick<ThemeParts, 'envelopeFlap' | 'envelopeMaterial' | 'envelopeLiner' | 'sealColor' | 'sealEmblem' | 'envelopeDecor'>

interface WaxPalette { hi: string; mid: string; lo: string; pool: [string, string]; under: [string, string]; rim: string; ring: string; textHi: string; text: string; chip: [string, string]; crack: string }

// Gold is the original seal; the others keep its light-to-shadow structure.
export const WAX_PALETTES: Record<ThemeParts['sealColor'], WaxPalette> = {
  gold: { hi: '#f6e0a0', mid: '#d1aa52', lo: '#8c6a22', pool: ['#b98f3a', '#e2c27a'], under: ['#9c7a30', '#6e5216'], rim: '#8a6721', ring: '#f3dc9c', textHi: '#f7e6b4', text: '#7a5a1a', chip: ['#f2d68e', '#a8812c'], crack: '#4a3208' },
  wine: { hi: '#d8707c', mid: '#9e2b3a', lo: '#5c111d', pool: ['#7d1c2a', '#b8495a'], under: ['#6e1824', '#430a14'], rim: '#5a1220', ring: '#e7a2ab', textHi: '#f0b9c0', text: '#4a0d18', chip: ['#d06a78', '#7a1a28'], crack: '#2e050c' },
  silver: { hi: '#f4f5f7', mid: '#b9bec6', lo: '#6c727c', pool: ['#959ba5', '#d6dae0'], under: ['#858b95', '#565b63'], rim: '#6b717a', ring: '#ffffff', textHi: '#ffffff', text: '#50555d', chip: ['#e8eaee', '#8d939c'], crack: '#3a3e44' },
  forest: { hi: '#9fc0a0', mid: '#4f7a55', lo: '#22402a', pool: ['#3d6444', '#79a07d'], under: ['#355a3b', '#1c3322'], rim: '#24442b', ring: '#c8e0c6', textHi: '#d9ecd6', text: '#183020', chip: ['#8fb592', '#3b6342'], crack: '#0f2014' },
  navy: { hi: '#8ea4cc', mid: '#3c5788', lo: '#172a4d', pool: ['#2d4673', '#6a83b0'], under: ['#26395e', '#121f3a'], rim: '#1a2c50', ring: '#c4d2ec', textHi: '#d6e0f2', text: '#0f1c38', chip: ['#8199c4', '#2c4470'], crack: '#0a1328' },
}

// Emblems are paths, not glyphs: ♥ and friends turn into colour emoji on some phones.
const EMBLEMS: Record<Exclude<ThemeParts['sealEmblem'], 'monogram'>, string> = {
  heart: 'M0 10C-15 0-10-13 0-5C10-13 15 0 0 10Z',
  leaf: 'M-11 11C-12-3 0-12 12-12C12 0 3 10-11 11ZM-11 11L7-7',
  star: 'M0-14L3.2-3.2L14 0L3.2 3.2L0 14L-3.2 3.2L-14 0L-3.2-3.2Z',
}

// The seal splits along the flap's edge with a jagged break. A pointed flap's edges slope
// down to the tip; round and straight flaps meet the seal along a level edge.
function buildGeometry(slope: number) {
  let blob = ''
  for (let i = 0; i <= 120; i++) {
    const t = i / 120 * Math.PI * 2
    const r = 35 + 2.2 * Math.sin(9 * t) + 1.3 * Math.sin(23 * t + 1) + 1.1 * Math.sin(5 * t + 2)
    blob += (i ? 'L' : 'M') + (r * Math.cos(t)).toFixed(2) + ' ' + (r * Math.sin(t)).toFixed(2)
  }
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
}
const GEOMETRY = { pointed: buildGeometry(153.4 / 170), level: buildGeometry(0) }
export function sealGeometry(flap: ThemeParts['envelopeFlap']) {
  return flap === 'pointed' ? GEOMETRY.pointed : GEOMETRY.level
}

// SVG ids are document-wide; each envelope gets its own prefix so two on one page (the
// editor preview next to anything else) never borrow each other's gradients or clips.
const IdPrefix = createContext('env')
export function EnvelopeIds({ children }: { children: ReactNode }) {
  return <IdPrefix.Provider value={'env' + useId().replace(/[^a-zA-Z0-9]/g, '')}>{children}</IdPrefix.Provider>
}
function useIds() {
  const p = useContext(IdPrefix)
  const id = (name: string) => p + '-' + name
  return { id, href: (name: string) => '#' + id(name), url: (name: string) => 'url(#' + id(name) + ')' }
}

// Usable width inside the seal's inner ring (radius 22.5) in seal units.
const SEAL_TEXT_WIDTH = 36

export interface SealText { text: string; size: number; x: number; y: number; family: string }

/**
 * Script capitals vary a lot in width, so initials are measured once the font is in and
 * shrunk to fit inside the inner ring. The lone ampersand keeps its original drawing.
 */
export function useSealText(monogram: string | undefined, svg: RefObject<SVGSVGElement | null>, enabled: boolean, family: string): SealText {
  const [sealFit, setSealFit] = useState<{ label: string; size: number; dx: number } | null>(null)
  useLayoutEffect(() => {
    const label = sealMonogram(monogram)
    const host = svg.current
    if (!enabled || label === '&' || !host) return
    let live = true
    const base = monogramFontSize(label)
    const name = family.replace(/["']/g, '')
    const measure = () => {
      if (!live) return
      const probe = document.createElementNS('http://www.w3.org/2000/svg', 'text')
      probe.setAttribute('font-family', family + ', cursive')
      probe.setAttribute('font-size', String(base))
      probe.setAttribute('text-anchor', 'middle')
      probe.setAttribute('visibility', 'hidden')
      probe.textContent = label
      host.appendChild(probe)
      const box = probe.getBBox()
      probe.remove()
      if (!box.width) return
      const k = Math.min(1, SEAL_TEXT_WIDTH / box.width)
      const next = { label, size: Math.round(base * k * 10) / 10, dx: Math.round(-(box.x + box.width / 2) * k * 10) / 10 }
      setSealFit(prev => prev && prev.label === next.label && prev.size === next.size && prev.dx === next.dx ? prev : next)
    }
    // The script face is registered lazily with the theme CSS, so measure again when that
    // face finishes loading; the first pass may still see the fallback.
    // Other fonts (the body face's unicode-range chunks) cannot change the seal width.
    const onFontsLoaded = (event: Event) => {
      const faces = (event as FontFaceSetLoadEvent).fontfaces ?? []
      if (faces.some(face => face.family.replace(/["']/g, '') === name)) measure()
    }
    document.fonts.load(`${base}px ${family}`, label).then(measure, measure)
    document.fonts.addEventListener('loadingdone', onFontsLoaded)
    return () => { live = false; document.fonts.removeEventListener('loadingdone', onFontsLoaded) }
  }, [enabled, monogram, svg, family])

  const label = sealMonogram(monogram)
  const fitted = sealFit?.label === label ? sealFit : null
  const size = fitted?.size ?? monogramFontSize(label)
  // Baseline sits about a third of the font size below centre, so the glyphs look centred.
  return { text: label, size, x: fitted?.dx ?? 0, y: Math.round(size * 0.32 * 10) / 10, family }
}

/** Gradients, the wax blob and the full seal artwork, referenced by the layers below. */
export function EnvelopeDefs({ parts, seal }: { parts: EnvelopeParts; seal: SealText }) {
  const wax = WAX_PALETTES[parts.sealColor]
  const geometry = sealGeometry(parts.envelopeFlap)
  const emblem = parts.sealEmblem === 'monogram' ? null : EMBLEMS[parts.sealEmblem]
  const { id, href, url } = useIds()
  return (
    <svg className="env-defs" aria-hidden="true" focusable="false">
      <defs>
        <radialGradient id={id('wax')} cx="38%" cy="32%" r="75%"><stop offset="0" stopColor={wax.hi} /><stop offset=".45" stopColor={wax.mid} /><stop offset="1" stopColor={wax.lo} /></radialGradient>
        <radialGradient id={id('pool')} cx="60%" cy="65%" r="70%"><stop offset="0" stopColor={wax.pool[0]} /><stop offset="1" stopColor={wax.pool[1]} /></radialGradient>
        <linearGradient id={id('under')} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor={wax.under[0]} /><stop offset="1" stopColor={wax.under[1]} /></linearGradient>
        <path id={id('blob')} d={geometry.blob} />
        <g id={id('seal-art')}>
          <use href={href('blob')} fill={url('wax')} />
          <circle r="25" fill={url('pool')} />
          <circle r="25" fill="none" stroke={wax.rim} strokeWidth="1.4" />
          <circle r="22.5" fill="none" stroke={wax.ring} strokeWidth=".6" opacity=".8" />
          {emblem
            ? <>
              <path d={emblem} transform="translate(.6 .7)" fill={wax.textHi} stroke={wax.textHi} strokeWidth="1.2" strokeLinejoin="round" opacity=".75" />
              <path d={emblem} fill={wax.text} stroke={wax.text} strokeWidth="1.2" strokeLinejoin="round" />
            </>
            : <>
              <text y={seal.y + .7} x={seal.x + .6} textAnchor="middle" fontFamily={seal.family + ', cursive'} fontSize={seal.size} fill={wax.textHi} opacity=".75">{seal.text}</text>
              <text y={seal.y} x={seal.x} textAnchor="middle" fontFamily={seal.family + ', cursive'} fontSize={seal.size} fill={wax.text}>{seal.text}</text>
            </>}
        </g>
        <clipPath id={id('cp-top')}><polygon points={geometry.top} /></clipPath>
        <clipPath id={id('cp-bottom')}><polygon points={geometry.bottom} /></clipPath>
      </defs>
    </svg>
  )
}

/** CSS variables for the wax chips and crack line, which are drawn outside the SVG defs. */
export function waxVars(parts: EnvelopeParts): Record<string, string> {
  const wax = WAX_PALETTES[parts.sealColor]
  return { '--env-chip-hi': wax.chip[0], '--env-chip-lo': wax.chip[1], '--env-crack': wax.crack }
}

/** Classes for the parts envelope.css draws (shape, material, liner); defaults add none. */
export function envelopeClassNames(parts: EnvelopeParts): string[] {
  return [
    parts.envelopeFlap !== 'pointed' && 'env-shape-' + parts.envelopeFlap,
    parts.envelopeMaterial !== 'smooth' && 'env-material-' + parts.envelopeMaterial,
    parts.envelopeLiner !== 'none' && 'env-liner-' + parts.envelopeLiner,
  ].filter((name): name is string => !!name)
}

/** Decoration on the front pocket; it stays with the envelope as the flap opens. */
export function EnvelopeDecor({ decor }: { decor: ThemeParts['envelopeDecor'] }) {
  if (decor === 'ribbon') return <div className="env-decor env-ribbon"><span /></div>
  if (decor === 'sprig') {
    return (
      <svg className="env-decor env-sprig" viewBox="0 0 60 80">
        <path d="M30 78C30 56 28 32 22 6" fill="none" stroke="#7d6a45" strokeWidth="1.4" strokeLinecap="round" />
        <path d="M29 60C20 56 14 50 12 42M28 46C38 42 43 36 44 28M26 32C18 28 14 22 14 14" fill="none" stroke="#7d6a45" strokeWidth="1" strokeLinecap="round" />
        {[[22, 6], [20, 12], [24, 14], [12, 42], [10, 36], [44, 28], [46, 22], [14, 14], [12, 9]].map(([x, y], i) => <ellipse key={i} cx={x} cy={y} rx="2.6" ry="4" transform={`rotate(${i % 2 ? 25 : -20} ${x} ${y})`} fill={i % 3 ? '#9a86b8' : '#b5a3cf'} />)}
      </svg>
    )
  }
  if (decor === 'stamp') {
    return (
      <div className="env-decor env-stamp">
        <svg viewBox="-20 -24 40 48"><path d={EMBLEMS.heart} transform="scale(.9) translate(0 -6)" /><text y="16" textAnchor="middle">LOVE</text></svg>
      </div>
    )
  }
  return null
}

type ElRef<T> = RefObject<T | null>

/** The flap with its two faces and the top half of the seal riding on it. */
export function EnvelopeFlap({ flap, waxTop }: { flap?: ElRef<HTMLDivElement>; waxTop?: ElRef<HTMLDivElement> }) {
  const { href, url } = useIds()
  return (
    <div className="env-lay env-lay-flap" ref={flap} aria-hidden="true">
      <div className="env-flap">
        <div className="env-face env-flap-front" />
        <div className="env-face env-flap-inner" />
        <div className="env-wax env-wax-top" ref={waxTop}>
          <svg className="env-wax-front" viewBox="-40 -40 80 80"><use href={href('seal-art')} clipPath={url('cp-top')} /></svg>
          <svg className="env-wax-under" viewBox="-40 -40 80 80"><g transform="scale(1,-1)"><use href={href('blob')} clipPath={url('cp-top')} fill={url('under')} /></g></svg>
        </div>
      </div>
    </div>
  )
}

/** Front pocket, its shadow and the decoration. */
export function EnvelopeFront({ decor }: { decor: ThemeParts['envelopeDecor'] }) {
  return (
    <div className="env-lay env-lay-front" aria-hidden="true">
      <div className="env-shadow" />
      <div className="env-pocket">
        <div className="env-pocket-shade"><div className="env-pocket-side env-pocket-left" /></div>
        <div className="env-pocket-shade"><div className="env-pocket-side env-pocket-right" /></div>
        <div className="env-pocket-shade"><div className="env-pocket-bottom" /></div>
      </div>
      <div className="env-flap-shadow" />
      <EnvelopeDecor decor={decor} />
    </div>
  )
}

/** The bottom half of the seal and its crack line. */
export function EnvelopeWaxBottom({ flap, svg, waxBottom, crackLeft, crackRight }: {
  flap: ThemeParts['envelopeFlap']; svg?: ElRef<SVGSVGElement>; waxBottom?: ElRef<HTMLDivElement>
  crackLeft?: ElRef<SVGPathElement>; crackRight?: ElRef<SVGPathElement>
}) {
  const geometry = sealGeometry(flap)
  const { href, url } = useIds()
  return (
    <div className="env-wax env-wax-bottom" ref={waxBottom} aria-hidden="true">
      <svg ref={svg} viewBox="-40 -40 80 80"><use href={href('seal-art')} clipPath={url('cp-bottom')} /></svg>
      <svg className="env-crack" viewBox="-40 -40 80 80"><path ref={crackLeft} d={geometry.crackLeft} /><path ref={crackRight} d={geometry.crackRight} /></svg>
    </div>
  )
}
