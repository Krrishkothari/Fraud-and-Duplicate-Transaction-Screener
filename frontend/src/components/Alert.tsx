import { CircleAlert, CircleCheck, Info, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'

const ICONS = {
  danger: CircleAlert,
  warning: TriangleAlert,
  success: CircleCheck,
  info: Info,
}

interface AlertProps {
  tone?: keyof typeof ICONS
  title?: string
  children: ReactNode
}

function Alert({ tone = 'info', title, children }: AlertProps) {
  const Icon = ICONS[tone]
  return (
    <div
      className={`alert alert-${tone}`}
      role={tone === 'danger' ? 'alert' : 'status'}
    >
      <Icon className="alert-icon" size={16} aria-hidden="true" />
      <div>
        {title && <p className="alert-title">{title}</p>}
        <div className="alert-body">{children}</div>
      </div>
    </div>
  )
}

export default Alert
