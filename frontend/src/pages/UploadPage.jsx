import { useState, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { PageHeader, Card, SectionLabel, Button, Spinner } from '../components/ui'
import '../components/ui.css'
import './UploadPage.css'
import api from '../api'

/* ── helpers ─────────────────────────────────────────────────── */
function detectExt(filename = '') {
  const ext = filename.split('.').pop().toLowerCase()
  const map = { xmi: 'XMI', xml: 'XMI', puml: 'PlantUML', plantuml: 'PlantUML', drawio: 'DrawIO', json: 'JSON' }
  return map[ext] || 'Unknown'
}

/* ── sub-components ──────────────────────────────────────────── */
function StatPill({ n, label }) {
  return (
    <div className="stat-pill">
      <span className="stat-num">{n}</span>
      <span className="stat-lbl">{label}</span>
    </div>
  )
}

function ActorBlock({ name, initial, useCases, extra }) {
  return (
    <div className="actor-block">
      <div className="actor-head">
        <div className="actor-left">
          <div className="actor-avatar">{initial}</div>
          <span className="actor-name">{name}</span>
        </div>
        <span className="uc-count-badge">{useCases.length + (extra || 0)} UC</span>
      </div>
      <div className="uc-list">
        {useCases.map((uc, i) => <div key={i} className="uc-row">{uc}</div>)}
        {extra > 0 && <div className="uc-row uc-extra">+{extra} nữa...</div>}
      </div>
    </div>
  )
}

function RelBox({ relationships }) {
  const tagCls = { include: 'rel-inc', extend: 'rel-ext', generalization: 'rel-gen' }
  return (
    <div className="rel-box">
      <SectionLabel>Include / Extend</SectionLabel>
      {relationships.map((r, i) => (
        <div key={i} className="rel-row">
          <span className={`rel-tag ${tagCls[r.type] || 'rel-inc'}`}>«{r.type}»</span>
          <span className="rel-text">{r.from} → {r.to}</span>
        </div>
      ))}
    </div>
  )
}

/* ── MOCK PARSED (hiển thị demo cấu trúc, thực tế sẽ parse từ file) ── */
const MOCK_PARSED = {
  stats: { actors: 5, useCases: 18, relationships: 24 },
  actors: [
    { name: 'Customer', initial: 'C', useCases: ['Login / Logout', 'View product', 'Add to cart'], extra: 0 },
    { name: 'Admin',    initial: 'A', useCases: ['Manage products', 'Manage orders', 'View reports'], extra: 2 },
  ],
  relationships: [
    { type: 'include', from: 'Checkout',  to: 'Payment'      },
    { type: 'include', from: 'Checkout',  to: 'Verify stock'  },
    { type: 'extend',  from: 'Login',     to: '2FA verify'   },
  ],
}

/* ── MAIN PAGE ───────────────────────────────────────────────── */
export default function UploadPage() {
  const navigate = useNavigate()
  const fileRef  = useRef(null)

  const [dragOver,   setDragOver]   = useState(false)
  const [file,       setFile]       = useState(null)
  const [parsed,     setParsed]     = useState(null)
  const [loading,    setLoading]    = useState(false)
  const [generating, setGenerating] = useState(false)
  const [error,      setError]      = useState(null)
  const [diagramId,  setDiagramId]  = useState(null)
  const [genSuccess, setGenSuccess] = useState(false)

  /* config state */
  const [projectName, setProjectName] = useState('')
  const [testType,    setTestType]    = useState('Functional')
  const [language,    setLanguage]    = useState('vi')
  const [includes,    setIncludes]    = useState({
    happy: true, negative: true, boundary: true, performance: false,
  })

  /* ── doUpload — nằm TRONG component để dùng state ── */
  const doUpload = async (f) => {
    setLoading(true)
    setError(null)
    setParsed(null)
    setDiagramId(null)
    setGenSuccess(false)

    try {
      const fd = new FormData()
      fd.append('file', f)
      if (projectName.trim()) fd.append('name', projectName.trim())

      const res = await api.post('/api/diagrams/upload', fd)
      const data = res.data?.data
      if (data?.id) setDiagramId(data.id)
      setParsed(MOCK_PARSED)
    } catch (err) {
      // Backend offline hoặc lỗi → vẫn hiển thị mock để demo UI
      console.warn('Upload backend error:', err?.response?.data || err.message)
      setParsed(MOCK_PARSED)
      setError('Upload thất bại (backend offline?). Đang dùng dữ liệu demo.')
    } finally {
      setLoading(false)
    }
  }

  /* ── handleGenerate — nằm TRONG component ── */
  const handleGenerate = async () => {
    if (!parsed) { setError('Vui lòng upload file trước'); return }
    setGenerating(true)
    setError(null)

    try {
      if (diagramId) {
        await api.post(`/api/diagrams/${diagramId}/generate`)
        setGenSuccess(true)
      }
      // Chờ 500ms để user thấy feedback rồi chuyển trang
      setTimeout(() => navigate('/testcases'), 600)
    } catch (err) {
      setError('Sinh test case thất bại: ' + (err?.response?.data?.message || err.message))
      setGenerating(false)
    }
  }

  /* ── File handling ── */
  const ALLOWED = ['xmi', 'xml', 'puml', 'plantuml', 'drawio', 'json']

  const handleFile = (f) => {
    if (!f) return
    const ext = f.name.split('.').pop().toLowerCase()
    if (!ALLOWED.includes(ext)) {
      setError('Định dạng không hỗ trợ. Vui lòng chọn .xmi, .xml, .puml, .drawio hoặc .json')
      return
    }
    setFile(f)
    setError(null)
    doUpload(f)
  }

  const toggleInclude = (k) => setIncludes(p => ({ ...p, [k]: !p[k] }))

  /* ── Render ── */
  return (
    <div className="upload-page">
      <PageHeader
        title="Upload Use Case Diagram"
        subtitle="Tải file UML lên, cấu hình và sinh test case tự động"
      />

      <div className="upload-grid">
        {/* ── LEFT ── */}
        <div className="upload-left">

          {/* Dropzone */}
          <section>
            <SectionLabel>Tải lên diagram</SectionLabel>
            <div
              className={`dropzone ${dragOver ? 'dz-over' : ''} ${parsed ? 'dz-done' : ''}`}
              onDragOver={(e) => { e.preventDefault(); setDragOver(true) }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => { e.preventDefault(); setDragOver(false); handleFile(e.dataTransfer.files[0]) }}
              onClick={() => fileRef.current?.click()}
            >
              <input
                ref={fileRef}
                type="file"
                accept=".xmi,.xml,.puml,.plantuml,.drawio,.json"
                style={{ display: 'none' }}
                onChange={(e) => handleFile(e.target.files[0])}
              />
              {loading ? (
                <div className="dz-loading">
                  <Spinner size={24} />
                  <span className="dz-loading-text">Đang upload và phân tích file...</span>
                </div>
              ) : (
                <>
                  <div className="dz-icon-wrap">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                      <polyline points="17 8 12 3 7 8"/>
                      <line x1="12" y1="3" x2="12" y2="15"/>
                    </svg>
                  </div>
                  <div className="dz-title">
                    {parsed ? `✓ ${file?.name}` : 'Kéo thả file vào đây'}
                  </div>
                  <div className="dz-formats">Hỗ trợ: .xmi · .xml · .puml · .drawio · .json</div>
                  <div className="dz-btns" onClick={(e) => e.stopPropagation()}>
                    <Button variant="secondary" size="sm" onClick={() => fileRef.current?.click()}>
                      {parsed ? 'Chọn file khác' : 'Chọn file'}
                    </Button>
                  </div>
                </>
              )}
            </div>
          </section>

          {/* File parsed card */}
          {parsed && file && (
            <Card>
              <div className="fcard-top">
                <div className="fcard-left">
                  <div className="fcard-icon">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/>
                      <polyline points="13 2 13 9 20 9"/>
                    </svg>
                  </div>
                  <div>
                    <div className="fcard-name">{file.name}</div>
                    <div className="fcard-meta">{detectExt(file.name)} · {Math.round(file.size / 1024)} KB</div>
                  </div>
                </div>
                <span className="badge-parsed">Parsed OK</span>
              </div>
              <div className="stats-row">
                <StatPill n={parsed.stats.actors}        label="Actors"        />
                <StatPill n={parsed.stats.useCases}      label="Use Cases"     />
                <StatPill n={parsed.stats.relationships} label="Relationships" />
              </div>
            </Card>
          )}

          {/* Error */}
          {error && (
            <div className="error-bar">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="8"  x2="12"    y2="12"/>
                <line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
              {error}
            </div>
          )}

          {/* Config */}
          <Card>
            <SectionLabel>Cấu hình sinh test case</SectionLabel>

            <div className="form-group">
              <label className="form-label">Tên dự án</label>
              <input
                className="form-input"
                value={projectName}
                onChange={(e) => setProjectName(e.target.value)}
                placeholder="VD: Online Shop System"
              />
            </div>

            <div className="form-row-2">
              <div className="form-group">
                <label className="form-label">Loại test</label>
                <select className="form-select" value={testType} onChange={(e) => setTestType(e.target.value)}>
                  <option value="Functional">Functional</option>
                  <option value="Integration">Integration</option>
                  <option value="E2E">End-to-End</option>
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Ngôn ngữ output</label>
                <select className="form-select" value={language} onChange={(e) => setLanguage(e.target.value)}>
                  <option value="vi">Tiếng Việt</option>
                  <option value="en">English</option>
                </select>
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Bao gồm test case cho</label>
              <div className="cb-row">
                {[
                  { k: 'happy',       l: 'Happy path'    },
                  { k: 'negative',    l: 'Negative case' },
                  { k: 'boundary',    l: 'Boundary'      },
                  { k: 'performance', l: 'Performance'   },
                ].map(({ k, l }) => (
                  <label key={k} className="cb-item">
                    <input type="checkbox" checked={includes[k]} onChange={() => toggleInclude(k)} />
                    {l}
                  </label>
                ))}
              </div>
            </div>

            <div className="action-row">
              <Button
                variant="primary"
                size="md"
                disabled={!parsed || generating}
                onClick={handleGenerate}
              >
                {generating
                  ? <><Spinner size={13} /> Đang sinh...</>
                  : genSuccess
                  ? '✓ Thành công!'
                  : 'Sinh test case →'}
              </Button>
              <Button variant="secondary" size="md" disabled={!parsed}>
                Xem preview
              </Button>
            </div>
          </Card>
        </div>

        {/* ── RIGHT ── */}
        <div className="upload-right">
          <SectionLabel>Cấu trúc đã phân tích</SectionLabel>

          {parsed ? (
            <>
              {parsed.actors.map((a) => (
                <ActorBlock key={a.name} {...a} />
              ))}
              <RelBox relationships={parsed.relationships} />
              <p className="hint-text">Click vào use case để xem chi tiết</p>
            </>
          ) : (
            <div className="empty-right">
              <div className="empty-icon">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="11" cy="11" r="8"/>
                  <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                </svg>
              </div>
              <div className="empty-text">Upload file để xem cấu trúc phân tích</div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}