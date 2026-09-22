import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { PageRenderer } from '../blocks/PageRenderer'
import {
  ACCOUNT_ROLE_LABELS,
  emptyAccountContent,
  type AccountContent,
  type AccountEntry,
  type AccountRole,
  type Invitation,
} from '../api/types'

const GROOM_ROLES: AccountRole[] = ['groom', 'groomFather', 'groomMother']
const BRIDE_ROLES: AccountRole[] = ['bride', 'brideFather', 'brideMother']

export function EditorPage() {
  const { id } = useParams<{ id: string }>()
  const [invitation, setInvitation] = useState<Invitation | null>(null)
  const [groomName, setGroomName] = useState('')
  const [brideName, setBrideName] = useState('')
  const [venueName, setVenueName] = useState('')
  const [address, setAddress] = useState('')
  const [galleryImages, setGalleryImages] = useState<string[]>([])
  const [accounts, setAccounts] = useState<AccountContent>(emptyAccountContent())
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

      const account = blocks.find((b) => b.type === 'account')?.content as Partial<AccountContent> | undefined
      setAccounts({ ...emptyAccountContent(), ...account })
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
          if (b.type === 'account') return { ...b, content: { ...b.content, ...accounts } }
          return b
        }),
      }
    : null

  function updateAccount(role: AccountRole, patch: Partial<AccountEntry>) {
    setAccounts((prev) => ({ ...prev, [role]: { ...prev[role], ...patch } }))
  }

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
        <div className="page-header">
          <h1>기본 정보</h1>
          <Link to={`/e/${invitation.id}/guests`} className="logout-button">
            응답 관리
          </Link>
        </div>

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
          <ImageListEditor invitationId={invitation.id} images={galleryImages} onChange={setGalleryImages} />
        </section>

        <section className="editor-section">
          <h2>마음 전하실 곳</h2>
          <p className="editor-section-hint">
            계좌번호가 없는 분은 계좌 대신 보여줄 메시지를 입력해주세요. 둘 다 비워두면 표시되지 않습니다.
          </p>
          <p className="editor-section-sub">신랑측</p>
          {GROOM_ROLES.map((role) => (
            <AccountRoleEditor
              key={role}
              label={ACCOUNT_ROLE_LABELS[role]}
              entry={accounts[role]}
              onChange={(patch) => updateAccount(role, patch)}
            />
          ))}
          <p className="editor-section-sub">신부측</p>
          {BRIDE_ROLES.map((role) => (
            <AccountRoleEditor
              key={role}
              label={ACCOUNT_ROLE_LABELS[role]}
              entry={accounts[role]}
              onChange={(patch) => updateAccount(role, patch)}
            />
          ))}
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

// R2 버킷을 아직 연동하지 않아 업로드 UI를 잠시 꺼둔다(서비스 구현 완료 후 되살릴 예정 — CLAUDE.md 미디어
// 섹션 참고). invitationId는 그때 다시 destructure해서 쓴다. 아래 주석 블록이 살아있던 원래 구현이다.
function ImageListEditor({
  images,
  onChange,
}: {
  invitationId: string
  images: string[]
  onChange: (images: string[]) => void
}) {
  // const [uploading, setUploading] = useState(false)
  // const [error, setError] = useState<string | null>(null)
  //
  // async function handleFiles(files: FileList | null) {
  //   if (!files || files.length === 0) return
  //   setUploading(true)
  //   setError(null)
  //   try {
  //     const uploaded: string[] = []
  //     for (const file of Array.from(files)) {
  //       const asset = await api.uploadImage(invitationId, file, 'GALLERY', images.length + uploaded.length)
  //       uploaded.push(asset.url)
  //     }
  //     onChange([...images, ...uploaded])
  //   } catch (err) {
  //     setError(err instanceof Error ? err.message : '업로드에 실패했습니다')
  //   } finally {
  //     setUploading(false)
  //   }
  // }

  return (
    <div className="list-editor">
      {images.map((url, i) => (
        <div key={i} className="list-editor-row">
          <img src={url} alt="" className="list-editor-thumb" />
          <button type="button" onClick={() => onChange(images.filter((_, idx) => idx !== i))}>
            삭제
          </button>
        </div>
      ))}
      {/*
      <label className="list-editor-add">
        {uploading ? '업로드 중...' : '+ 사진 추가'}
        <input
          type="file"
          accept="image/*"
          multiple
          hidden
          disabled={uploading}
          onChange={(e) => {
            void handleFiles(e.target.files)
            e.target.value = ''
          }}
        />
      </label>
      {error && <p className="form-error">{error}</p>}
      */}
      <p className="editor-section-hint">이미지 업로드는 R2 연동 후 사용할 수 있습니다 (현재 비활성화).</p>
    </div>
  )
}

function AccountRoleEditor({
  label,
  entry,
  onChange,
}: {
  label: string
  entry: AccountEntry
  onChange: (patch: Partial<AccountEntry>) => void
}) {
  return (
    <div className="account-role-editor">
      <p className="account-role-label">{label}</p>
      <div className="account-editor-row">
        <input value={entry.holder} onChange={(e) => onChange({ holder: e.target.value })} placeholder="예금주" />
        <input value={entry.bank} onChange={(e) => onChange({ bank: e.target.value })} placeholder="은행" />
        <input
          value={entry.accountNumber}
          onChange={(e) => onChange({ accountNumber: e.target.value })}
          placeholder="계좌번호"
        />
      </div>
      <input
        value={entry.message}
        onChange={(e) => onChange({ message: e.target.value })}
        placeholder="계좌 대신 표시할 메시지 (예: 마음만 감사히 받겠습니다)"
      />
    </div>
  )
}
