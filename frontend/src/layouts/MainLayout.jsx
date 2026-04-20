import { Outlet, NavLink } from 'react-router-dom'
import './MainLayout.css'

const NAV_ITEMS = [
  { to: '/',          label: 'Trang chủ',    icon: '⊞' },
  { to: '/upload',    label: 'Upload',        icon: '↑' },
  { to: '/testcases', label: 'Danh sách',     icon: '≡' },
  { to: '/results',   label: 'Kết quả',       icon: '◎' },
]

export default function MainLayout() {
  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <span className="brand-icon">⬡</span>
          <span className="brand-name">TestGen</span>
          <span className="brand-version">v0.1</span>
        </div>
        <nav className="sidebar-nav">
          {NAV_ITEMS.map(item => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
            >
              <span className="nav-icon">{item.icon}</span>
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <span>Spring Boot · MySQL</span>
        </div>
      </aside>
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  )
}