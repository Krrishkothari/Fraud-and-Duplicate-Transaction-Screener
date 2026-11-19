import { CircleAlert, CircleCheck, Info, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'
import type { ApiError } from '../api/client'
import Button from './Button'

const ICONS = {
  danger: CircleAlert,
  warning: TriangleAlert,
  success: CircleCheck,
  info: Info,
}

interface AlertProps {
  tone?: keyof typeof ICONS
  title?: ReactNode
  children?: ReactNode
  /** A button on the right, such as Try again. */
  action?: ReactNode
}

function Alert({ tone = 'info', title, children, action }: AlertProps) {
  const Icon = ICONS[tone]
  return (
    <div
      className={`alert alert-${tone}`}
      role={tone === 'danger' ? 'alert' : 'status'}
    >
      <Icon className="alert-icon" aria-hidden="true" />
      <div className="alert-content">
        {title && <p className="alert-title">{title}</p>}
        {children && <div className="alert-body">{children}</div>}
      </div>
      {action && <div className="alert-action">{action}</div>}
    </div>
  )
}

interface ErrorAlertProps {
  error: ApiError
  /** Offers a Try again button that calls this. */
  onRetry?: () => void
}

/** An API error: its message, with its code underneath in monospace. */
export function ErrorAlert({ error, onRetry }: ErrorAlertProps) {
  return (
    <Alert
      tone="danger"
      title={error.message}
      action={
        onRetry && (
          <Button variant="secondary" size="sm" onClick={onRetry}>
            Try again
          </Button>
        )
      }
    >
      <code className="alert-code">{error.code}</code>
    </Alert>
  )
}

export default Alert
