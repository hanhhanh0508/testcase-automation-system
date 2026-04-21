import { useState, useEffect, useRef } from 'react'
import { PageHeader, Badge, Button, Card, SectionLabel } from '../components/ui'
import '../components/ui.css'
import './RunTestPage.css'

/* ── MOCK DATA ───────────────────────────────────────────────── */
const QUEUE_ITEMS = [
  { id: 'TC-001', status: 'pass',   time: '1.2s' },
  { id: 'TC-002', status: 'pass',   time: '0.8s' },
  { id: 'TC-006', status: 'running', time: ''     },
  { id: 'TC-007', status: 'wait',   time: ''     },
  { id: 'TC-008', status: 'wait',   time: ''     },
  { id: 'TC-009', status: 'wait',   time: ''     },
]

const ACTIVE_TC = {
  id:      'TC-006',
  name:    'Checkout thành công',
  useCase: 'Checkout',
  actor:   'Customer',
  type:    'Happy path',
}

const STEPS_DATA = [
  {
    n: 1,
    title: 'Customer đã đăng nhập vào hệ thống',
    detail: 'Precondition: user.isLoggedIn = true',
    timing: 'Pass · 0.12s',
    status: 'done',
  },
  {
    n: 2,
    title: 'Customer nhấn "Checkout" với giỏ hàng có 3 sản phẩm',
    detail: 'Input cart = [{id:1,qty:2},{id:5,qty:1},{id:12,qty:3}]',
    timing: 'Pass · 0.08s',
    status: 'done',
  },
  {
    n: 3,
    title: 'Hệ thống gọi «include» VerifyStock — kiểm tra tồn kho',
    detail: 'Verify VerifyStock.execute(cartItems) · Stock OK',
    timing: 'Pass · 0.34s',
    status: 'done',
  },
  {
    n: 4,
    title: 'Hệ thống gọi «include» Payment — xử lý thanh toán',
    detail: 'Payment.process(amount=450000, method=CREDIT_CARD)',
    timing: '',
    status: 'running',
    progress: 60,
  },
  {
    n: 5,
    title: 'Hệ thống gửi order confirmation và email',
    detail: 'Expected: order.status = CONFIRMED; email.sent = true',
    timing: '',
    status: 'wait',
  },
  {
    n: 6,
    title: 'Postconditions: giỏ hàng bị xóa, hiển thị trang xác nhận',
    detail: 'Assert cart.isEmpty = true, page = confirm',
    timing: '',
    status: 'wait',
  },
]

const LOGS_INIT = [
  { time: '18:24:31', level: 'INFO',  msg: 'TC-006 started' },
  { time: '18:24:31', level: 'DEBUG', msg: 'login check = true' },
  { time: '18:24:32', level: 'DEBUG', msg: 'cart items: 3' },
  { time: '18:24:32', level: 'INFO',  msg: 'VerifyStock.execute() = OK' },
  { time: '18:24:33', level: 'INFO',  msg: 'Payment.process() = IN_PROGRESS...' },
]

/* ── HELPERS ─────────────────────────────────────────────────── */
function QueueItem({ id, status, time }) {
  const cfg = {
    pass:    { label: 'PASS', cls: 'qi-pass' },
    fail:    { label: 'FAIL', cls: 'qi-fail' },
    running: { label: 'RUN',  cls: 'qi-run'  },
    wait:    { label: 'WAIT', cls: 'qi-wait' },
  }
  const c = cfg[status] || cfg.wait
  return (
    <div className={`queue-item ${status === 'running' ? 'qi-active' : ''}`}>
      <span className={`qi-badge ${c.cls}`}>{c.label}</span>
      <span className="qi-id">{id}</span>
      <span className="qi-time">{time}</span>
    </div>
  )
}

