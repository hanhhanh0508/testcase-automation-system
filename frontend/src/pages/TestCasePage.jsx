import { useState, useMemo, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader, Badge, Button, SectionLabel } from '../components/ui'
import '../components/ui.css'
import './TestCasePage.css'
import api from '../api'

/* ── Constants ──────────────────────────────────────────────── */
const TYPES = [
  { k: 'all',      label: 'Tất cả'     },
  { k: 'happy',    label: 'Happy path'  },
  { k: 'negative', label: 'Negative'    },
  { k: 'boundary', label: 'Boundary'    },
]

const TYPE_BADGE   = { happy: 'happy', negative: 'negative', boundary: 'boundary' }
const STATUS_BADGE = { pending: 'pending', passed: 'pass', failed: 'fail', running: 'running' }
const STATUS_LABEL = { pending: 'Chưa chạy', passed: 'Pass', failed: 'Fail', running: 'Đang chạy' }
const TYPE_LABEL   = { happy: 'Happy path', negative: 'Negative', boundary: 'Boundary' }

/* ── Helper: map backend TestCase → frontend row ────────────── */
function mapTc(tc) {
  return {
    id:      tc.id,
    code:    tc.tcCode || tc.id,
    name:    tc.name,
    type:    tc.testType === 'HAPPY_PATH' ? 'happy'
           : tc.testType === 'NEGATIVE'   ? 'negative' : 'boundary',
    status:  (tc.status || 'PENDING').toLowerCase(),
    useCase: tc.useCase?.name || 'N/A',
    steps:   tc.steps || [],
    expectedResult: tc.expectedResult || '',
  }
}

