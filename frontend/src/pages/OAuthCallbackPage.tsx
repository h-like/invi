import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { loginPath, readReturnTo, takeReturnTo } from '../auth/returnTo'

export function OAuthCallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { completeLogin } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const [returnTo] = useState(readReturnTo)
  const operation = useRef<Promise<void> | null>(null)

  useEffect(() => {
    const token = searchParams.get('token')
    // Remove the credential from history before completing the login request.
    window.history.replaceState(window.history.state, '', '/oauth/callback')
    if (!token) {
      navigate(loginPath(returnTo), { replace: true })
      return
    }
    let active = true
    operation.current ??= completeLogin(token)
    operation.current.then(() => { if (active) { takeReturnTo(); navigate(returnTo, { replace: true }) } })
      .catch(() => { if (active) setError('로그인을 완료하지 못했습니다. 다시 로그인해 주세요.') })
    return () => { active = false }
  }, [completeLogin, navigate, returnTo, searchParams])

  return <main className="page">{error ? <><p className="form-error" role="alert">{error}</p><Link to={loginPath(returnTo)}>다시 로그인</Link></> : <p role="status">로그인 처리 중...</p>}</main>
}
