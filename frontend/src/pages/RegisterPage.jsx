import { useState, useMemo } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import './auth.css'

const IconUser  = () => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
const IconMail  = () => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
const IconLock  = () => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
const IconEye   = () => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
const IconEyeOff = () => <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
const IconCheck = () => <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>

function pwStrength(pw) {
  if (!pw) return 0
  let s = 0
  if (pw.length >= 8)        s++
  if (/[A-Z]/.test(pw))      s++
  if (/[0-9]/.test(pw))      s++
  if (/[^A-Za-z0-9]/.test(pw)) s++
  return Math.min(s, 3)
}

const STRENGTH_LABEL = ['', 'Yếu', 'Trung bình', 'Mạnh']
const STRENGTH_COLOR = ['', '#f85149', '#d29922', '#3fb950']

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate     = useNavigate()

  const [form,    setForm]    = useState({ username: '', email: '', password: '', confirm: '' })
  const [showPw,  setShowPw]  = useState(false)
  const [loading, setLoading] = useState(false)
  const [error,   setError]   = useState('')

  const strength = useMemo(() => pwStrength(form.password), [form.password])

  const set = (k) => (e) => { setForm(p => ({ ...p, [k]: e.target.value })); setError('') }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.username.trim() || !form.email.trim() || !form.password)
      return setError('Vui lòng điền đầy đủ thông tin')
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email))
      return setError('Email không hợp lệ')
    if (form.password.length < 6)
      return setError('Mật khẩu phải có ít nhất 6 ký tự')
    if (form.password !== form.confirm)
      return setError('Mật khẩu xác nhận không khớp')

    setLoading(true)
    setError('')
    try {
      await register(form.username.trim(), form.email.trim(), form.password)
      navigate('/upload', { replace: true })
    } catch (err) {
      setError(err.message || 'Đăng ký thất bại')
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
          <div className="auth-logo-icon"><IconCheck /></div>
          <span className="auth-logo-text">TestCase Gen</span>
        </div>
        <h1 className="auth-headline">
          Bắt đầu<br /><span>kiểm thử</span><br />thông minh
        </h1>
        <p className="auth-tagline">
          Tạo tài khoản miễn phí và bắt đầu sinh test case tự động
          từ Use Case Diagram ngay hôm nay.
        </p>
        <div className="auth-features">
          {[
            'Đăng ký nhanh, không cần thẻ tín dụng',
            'Hỗ trợ XMI, PlantUML, Draw.io, JSON',
            'AI (Claude) hỗ trợ sinh test case chất lượng cao',
            'Quản lý và chia sẻ kết quả với nhóm',
          ].map((f, i) => (
            <div key={i} className="auth-feature">
              <span className="af-dot" />{f}
            </div>
          ))}
        </div>
      </div>

      {/* ── Right ── */}
      <div className="auth-form-panel">
        <div className="auth-form-inner">
          <h2 className="auth-form-title">Tạo tài khoản</h2>
          <p className="auth-form-sub">Điền thông tin bên dưới để bắt đầu</p>

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
              <label className="auth-label">Tên đăng nhập</label>
              <div className="auth-input-wrap">
                <span className="auth-input-icon"><IconUser /></span>
                <input className="auth-input" type="text" placeholder="vd: tucam_dev"
                  value={form.username} onChange={set('username')} autoFocus autoComplete="username" />
              </div>
            </div>

            <div className="auth-field">
              <label className="auth-label">Email</label>
              <div className="auth-input-wrap">
                <span className="auth-input-icon"><IconMail /></span>
                <input className="auth-input" type="email" placeholder="you@example.com"
                  value={form.email} onChange={set('email')} autoComplete="email" />
              </div>
            </div>

            <div className="auth-field">
              <label className="auth-label">Mật khẩu</label>
              <div className="auth-input-wrap">
                <span className="auth-input-icon"><IconLock /></span>
                <input className={`auth-input`} type={showPw ? 'text' : 'password'}
                  placeholder="Ít nhất 6 ký tự"
                  value={form.password} onChange={set('password')} autoComplete="new-password" />
                <button type="button" className="pw-toggle" onClick={() => setShowPw(p => !p)}>
                  {showPw ? <IconEyeOff /> : <IconEye />}
                </button>
              </div>
              {form.password && (
                <>
                  <div className="pw-strength">
                    {[1, 2, 3].map(n => (
                      <div key={n} className={`pw-bar ${strength >= n ? `s${strength}` : ''}`} />
                    ))}
                  </div>
                  <div className="pw-hint" style={{ color: STRENGTH_COLOR[strength] }}>
                    {STRENGTH_LABEL[strength]}
                  </div>
                </>
              )}
            </div>

            <div className="auth-field">
              <label className="auth-label">Xác nhận mật khẩu</label>
              <div className="auth-input-wrap">
                <span className="auth-input-icon"><IconLock /></span>
                <input className={`auth-input ${form.confirm && form.confirm !== form.password ? 'input-error' : ''}`}
                  type="password" placeholder="Nhập lại mật khẩu"
                  value={form.confirm} onChange={set('confirm')} autoComplete="new-password" />
              </div>
            </div>

            <button className="auth-btn" type="submit" disabled={loading}>
              {loading ? <><span className="auth-spin" /> Đang tạo tài khoản...</> : 'Tạo tài khoản →'}
            </button>
          </form>

          <div className="auth-switch">
            Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
          </div>
        </div>
      </div>
    </div>
  )
}