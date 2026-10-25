import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { Template } from '../api/types'

export function HomePage() {
  const navigate = useNavigate()
  const { member, loading, loginWithKakao, loginWithGoogle, completeLogin, logout } = useAuth()
  const [registering, setRegistering] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [passwordConfirmation, setPasswordConfirmation] = useState('')
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

  async function handleAuth(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (registering && password !== passwordConfirmation) {
      setError('비밀번호가 일치하지 않습니다.')
      return
    }
    setSubmitting(true)
    try {
      const result = registering
        ? await api.register({ email: email.trim(), password, name: name.trim() })
        : await api.login({ email: email.trim(), password })
      await completeLogin(result.token)
      setPassword('')
      setPasswordConfirmation('')
    } catch (err) {
      setError(err instanceof Error ? err.message : '로그인에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  if (!member) {
    return (
      <main className="page login-page">
        <h1>모바일 청첩장 만들기</h1>
        <p className="login-lede">{registering ? '회원가입하고 나만의 청첩장을 만들어보세요.' : '시작하려면 로그인해주세요.'}</p>
        <form className="local-auth-form" onSubmit={handleAuth}>
          {registering && <label>이름<input autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={50} /></label>}
          <label>이메일<input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required maxLength={254} /></label>
          <label>비밀번호<input type="password" autoComplete={registering ? 'new-password' : 'current-password'} value={password} onChange={(e) => setPassword(e.target.value)} required minLength={registering ? 8 : undefined} maxLength={64} placeholder={registering ? '8자 이상 입력해주세요' : undefined} /></label>
          {registering && <label>비밀번호 확인<input type="password" autoComplete="new-password" value={passwordConfirmation} onChange={(e) => setPasswordConfirmation(e.target.value)} required maxLength={64} /></label>}
          {error && <p className="form-error" role="alert">{error}</p>}
          <button type="submit" disabled={submitting}>{submitting ? '처리 중...' : registering ? '회원가입' : '로그인'}</button>
          <button type="button" className="auth-switch" disabled={submitting} onClick={() => { setRegistering(!registering); setError(null); setPassword(''); setPasswordConfirmation('') }}>{registering ? '이미 계정이 있나요? 로그인' : '계정이 없나요? 회원가입'}</button>
        </form>
        <p className="login-lede">또는 소셜 계정으로 시작하기</p>
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
