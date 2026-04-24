import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import ProtectedRoute   from './components/ProtectedRoute'
import MainLayout       from './layouts/MainLayout'
import LoginPage        from './pages/LoginPage'
import RegisterPage     from './pages/RegisterPage'
import UploadPage       from './pages/UploadPage'
import TestCasePage     from './pages/TestCasePage'
import RunTestPage      from './pages/RunTestPage'
import SettingsPage     from './pages/SettingsPage'

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        {/* ── Public ── */}
        <Route path="/login"    element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        {/* ── Protected ── */}
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <MainLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="/upload" replace />} />
          <Route path="upload"    element={<UploadPage />} />
          <Route path="testcases" element={<TestCasePage />} />
          <Route path="run"       element={<RunTestPage />} />
          <Route path="settings"  element={<SettingsPage />} />
        </Route>

        {/* ── Fallback ── */}
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </AuthProvider>
  )
}