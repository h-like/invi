import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { Template } from '../api/types'

export function HomePage() {
  const navigate = useNavigate()
  const { member, loading, loginWithKakao, loginWithGoogle, logout } = useAuth()
  const [templates, setTemplates] = useState<Template[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [slug, setSlug] = useState('')
  const [weddingDate, setWeddingDate] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    api.listTemplates().then((list) => {
      setTemplates(list)
      if (list.length > 0) setSelectedId(list[0].id)
    })
  }, [])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!selectedId) return
    setSubmitting(true)
    setError(null)
    try {
      const invitation = await api.createInvitation({ templateId: selectedId, slug, weddingDate })
      navigate(`/e/${invitation.id}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : '청첩장 생성에 실패했습니다')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return <main className="page">불러오는 중...</main>
  }

  if (!member) {
    return (
      <main className="page login-page">
        <h1>모바일 청첩장 만들기</h1>
        <p className="login-lede">시작하려면 로그인해주세요.</p>
        <div className="login-buttons">
          <button type="button" className="login-kakao" onClick={loginWithKakao}>
            카카오로 시작하기
          </button>
          <button type="button" className="login-google" onClick={loginWithGoogle}>
            Google로 시작하기
          </button>
        </div>
      </main>
    )
  }

  return (
    <main className="page">
      <div className="page-header">
        <h1>모바일 청첩장 만들기</h1>
        <button type="button" className="logout-button" onClick={logout}>
          {member.name}님 · 로그아웃
        </button>
      </div>
      <form onSubmit={handleSubmit} className="create-form">
        <h2>템플릿 선택</h2>
        <div className="template-grid">
          {templates.map((t) => (
            <label key={t.id} className={`template-card ${selectedId === t.id ? 'selected' : ''}`}>
              <input
                type="radio"
                name="template"
                checked={selectedId === t.id}
                onChange={() => setSelectedId(t.id)}
              />
              {t.name}
            </label>
          ))}
        </div>

        <label>
          서브도메인 (영문 소문자, 숫자, 하이픈)
          <input
            value={slug}
            onChange={(e) => setSlug(e.target.value)}
            placeholder="minsu-jiyoung"
            required
            pattern="[a-z0-9\-]{3,50}"
          />
        </label>

        <label>
          결혼식 날짜
          <input type="date" value={weddingDate} onChange={(e) => setWeddingDate(e.target.value)} required />
        </label>

        {error && <p className="form-error">{error}</p>}
        <button type="submit" disabled={submitting || !selectedId}>
          {submitting ? '만드는 중...' : '청첩장 만들기'}
        </button>
      </form>
    </main>
  )
}
