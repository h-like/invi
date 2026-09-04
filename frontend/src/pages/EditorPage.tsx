import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api/client'
import { PageRenderer } from '../blocks/PageRenderer'
import type { Invitation } from '../api/types'

export function EditorPage() {
  const { id } = useParams<{ id: string }>()
  const [invitation, setInvitation] = useState<Invitation | null>(null)
  const [groomName, setGroomName] = useState('')
  const [brideName, setBrideName] = useState('')
  const [saving, setSaving] = useState(false)
  const [publishedUrl, setPublishedUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    api.getInvitation(id).then((inv) => {
      setInvitation(inv)
      const hero = inv.pageData?.blocks.find((b) => b.type === 'hero')
      const content = (hero?.content ?? {}) as { groomName?: string; brideName?: string }
      setGroomName(content.groomName ?? '')
      setBrideName(content.brideName ?? '')
    })
  }, [id])

  if (!invitation) {
    return <main className="page">불러오는 중...</main>
  }

  const previewPageData = invitation.pageData
    ? {
        blocks: invitation.pageData.blocks.map((b) =>
          b.type === 'hero' ? { ...b, content: { ...b.content, groomName, brideName } } : b,
        ),
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
        <label>
          신랑 이름
          <input value={groomName} onChange={(e) => setGroomName(e.target.value)} />
        </label>
        <label>
          신부 이름
          <input value={brideName} onChange={(e) => setBrideName(e.target.value)} />
        </label>
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
