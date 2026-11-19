import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

interface EmptyStateProps {
  icon?: LucideIcon
  title: string
  /** What the user can do next. */
  children?: ReactNode
  action?: ReactNode
}

function EmptyState({ icon: Icon, title, children, action }: EmptyStateProps) {
  return (
    <div className="empty-state">
      {Icon && (
        <span className="empty-state-icon">
          <Icon aria-hidden="true" />
        </span>
      )}
      <p className="empty-state-title">{title}</p>
      {children && <div className="empty-state-body">{children}</div>}
      {action && <div className="empty-state-action">{action}</div>}
    </div>
  )
}

export default EmptyState
