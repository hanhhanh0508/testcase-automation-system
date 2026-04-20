import { Routes, Route } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import HomePage from './pages/HomePage'
import UploadPage from './pages/UploadPage'
import TestCasePage from './pages/TestCasePage'
import ResultPage from './pages/ResultPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<MainLayout />}>
        <Route index element={<HomePage />} />
        <Route path="upload" element={<UploadPage />} />
        <Route path="testcases" element={<TestCasePage />} />
        <Route path="results" element={<ResultPage />} />
      </Route>
    </Routes>
  )
}