import { useState, useEffect, useRef, useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { PageHeader, Badge, Button, Card, SectionLabel } from '../components/ui'
import '../components/ui.css'
import './RunTestPage.css'
import api from '../api'

/* ── helpers ─────────────────────────────────────────────────── */
const statusBadge = (s) => {
  if (s === 'passed')  return 'pass'
  if (s === 'failed')  return 'fail'
  if (s === 'running') return 'running'
  return 'pending'
}

const statusLabel = { pending: 'Chờ', running: 'Đang chạy', passed: 'Pass', failed: 'Fail' }

function QueueItem({ code, name, status, durationMs }) {
  const cfg = {
    passed:  { label: 'PASS', cls: 'qi-pass' },
    failed:  { label: 'FAIL', cls: 'qi-fail' },
    running: { label: 'RUN',  cls: 'qi-run'  },
    pending: { label: 'WAIT', cls: 'qi-wait' },
  }
  const c = cfg[status] || cfg.pending
  return (
    <div className={`queue-item ${status === 'running' ? 'qi-active' : ''}`}>
      <span className={`qi-badge ${c.cls}`}>{c.label}</span>
      <span className="qi-id">{code || '...'}</span>
      {durationMs != null && (
        <span className="qi-time">{(durationMs / 1000).toFixed(1)}s</span>
      )}
    </div>
  )
}

function LogLine({ log }) {
  const lvlCls = { INFO: 'log-info', DEBUG: 'log-debug', ERROR: 'log-error', WARN: 'log-warn' }
  return (
    <div className="log-line">
      <span className="log-time">[{log.time}]</span>
      <span className={`log-level ${lvlCls[log.level] || ''}`}>{log.level}</span>
      <span className="log-msg">{log.msg}</span>
    </div>
  )
}

/* ── PAGE ────────────────────────────────────────────────────── */
export default function RunTestPage() {
  const location   = useLocation()
  const navigate   = useNavigate()
  const logsRef    = useRef(null)

  // Danh sách id từ TestCasePage (state: {ids, diagramId})
  const idsFromNav    = location.state?.ids || []
  const diagramIdNav  = location.state?.diagramId || null

  // ── Queue state ──
  // Mỗi item: { id, code, name, status, durationMs, result }
  const [queue, setQueue] = useState([])

  // Đang chạy item nào (index trong queue)
  const [runningIdx, setRunningIdx] = useState(-1)

  // Trạng thái tổng
  const [running,   setRunning]   = useState(false)
  const [paused,    setPaused]    = useState(false)
  const [finished,  setFinished]  = useState(false)
  const pausedRef = useRef(false)

  // Logs
  const [logs, setLogs] = useState([])

  // Kết quả hiện tại
  const [currentResult, setCurrentResult] = useState(null)

  // ── Load test case info khi mount ──
  useEffect(() => {
    if (idsFromNav.length === 0) return

    // Nếu có diagramId, load từ /api/diagrams/{id}/testcases
    // Ngược lại gọi batch
    const loadFn = diagramIdNav
      ? api.get(`/api/diagrams/${diagramIdNav}/testcases`)
      : api.post('/api/testcases/batch', { ids: idsFromNav })

    loadFn.then(res => {
      const raw = res.data?.data || []
      // Lọc chỉ lấy các id được chọn
      const selected = diagramIdNav
        ? raw.filter(tc => idsFromNav.includes(tc.id))
        : raw

      setQueue(selected.map(tc => ({
        id:         tc.id,
        code:       tc.tcCode || tc.id.slice(0, 8),
        name:       tc.name,
        steps:      tc.steps || [],
        expected:   tc.expectedResult || '',
        useCase:    tc.useCase?.name || '',
        testType:   tc.testType,
        status:     'pending',
        durationMs: null,
        result:     null,
      })))
    }).catch(() => {
      addLog('ERROR', 'Không thể tải danh sách test case')
    })
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // ── Scroll logs ──
  useEffect(() => {
    if (logsRef.current) logsRef.current.scrollTop = logsRef.current.scrollHeight
  }, [logs])

  const addLog = useCallback((level, msg) => {
    const time = new Date().toTimeString().slice(0, 8)
    setLogs(p => [...p.slice(-80), { time, level, msg }])
  }, [])

  // ── Cập nhật 1 item trong queue ──
  const updateQueue = useCallback((id, patch) => {
    setQueue(q => q.map(item => item.id === id ? { ...item, ...patch } : item))
  }, [])

  // ── Chạy từng TC tuần tự ──
  const startRun = useCallback(async () => {
    if (queue.length === 0) return
    setRunning(true)
    setFinished(false)
    setPaused(false)
    pausedRef.current = false
    setCurrentResult(null)

    addLog('INFO', `Bắt đầu chạy ${queue.length} test case...`)

    for (let i = 0; i < queue.length; i++) {
      // Chờ nếu đang pause
      while (pausedRef.current) {
        await new Promise(r => setTimeout(r, 300))
      }

      const item = queue[i]
      setRunningIdx(i)
      updateQueue(item.id, { status: 'running' })
      addLog('INFO', `[${item.code}] Bắt đầu: ${item.name}`)

      try {
        const res = await api.post(`/api/execute/${item.id}`)
        const result = res.data?.data   // TestResult object

        const outcome = result?.outcome?.toLowerCase() || 'failed'
        const status  = outcome === 'passed' ? 'passed' : 'failed'
        const ms      = result?.durationMs || 0

        updateQueue(item.id, { status, durationMs: ms, result })
        setCurrentResult({ ...item, status, result })
        addLog(status === 'passed' ? 'INFO' : 'ERROR',
          `[${item.code}] ${status.toUpperCase()} (${(ms/1000).toFixed(2)}s)`)

        if (result?.errorMessage) {
          addLog('ERROR', `  ↳ ${result.errorMessage}`)
        }
        if (result?.actualResult) {
          // Log từng dòng của actualResult
          result.actualResult.split('\n').forEach(line => {
            if (line.trim()) addLog('DEBUG', `  ${line}`)
          })
        }
      } catch (err) {
        const errMsg = err?.response?.data?.message || err.message || 'Unknown error'
        updateQueue(item.id, { status: 'failed', durationMs: 0 })
        addLog('ERROR', `[${item.code}] ERROR: ${errMsg}`)
      }

      // Delay nhỏ giữa các TC
      await new Promise(r => setTimeout(r, 400))
    }

    setRunning(false)
    setFinished(true)
    setRunningIdx(-1)
    addLog('INFO', '─── Hoàn thành tất cả test case ───')
  }, [queue, updateQueue, addLog])

  const handlePauseResume = () => {
    const next = !paused
    setPaused(next)
    pausedRef.current = next
    addLog('INFO', next ? '⏸ Tạm dừng' : '▶ Tiếp tục')
  }

  const handleStop = () => {
    // Không thể thực sự dừng giữa chừng khi đang gọi API,
    // nhưng set pause để dừng vòng lặp sau TC hiện tại
    pausedRef.current = true
    setPaused(true)
    setRunning(false)
    setFinished(true)
    addLog('WARN', '⏹ Đã dừng bởi người dùng')
  }

  // ── Summary ──
  const summary = {
    total:   queue.length,
    passed:  queue.filter(t => t.status === 'passed').length,
    failed:  queue.filter(t => t.status === 'failed').length,
    running: queue.filter(t => t.status === 'running').length,
    pending: queue.filter(t => t.status === 'pending').length,
  }
  const passRate = summary.total > 0
    ? Math.round(((summary.passed) / summary.total) * 100)
    : 0
  const progress = summary.total > 0
    ? Math.round(((summary.passed + summary.failed) / summary.total) * 100)
    : 0

  const activeItem = runningIdx >= 0 ? queue[runningIdx] : null
  const errors     = queue.filter(t => t.status === 'failed')

  return (
    <div className="run-page">
      <PageHeader
        title="Chạy Test"
        subtitle="Thực thi test case với Selenium WebDriver, xem kết quả từng bước"
        right={
          running
            ? <Badge variant="running" size="md">
                {activeItem?.code || 'Đang chạy'} · In progress
              </Badge>
            : finished
            ? <Badge variant={summary.failed === 0 ? 'pass' : 'fail'} size="md">
                {summary.failed === 0 ? '✓ Hoàn thành' : `${summary.failed} lỗi`}
              </Badge>
            : null
        }
      />

      {/* Nếu không có TC nào được chọn */}
      {queue.length === 0 && (
        <Card>
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
            <p style={{ marginBottom: 12 }}>Chưa có test case nào được chọn.</p>
            <Button variant="secondary" size="md" onClick={() => navigate('/testcases')}>
              ← Quay lại chọn test case
            </Button>
          </div>
        </Card>
      )}

      {queue.length > 0 && (
        <div className="run-layout">
          {/* ── COL 1: Queue ── */}
          <div className="run-col-queue">
            <Card>
              <SectionLabel>Hàng đợi ({queue.length})</SectionLabel>

              <div className="queue-progress-row">
                <span className="qp-label">Tiến độ</span>
                <span className="qp-val">{summary.passed + summary.failed} / {summary.total}</span>
              </div>
              <div className="queue-bar-bg">
                <div className="queue-bar-fg" style={{ width: `${progress}%` }} />
              </div>

              <div className="queue-list">
                {queue.map(item => (
                  <QueueItem key={item.id} {...item} />
                ))}
              </div>

              <div className="queue-actions">
                {!running && !finished && (
                  <Button variant="primary" size="sm" onClick={startRun}
                    style={{ flex: 1, justifyContent: 'center' }}>
                    ▶ Bắt đầu
                  </Button>
                )}
                {running && (
                  <>
                    <Button
                      variant={paused ? 'primary' : 'secondary'}
                      size="sm"
                      onClick={handlePauseResume}
                    >
                      {paused ? '▶ Tiếp tục' : '⏸ Dừng'}
                    </Button>
                    <Button variant="danger" size="sm" onClick={handleStop}>⏹</Button>
                  </>
                )}
                {finished && (
                  <Button variant="secondary" size="sm"
                    onClick={() => navigate('/testcases')}
                    style={{ flex: 1, justifyContent: 'center' }}>
                    ← Quay lại
                  </Button>
                )}
              </div>
            </Card>
          </div>

          {/* ── COL 2: Active TC + Logs ── */}
          <div className="run-col-main">
            <Card>
              {activeItem ? (
                <>
                  <div className="active-tc-header">
                    <div>
                      <div className="active-tc-title">
                        {activeItem.code} — {activeItem.name}
                      </div>
                      <div className="active-tc-meta">
                        Use case: {activeItem.useCase} · {activeItem.testType}
                      </div>
                    </div>
                    <Badge variant="running">Đang chạy</Badge>
                  </div>

                  <SectionLabel>Các bước ({activeItem.steps.length})</SectionLabel>
                  <div className="steps-list">
                    {activeItem.steps.map((step, i) => (
                      <div key={i} className="step-row">
                        <div className="step-dot dot-run">{i + 1}</div>
                        <div className="step-body">
                          <div className="step-title">{step}</div>
                        </div>
                      </div>
                    ))}
                    {activeItem.steps.length === 0 && (
                      <p style={{ fontSize: 12, color: 'var(--text-muted)', padding: '8px 0' }}>
                        Đang thực thi...
                      </p>
                    )}
                  </div>
                </>
              ) : currentResult ? (
                /* Hiển thị kết quả TC vừa xong */
                <div>
                  <div className="active-tc-header">
                    <div>
                      <div className="active-tc-title">
                        {currentResult.code} — {currentResult.name}
                      </div>
                      <div className="active-tc-meta">Use case: {currentResult.useCase}</div>
                    </div>
                    <Badge variant={statusBadge(currentResult.status)} size="md">
                      {statusLabel[currentResult.status] || currentResult.status}
                    </Badge>
                  </div>
                  {currentResult.result?.errorMessage && (
                    <div style={{
                      background: 'var(--danger-bg)', border: '1px solid #fecaca',
                      borderRadius: 'var(--radius-md)', padding: '10px 14px',
                      fontSize: 12, color: 'var(--danger)', lineHeight: 1.55
                    }}>
                      <strong>Lỗi:</strong> {currentResult.result.errorMessage}
                    </div>
                  )}
                  {!running && !finished && (
                    <p style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 12 }}>
                      Nhấn "Bắt đầu" để chạy test case.
                    </p>
                  )}
                </div>
              ) : (
                <div style={{ padding: '20px 0', textAlign: 'center', color: 'var(--text-muted)', fontSize: 12 }}>
                  {finished ? 'Đã hoàn thành tất cả test case.' : 'Nhấn "Bắt đầu" để chạy.'}
                </div>
              )}
            </Card>

            {/* Console */}
            <Card>
              <SectionLabel>Console log</SectionLabel>
              <div className="log-panel" ref={logsRef}>
                {logs.length === 0 && (
                  <span style={{ color: '#484f58' }}>Chờ chạy test...</span>
                )}
                {logs.map((l, i) => <LogLine key={i} log={l} />)}
              </div>
            </Card>
          </div>

          {/* ── COL 3: Results ── */}
          <div className="run-col-results">
            <Card>
              <SectionLabel>Kết quả</SectionLabel>
              <div className="result-grid">
                <div className="result-cell rc-pass">
                  <span className="rc-num">{summary.passed}</span>
                  <span className="rc-lbl">Pass</span>
                </div>
                <div className="result-cell rc-fail">
                  <span className="rc-num">{summary.failed}</span>
                  <span className="rc-lbl">Fail</span>
                </div>
                <div className="result-cell rc-run">
                  <span className="rc-num">{summary.running}</span>
                  <span className="rc-lbl">Running</span>
                </div>
                <div className="result-cell rc-pending">
                  <span className="rc-num">{summary.pending}</span>
                  <span className="rc-lbl">Pending</span>
                </div>
              </div>

              <div className="pass-rate-section">
                <div className="pass-rate-row">
                  <span className="pr-label">Pass rate</span>
                  <span className="pr-val">{passRate}%</span>
                </div>
                <div className="pr-bar-bg">
                  <div className="pr-bar-fg" style={{ width: `${passRate}%` }} />
                </div>
                <div className="pr-note">{summary.passed + summary.failed} / {summary.total} hoàn thành</div>
              </div>
            </Card>

            {/* Errors */}
            <Card>
              <SectionLabel>Chi tiết lỗi ({errors.length})</SectionLabel>
              {errors.length === 0 ? (
                <div className="no-error">Chưa có lỗi nào ✓</div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {errors.map(e => (
                    <div key={e.id} style={{
                      background: 'var(--danger-bg)', border: '1px solid #fecaca',
                      borderRadius: 'var(--radius-sm)', padding: '8px 10px'
                    }}>
                      <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--danger)', fontFamily: 'var(--font-mono)', marginBottom: 3 }}>
                        {e.code}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--danger)', lineHeight: 1.4 }}>
                        {e.result?.errorMessage
                          ? e.result.errorMessage.slice(0, 120) + (e.result.errorMessage.length > 120 ? '...' : '')
                          : 'Thực thi thất bại'}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Card>

            {/* Export */}
            <Card>
              <SectionLabel>Báo cáo</SectionLabel>
              <div className="report-btns">
                <Button
                  variant="secondary" size="sm" className="full-w"
                  disabled={!finished}
                  onClick={() => {
                    const data = queue.map(t => ({
                      code: t.code, name: t.name,
                      status: t.status, durationMs: t.durationMs,
                      error: t.result?.errorMessage || null,
                    }))
                    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
                    const url  = URL.createObjectURL(blob)
                    const a    = document.createElement('a')
                    a.href = url; a.download = `run-report-${Date.now()}.json`; a.click()
                    URL.revokeObjectURL(url)
                  }}
                >
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                    <polyline points="7 10 12 15 17 10"/>
                    <line x1="12" y1="15" x2="12" y2="3"/>
                  </svg>
                  Export JSON report
                </Button>
              </div>
            </Card>
          </div>
        </div>
      )}
    </div>
  )
}