import { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import './TestCasePage.css'

// ── Mock data (thay bằng API call sau) ─────────────────────
const MOCK_TEST_CASES = [
  { id: 'TC-001', name: 'Đăng nhập thành công với email và mật khẩu hợp lệ', type: 'happy', status: 'pending', actor: 'Customer', useCase: 'Login / Logout' },
  { id: 'TC-002', name: 'Đăng nhập thất bại khi nhập sai mật khẩu 3 lần liên tiếp', type: 'negative', status: 'pass', actor: 'Customer', useCase: 'Login / Logout' },
  { id: 'TC-003', name: 'Đăng nhập với email rỗng — thông báo lỗi validation', type: 'negative', status: 'fail', actor: 'Customer', useCase: 'Login / Logout' },
  { id: 'TC-004', name: 'Xác thực 2FA khi đăng nhập từ thiết bị lạ', type: 'extend', status: 'pending', actor: 'Customer', useCase: 'Login / Logout' },
  { id: 'TC-005', name: 'Thêm sản phẩm vào giỏ hàng khi chưa đăng nhập', type: 'negative', status: 'pending', actor: 'Customer', useCase: 'Add to cart' },
  { id: 'TC-006', name: 'Checkout thành công: verify stock → payment → xác nhận đơn hàng', type: 'happy', status: 'pass', actor: 'Customer', useCase: 'Checkout' },
  { id: 'TC-007', name: 'Thêm sản phẩm vào giỏ hàng thành công', type: 'happy', status: 'pending', actor: 'Customer', useCase: 'Add to cart' },
  { id: 'TC-008', name: 'Thêm quá số lượng tồn kho vào giỏ hàng', type: 'boundary', status: 'pending', actor: 'Customer', useCase: 'Add to cart' },
  { id: 'TC-009', name: 'Xem chi tiết sản phẩm không tồn tại (404)', type: 'negative', status: 'fail', actor: 'Customer', useCase: 'View product' },
  { id: 'TC-010', name: 'Admin tạo sản phẩm mới với đầy đủ thông tin', type: 'happy', status: 'pending', actor: 'Admin', useCase: 'Manage products' },
  { id: 'TC-011', name: 'Admin tạo sản phẩm với tên rỗng — validation lỗi', type: 'negative', status: 'pending', actor: 'Admin', useCase: 'Manage products' },
  { id: 'TC-012', name: 'Checkout khi giỏ hàng rỗng', type: 'negative', status: 'pending', actor: 'Customer', useCase: 'Checkout' },
]

const USE_CASES = ['Tất cả', 'Login / Logout', 'View product', 'Add to cart', 'Checkout', 'Payment', 'Manage products', 'Manage orders', 'View reports']
const USE_CASE_COUNTS = { 'Tất cả': 72, 'Login / Logout': 8, 'View product': 6, 'Add to cart': 5, 'Checkout': 12, 'Payment': 10, 'Manage products': 14, 'Manage orders': 9, 'View reports': 8 }

const TYPE_LABELS = {
  happy: { label: 'Happy path', className: 'badge-happy' },
  negative: { label: 'Negative', className: 'badge-neg' },
  extend: { label: 'Extend', className: 'badge-ext' },
  boundary: { label: 'Boundary', className: 'badge-bound' },
}

const STATUS_LABELS = {
  pending: { label: 'Chưa chạy', className: 'badge-pending' },
  pass: { label: 'Pass', className: 'badge-pass' },
  fail: { label: 'Fail', className: 'badge-fail' },
  running: { label: 'Đang chạy', className: 'badge-running' },
}

// ── Badge component ─────────────────────────────────────────
function Badge({ config }) {
  return (
    <span className={`badge ${config.className}`}>{config.label}</span>
  )
}

// ── Main Page ───────────────────────────────────────────────
export default function TestCasePage() {
  const navigate = useNavigate()

  const [search, setSearch] = useState('')
  const [selectedUseCase, setSelectedUseCase] = useState('Tất cả')
  const [selectedType, setSelectedType] = useState('Tất cả')
  const [checkedIds, setCheckedIds] = useState(new Set())
  const [allChecked, setAllChecked] = useState(false)

  // ── Filter logic ─────────────────────────────────────────
  const filtered = useMemo(() => {
    return MOCK_TEST_CASES.filter(tc => {
      const matchSearch = tc.name.toLowerCase().includes(search.toLowerCase()) ||
                          tc.id.toLowerCase().includes(search.toLowerCase())
      const matchUC = selectedUseCase === 'Tất cả' || tc.useCase === selectedUseCase
      const matchType = selectedType === 'Tất cả' || tc.type === selectedType
      return matchSearch && matchUC && matchType
    })
  }, [search, selectedUseCase, selectedType])

  // ── Checkbox logic ───────────────────────────────────────
  const toggleCheck = (id) => {
    setCheckedIds(prev => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  const toggleAll = () => {
    if (allChecked) {
      setCheckedIds(new Set())
    } else {
      setCheckedIds(new Set(filtered.map(tc => tc.id)))
    }
    setAllChecked(!allChecked)
  }

  const selectedCount = checkedIds.size

  // ── Export handlers ──────────────────────────────────────
  const handleExportJSON = () => {
    const data = filtered.filter(tc => checkedIds.has(tc.id) || checkedIds.size === 0)
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = url; a.download = 'test-cases.json'; a.click()
  }

  return (
    <div className="tc-page">
      <div className="page-header">
        <div className="page-header-left">
          <h1 className="page-title">Danh sách Test Case</h1>
          <p className="page-subtitle">Xem, lọc, chỉnh sửa test case đã sinh; chọn test để chạy</p>
        </div>
        <div className="page-header-right">
          <span className="total-badge">72 test cases</span>
        </div>
      </div>

      <div className="tc-layout">
        {/* ── SIDEBAR ── */}
        <aside className="tc-sidebar">
          {/* Search */}
          <div className="search-box">
            <span className="search-icon">🔍</span>
            <input
              type="text"
              placeholder="Tìm kiếm..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="search-input"
            />
          </div>

          {/* Filter by use case */}
          <div className="sidebar-group">
            <div className="sidebar-group-label">Lọc theo use case</div>
            {USE_CASES.map(uc => (
              <button
                key={uc}
                className={`sidebar-item ${selectedUseCase === uc ? 'active' : ''}`}
                onClick={() => setSelectedUseCase(uc)}
              >
                <span className="sidebar-item-name">{uc}</span>
                <span className="sidebar-count">{USE_CASE_COUNTS[uc] || ''}</span>
              </button>
            ))}
          </div>

          {/* Filter by type */}
          <div className="sidebar-group">
            <div className="sidebar-group-label">Loại test</div>
            {['Tất cả', 'happy', 'negative', 'boundary', 'extend'].map(type => (
              <button
                key={type}
                className={`sidebar-item ${selectedType === type ? 'active' : ''}`}
                onClick={() => setSelectedType(type)}
              >
                <span className="sidebar-item-name">
                  {type === 'Tất cả' ? 'Tất cả' : TYPE_LABELS[type]?.label}
                </span>
              </button>
            ))}
          </div>
        </aside>

        {/* ── MAIN TABLE ── */}
        <div className="tc-main">
          {/* Toolbar */}
          <div className="toolbar">
            <span className="toolbar-count">
              {filtered.length} test cases
              {selectedCount > 0 && (
                <span className="selected-count"> · {selectedCount} đã chọn</span>
              )}
            </span>
            <div className="toolbar-actions">
              <button className="btn-outline" onClick={handleExportJSON}>
                Export JSON
              </button>
              <button className="btn-outline">Export Excel</button>
              {selectedCount > 0 ? (
                <button
                  className="btn btn-primary"
                  onClick={() => navigate('/results')}
                >
                  Chạy {selectedCount} test →
                </button>
              ) : (
                <button
                  className="btn btn-primary"
                  onClick={() => navigate('/results')}
                >
                  Chạy tất cả →
                </button>
              )}
            </div>
          </div>

          {/* Table */}
          <div className="table-wrapper">
            <table className="tc-table">
              <thead>
                <tr>
                  <th style={{ width: 36 }}>
                    <input
                      type="checkbox"
                      className="tc-check"
                      checked={allChecked}
                      onChange={toggleAll}
                    />
                  </th>
                  <th style={{ width: 72 }}>ID</th>
                  <th>Tên test case</th>
                  <th style={{ width: 100 }}>Loại</th>
                  <th style={{ width: 90 }}>Trạng thái</th>
                  <th style={{ width: 80 }}>Actor</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(tc => (
                  <tr
                    key={tc.id}
                    className={checkedIds.has(tc.id) ? 'row-checked' : ''}
                    onClick={() => toggleCheck(tc.id)}
                  >
                    <td onClick={e => e.stopPropagation()}>
                      <input
                        type="checkbox"
                        className="tc-check"
                        checked={checkedIds.has(tc.id)}
                        onChange={() => toggleCheck(tc.id)}
                      />
                    </td>
                    <td>
                      <span className="tc-code">{tc.id}</span>
                    </td>
                    <td>
                      <span className="tc-name">{tc.name}</span>
                    </td>
                    <td>
                      <Badge config={TYPE_LABELS[tc.type]} />
                    </td>
                    <td>
                      <Badge config={STATUS_LABELS[tc.status]} />
                    </td>
                    <td className="tc-actor">{tc.actor}</td>
                  </tr>
                ))}

                {filtered.length === 0 && (
                  <tr>
                    <td colSpan={6} className="empty-row">
                      Không tìm thấy test case nào
                    </td>
                  </tr>
                )}
              </tbody>
            </table>

            {filtered.length < 72 ? null : (
              <div className="table-more">+ {72 - MOCK_TEST_CASES.length} test cases khác chưa hiển thị (kết nối API để xem đầy đủ)</div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}