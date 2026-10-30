import { createBrowserRouter, Outlet, RouterProvider } from 'react-router-dom'
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

// Data Router provides the supported useBlocker API for browser back/forward.
const router = createBrowserRouter([
  {
    element: <><RouteScrollReset /><Outlet /></>,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/templates', element: <TemplatesPage /> },
      { path: '/templates/:id', element: <TemplateDetailPage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/oauth/callback', element: <OAuthCallbackPage /> },
      { path: '/create', element: <RequireAuth><CreateInvitationPage /></RequireAuth> },
      { path: '/invitations', element: <RequireAuth><MyInvitationsPage /></RequireAuth> },
      { path: '/e/:id', element: <RequireAuth><InvitationBuilderPage /></RequireAuth> },
      { path: '/e/:id/guests', element: <RequireAuth><GuestManagementPage /></RequireAuth> },
      { path: '/i/:slug', element: <GuestPage /> },
    ],
  },
])

if (import.meta.hot) import.meta.hot.dispose(() => router.dispose())

function App() {
  return <AuthProvider><RouterProvider router={router} /></AuthProvider>
}

export default App
