import { useState } from 'react'
import { PageHeader, Card, SectionLabel, Button } from '../components/ui'
import '../components/ui.css'
import './SettingsPage.css'

export default function SettingsPage() {
  const [apiUrl,     setApiUrl]     = useState('http://localhost:8080')
  const [timeout,    setTimeout_]   = useState('5000')
  const [apiKey,     setApiKey]     = useState('')
  const [model,      setModel]      = useState('claude-sonnet-4-20250514')
  const [language,   setLanguage]   = useState('vi')
  const [maxRetries, setMaxRetries] = useState('3')
  const [saved,      setSaved]      = useState(false)

  const handleSave = () => {
    setSaved(true)
    setTimeout(() => setSaved(false), 2000)
  }

  return (
    <div className="settings-page">
      <PageHeader
        title="Cài đặt"
        subtitle="Cấu hình kết nối backend, AI model và môi trường chạy test"
      />

      <div className="settings-grid">
        {/* Backend */}
        <Card>
          <SectionLabel>Kết nối Backend</SectionLabel>
          <div className="settings-form">
            <div className="sf-row">
              <label className="sf-label">Base URL</label>
              <input className="sf-input" value={apiUrl} onChange={e => setApiUrl(e.target.value)} placeholder="http://localhost:8080" />
            </div>
            <div className="sf-row">
              <label className="sf-label">Timeout (ms)</label>
              <input className="sf-input" value={timeout} onChange={e => setTimeout_(e.target.value)} type="number" />
            </div>
            <div className="sf-row">
              <label className="sf-label">Max retries</label>
              <input className="sf-input" value={maxRetries} onChange={e => setMaxRetries(e.target.value)} type="number" />
            </div>
          </div>
          <div className="settings-status">
            <span className="status-dot green" />
            <span className="status-txt">Kết nối OK · MySQL · Spring Boot 4.0.5</span>
          </div>
        </Card>

        {/* AI */}
        <Card>
          <SectionLabel>AI Model (Claude)</SectionLabel>
          <div className="settings-form">
            <div className="sf-row">
              <label className="sf-label">API Key</label>
              <input
                className="sf-input sf-mono"
                type="password"
                value={apiKey}
                onChange={e => setApiKey(e.target.value)}
                placeholder="sk-ant-..."
              />
            </div>
            <div className="sf-row">
              <label className="sf-label">Model</label>
              <select className="sf-select" value={model} onChange={e => setModel(e.target.value)}>
                <option value="claude-sonnet-4-20250514">Claude Sonnet 4 (khuyên dùng)</option>
                <option value="claude-opus-4-20250514">Claude Opus 4 (chính xác hơn)</option>
                <option value="claude-haiku-4-5-20251001">Claude Haiku 4.5 (nhanh hơn)</option>
              </select>
            </div>
            <div className="sf-row">
              <label className="sf-label">Ngôn ngữ output</label>
              <select className="sf-select" value={language} onChange={e => setLanguage(e.target.value)}>
                <option value="vi">Tiếng Việt</option>
                <option value="en">English</option>
              </select>
            </div>
          </div>
        </Card>

        {/* Selenium */}
        <Card>
          <SectionLabel>Selenium / WebDriver</SectionLabel>
          <div className="settings-form">
            <div className="sf-row">
              <label className="sf-label">Browser</label>
              <select className="sf-select">
                <option>Chrome (khuyên dùng)</option>
                <option>Firefox</option>
                <option>Edge</option>
              </select>
            </div>
            <div className="sf-row">
              <label className="sf-label">Headless mode</label>
              <label className="sf-toggle">
                <input type="checkbox" defaultChecked />
                <span>Chạy nền (không hiện browser)</span>
              </label>
            </div>
            <div className="sf-row">
              <label className="sf-label">Screenshot khi fail</label>
              <label className="sf-toggle">
                <input type="checkbox" defaultChecked />
                <span>Tự động chụp màn hình</span>
              </label>
            </div>
          </div>
        </Card>

        {/* Database */}
        <Card>
          <SectionLabel>Database</SectionLabel>
          <div className="settings-form">
            <div className="sf-row">
              <label className="sf-label">Host</label>
              <input className="sf-input sf-mono" defaultValue="localhost:3306" readOnly />
            </div>
            <div className="sf-row">
              <label className="sf-label">Database</label>
              <input className="sf-input sf-mono" defaultValue="testcase_db" readOnly />
            </div>
          </div>
          <div className="settings-status">
            <span className="status-dot green" />
            <span className="status-txt">MySQL 8.0 · Connected</span>
          </div>
        </Card>
      </div>

      <div className="settings-actions">
        <Button variant="primary" size="md" onClick={handleSave}>
          {saved ? '✓ Đã lưu!' : 'Lưu cài đặt'}
        </Button>
        <Button variant="secondary" size="md">Khôi phục mặc định</Button>
      </div>
    </div>
  )
}