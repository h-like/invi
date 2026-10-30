import { useState } from 'react'
import { ACCOUNT_ROLES, ACCOUNT_ROLE_LABELS, type AccountEntry, type AccountRole, type Block } from '../api/types'

const GROOM_ROLES: AccountRole[] = ['groom', 'groomFather', 'groomMother']
const BRIDE_ROLES: AccountRole[] = ['bride', 'brideFather', 'brideMother']

export function AccountBlock({ block }: { block: Block }) {
  const content = block.content as Partial<Record<AccountRole, AccountEntry>>
  const hasAny = ACCOUNT_ROLES.some((role) => isFilled(content[role]))

  if (!hasAny) {
    return (
      <section className="block account-block">
        <p className="block-placeholder">계좌번호를 아직 추가하지 않았습니다</p>
      </section>
    )
  }

  return (
    <section className="block account-block">
      <p className="invitation-eyebrow">ACCOUNT</p><h3>마음 전하실 곳</h3>
      <p className="block-sub account-note">참석이 어려우신 분들을 위해 계좌번호를 함께 적어 두었습니다.</p>
      <AccountGroup title="신랑측" roles={GROOM_ROLES} content={content} />
      <AccountGroup title="신부측" roles={BRIDE_ROLES} content={content} />
    </section>
  )
}

function isFilled(entry?: AccountEntry): entry is AccountEntry {
  if (!entry) return false
  return Boolean(entry.holder || entry.bank || entry.accountNumber || entry.message)
}

function AccountGroup({
  title,
  roles,
  content,
}: {
  title: string
  roles: AccountRole[]
  content: Partial<Record<AccountRole, AccountEntry>>
}) {
  const [copied, setCopied] = useState<{ role: AccountRole; ok: boolean } | null>(null)
  const filledRoles = roles.filter((role) => isFilled(content[role]))
  if (filledRoles.length === 0) return null

  return (
    // Collapsed by default so a long list of numbers doesn't dominate the page.
    <details className="account-group">
      <summary className="account-group-title">{title} 계좌번호</summary>
      {filledRoles.map((role) => {
        const entry = content[role] as AccountEntry
        const hasAccount = Boolean(entry.bank.trim() && entry.accountNumber.trim())

        return (
          <div key={role} className="account-row">
            {hasAccount ? (
              <>
                <span>
                  {ACCOUNT_ROLE_LABELS[role]} · {entry.bank} {entry.accountNumber}
                  {entry.holder ? ` (${entry.holder})` : ''}
                </span>
                <button
                  type="button"
                  onClick={async () => {
                    let ok = true
                    try { await navigator.clipboard.writeText(entry.accountNumber) } catch { ok = false }
                    setCopied({ role, ok })
                    setTimeout(() => setCopied(null), 1500)
                  }}
                >
                  {copied?.role === role ? (copied.ok ? '복사됨' : '복사 안 됨') : '복사'}
                </button>
              </>
            ) : (
              <span>
                {ACCOUNT_ROLE_LABELS[role]} · {entry.message}
              </span>
            )}
          </div>
        )
      })}
    </details>
  )
}
