import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import './MainLayout.css'

const NAV = [
  {
    to: '/upload',
    label: 'Upload',
    icon: (
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
        <polyline points="17 8 12 3 7 8"/>
        <line x1="12" y1="3" x2="12" y2="15"/>
      </svg>
    ),
  },
  {
    to: '/testcases',
    label: 'Danh sách',
    icon: (
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <line x1="8"  y1="6"  x2="21"    y2="6"/>
        <line x1="8"  y1="12" x2="21"    y2="12"/>
        <line x1="8"  y1="18" x2="21"    y2="18"/>
        <line x1="3"  y1="6"  x2="3.01"  y2="6"/>
        <line x1="3"  y1="12" x2="3.01"  y2="12"/>
        <line x1="3"  y1="18" x2="3.01"  y2="18"/>
      </svg>
    ),
  },
  {
    to: '/run',
    label: 'Chạy test',
    icon: (
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <polygon points="5 3 19 12 5 21 5 3"/>
      </svg>
    ),
  },
  {
    to: '/settings',
    label: 'Cài đặt',
    icon: (
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="3"/>
        <path d="M19.07 4.93a10 10 0 0 1 0 14.14M4.93 4.93a10 10 0 0 0 0 14.14"/>
        <path d="M15.54 8.46a5 5 0 0 1 0 7.07M8.46 8.46a5 5 0 0 0 0 7.07"/>
      </svg>
    ),
  },
]

export default function MainLayout() {
  const { logout, user } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  // Lấy chữ cái đầu của username để làm avatar
  const avatar = user?.username?.charAt(0)?.toUpperCase() || 'U'

  return (
    <div className="layout">
      <aside className="sidebar">
        {/* Brand */}
        <div className="sidebar-brand">
          <div className="brand-logo">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M9 11l3 3L22 4"/>
              <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
            </svg>
          </div>
          <div>
            <div className="brand-name">TestCase Gen</div>
            <div className="brand-sub">v0.1 · Spring Boot</div>
          </div>
        </div>

        {/* Nav */}
        <nav className="sidebar-nav">
          {NAV.map(item => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
            >
              <span className="nav-icon">{item.icon}</span>
              <span className="nav-label">{item.label}</span>
            </NavLink>
          ))}
        </nav>

        {/* Footer: user info + logout */}
        <div className="sidebar-footer">
          <div className="env-badge">
            <span className="env-dot" />
            localhost:8080
          </div>

          {/* User row */}
          <div className="user-row">
            <div className="user-avatar">{avatar}</div>
            <div className="user-info">
              <div className="user-name">{user?.username || 'User'}</div>
              <div className="user-email">{user?.email || ''}</div>
            </div>
            <button
              className="logout-btn"
              onClick={handleLogout}
              title="Đăng xuất"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
                <polyline points="16 17 21 12 16 7"/>
                <line x1="21" y1="12" x2="9" y2="12"/>
              </svg>
            </button>
          </div>
        </div>
      </aside>

      <main className="main-area">
        <Outlet />
      </main>
    </div>
  )
}