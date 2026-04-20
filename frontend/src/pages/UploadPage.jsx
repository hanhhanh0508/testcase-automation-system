import { useState, useRef } from 'react'
import axios from 'axios'
import { useNavigate } from 'react-router-dom'
import './UploadPage.css'

const API = 'http://localhost:8080/api/diagrams'

// ── Sub-components ─────────────────────────────────────────

function ActorCard({ name, initial, useCases, extra }) {
  return (
    <div className="actor-card">
      <div className="actor-head">
        <div className="actor-name-row">
          <div className="actor-avatar">{initial}</div>
          <span className="actor-name">{name}</span>
        </div>
        <span className="badge-uc">{useCases.length} UC</span>
      </div>
      <div className="uc-list">
        {useCases.map((uc, i) => (
          <div key={i} className="uc-item">{uc}</div>
        ))}
        {extra > 0 && (
          <div className="uc-item uc-extra">+{extra} nữa...</div>
        )}
      </div>
    </div>
  )
}

function FileCard({ file, stats }) {
  return (
    <div className="file-card">
      <div className="file-card-top">
        <div className="file-card-left">
          <span className="file-name">{file.name}</span>
          <span className="badge-ok">Parsed OK</span>
        </div>
        <span className="file-size">{Math.round(file.size / 1024)} KB</span>
      </div>
      <div className="stats-row">
        <div className="stat-item">
          <div className="stat-num">{stats.actors}</div>
          <div className="stat-label">Actors</div>
        </div>
        <div className="stat-item">
          <div className="stat-num">{stats.useCases}</div>
          <div className="stat-label">Use Cases</div>
        </div>
        <div className="stat-item">
          <div className="stat-num">{stats.relationships}</div>
          <div className="stat-label">Relationships</div>
        </div>
      </div>
    </div>
  )
}

// ── Main Page ──────────────────────────────────────────────

