import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { loginPath } from './returnTo'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { member, loading } = useAuth()
  const location = useLocation()
  if (loading) return <main className="page" role="status">로그인 상태를 확인하고 있어요.</main>
  if (!member) return <Navigate to={loginPath(location.pathname + location.search + location.hash)} replace />
  return children
}
