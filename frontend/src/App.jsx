import { Routes, Route, Navigate } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import UploadPage from './pages/UploadPage'
import TestCasePage from './pages/TestCasePage'
import RunTestPage from './pages/RunTestPage'
import SettingsPage from './pages/SettingsPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<MainLayout />}>
        <Route index element={<Navigate to="/upload" replace />} />
        <Route path="upload"    element={<UploadPage />} />
        <Route path="testcases" element={<TestCasePage />} />
        <Route path="run"       element={<RunTestPage />} />
        <Route path="settings"  element={<SettingsPage />} />
      </Route>
    </Routes>
  )
}