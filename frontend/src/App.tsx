import { BrowserRouter, Route, Routes } from 'react-router-dom'
import './App.css'
import { EditorPage } from './pages/EditorPage'
import { GuestPage } from './pages/GuestPage'
import { HomePage } from './pages/HomePage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/e/:id" element={<EditorPage />} />
        <Route path="/i/:slug" element={<GuestPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
