import { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader, Badge, Button, SectionLabel } from '../components/ui'
import '../components/ui.css'
import './TestCasePage.css'

/* ── MOCK DATA ───────────────────────────────────────────────── */
const ALL_TC = [
  { id:'TC-001', name:'Đăng nhập thành công với email và mật khẩu hợp lệ',      type:'happy',    status:'pending', actor:'Customer', useCase:'Login / Logout' },
  { id:'TC-002', name:'Đăng nhập thất bại khi nhập sai mật khẩu 3 lần liên tiếp',type:'negative', status:'pass',    actor:'Customer', useCase:'Login / Logout' },
  { id:'TC-003', name:'Đăng nhập với email rỗng — thông báo lỗi validation',     type:'negative', status:'fail',    actor:'Customer', useCase:'Login / Logout' },
  { id:'TC-004', name:'Xác thực 2FA khi đăng nhập từ thiết bị lạ',              type:'extend',   status:'pending', actor:'Customer', useCase:'Login / Logout' },
  { id:'TC-005', name:'Thêm sản phẩm vào giỏ hàng khi chưa đăng nhập',         type:'negative', status:'pending', actor:'Customer', useCase:'Add to cart'    },
  { id:'TC-006', name:'Checkout thành công: verify stock → payment → xác nhận', type:'happy',    status:'pass',    actor:'Customer', useCase:'Checkout'       },
  { id:'TC-007', name:'Thêm sản phẩm vào giỏ hàng thành công',                 type:'happy',    status:'pending', actor:'Customer', useCase:'Add to cart'    },
  { id:'TC-008', name:'Thêm quá số lượng tồn kho vào giỏ hàng',                type:'boundary', status:'pending', actor:'Customer', useCase:'Add to cart'    },
  { id:'TC-009', name:'Xem chi tiết sản phẩm không tồn tại (404)',              type:'negative', status:'fail',    actor:'Customer', useCase:'View product'   },
  { id:'TC-010', name:'Admin tạo sản phẩm mới với đầy đủ thông tin',            type:'happy',    status:'pending', actor:'Admin',    useCase:'Manage products'},
  { id:'TC-011', name:'Admin tạo sản phẩm với tên rỗng — validation lỗi',      type:'negative', status:'pending', actor:'Admin',    useCase:'Manage products'},
  { id:'TC-012', name:'Checkout khi giỏ hàng rỗng',                             type:'negative', status:'pending', actor:'Customer', useCase:'Checkout'       },
]

const USE_CASES = [
  { label: 'Tất cả',          count: 72 },
  { label: 'Login / Logout',  count:  8 },
  { label: 'View product',    count:  6 },
  { label: 'Add to cart',     count:  5 },
  { label: 'Checkout',        count: 12 },
  { label: 'Payment',         count: 10 },
  { label: 'Manage products', count: 14 },
  { label: 'Manage orders',   count:  9 },
  { label: 'View reports',    count:  8 },
]

const TYPES = [
  { k: 'all',      label: 'Tất cả'      },
  { k: 'happy',    label: 'Happy path'  },
  { k: 'negative', label: 'Negative'    },
  { k: 'boundary', label: 'Boundary'    },
  { k: 'extend',   label: 'Extend'      },
]

const TYPE_BADGE   = { happy:'happy', negative:'negative', boundary:'boundary', extend:'extend' }
const STATUS_BADGE = { pending:'pending', pass:'pass', fail:'fail', running:'running' }
const STATUS_LABEL = { pending:'Chưa chạy', pass:'Pass', fail:'Fail', running:'Đang chạy' }
const TYPE_LABEL   = { happy:'Happy path', negative:'Negative', boundary:'Boundary', extend:'Extend' }

function exportJSON(data) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url  = URL.createObjectURL(blob)
  const a    = document.createElement('a')
  a.href = url; a.download = 'test-cases.json'; a.click()
  URL.revokeObjectURL(url)
}

