import { useState, useMemo, useEffect, useCallback } from 'react'
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

function mapTc(tc) {
  return {
    id:             tc.id,
    code:           tc.tcCode || tc.id,
    name:           tc.name,
    type:           tc.testType === 'HAPPY_PATH' ? 'happy'
                  : tc.testType === 'NEGATIVE'   ? 'negative' : 'boundary',
    status:         (tc.status || 'PENDING').toLowerCase(),
    useCase:        tc.useCase?.name || 'N/A',
    steps:          tc.steps || [],
    expectedResult: tc.expectedResult || '',
    createdAt:      tc.createdAt || '',
  }
}

/* ── Modal chi tiết test case ────────────────────────────────── */
function DetailModal({ tc, onClose, onRun }) {
  if (!tc) return null

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-box" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="modal-header">
          <div className="modal-title-row">
            <span className="modal-tc-code">{tc.code}</span>
            <Badge variant={TYPE_BADGE[tc.type] || 'default'} size="md">
              {TYPE_LABEL[tc.type] || tc.type}
            </Badge>
            <Badge variant={STATUS_BADGE[tc.status] || 'default'} size="md">
              {STATUS_LABEL[tc.status] || tc.status}
            </Badge>
          </div>
          <h2 className="modal-tc-name">{tc.name}</h2>
          <p className="modal-tc-usecase">Use case: {tc.useCase}</p>
          <button className="modal-close-btn" onClick={onClose} aria-label="Đóng">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>

        {/* Steps */}
        <div className="modal-body">
          <SectionLabel>Các bước thực hiện ({tc.steps.length})</SectionLabel>
          {tc.steps.length > 0 ? (
            <ol className="modal-steps">
              {tc.steps.map((step, i) => (
                <li key={i} className="modal-step-item">
                  <span className="modal-step-num">{i + 1}</span>
                  <span className="modal-step-text">{step}</span>
                </li>
              ))}
            </ol>
          ) : (
            <p className="modal-empty">Chưa có bước nào được định nghĩa.</p>
          )}

          {tc.expectedResult && (
            <div className="modal-expected">
              <SectionLabel>Kết quả kỳ vọng</SectionLabel>
              <p className="modal-expected-text">{tc.expectedResult}</p>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="modal-footer">
          <Button variant="secondary" size="md" onClick={onClose}>Đóng</Button>
          <Button variant="primary" size="md" onClick={() => { onRun(tc); onClose(); }}>
            <svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor">
              <polygon points="5 3 19 12 5 21 5 3"/>
            </svg>
            Chạy test này
          </Button>
        </div>
      </div>
    </div>
  )
}

/* ── Generate toast ──────────────────────────────────────────── */
function Toast({ msg, type, onClose }) {
  useEffect(() => {
    const t = setTimeout(onClose, 3500)
    return () => clearTimeout(t)
  }, [onClose])

  return (
    <div className={`toast toast-${type}`}>
      {type === 'success'
        ? <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
        : <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
      }
      {msg}
    </div>
  )
}

/* ── PAGE ────────────────────────────────────────────────────── */
export default function TestCasePage() {
  const navigate = useNavigate()

  // Diagram
  const [diagrams,          setDiagrams]          = useState([])
  const [selectedDiagramId, setSelectedDiagramId] = useState(null)

  // Test cases
  const [allTc,    setAllTc]    = useState([])
  const [loading,  setLoading]  = useState(false)
  const [loadErr,  setLoadErr]  = useState(null)

  // Generating
  const [generating, setGenerating] = useState(false)

  // Filters
  const [search,  setSearch]  = useState('')
  const [selUC,   setSelUC]   = useState('Tất cả')
  const [selType, setSelType] = useState('all')

  // Selection
  const [checked, setChecked] = useState(new Set())

  // Modal
  const [detailTc, setDetailTc] = useState(null)

  // Toast
  const [toast, setToast] = useState(null)

  const showToast = (msg, type = 'success') => setToast({ msg, type })

  /* Load diagrams */
  useEffect(() => {
    api.get('/api/diagrams')
      .then(res => {
        const list = res.data?.data || []
        setDiagrams(list)
        if (list.length > 0) setSelectedDiagramId(list[list.length - 1].id)
      })
      .catch(() => setLoadErr('Không thể tải danh sách diagram'))
  }, [])

  /* Load test cases khi đổi diagram */
  const loadTestCases = useCallback((diagramId) => {
    if (!diagramId) return
    setLoading(true)
    setLoadErr(null)
    setChecked(new Set())
    setAllTc([])
    setSelUC('Tất cả')

    api.get(`/api/diagrams/${diagramId}/testcases`)
      .then(res => {
        const raw = res.data?.data || []
        setAllTc(raw.map(mapTc))
      })
      .catch(() => setLoadErr('Không thể tải test case. Kiểm tra lại kết nối backend.'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    loadTestCases(selectedDiagramId)
  }, [selectedDiagramId, loadTestCases])

  /* Sinh test case */
  const handleGenerate = async () => {
    if (!selectedDiagramId) { showToast('Chưa chọn diagram', 'error'); return }
    setGenerating(true)
    try {
      const res = await api.post(`/api/diagrams/${selectedDiagramId}/generate`)
      const count = res.data?.data?.length || 0
      showToast(`Sinh thành công ${count} test case!`)
      loadTestCases(selectedDiagramId)  // reload
    } catch (err) {
      const msg = err?.response?.data?.message || err.message
      showToast('Sinh thất bại: ' + msg, 'error')
    } finally {
      setGenerating(false)
    }
  }

  /* Sidebar use-case list */
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

  /* Filter */
  const filtered = useMemo(() => {
    const q = search.toLowerCase()
    return allTc.filter(tc => {
      const matchSearch = tc.name.toLowerCase().includes(q) || tc.code.toLowerCase().includes(q)
      const matchUC     = selUC   === 'Tất cả'  || tc.useCase === selUC
      const matchType   = selType === 'all'      || tc.type    === selType
      return matchSearch && matchUC && matchType
    })
  }, [allTc, search, selUC, selType])

  /* Selection */
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

  /* Export JSON */
  const exportJSON = () => {
    const data = filtered.filter(tc => checked.size === 0 || checked.has(tc.id))
    const blob  = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url   = URL.createObjectURL(blob)
    const a     = document.createElement('a'); a.href = url
    a.download  = `testcases-${selectedDiagramId?.slice(0, 8) || 'export'}.json`
    a.click(); URL.revokeObjectURL(url)
  }

  /* Run */
  const runSelected = (singleTc = null) => {
    const ids = singleTc
      ? [singleTc.id]
      : checked.size > 0
        ? [...checked]
        : filtered.map(tc => tc.id)

    navigate('/run', { state: { ids, diagramId: selectedDiagramId } })
  }

  /* Stats bar */
  const stats = useMemo(() => ({
    total:   allTc.length,
    pending: allTc.filter(t => t.status === 'pending').length,
    passed:  allTc.filter(t => t.status === 'passed').length,
    failed:  allTc.filter(t => t.status === 'failed').length,
  }), [allTc])

  return (
    <div className="tc-page">
      {/* Toast */}
      {toast && (
        <Toast msg={toast.msg} type={toast.type} onClose={() => setToast(null)} />
      )}

      {/* Detail modal */}
      <DetailModal
        tc={detailTc}
        onClose={() => setDetailTc(null)}
        onRun={runSelected}
      />

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
            <Button
              variant="primary"
              size="sm"
              disabled={!selectedDiagramId || generating}
              onClick={handleGenerate}
            >
              {generating ? (
                <>
                  <span className="tc-spinner" style={{ width: 11, height: 11, borderWidth: 2 }} />
                  Đang sinh...
                </>
              ) : (
                <>
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M12 5v14M5 12l7-7 7 7"/>
                  </svg>
                  Sinh test case
                </>
              )}
            </Button>
            <span className="total-pill">{allTc.length} test cases</span>
          </div>
        }
      />

      {/* Stats bar */}
      {allTc.length > 0 && (
        <div className="tc-stats-bar">
          <div className="tc-stat-item">
            <span className="ts-num">{stats.total}</span>
            <span className="ts-lbl">Tổng</span>
          </div>
          <div className="tc-stat-divider" />
          <div className="tc-stat-item tc-stat-pending">
            <span className="ts-num">{stats.pending}</span>
            <span className="ts-lbl">Chưa chạy</span>
          </div>
          <div className="tc-stat-divider" />
          <div className="tc-stat-item tc-stat-pass">
            <span className="ts-num">{stats.passed}</span>
            <span className="ts-lbl">Pass</span>
          </div>
          <div className="tc-stat-divider" />
          <div className="tc-stat-item tc-stat-fail">
            <span className="ts-num">{stats.failed}</span>
            <span className="ts-lbl">Fail</span>
          </div>
          {stats.total > 0 && (
            <>
              <div className="tc-stat-divider" />
              <div className="tc-pass-rate">
                <div className="tc-pr-bar-bg">
                  <div
                    className="tc-pr-bar-fg"
                    style={{ width: `${Math.round((stats.passed / stats.total) * 100)}%` }}
                  />
                </div>
                <span className="tc-pr-pct">
                  {Math.round((stats.passed / stats.total) * 100)}% pass rate
                </span>
              </div>
            </>
          )}
        </div>
      )}

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
                onClick={() => runSelected()}
                disabled={allTc.length === 0 || loading}
              >
                {checked.size > 0 ? `Chạy ${checked.size} test →` : 'Chạy tất cả →'}
              </Button>
            </div>
          </div>

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
                    <th style={{ width: 80, textAlign: 'center' }}>Chi tiết</th>
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
                      <td
                        style={{ textAlign: 'center' }}
                        onClick={e => { e.stopPropagation(); setDetailTc(tc) }}
                      >
                        <button className="detail-btn" title="Xem chi tiết">
                          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                            <circle cx="12" cy="12" r="3"/>
                          </svg>
                        </button>
                      </td>
                    </tr>
                  ))}

                  {filtered.length === 0 && !loading && (
                    <tr>
                      <td colSpan={7} className="empty-row">
                        {diagrams.length === 0
                          ? 'Chưa có diagram nào. Hãy upload và sinh test case trước!'
                          : allTc.length === 0
                          ? 'Diagram này chưa có test case. Nhấn "Sinh test case" ở trên!'
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