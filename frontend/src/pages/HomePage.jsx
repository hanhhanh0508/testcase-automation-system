// HomePage.jsx
import { useNavigate } from 'react-router-dom'

export function HomePage() {
  const navigate = useNavigate()
  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 20, fontWeight: 600, color: '#0f172a', margin: '0 0 4px' }}>Trang chủ</h1>
        <p style={{ fontSize: 13, color: '#64748b', margin: 0 }}>Hệ thống tự động sinh test case từ Use Case Diagram</p>
      </div>
      <div style={{ display: 'flex', gap: 16 }}>
        {[
          { label: 'Upload Use Case', desc: 'Tải file UML lên để bắt đầu', to: '/upload', icon: '↑' },
          { label: 'Danh sách Test Case', desc: 'Xem và chọn test case để chạy', to: '/testcases', icon: '≡' },
          { label: 'Kết quả chạy test', desc: 'PASS/FAIL, log, screenshot', to: '/results', icon: '◎' },
        ].map(card => (
          <div
            key={card.to}
            onClick={() => navigate(card.to)}
            style={{
              background: '#fff', border: '1px solid #e2e8f0', borderRadius: 12,
              padding: 20, width: 200, cursor: 'pointer',
              transition: 'box-shadow 0.15s',
            }}
            onMouseEnter={e => e.currentTarget.style.boxShadow = '0 4px 16px rgba(0,0,0,0.08)'}
            onMouseLeave={e => e.currentTarget.style.boxShadow = 'none'}
          >
            <div style={{ fontSize: 22, marginBottom: 8 }}>{card.icon}</div>
            <div style={{ fontSize: 14, fontWeight: 600, color: '#1e293b', marginBottom: 4 }}>{card.label}</div>
            <div style={{ fontSize: 12, color: '#64748b' }}>{card.desc}</div>
          </div>
        ))}
      </div>
    </div>
  )
}

export default HomePage