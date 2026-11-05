import type { HeroLayout, LookOverrides, PageDesign } from '../api/types'
import { EnvelopePreview } from '../blocks/EnvelopePreview'
import { scriptFamily } from '../blocks/fonts'
import { envelopeColor, HERO_LAYOUTS, partIsActive, NAME_TRACKING_OPTIONS, PART_GROUPS, PART_LABELS, PART_OPTIONS, pageLook, slotsIn, themeLook, TOKEN_KINDS, TOKEN_LABELS, tokenColorHint, withPart, withToken, type PartSlot, type TokenName } from './model'

// The envelope group has its own section with a preview, below.
const SLOT_GROUPS = PART_GROUPS.filter(group => group.id !== 'envelope').map(group => ({ title: group.title, slots: slotsIn(group.id) }))
const ENVELOPE_GROUP = PART_GROUPS.find(group => group.id === 'envelope')!
// The envelope colour sits with the other envelope choices.
const TOKEN_ORDER: TokenName[] = ['ground', 'paper2', 'line', 'numeralColor', 'nameTracking']

type SlotMeta = { label: string; options: Record<string, string>; layouts?: Record<string, HeroLayout> }

// Fine-tuning on top of the chosen template; withPart/withToken store only what differs.
export function LookEditor({ design, monogram, onChange }: { design: PageDesign; monogram?: string; onChange: (look: LookOverrides | undefined) => void }) {
  const base = themeLook(design.theme)
  const look = design.look ?? {}
  const resolved = pageLook(design)
  // Count only what shows: envelope details saved before the envelope was turned off stay
  // hidden (withPart drops them on the next part change).
  const changed = (Object.keys(look.parts ?? {}) as PartSlot[]).filter(slot => partIsActive(resolved.parts, slot)).length
    + Object.keys(look.tokens ?? {}).length

  function partSelect(slot: PartSlot) {
    const meta = PART_LABELS[slot] as SlotMeta
    const overridden = look.parts?.[slot] !== undefined
    return <label key={slot} className={overridden ? 'look-changed' : undefined}>{meta.label}
      <select value={look.parts?.[slot] ?? base.parts[slot]} onChange={e => onChange(withPart(design, slot, e.target.value))}>
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
    onChange(withToken(design, name, value))
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
        <p className="editor-section-sub">{ENVELOPE_GROUP.title}</p>
        <EnvelopePreview paper={design.backgroundColor} wine={envelopeColor(design, resolved)} parts={resolved.parts} monogram={monogram} script={scriptFamily(resolved.fonts)} />
        <div className="form-columns">{slotsIn(ENVELOPE_GROUP.id).map(partSelect)}{colorInput('envelopeColor')}</div>
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