/* ── PAGE ────────────────────────────────────────────────────── */
export default function TestCasePage() {
  const navigate = useNavigate()

  const [search,   setSearch]   = useState('')
  const [selUC,    setSelUC]    = useState('Tất cả')
  const [selType,  setSelType]  = useState('all')
  const [checked,  setChecked]  = useState(new Set())

  const filtered = useMemo(() => ALL_TC.filter(tc => {
    const q = search.toLowerCase()
    const matchSearch = tc.name.toLowerCase().includes(q) || tc.id.toLowerCase().includes(q)
    const matchUC   = selUC  === 'Tất cả' || tc.useCase === selUC
    const matchType = selType === 'all'    || tc.type    === selType
    return matchSearch && matchUC && matchType
  }), [search, selUC, selType])

  const allChecked  = filtered.length > 0 && filtered.every(tc => checked.has(tc.id))

  const toggleCheck = (id) => setChecked(prev => {
    const s = new Set(prev); s.has(id) ? s.delete(id) : s.add(id); return s
  })

  const toggleAll = () => {
    if (allChecked) setChecked(new Set())
    else setChecked(new Set(filtered.map(tc => tc.id)))
  }

  const runSelected = () => navigate('/run')

  return (
    <div className="tc-page">
      <PageHeader
        title="Danh sách Test Case"
        subtitle="Xem, lọc, chỉnh sửa test case đã sinh; chọn test để chạy"
        right={
          <span className="total-pill">72 test cases</span>
        }
      />

      <div className="tc-layout">
        {/* ── SIDEBAR ── */}
        <aside className="tc-sidebar">
          <div className="search-wrap">
            <svg className="search-ico" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
            <input
              className="search-input"
              placeholder="Tìm kiếm..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <SectionLabel>Lọc theo use case</SectionLabel>
          <div className="filter-group">
            {USE_CASES.map(({ label, count }) => (
              <button
                key={label}
                className={`filter-btn ${selUC === label ? 'active' : ''}`}
                onClick={() => setSelUC(label)}
              >
                <span className="filter-label">{label}</span>
                <span className="filter-count">{count}</span>
              </button>
            ))}
          </div>

          <SectionLabel>Loại test</SectionLabel>
          <div className="filter-group">
            {TYPES.map(({ k, label }) => (
              <button
                key={k}
                className={`filter-btn ${selType === k ? 'active' : ''}`}
                onClick={() => setSelType(k)}
              >
                <span className="filter-label">{label}</span>
              </button>
            ))}
          </div>
        </aside>

        {/* ── TABLE ── */}
        <div className="tc-table-wrap">
          {/* Toolbar */}
          <div className="tc-toolbar">
            <span className="toolbar-info">
              {filtered.length} test cases
              {checked.size > 0 && (
                <span className="toolbar-selected"> · {checked.size} đã chọn</span>
              )}
            </span>
            <div className="toolbar-actions">
              <Button variant="secondary" size="sm" onClick={() => exportJSON(filtered)}>Export JSON</Button>
              <Button variant="secondary" size="sm">Export Excel</Button>
              <Button variant="primary" size="sm" onClick={runSelected}>
                {checked.size > 0 ? `Chạy ${checked.size} test →` : 'Chạy tất cả →'}
              </Button>
            </div>
          </div>

          {/* Table */}
          <div className="table-scroll">
            <table className="tc-table">
              <thead>
                <tr>
                  <th style={{ width: 36 }}>
                    <input type="checkbox" className="row-check" checked={allChecked} onChange={toggleAll} />
                  </th>
                  <th style={{ width: 72 }}>ID</th>
                  <th>Tên test case</th>
                  <th style={{ width: 100 }}>Loại</th>
                  <th style={{ width: 90 }}>Trạng thái</th>
                  <th style={{ width: 80 }}>Actor</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((tc) => (
                  <tr
                    key={tc.id}
                    className={checked.has(tc.id) ? 'row-checked' : ''}
                    onClick={() => toggleCheck(tc.id)}
                  >
                    <td onClick={(e) => e.stopPropagation()}>
                      <input
                        type="checkbox"
                        className="row-check"
                        checked={checked.has(tc.id)}
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
                      <Badge variant={TYPE_BADGE[tc.type] || 'default'}>
                        {TYPE_LABEL[tc.type] || tc.type}
                      </Badge>
                    </td>
                    <td>
                      <Badge variant={STATUS_BADGE[tc.status] || 'default'}>
                        {STATUS_LABEL[tc.status] || tc.status}
                      </Badge>
                    </td>
                    <td className="tc-actor">{tc.actor}</td>
                  </tr>
                ))}
                {filtered.length === 0 && (
                  <tr>
                    <td colSpan={6} className="empty-row">Không tìm thấy test case nào</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {filtered.length > 0 && (
            <div className="table-footer">
              + {72 - ALL_TC.length} test cases khác (kết nối API để xem đầy đủ)
            </div>
          )}
        </div>
      </div>
    </div>
  )
}