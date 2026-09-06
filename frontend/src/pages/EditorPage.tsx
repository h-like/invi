import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api/client'
import { PageRenderer } from '../blocks/PageRenderer'
import type { AccountInfo, Invitation } from '../api/types'

export function EditorPage() {
  const { id } = useParams<{ id: string }>()
  const [invitation, setInvitation] = useState<Invitation | null>(null)
  const [groomName, setGroomName] = useState('')
  const [brideName, setBrideName] = useState('')
  const [venueName, setVenueName] = useState('')
  const [address, setAddress] = useState('')
  const [galleryImages, setGalleryImages] = useState<string[]>([])
  const [groomAccounts, setGroomAccounts] = useState<AccountInfo[]>([])
  const [brideAccounts, setBrideAccounts] = useState<AccountInfo[]>([])
  const [saving, setSaving] = useState(false)
  const [publishedUrl, setPublishedUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    api.getInvitation(id).then((inv) => {
      setInvitation(inv)
      const blocks = inv.pageData?.blocks ?? []

      const hero = blocks.find((b) => b.type === 'hero')?.content as
        | { groomName?: string; brideName?: string }
        | undefined
      setGroomName(hero?.groomName ?? '')
      setBrideName(hero?.brideName ?? '')

      const map = blocks.find((b) => b.type === 'map')?.content as
        | { venueName?: string; address?: string }
        | undefined
      setVenueName(map?.venueName ?? '')
      setAddress(map?.address ?? '')

      const gallery = blocks.find((b) => b.type === 'gallery')?.content as { images?: string[] } | undefined
      setGalleryImages(gallery?.images ?? [])

      const account = blocks.find((b) => b.type === 'account')?.content as
        | { groomAccounts?: AccountInfo[]; brideAccounts?: AccountInfo[] }
        | undefined
      setGroomAccounts(account?.groomAccounts ?? [])
      setBrideAccounts(account?.brideAccounts ?? [])
    }).catch((err) => setError(err instanceof Error ? err.message : '청첩장을 불러오지 못했습니다'))
  }, [id])

  if (error && !invitation) {
    return (
      <main className="page">
        <p className="form-error">{error}</p>
      </main>
    )
  }

  if (!invitation) {
    return <main className="page">불러오는 중...</main>
  }

  const previewPageData = invitation.pageData
    ? {
        blocks: invitation.pageData.blocks.map((b) => {
          if (b.type === 'hero') return { ...b, content: { ...b.content, groomName, brideName } }
          if (b.type === 'map') return { ...b, content: { ...b.content, venueName, address } }
          if (b.type === 'gallery') return { ...b, content: { ...b.content, images: galleryImages } }
          if (b.type === 'account') return { ...b, content: { ...b.content, groomAccounts, brideAccounts } }
          return b
        }),
      }
    : null

  async function handleSave() {
    if (!invitation || !previewPageData) return null
    setSaving(true)
    setError(null)
    try {
      const updated = await api.updatePageData(invitation.id, previewPageData)
      setInvitation(updated)
      return updated
    } catch (err) {
      setError(err instanceof Error ? err.message : '저장에 실패했습니다')
      return null
    } finally {
      setSaving(false)
    }
  }

  async function handlePublish() {
    setError(null)
    const saved = await handleSave()
    if (!saved) return
    try {
      const published = await api.publishInvitation(saved.id)
      setInvitation(published)
      setPublishedUrl(`/i/${published.slug}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : '공개에 실패했습니다')
    }
  }

  return (
    <main className="page editor-page">
      <div className="editor-panel">
        <h1>기본 정보</h1>

        <section className="editor-section">
          <h2>신랑 · 신부</h2>
          <label>
            신랑 이름
            <input value={groomName} onChange={(e) => setGroomName(e.target.value)} />
          </label>
          <label>
            신부 이름
            <input value={brideName} onChange={(e) => setBrideName(e.target.value)} />
          </label>
        </section>

        <section className="editor-section">
          <h2>오시는 길</h2>
          <label>
            식장 이름
            <input value={venueName} onChange={(e) => setVenueName(e.target.value)} placeholder="○○ 웨딩홀" />
          </label>
          <label>
            주소
            <input value={address} onChange={(e) => setAddress(e.target.value)} placeholder="서울시 ..." />
          </label>
        </section>

        <section className="editor-section">
          <h2>갤러리</h2>
          <ImageListEditor images={galleryImages} onChange={setGalleryImages} />
        </section>

        <section className="editor-section">
          <h2>마음 전하실 곳</h2>
          <p className="editor-section-sub">신랑측</p>
          <AccountListEditor accounts={groomAccounts} onChange={setGroomAccounts} />
          <p className="editor-section-sub">신부측</p>
          <AccountListEditor accounts={brideAccounts} onChange={setBrideAccounts} />
        </section>

        {error && <p className="form-error">{error}</p>}
        <div className="editor-actions">
          <button type="button" onClick={handleSave} disabled={saving}>
            {saving ? '저장 중...' : '저장'}
          </button>
          <button type="button" onClick={handlePublish} className="publish-button">
            공개하기
          </button>
        </div>
        {publishedUrl && (
          <p className="publish-success">
            공개되었습니다:{' '}
            <a href={publishedUrl} target="_blank" rel="noreferrer">
              {window.location.origin}
              {publishedUrl}
            </a>
          </p>
        )}
      </div>

      <div className="editor-preview">
        <PageRenderer
          pageData={previewPageData}
          weddingDate={invitation.weddingDate}
          invitationId={invitation.id}
          interactive={false}
        />
      </div>
    </main>
  )
}

function ImageListEditor({ images, onChange }: { images: string[]; onChange: (images: string[]) => void }) {
  return (
    <div className="list-editor">
      {images.map((url, i) => (
        <div key={i} className="list-editor-row">
          <input
            value={url}
            onChange={(e) => onChange(images.map((v, idx) => (idx === i ? e.target.value : v)))}
            placeholder="이미지 URL"
          />
          <button type="button" onClick={() => onChange(images.filter((_, idx) => idx !== i))}>
            삭제
          </button>
        </div>
      ))}
      <button type="button" className="list-editor-add" onClick={() => onChange([...images, ''])}>
        + 사진 추가
      </button>
    </div>
  )
}

function AccountListEditor({
  accounts,
  onChange,
}: {
  accounts: AccountInfo[]
  onChange: (accounts: AccountInfo[]) => void
}) {
  function update(i: number, patch: Partial<AccountInfo>) {
    onChange(accounts.map((acc, idx) => (idx === i ? { ...acc, ...patch } : acc)))
  }

  return (
    <div className="list-editor">
      {accounts.map((acc, i) => (
        <div key={i} className="account-editor-row">
          <input value={acc.bank} onChange={(e) => update(i, { bank: e.target.value })} placeholder="은행" />
          <input
            value={acc.accountNumber}
            onChange={(e) => update(i, { accountNumber: e.target.value })}
            placeholder="계좌번호"
          />
          <input value={acc.holder} onChange={(e) => update(i, { holder: e.target.value })} placeholder="예금주" />
          <button type="button" onClick={() => onChange(accounts.filter((_, idx) => idx !== i))}>
            삭제
          </button>
        </div>
      ))}
      <button
        type="button"
        className="list-editor-add"
        onClick={() => onChange([...accounts, { bank: '', accountNumber: '', holder: '' }])}
      >
        + 계좌 추가
      </button>
    </div>
  )
}
