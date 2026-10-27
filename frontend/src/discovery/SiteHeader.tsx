import { useEffect, useRef } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import './discovery.css'

export function SiteHeader() {
  const { member, loading, logout } = useAuth()
  const accountRef = useRef<HTMLDetailsElement>(null)

  useEffect(() => {
    function closeOutside(event: PointerEvent) {
      if (event.target instanceof Node && !accountRef.current?.contains(event.target)) {
        accountRef.current?.removeAttribute('open')
      }
    }
    document.addEventListener('pointerdown', closeOutside)
    return () => document.removeEventListener('pointerdown', closeOutside)
  }, [])

  return (
    <header className="discovery-header">
      <div className="discovery-container discovery-header-inner">
        <Link className="discovery-wordmark" to="/" aria-label="invi 홈">invi<span>.</span></Link>
        <nav className="discovery-nav" aria-label="주 메뉴">
          <NavLink to="/templates" className="discovery-nav-link">템플릿</NavLink>
          <div className="discovery-header-auth" aria-busy={loading}>
            {loading ? (
              <span className="discovery-auth-placeholder" role="status"><span className="discovery-sr-only">계정 확인 중</span></span>
            ) : member ? (
              <>
                <NavLink to="/invitations" className="discovery-nav-link">내 청첩장</NavLink>
                <details className="discovery-account" ref={accountRef} onKeyDown={(event) => {
                  if (event.key === 'Escape') {
                    event.currentTarget.removeAttribute('open')
                    event.currentTarget.querySelector('summary')?.focus()
                  }
                }} onBlur={(event) => {
                  if (!event.currentTarget.contains(event.relatedTarget as Node | null)) event.currentTarget.removeAttribute('open')
                }}>
                  <summary aria-label={`${member.name || '회원'}님 계정 정보`}>
                    <span className="discovery-account-avatar" aria-hidden="true">{member.name?.trim().slice(0, 1) || 'I'}</span>
                    <span className="discovery-account-name">{member.name || '회원'}님</span>
                    <span className="discovery-account-chevron" aria-hidden="true">⌄</span>
                  </summary>
                  <div className="discovery-account-panel">
                    <p className="discovery-account-label">내 계정</p>
                    <strong>{member.name || '회원'}님</strong>
                    <p className="discovery-account-email">{member.email}</p>
                    <button type="button" onClick={logout}>로그아웃</button>
                  </div>
                </details>
              </>
            ) : (
              <Link className="discovery-button discovery-button-secondary discovery-login-link" to="/login">로그인</Link>
            )}
          </div>
        </nav>
      </div>
    </header>
  )
}
