import { useRef, useState, type CSSProperties } from 'react'
import { EnvelopeDefs, EnvelopeFlap, EnvelopeIds, EnvelopeFront, EnvelopeWaxBottom, envelopeClassNames, useSealText, waxVars, type EnvelopeParts } from './envelopeArt'
import './envelope.css'

// A still envelope for the editor: the real intro only runs on the guest page, so this
// shows the chosen flap, material, liner, seal and decoration, closed or opened.
export function EnvelopePreview({ paper, wine, parts, monogram, script }: { paper: string; wine: string; parts: EnvelopeParts; monogram?: string; script: string }) {
  const [open, setOpen] = useState(false)
  const svg = useRef<SVGSVGElement>(null)
  const seal = useSealText(monogram, svg, parts.sealEmblem === 'monogram', script)
  const style = { '--env-paper': paper, '--env-wine': wine, ...waxVars(parts) } as CSSProperties
  return (
    <EnvelopeIds>
      <div className="envelope-preview-wrap">
        <div className={['envelope', 'envelope-preview', ...envelopeClassNames(parts), ...(open ? ['is-open', 'is-cracked'] : [])].join(' ')} style={style} aria-hidden="true">
          <EnvelopeDefs parts={parts} seal={seal} />
          <div className="env-lay env-lay-back"><div className="env-back" /></div>
          <div className="env-preview-letter" />
          <EnvelopeFlap />
          <EnvelopeFront decor={parts.envelopeDecor} />
          <div className="env-lay env-lay-wax"><EnvelopeWaxBottom flap={parts.envelopeFlap} svg={svg} /></div>
        </div>
        <button type="button" className="secondary-button" onClick={() => setOpen(o => !o)}>{open ? '닫힌 모습 보기' : '열린 모습 보기'}</button>
      </div>
    </EnvelopeIds>
  )
}
