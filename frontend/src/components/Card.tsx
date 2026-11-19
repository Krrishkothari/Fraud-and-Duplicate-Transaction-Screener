import type { ReactNode } from 'react'

interface CardProps {
  title?: ReactNode
  description?: ReactNode
  /** Controls on the right of the header, such as an export button. */
  actions?: ReactNode
  /** Lets a link jump to this card. */
  id?: string
  /** h2 on a page, h3 inside a tab or another section. */
  headingLevel?: 2 | 3
  /** Lets a table run to the card's edges instead of sitting inside its padding. */
  flush?: boolean
  className?: string
  children: ReactNode
}

/** A panel for something that is an object on the page: a result, a filter, an uploaded file. */
function Card({
  title,
  description,
  actions,
  id,
  headingLevel = 2,
  flush = false,
  className,
  children,
}: CardProps) {
  const Heading = headingLevel === 3 ? 'h3' : 'h2'
  return (
    <section className={className ? `card ${className}` : 'card'} id={id}>
      {(title || actions) && (
        <header className="card-header">
          <div className="card-heading">
            {title && <Heading className="card-title">{title}</Heading>}
            {description && <p className="card-description">{description}</p>}
          </div>
          {actions && <div className="card-actions">{actions}</div>}
        </header>
      )}
      <div className={flush ? 'card-body card-body-flush' : 'card-body'}>
        {children}
      </div>
    </section>
  )
}

export default Card
