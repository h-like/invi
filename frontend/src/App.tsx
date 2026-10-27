import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './App.css'
import './editor/builder.css'
import { AuthProvider } from './auth/AuthContext'
import { RequireAuth } from './auth/RequireAuth'
import { RouteScrollReset } from './discovery/RouteScrollReset'
import { CreateInvitationPage } from './pages/CreateInvitationPage'
import { LoginPage } from './pages/LoginPage'
import { MyInvitationsPage } from './pages/MyInvitationsPage'
import { TemplateDetailPage } from './pages/TemplateDetailPage'
import { TemplatesPage } from './pages/TemplatesPage'
import { InvitationBuilderPage } from './pages/InvitationBuilderPage'
import { GuestManagementPage } from './pages/GuestManagementPage'
import { GuestPage } from './pages/GuestPage'
import { HomePage } from './pages/HomePage'
import { OAuthCallbackPage } from './pages/OAuthCallbackPage'

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <RouteScrollReset />
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/templates" element={<TemplatesPage />} />
          <Route path="/templates/:id" element={<TemplateDetailPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
          <Route path="/create" element={<RequireAuth><CreateInvitationPage /></RequireAuth>} />
          <Route path="/invitations" element={<RequireAuth><MyInvitationsPage /></RequireAuth>} />
          <Route path="/e/:id" element={<RequireAuth><InvitationBuilderPage /></RequireAuth>} />
          <Route path="/e/:id/guests" element={<RequireAuth><GuestManagementPage /></RequireAuth>} />
          <Route path="/i/:slug" element={<GuestPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