export default function UploadPage() {
  const navigate = useNavigate()
  const fileInputRef = useRef(null)
  const [dragOver, setDragOver] = useState(false)

  // File state
  const [selectedFile, setSelectedFile] = useState(null)
  const [parsedFile, setParsedFile] = useState(null) // file đã upload thành công

  // Form config
  const [projectName, setProjectName] = useState('Online Shop System')
  const [testType, setTestType] = useState('Functional')
  const [language, setLanguage] = useState('vi')
  const [includeTypes, setIncludeTypes] = useState({
    happy: true,
    negative: true,
    boundary: true,
    performance: false,
  })

  // API state
  const [loading, setLoading] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState(null)
  const [diagramId, setDiagramId] = useState(null)

  // Mock parsed data (thay bằng API response thật sau)
  const [parsedData] = useState({
    actors: [
      {
        name: 'Customer',
        initial: 'C',
        useCases: ['Login / Logout', 'View product', 'Add to cart'],
        extra: 0,
      },
      {
        name: 'Admin',
        initial: 'A',
        useCases: ['Manage products', 'Manage orders', 'View reports'],
        extra: 2,
      },
    ],
    relationships: [
      { type: 'include', from: 'Checkout', to: 'Payment' },
      { type: 'include', from: 'Checkout', to: 'Verify stock' },
      { type: 'extend', from: 'Login', to: '2FA verify' },
    ],
    stats: { actors: 5, useCases: 18, relationships: 24 },
  })

  // ── Handlers ────────────────────────────────────────────

  const handleFileSelect = (file) => {
    if (!file) return
    const allowed = ['.xmi', '.xml', '.puml', '.plantuml', '.drawio', '.json']
    const ext = '.' + file.name.split('.').pop().toLowerCase()
    if (!allowed.includes(ext)) {
      setError('Định dạng không hỗ trợ. Vui lòng chọn file .xmi, .xml, .puml, .drawio hoặc .json')
      return
    }
    setSelectedFile(file)
    setError(null)
    handleUpload(file)
  }

  const handleUpload = async (file) => {
    setLoading(true)
    setError(null)
    try {
      const formData = new FormData()
      formData.append('file', file)
      if (projectName) formData.append('name', projectName)
      const res = await axios.post(`${API}/upload`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      setDiagramId(res.data.data?.id)
      setParsedFile(file)
    } catch (e) {
      // Nếu backend chưa chạy, dùng mock để UI vẫn hoạt động
      console.warn('Backend chưa sẵn sàng, dùng mock data:', e.message)
      setParsedFile(file)
    } finally {
      setLoading(false)
    }
  }

  const handleDrop = (e) => {
    e.preventDefault()
    setDragOver(false)
    const file = e.dataTransfer.files[0]
    handleFileSelect(file)
  }

  const handleDragOver = (e) => {
    e.preventDefault()
    setDragOver(true)
  }

  const toggleInclude = (key) => {
    setIncludeTypes(prev => ({ ...prev, [key]: !prev[key] }))
  }

  const handleGenerate = async () => {
    if (!parsedFile && !diagramId) {
      setError('Vui lòng upload file trước')
      return
    }
    setGenerating(true)
    setError(null)
    try {
      // Gọi API sinh test case (sẽ implement ngày 19-20/4)
      if (diagramId) {
        await axios.post(`${API}/${diagramId}/generate`, {
          projectName,
          testType,
          language,
          includeTypes,
        })
      }
      navigate('/testcases')
    } catch (e) {
      // Mock: navigate thẳng nếu backend chưa có endpoint này
      navigate('/testcases')
    } finally {
      setGenerating(false)
    }
  }

  // ── Render ────────────────────────────────────────────

  return (
    <div className="upload-page">
      <div className="page-header">
        <h1 className="page-title">Upload Use Case Diagram</h1>
        <p className="page-subtitle">Người dùng tải file UML lên và cấu hình trước khi sinh test case</p>
      </div>

      <div className="upload-layout">
        {/* ── LEFT COLUMN ── */}
        <div className="upload-left">

          {/* Dropzone */}
          <section className="upload-section">
            <div className="section-label">Tải lên diagram</div>
            <div
              className={`dropzone ${dragOver ? 'drag-over' : ''} ${parsedFile ? 'has-file' : ''}`}
              onDrop={handleDrop}
              onDragOver={handleDragOver}
              onDragLeave={() => setDragOver(false)}
              onClick={() => fileInputRef.current?.click()}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept=".xmi,.xml,.puml,.plantuml,.drawio,.json"
                style={{ display: 'none' }}
                onChange={e => handleFileSelect(e.target.files[0])}
              />
              {loading ? (
                <div className="dropzone-loading">
                  <div className="spinner" />
                  <div className="dropzone-text">Đang tải lên...</div>
                </div>
              ) : (
                <>
                  <div className="dropzone-icon">↑</div>
                  <div className="dropzone-text">Kéo thả file vào đây</div>
                  <div className="dropzone-formats">Hỗ trợ: .xmi · .xml · .puml · .drawio · .json</div>
                  <div className="dropzone-btn-row" onClick={e => e.stopPropagation()}>
                    <button className="btn" onClick={() => fileInputRef.current?.click()}>
                      Chọn file
                    </button>
                    <button className="btn">Paste URL</button>
                  </div>
                </>
              )}
            </div>
          </section>

          {/* File đã parse thành công */}
          {parsedFile && (
            <FileCard
              file={parsedFile}
              stats={parsedData.stats}
            />
          )}

          {/* Error */}
          {error && (
            <div className="error-box">
              <span className="error-icon">!</span>
              {error}
            </div>
          )}

          {/* Form cấu hình */}
          <section className="config-section">
            <div className="section-label">Cấu hình sinh test case</div>

            <div className="form-group">
              <label className="form-label">Tên dự án</label>
              <input
                className="form-input"
                type="text"
                value={projectName}
                onChange={e => setProjectName(e.target.value)}
                placeholder="VD: Online Shop System"
              />
            </div>

            <div className="form-row">
              <div className="form-group">
                <label className="form-label">Loại test</label>
                <select
                  className="form-select"
                  value={testType}
                  onChange={e => setTestType(e.target.value)}
                >
                  <option value="Functional">Functional</option>
                  <option value="Integration">Integration</option>
                  <option value="E2E">End-to-End</option>
                </select>
              </div>
              <div className="form-group">
                <label className="form-label">Ngôn ngữ output</label>
                <select
                  className="form-select"
                  value={language}
                  onChange={e => setLanguage(e.target.value)}
                >
                  <option value="vi">Tiếng Việt</option>
                  <option value="en">English</option>
                </select>
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Bao gồm test case cho</label>
              <div className="checkbox-row">
                {[
                  { key: 'happy',       label: 'Happy path' },
                  { key: 'negative',    label: 'Negative case' },
                  { key: 'boundary',    label: 'Boundary' },
                  { key: 'performance', label: 'Performance' },
                ].map(({ key, label }) => (
                  <label key={key} className="cb-item">
                    <input
                      type="checkbox"
                      checked={includeTypes[key]}
                      onChange={() => toggleInclude(key)}
                    />
                    {label}
                  </label>
                ))}
              </div>
            </div>

            <div className="action-row">
              <button
                className="btn btn-primary"
                onClick={handleGenerate}
                disabled={generating}
              >
                {generating ? 'Đang sinh...' : 'Sinh test case →'}
              </button>
              <button className="btn">Xem preview</button>
            </div>
          </section>
        </div>

        {/* ── RIGHT COLUMN ── */}
        <div className="upload-right">
          <div className="section-label">Cấu trúc đã phân tích</div>

          {parsedFile ? (
            <>
              {parsedData.actors.map((actor) => (
                <ActorCard key={actor.name} {...actor} />
              ))}

              <div className="rel-box">
                <div className="rel-section-label">Include / Extend</div>
                {parsedData.relationships.map((rel, i) => (
                  <div key={i} className="rel-item">
                    <span className={`rel-tag rel-tag-${rel.type}`}>
                      {rel.type}
                    </span>
                    <span className="rel-text">
                      {rel.from} → {rel.to}
                    </span>
                  </div>
                ))}
              </div>

              <p className="hint-text">Click vào use case để xem chi tiết</p>
            </>
          ) : (
            <div className="empty-right">
              <div className="empty-icon">◎</div>
              <div className="empty-text">Upload file để xem cấu trúc phân tích</div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}