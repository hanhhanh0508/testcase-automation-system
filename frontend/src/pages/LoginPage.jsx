import { useState } from 'react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import './auth.css'

/* ── Icons ── */
const IconUser  = () => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
const IconLock  = () => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
const IconEye   = () => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
const IconEyeOff = () => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
const IconCheck = () => <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>

export default function LoginPage() {
  const { login }  = useAuth()
  const navigate   = useNavigate()
  const location   = useLocation()
  const from       = location.state?.from?.pathname || '/upload'

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPw,   setShowPw]   = useState(false)
  const [loading,  setLoading]  = useState(false)
  const [error,    setError]    = useState('')

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!username.trim() || !password) { setError('Vui lòng điền đầy đủ thông tin'); return }
    setLoading(true)
    setError('')
    try {
      await login(username.trim(), password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err.message || 'Đăng nhập thất bại')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      {/* ── Left ── */}
      <div className="auth-brand">
        <div className="brand-grid" />
        <div className="auth-logo">
          <div className="auth-logo-icon">
            <IconCheck />
          </div>
          <span className="auth-logo-text">TestCase Gen</span>
        </div>
        <h1 className="auth-headline">
          Tự động sinh<br /><span>test case</span><br />từ Use Case
        </h1>
        <p className="auth-tagline">
          Phân tích Use Case Diagram, sinh test case thông minh
          và thực thi tự động với Selenium — tất cả trong một nền tảng.
        </p>
        <div className="auth-features">
          {[
            'Nhập Use Case qua text hoặc file XMI / PlantUML',
            'Tự động sinh Happy path, Negative, Boundary test',
            'Thực thi Selenium và xem kết quả trực tiếp',
            'Xuất báo cáo PDF / Excel',
          ].map((f, i) => (
            <div key={i} className="auth-feature">
              <span className="af-dot" />
              {f}
            </div>
          ))}
        </div>
      </div>

      {/* ── Right ── */}
      <div className="auth-form-panel">
        <div className="auth-form-inner">
          <h2 className="auth-form-title">Đăng nhập</h2>
          <p className="auth-form-sub">Chào mừng trở lại!</p>

          {error && (
            <div className="auth-error">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate>
            <div className="auth-field">
              <label className="auth-label">Tên đăng nhập / Email</label>
              <div className="auth-input-wrap">
                <span className="auth-input-icon"><IconUser /></span>
                <input
                  className={`auth-input ${error ? 'input-error' : ''}`}
                  type="text"
                  placeholder="username hoặc email"
                  value={username}
                  onChange={e => { setUsername(e.target.value); setError('') }}
                  autoComplete="username"
                  autoFocus
                />
              </div>
            </div>

            <div className="auth-field">
              <label className="auth-label">Mật khẩu</label>
              <div className="auth-input-wrap">
                <span className="auth-input-icon"><IconLock /></span>
                <input
                  className={`auth-input ${error ? 'input-error' : ''}`}
                  type={showPw ? 'text' : 'password'}
                  placeholder="••••••••"
                  value={password}
                  onChange={e => { setPassword(e.target.value); setError('') }}
                  autoComplete="current-password"
                />
                <button type="button" className="pw-toggle" onClick={() => setShowPw(p => !p)}>
                  {showPw ? <IconEyeOff /> : <IconEye />}
                </button>
              </div>
            </div>

            <button className="auth-btn" type="submit" disabled={loading}>
              {loading ? <><span className="auth-spin" /> Đang đăng nhập...</> : 'Đăng nhập →'}
            </button>
          </form>

          <div className="auth-switch">
            Chưa có tài khoản? <Link to="/register">Đăng ký ngay</Link>
          </div>
        </div>
      </div>
    </div>
  )
}