import { useEffect, useState } from 'react'
import './App.css'

type HelloResponse = {
  message: string
  serverTime: string
}

function App() {
  const [hello, setHello] = useState<HelloResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('http://localhost:8080/api/hello')
      .then((res) => {
        if (!res.ok) throw new Error(`API responded with ${res.status}`)
        return res.json() as Promise<HelloResponse>
      })
      .then(setHello)
      .catch((err: Error) => setError(err.message))
  }, [])

  return (
    <main className="status-card">
      <h1>invi — Phase 0</h1>
      {error && <p className="status-error">백엔드 연결 실패: {error}</p>}
      {!error && !hello && <p>백엔드 응답 대기 중...</p>}
      {hello && (
        <>
          <p className="status-ok">{hello.message}</p>
          <p className="status-time">서버 시각: {hello.serverTime}</p>
        </>
      )}
    </main>
  )
}

export default App