function StepRow({ step }) {
  const isDone    = step.status === 'done'
  const isRunning = step.status === 'running'
  const isWait    = step.status === 'wait'
  return (
    <div className={`step-row ${isRunning ? 'step-running' : ''} ${isWait ? 'step-wait' : ''}`}>
      <div className={`step-dot ${isDone ? 'dot-done' : isRunning ? 'dot-run' : 'dot-wait'}`}>
        {isDone ? (
          <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
        ) : (
          step.n
        )}
      </div>
      <div className="step-body">
        <div className="step-title">{step.title}</div>
        <div className="step-detail">{step.detail}</div>
        {isRunning && (
          <div className="step-progress">
            <div className="step-bar" style={{ width: `${step.progress}%` }} />
          </div>
        )}
        {step.timing && (
          <div className="step-timing">{step.timing}</div>
        )}
      </div>
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
  const [paused,   setPaused]   = useState(false)
  const [progress, setProgress] = useState(33)
  const [logs,     setLogs]     = useState(LOGS_INIT)
  const logsRef = useRef(null)

  // simulate log ticking
  useEffect(() => {
    if (paused) return
    const id = setInterval(() => {
      const msgs = [
        'Processing payment gateway...',
        'Waiting for response...',
        'Gateway responded: PENDING',
        'Retrying (attempt 2/3)...',
      ]
      const rnd = msgs[Math.floor(Math.random() * msgs.length)]
      setLogs(p => [...p.slice(-30), { time: new Date().toTimeString().slice(0, 8), level: 'DEBUG', msg: rnd }])
      setProgress(p => Math.min(p + 1, 100))
    }, 1800)
    return () => clearInterval(id)
  }, [paused])

  useEffect(() => {
    if (logsRef.current) logsRef.current.scrollTop = logsRef.current.scrollHeight
  }, [logs])

  return (
    <div className="run-page">
      <PageHeader
        title="Chạy Test"
        subtitle="Thực thi test case, xem kết quả từng bước, báo cáo pass/fail"
        right={
          <Badge variant="running" size="md">TC-006 · In progress</Badge>
        }
      />

      <div className="run-layout">
        {/* ── COL 1: Queue ── */}
        <div className="run-col-queue">
          <Card>
            <SectionLabel>Hàng đợi chạy ({QUEUE_ITEMS.length})</SectionLabel>

            <div className="queue-progress-row">
              <span className="qp-label">Tiến độ</span>
              <span className="qp-val">2 / 6</span>
            </div>
            <div className="queue-bar-bg">
              <div className="queue-bar-fg" style={{ width: `${(2/6)*100}%` }} />
            </div>

            <div className="queue-list">
              {QUEUE_ITEMS.map(q => <QueueItem key={q.id} {...q} />)}
            </div>

            <div className="queue-actions">
              <Button
                variant={paused ? 'primary' : 'secondary'}
                size="sm"
                onClick={() => setPaused(p => !p)}
              >
                {paused ? '▶ Tiếp tục' : '⏸ Tạm dừng'}
              </Button>
              <Button variant="danger" size="sm">⏹ Dừng</Button>
            </div>
          </Card>
        </div>

        {/* ── COL 2: Steps + Logs ── */}
        <div className="run-col-main">
          <Card>
            <div className="active-tc-header">
              <div>
                <div className="active-tc-title">{ACTIVE_TC.id} — {ACTIVE_TC.name}</div>
                <div className="active-tc-meta">
                  Use case: {ACTIVE_TC.useCase} · Actor: {ACTIVE_TC.actor} · {ACTIVE_TC.type}
                </div>
              </div>
              <Badge variant="running">Đang chạy</Badge>
            </div>

            <SectionLabel>Các bước thực hiện</SectionLabel>

            <div className="steps-list">
              {STEPS_DATA.map(s => <StepRow key={s.n} step={s} />)}
            </div>
          </Card>

          <Card>
            <SectionLabel>Console log</SectionLabel>
            <div className="log-panel" ref={logsRef}>
              {logs.map((l, i) => <LogLine key={i} log={l} />)}
            </div>
          </Card>
        </div>

        {/* ── COL 3: Results ── */}
        <div className="run-col-results">
          <Card>
            <SectionLabel>Kết quả hiện tại</SectionLabel>
            <div className="result-grid">
              <div className="result-cell rc-pass">
                <span className="rc-num">2</span>
                <span className="rc-lbl">Pass</span>
              </div>
              <div className="result-cell rc-fail">
                <span className="rc-num">0</span>
                <span className="rc-lbl">Fail</span>
              </div>
              <div className="result-cell rc-run">
                <span className="rc-num">1</span>
                <span className="rc-lbl">Running</span>
              </div>
              <div className="result-cell rc-pending">
                <span className="rc-num">3</span>
                <span className="rc-lbl">Pending</span>
              </div>
            </div>

            <div className="pass-rate-section">
              <div className="pass-rate-row">
                <span className="pr-label">Pass rate</span>
                <span className="pr-val">{Math.round((2/6)*100)}%</span>
              </div>
              <div className="pr-bar-bg">
                <div className="pr-bar-fg" style={{ width: `${(2/6)*100}%` }} />
              </div>
              <div className="pr-note">2 / 6 hoàn thành</div>
            </div>
          </Card>

          <Card>
            <SectionLabel>Chi tiết lỗi</SectionLabel>
            <div className="no-error">Chưa có lỗi nào ✓</div>
          </Card>

          <Card>
            <SectionLabel>Báo cáo</SectionLabel>
            <div className="report-btns">
              <Button variant="secondary" size="sm" className="full-w">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                </svg>
                Export PDF report
              </Button>
              <Button variant="secondary" size="sm" className="full-w">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                  <polyline points="7 10 12 15 17 10"/>
                  <line x1="12" y1="15" x2="12" y2="3"/>
                </svg>
                Export Excel
              </Button>
              <Button variant="secondary" size="sm" className="full-w">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/>
                  <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
                  <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
                </svg>
                Share link
              </Button>
            </div>
          </Card>

          <Card>
            <SectionLabel>Cấu hình môi trường</SectionLabel>
            <div className="env-form">
              <label className="env-label">Base URL</label>
              <input className="env-input" defaultValue="http://localhost:8080" />
              <label className="env-label">Timeout (ms)</label>
              <input className="env-input" defaultValue="5000" type="number" />
            </div>
          </Card>
        </div>
      </div>
    </div>
  )
}