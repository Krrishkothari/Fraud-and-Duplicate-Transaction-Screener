import type { ReactNode } from 'react'

interface PageHeaderProps {
  title: string
  description?: ReactNode
  /** Small facts under the description, such as an ID and a date. */
  meta?: ReactNode
  /** Buttons on the right. */
  actions?: ReactNode
}

/** The title block at the top of a page. */
function PageHeader({ title, description, meta, actions }: PageHeaderProps) {
  return (
    <header className="page-header">
      <div className="page-header-text">
        <h1>{title}</h1>
        {description && <p className="page-description">{description}</p>}
        {meta && <div className="page-meta">{meta}</div>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  )
}

export default PageHeader
