import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api } from '../api/client'
import type { Member } from '../api/types'
import { clearToken, getToken, setToken } from './token'

const API_BASE = 'http://localhost:8080'

interface AuthState {
  member: Member | null
  loading: boolean
  loginWithKakao: () => void
  loginWithGoogle: () => void
  completeLogin: (token: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [member, setMember] = useState<Member | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!getToken()) {
      setLoading(false)
      return
    }
    api
      .getMe()
      .then(setMember)
      .catch(() => clearToken())
      .finally(() => setLoading(false))
  }, [])

  async function completeLogin(token: string) {
    setToken(token)
    setLoading(true)
    try {
      setMember(await api.getMe())
    } finally {
      setLoading(false)
    }
  }

  function logout() {
    clearToken()
    setMember(null)
  }

  return (
    <AuthContext.Provider
      value={{
        member,
        loading,
        loginWithKakao: () => window.location.assign(`${API_BASE}/oauth2/authorization/kakao`),
        loginWithGoogle: () => window.location.assign(`${API_BASE}/oauth2/authorization/google`),
        completeLogin,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
