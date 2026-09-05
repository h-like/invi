import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function OAuthCallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { completeLogin } = useAuth()

  useEffect(() => {
    const token = searchParams.get('token')
    if (!token) {
      navigate('/')
      return
    }
    completeLogin(token).then(() => navigate('/'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return <main className="page">로그인 처리 중...</main>
}
