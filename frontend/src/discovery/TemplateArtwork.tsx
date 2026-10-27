import { useState } from 'react'
import type { Template } from '../api/types'

export interface TemplateArtworkProps {
  template: Template
  compact?: boolean
}

function safeThumbnail(value: string | null): string | null {
  // Control characters and backslashes are intentionally rejected before URL parsing.
  // eslint-disable-next-line no-control-regex
  if (!value?.trim() || /[\u0000-\u001f\u007f\\]/.test(value)) return null
  try {
    const url = new URL(value.trim(), 'https://invi.invalid/')
    return url.protocol === 'https:' || url.protocol === 'http:' ? value.trim() : null
  } catch {
    return null
  }
}

function BotanicalSprig({ floral = false }: { floral?: boolean }) {
  return (
    <svg className="discovery-cover-botanical" viewBox="0 0 200 250" fill="none" aria-hidden="true">
      <g stroke="currentColor" strokeWidth="1.2" strokeLinecap="round">
        <path d="M93 237C78 173 122 106 102 20M94 200C57 164 47 118 40 79M96 162C132 140 153 104 163 55" />
        <path d="M87 218C68 200 48 192 26 188M100 133C80 107 70 76 72 47" />
      </g>
      <g fill="currentColor" opacity=".65">
        <ellipse cx="101" cy="38" rx="8" ry="19" transform="rotate(-14 101 38)" />
        <ellipse cx="114" cy="70" rx="9" ry="22" transform="rotate(40 114 70)" />
        <ellipse cx="94" cy="95" rx="9" ry="22" transform="rotate(-35 94 95)" />
        <ellipse cx="106" cy="127" rx="9" ry="20" transform="rotate(37 106 127)" />
        <ellipse cx="79" cy="158" rx="9" ry="23" transform="rotate(-40 79 158)" />
        <ellipse cx="56" cy="136" rx="8" ry="21" transform="rotate(-30 56 136)" />
        <ellipse cx="39" cy="88" rx="7" ry="19" transform="rotate(-20 39 88)" />
        <ellipse cx="142" cy="108" rx="8" ry="22" transform="rotate(32 142 108)" />
        <ellipse cx="164" cy="56" rx="7" ry="18" transform="rotate(20 164 56)" />
        <ellipse cx="70" cy="69" rx="6" ry="17" transform="rotate(-8 70 69)" />
        <ellipse cx="46" cy="193" rx="7" ry="21" transform="rotate(-66 46 193)" />
      </g>
      {floral && <g fill="#d7b2a6" stroke="#b9897e" strokeWidth=".5"><circle cx="41" cy="69" r="11" /><circle cx="28" cy="64" r="10" /><circle cx="35" cy="53" r="11" /><circle cx="47" cy="55" r="10" /><circle cx="38" cy="60" r="5" fill="#c29a65" /><circle cx="159" cy="47" r="9" /><circle cx="173" cy="45" r="10" /><circle cx="165" cy="34" r="9" /><circle cx="165" cy="43" r="4" fill="#c29a65" /></g>}
    </svg>
  )
}

export function TemplateArtwork({ template, compact = false }: TemplateArtworkProps) {
  const [failedSource, setFailedSource] = useState<string | null>(null)
  const thumbnail = safeThumbnail(template.thumbnailUrl)
  const category = template.category.toLowerCase()
  const variant = ['classic', 'minimal', 'romantic', 'modern'].includes(category) ? category : 'classic'

  return (
    <div className={`discovery-artwork discovery-artwork-${variant}${compact ? ' discovery-artwork-compact' : ''}`}>
      {thumbnail && thumbnail !== failedSource ? (
        <img className="discovery-template-thumbnail" src={thumbnail} alt={`${template.name} 커버`} loading="lazy" decoding="async" onError={() => setFailedSource(thumbnail)} />
      ) : (
        <div className="discovery-cover-scene" aria-hidden="true">
          <div className="discovery-cover-shadow-paper" />
          <div className="discovery-cover-paper">
            <span className="discovery-cover-overline">{variant === 'modern' ? 'OUR NEW CHAPTER' : 'THE WEDDING INVITATION'}</span>
            <BotanicalSprig floral={variant === 'romantic'} />
            <span className="discovery-cover-title">{variant === 'minimal' ? <>you<br /><i>& me.</i></> : variant === 'modern' ? <>With<br /><i>love.</i></> : <>We are<br /><i>getting married</i></>}</span>
            <span className="discovery-cover-rule" />
            <span className="discovery-cover-korean">우리의 새로운 시작에<br />당신을 초대합니다</span>
            <span className="discovery-cover-bottom">A DAY TO REMEMBER</span>
          </div>
        </div>
      )}
    </div>
  )
}
