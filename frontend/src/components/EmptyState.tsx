import type { ReactNode } from 'react'

interface EmptyStateProps {
  title: string
  /** What the user can do next. */
  children?: ReactNode
  action?: ReactNode
}

function EmptyState({ title, children, action }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <p className="empty-state-title">{title}</p>
      {children && <div className="empty-state-body">{children}</div>}
      {action && <div className="empty-state-action">{action}</div>}
    </div>
  )
}

export default EmptyState
