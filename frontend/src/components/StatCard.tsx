import type { ReactNode } from 'react'
import type { Tone } from './Badge'

interface StatCardProps {
  label: string
  value: ReactNode
  /** A short line under the value, such as a breakdown. */
  detail?: ReactNode
  /** A dot beside the label in a meaning color. */
  tone?: Tone
}

/** One headline number. Place StatCards inside a dl with the class stat-grid. */
function StatCard({ label, value, detail, tone }: StatCardProps) {
  return (
    <div className="stat-card">
      <dt className="stat-card-label">
        {tone && <span className={`dot dot-${tone}`} aria-hidden="true" />}
        {label}
      </dt>
      <dd className="stat-card-value mono">{value}</dd>
      {detail && <dd className="stat-card-detail">{detail}</dd>}
    </div>
  )
}

export default StatCard
