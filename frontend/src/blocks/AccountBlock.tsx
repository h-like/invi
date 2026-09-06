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
      <h3>마음 전하실 곳</h3>
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
  const [copiedRole, setCopiedRole] = useState<AccountRole | null>(null)
  const filledRoles = roles.filter((role) => isFilled(content[role]))
  if (filledRoles.length === 0) return null

  return (
    <div className="account-group">
      <p className="account-group-title">{title}</p>
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
                  onClick={() => {
                    navigator.clipboard.writeText(entry.accountNumber)
                    setCopiedRole(role)
                    setTimeout(() => setCopiedRole(null), 1500)
                  }}
                >
                  {copiedRole === role ? '복사됨' : '복사'}
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
    </div>
  )
}
