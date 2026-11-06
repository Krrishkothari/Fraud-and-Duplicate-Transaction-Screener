import type { ReactNode } from 'react'

interface CardProps {
  title?: string
  children: ReactNode
}

function Card({ title, children }: CardProps) {
  return (
    <section className="card">
      {title && <h2 className="card-title">{title}</h2>}
      {children}
    </section>
  )
}

export default Card
