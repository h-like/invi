import { useState } from 'react'
import type { AccountInfo, Block } from '../api/types'

export function AccountBlock({ block }: { block: Block }) {
  const content = block.content as { groomAccounts?: AccountInfo[]; brideAccounts?: AccountInfo[] }
  const groom = content.groomAccounts ?? []
  const bride = content.brideAccounts ?? []

  if (groom.length === 0 && bride.length === 0) {
    return (
      <section className="block account-block">
        <p className="block-placeholder">계좌번호를 아직 추가하지 않았습니다</p>
      </section>
    )
  }

  return (
    <section className="block account-block">
      <h3>마음 전하실 곳</h3>
      <AccountGroup title="신랑측" accounts={groom} />
      <AccountGroup title="신부측" accounts={bride} />
    </section>
  )
}

function AccountGroup({ title, accounts }: { title: string; accounts: AccountInfo[] }) {
  const [copiedIndex, setCopiedIndex] = useState<number | null>(null)
  if (accounts.length === 0) return null

  return (
    <div className="account-group">
      <p className="account-group-title">{title}</p>
      {accounts.map((acc, i) => (
        <div key={`${acc.bank}-${acc.accountNumber}`} className="account-row">
          <span>
            {acc.bank} {acc.accountNumber} ({acc.holder})
          </span>
          <button
            type="button"
            onClick={() => {
              navigator.clipboard.writeText(acc.accountNumber)
              setCopiedIndex(i)
              setTimeout(() => setCopiedIndex(null), 1500)
            }}
          >
            {copiedIndex === i ? '복사됨' : '복사'}
          </button>
        </div>
      ))}
    </div>
  )
}
