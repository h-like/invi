import { useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { rememberReturnTo, safeReturnTo, takeReturnTo } from '../auth/returnTo'
import { SiteHeader } from '../discovery/SiteHeader'
import '../discovery/flow.css'

export function LoginPage() {
  const [params] = useSearchParams()
  const returnTo = safeReturnTo(params.get('returnTo'))
  const { member, loading, completeLogin, loginWithKakao, loginWithGoogle } = useAuth()
  const [registering, setRegistering] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const pending = useRef(false)

  async function handleAuth(event: FormEvent) {
    event.preventDefault()
    if (pending.current) return
    setError(null)
    if (registering && password !== confirmation) { setError('비밀번호가 일치하지 않습니다.'); return }
    pending.current = true
    setSubmitting(true)
    try {
      const result = registering
        ? await api.register({ email: email.trim(), password, name: name.trim() })
        : await api.login({ email: email.trim(), password })
      // The route's returnTo remains the source of truth for local login.
      takeReturnTo()
      await completeLogin(result.token)
      setPassword('')
      setConfirmation('')
    } catch (err) {
      setError(err instanceof Error ? err.message : '로그인에 실패했습니다. 다시 시도해 주세요.')
    } finally { pending.current = false; setSubmitting(false) }
  }

  function socialLogin(login: () => void) {
    rememberReturnTo(returnTo)
    login()
  }

  if (!loading && member) return <Navigate to={returnTo} replace />
  return (
    <div className="discovery-shell">
      <SiteHeader />
      <main className="flow-narrow auth-card">
        <p className="flow-eyebrow">YOUR INVITATION STARTS HERE</p>
        <h1>{registering ? '우리의 이야기를 시작해요.' : '다시 만나 반가워요.'}</h1>
        <p className="flow-muted">{returnTo.startsWith('/create?') ? '선택한 템플릿을 기억하고 있어요. 로그인 후 제작을 이어가세요.' : '템플릿 구경은 자유롭게, 제작과 관리는 로그인 후에.'}</p>
        {loading ? <p role="status">로그인 상태를 확인하고 있어요.</p> : <>
          <form className="flow-form" onSubmit={handleAuth}>
            <fieldset disabled={submitting}>
              {registering && <label>이름<input autoComplete="name" value={name} onChange={e => setName(e.target.value)} required maxLength={50} /></label>}
              <label>이메일<input type="email" autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} required maxLength={254} /></label>
              <label>비밀번호<input type="password" autoComplete={registering ? 'new-password' : 'current-password'} value={password} onChange={e => setPassword(e.target.value)} required minLength={registering ? 8 : undefined} maxLength={64} placeholder={registering ? '8자 이상 입력해 주세요' : undefined} /></label>
              {registering && <label>비밀번호 확인<input type="password" autoComplete="new-password" value={confirmation} onChange={e => setConfirmation(e.target.value)} required maxLength={64} /></label>}
              {error && <p className="form-error" role="alert">{error}</p>}
              <button className="discovery-button flow-full" type="submit">{submitting ? '처리 중...' : registering ? '회원가입하고 계속하기' : '로그인하고 계속하기'}</button>
              <button type="button" className="flow-text-button" onClick={() => { setRegistering(!registering); setError(null); setPassword(''); setConfirmation('') }}>{registering ? '이미 계정이 있나요? 로그인' : '처음 오셨나요? 회원가입'}</button>
            </fieldset>
          </form>
          <div className="auth-divider">또는 소셜 계정으로</div>
          <div className="flow-social">
            <button type="button" className="login-kakao" disabled={submitting} onClick={() => socialLogin(loginWithKakao)}>카카오로 시작하기</button>
            <button type="button" className="login-google" disabled={submitting} onClick={() => socialLogin(loginWithGoogle)}>Google로 시작하기</button>
          </div>
        </>}
        <Link className="flow-back" to="/templates">로그인 없이 템플릿 둘러보기</Link>
      </main>
    </div>
  )
}
