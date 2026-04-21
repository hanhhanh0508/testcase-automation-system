/* ── PageHeader ─────────────────────────────────────────────── */
export function PageHeader({ title, subtitle, right }) {
  return (
    <div className="page-header">
      <div>
        <h1 className="page-title">{title}</h1>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {right && <div className="page-header-right">{right}</div>}
    </div>
  )
}

/* ── Badge ──────────────────────────────────────────────────── */
export function Badge({ variant = 'default', children, size = 'sm' }) {
  return (
    <span className={`badge badge-${variant} badge-size-${size}`}>
      {children}
    </span>
  )
}

/* ── Button ─────────────────────────────────────────────────── */
export function Button({ variant = 'secondary', size = 'md', children, disabled, onClick, className = '' }) {
  return (
    <button
      className={`btn btn-${variant} btn-${size} ${className}`}
      disabled={disabled}
      onClick={onClick}
    >
      {children}
    </button>
  )
}

/* ── Card ───────────────────────────────────────────────────── */
export function Card({ children, className = '', padding = true }) {
  return (
    <div className={`card ${padding ? 'card-padded' : ''} ${className}`}>
      {children}
    </div>
  )
}

/* ── SectionLabel ───────────────────────────────────────────── */
export function SectionLabel({ children }) {
  return <div className="section-label">{children}</div>
}

/* ── Spinner ────────────────────────────────────────────────── */
export function Spinner({ size = 20 }) {
  return (
    <div
      className="spinner"
      style={{ width: size, height: size }}
    />
  )
}