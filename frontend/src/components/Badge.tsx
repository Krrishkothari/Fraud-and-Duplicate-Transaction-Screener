import type { ReactNode } from 'react'

interface BadgeProps {
  tone?: 'danger' | 'warning' | 'info' | 'neutral'
  children: ReactNode
}

function Badge({ tone = 'neutral', children }: BadgeProps) {
  return <span className={`badge badge-${tone}`}>{children}</span>
}

export default Badge
