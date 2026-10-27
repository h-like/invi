import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

export function RouteScrollReset() {
  const { pathname, search, hash } = useLocation()
  useEffect(() => {
    if (hash) {
      try {
        const target = document.getElementById(decodeURIComponent(hash.slice(1)))
        if (target) { target.scrollIntoView(); return }
      } catch { /* A malformed anchor should not break navigation. */ }
    }
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
  }, [pathname, search, hash])
  return null
}
