import type { ReactNode } from 'react'

interface CardProps {
  title?: string
  /** Lets a link jump to this card. */
  id?: string
  children: ReactNode
}

function Card({ title, id, children }: CardProps) {
  return (
    <section className="card" id={id}>
      {title && <h2 className="card-title">{title}</h2>}
      {children}
    </section>
  )
}

export default Card
