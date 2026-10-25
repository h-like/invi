import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './App.css'
import './editor/builder.css'
import { AuthProvider } from './auth/AuthContext'
import { InvitationBuilderPage } from './pages/InvitationBuilderPage'
import { GuestManagementPage } from './pages/GuestManagementPage'
import { GuestPage } from './pages/GuestPage'
import { HomePage } from './pages/HomePage'
import { OAuthCallbackPage } from './pages/OAuthCallbackPage'

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
          <Route path="/e/:id" element={<InvitationBuilderPage />} />
          <Route path="/e/:id/guests" element={<GuestManagementPage />} />
          <Route path="/i/:slug" element={<GuestPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
