import type { HeroLayout, LookOverrides, PageDesign } from '../api/types'
import { EnvelopePreview } from '../blocks/EnvelopePreview'
import { ENVELOPE_SLOTS } from '../blocks/envelopeArt'
import { envelopeColor, HERO_LAYOUTS, NAME_TRACKING_OPTIONS, PART_LABELS, PART_OPTIONS, pageLook, sanitizeLook, themeLook, TOKEN_KINDS, TOKEN_LABELS, tokenColorHint, type PartSlot, type TokenName } from './model'

const SLOT_GROUPS: { title: string; slots: PartSlot[] }[] = [
  { title: '종이와 첫 화면', slots: ['sheet', 'texture', 'heroFrame', 'heroMark', 'photoFrame'] },
  { title: '글자와 장식', slots: ['typeset', 'headingOrnament', 'surfaces', 'closing'] },
  { title: '연출', slots: ['envelope', 'motion'] },
]
// The envelope colour sits with the other envelope choices.
const TOKEN_ORDER: TokenName[] = ['ground', 'paper2', 'line', 'numeralColor', 'nameTracking']

type SlotMeta = { label: string; options: Record<string, string>; layouts?: Record<string, HeroLayout> }

// Fine-tuning on top of the chosen template: only what differs from it is stored, so
// "template default" is always one choice away and template updates still reach the page.
export function LookEditor({ design, monogram, onChange }: { design: PageDesign; monogram?: string; onChange: (look: LookOverrides | undefined) => void }) {
  const base = themeLook(design.theme)
  const look = sanitizeLook(design.look) ?? {}
  const changed = Object.keys(look.parts ?? {}).length + Object.keys(look.tokens ?? {}).length
  const resolved = pageLook(design)

  function setPart(slot: PartSlot, value: string) {
    const parts = { ...look.parts }
    if (value === base.parts[slot]) delete parts[slot]
    else parts[slot] = value
    onChange(sanitizeLook({ ...look, parts }))
  }
  function partSelect(slot: PartSlot) {
    const meta = PART_LABELS[slot] as SlotMeta
    const overridden = look.parts?.[slot] !== undefined
    return <label key={slot} className={overridden ? 'look-changed' : undefined}>{meta.label}
      <select value={look.parts?.[slot] ?? base.parts[slot]} onChange={e => setPart(slot, e.target.value)}>
        {(PART_OPTIONS[slot] as readonly string[]).map(value => {
          const only = meta.layouts?.[value]
          const hidden = only && only !== design.heroLayout ? ` (${HERO_LAYOUTS.find(l => l.value === only)?.label}에서 보임)` : ''
          return <option key={value} value={value}>{meta.options[value]}{value === base.parts[slot] ? ' · 템플릿 기본' : ''}{hidden}</option>
        })}
      </select>
    </label>
  }
  function colorInput(name: TokenName) {
    const value = look.tokens?.[name]
    return <div key={name} className="look-token">
      <label className={value ? 'look-changed' : undefined}>{TOKEN_LABELS[name]}<input type="color" value={value ?? tokenColorHint(name, design)} onChange={e => setToken(name, e.target.value)} /></label>
      {value && <button type="button" className="secondary-button look-reset" aria-label={TOKEN_LABELS[name] + ' 템플릿 기본으로'} onClick={() => setToken(name)}>기본</button>}
    </div>
  }
  function setToken(name: TokenName, value?: string) {
    const tokens = { ...look.tokens }
    if (value) tokens[name] = value
    else delete tokens[name]
    onChange(sanitizeLook({ ...look, tokens }))
  }

  return (
    <section className="editor-section look-editor">
      <h2>세부 꾸미기 <small>{changed ? `${changed}개 변경` : '템플릿 그대로'}</small></h2>
      <p className="editor-section-hint">템플릿 위에 원하는 부분만 바꿔요. 다른 템플릿을 고르면 여기서 바꾼 내용은 처음으로 돌아갑니다.</p>
      {SLOT_GROUPS.map(group => <div key={group.title}>
        <p className="editor-section-sub">{group.title}</p>
        <div className="form-columns">{group.slots.map(partSelect)}</div>
      </div>)}
      {resolved.parts.envelope !== 'none' && <div>
        <p className="editor-section-sub">봉투</p>
        <EnvelopePreview paper={design.backgroundColor} wine={envelopeColor(design, resolved)} parts={resolved.parts} monogram={monogram} />
        <div className="form-columns">{ENVELOPE_SLOTS.map(partSelect)}{colorInput('envelopeColor')}</div>
      </div>}
      <p className="editor-section-sub">색과 간격</p>
      <div className="form-columns">{TOKEN_ORDER.map(name => {
        const value = look.tokens?.[name]
        if (TOKEN_KINDS[name] === 'tracking') {
          return <label key={name} className={value ? 'look-changed' : undefined}>{TOKEN_LABELS[name]}
            <select value={value ?? ''} onChange={e => setToken(name, e.target.value || undefined)}>
              <option value="">템플릿 기본</option>
              {NAME_TRACKING_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </label>
        }
        return colorInput(name)
      })}</div>
      <button type="button" className="secondary-button look-reset-all" disabled={!changed} onClick={() => onChange(undefined)}>템플릿 기본값으로 되돌리기</button>
    </section>
  )
}
