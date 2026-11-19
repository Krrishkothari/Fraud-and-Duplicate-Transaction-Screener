import type { ReactNode } from 'react'

export type Tone = 'danger' | 'warning' | 'info' | 'success' | 'neutral'

interface BadgeProps {
  tone?: Tone
  /** A dot in the tone color before the text, for statuses and flag reasons. */
  dot?: boolean
  children: ReactNode
}

function Badge({ tone = 'neutral', dot = false, children }: BadgeProps) {
  return (
    <span className={`badge badge-${tone}`}>
      {dot && <span className="badge-dot" aria-hidden="true" />}
      {children}
    </span>
  )
}

export default Badge