/* ── PAGE ────────────────────────────────────────────────────── */
export default function TestCasePage() {
  const navigate = useNavigate()

  // ── Diagram list
  const [diagrams,         setDiagrams]         = useState([])
  const [selectedDiagramId, setSelectedDiagramId] = useState(null)

  // ── Test case list
  const [allTc,    setAllTc]    = useState([])   // raw từ API (đã map)
  const [loading,  setLoading]  = useState(false)
  const [loadErr,  setLoadErr]  = useState(null)

  // ── Filters
  const [search,  setSearch]  = useState('')
  const [selUC,   setSelUC]   = useState('Tất cả')
  const [selType, setSelType] = useState('all')

  // ── Selection
  const [checked, setChecked] = useState(new Set())

  /* Load danh sách diagram khi mount */
  useEffect(() => {
    api.get('/api/diagrams')
      .then(res => {
        const list = res.data?.data || []
        setDiagrams(list)
        if (list.length > 0) {
          // Chọn diagram mới nhất mặc định
          setSelectedDiagramId(list[list.length - 1].id)
        }
      })
      .catch(() => setLoadErr('Không thể tải danh sách diagram'))
  }, [])

  /* Load test case khi đổi diagram */
  useEffect(() => {
    if (!selectedDiagramId) return
    setLoading(true)
    setLoadErr(null)
    setChecked(new Set())
    setAllTc([])
    setSelUC('Tất cả')

    api.get(`/api/diagrams/${selectedDiagramId}/testcases`)
      .then(res => {
        const raw = res.data?.data || []
        setAllTc(raw.map(mapTc))
      })
      .catch(() => setLoadErr('Không thể tải test case. Kiểm tra lại kết nối backend.'))
      .finally(() => setLoading(false))
  }, [selectedDiagramId])

  /* Build use-case sidebar list từ data */
  const useCaseList = useMemo(() => {
    const counts = {}
    allTc.forEach(tc => {
      counts[tc.useCase] = (counts[tc.useCase] || 0) + 1
    })
    return [
      { label: 'Tất cả', count: allTc.length },
      ...Object.entries(counts).map(([label, count]) => ({ label, count })),
    ]
  }, [allTc])

  /* Lọc */
  const filtered = useMemo(() => {
    const q = search.toLowerCase()
    return allTc.filter(tc => {
      const matchSearch = tc.name.toLowerCase().includes(q) || tc.code.toLowerCase().includes(q)
      const matchUC     = selUC   === 'Tất cả'  || tc.useCase === selUC
      const matchType   = selType === 'all'      || tc.type    === selType
      return matchSearch && matchUC && matchType
    })
  }, [allTc, search, selUC, selType])

  /* Selection helpers */
  const allChecked = filtered.length > 0 && filtered.every(tc => checked.has(tc.id))

  const toggleCheck = (id) => setChecked(prev => {
    const s = new Set(prev)
    s.has(id) ? s.delete(id) : s.add(id)
    return s
  })

  const toggleAll = () => {
    if (allChecked) setChecked(new Set())
    else            setChecked(new Set(filtered.map(tc => tc.id)))
  }

  /* Actions */
  const exportJSON = () => {
    const data = filtered.filter(tc => checked.size === 0 || checked.has(tc.id))
    const blob  = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url   = URL.createObjectURL(blob)
    const a     = document.createElement('a')
    a.href = url
    a.download = `test-cases-${selectedDiagramId?.slice(0, 8) || 'export'}.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  const runSelected = () => {
    const ids = checked.size > 0
      ? [...checked]
      : filtered.map(tc => tc.id)

    navigate('/run', {
      state: { ids, diagramId: selectedDiagramId },
    })
  }

  /* ── Render ── */
  return (
    <div className="tc-page">
      <PageHeader
        title="Danh sách Test Case"
        subtitle="Xem, lọc, chọn test case để chạy — dữ liệu thực từ backend"
        right={
          <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
            {diagrams.length > 0 && (
              <select
                className="diagram-select"
                value={selectedDiagramId || ''}
                onChange={e => setSelectedDiagramId(e.target.value)}
              >
                {diagrams.map(d => (
                  <option key={d.id} value={d.id}>{d.name}</option>
                ))}
              </select>
            )}
            <span className="total-pill">{allTc.length} test cases</span>
          </div>
        }
      />

      {loadErr && (
        <div className="tc-error-bar">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          {loadErr}
        </div>
      )}

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
              onChange={e => setSearch(e.target.value)}
            />
          </div>

          <SectionLabel>Lọc theo use case</SectionLabel>
          <div className="filter-group">
            {useCaseList.map(({ label, count }) => (
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
              <Button variant="secondary" size="sm" onClick={exportJSON} disabled={allTc.length === 0}>
                Export JSON
              </Button>
              <Button
                variant="primary"
                size="sm"
                onClick={runSelected}
                disabled={allTc.length === 0 || loading}
              >
                {checked.size > 0 ? `Chạy ${checked.size} test →` : 'Chạy tất cả →'}
              </Button>
            </div>
          </div>

          {/* Table */}
          <div className="table-scroll">
            {loading ? (
              <div className="tc-loading">
                <div className="tc-spinner" />
                <span>Đang tải test case...</span>
              </div>
            ) : (
              <table className="tc-table">
                <thead>
                  <tr>
                    <th style={{ width: 36 }}>
                      <input
                        type="checkbox"
                        className="row-check"
                        checked={allChecked}
                        onChange={toggleAll}
                        disabled={filtered.length === 0}
                      />
                    </th>
                    <th style={{ width: 80 }}>Mã</th>
                    <th>Tên test case</th>
                    <th style={{ width: 105 }}>Loại</th>
                    <th style={{ width: 100 }}>Trạng thái</th>
                    <th style={{ width: 130 }}>Use case</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(tc => (
                    <tr
                      key={tc.id}
                      className={checked.has(tc.id) ? 'row-checked' : ''}
                      onClick={() => toggleCheck(tc.id)}
                    >
                      <td onClick={e => e.stopPropagation()}>
                        <input
                          type="checkbox"
                          className="row-check"
                          checked={checked.has(tc.id)}
                          onChange={() => toggleCheck(tc.id)}
                        />
                      </td>
                      <td><span className="tc-code">{tc.code}</span></td>
                      <td><span className="tc-name">{tc.name}</span></td>
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
                      <td className="tc-actor">{tc.useCase}</td>
                    </tr>
                  ))}

                  {filtered.length === 0 && !loading && (
                    <tr>
                      <td colSpan={6} className="empty-row">
                        {diagrams.length === 0
                          ? 'Chưa có diagram nào. Hãy upload và sinh test case trước!'
                          : allTc.length === 0
                          ? 'Diagram này chưa có test case. Nhấn "Sinh test case" ở trang Upload!'
                          : 'Không tìm thấy test case phù hợp'}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}