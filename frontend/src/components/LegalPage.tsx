import type { ReactNode } from 'react'

/** The address for questions about the privacy policy and terms. */
export const CONTACT_EMAIL = 'sonarhardik08@gmail.com'

interface LegalPageProps {
  title: string
  lastUpdated: string
  intro: string
  children: ReactNode
}

/** A plain text policy page: a title, the date it last changed, a one-line summary and short sections. */
export function LegalPage({
  title,
  lastUpdated,
  intro,
  children,
}: LegalPageProps) {
  return (
    <article className="page legal">
      <header className="page-header">
        <h1>{title}</h1>
        <p className="legal-updated">Last updated {lastUpdated}</p>
        <p>{intro}</p>
      </header>
      {children}
    </article>
  )
}

interface LegalSectionProps {
  title: string
  children: ReactNode
}

/** One titled section of a policy page. */
export function LegalSection({ title, children }: LegalSectionProps) {
  return (
    <section className="legal-section">
      <h2>{title}</h2>
      {children}
    </section>
  )
}

/** A link that opens an email to the contact address. */
export function ContactLink() {
  return <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>
}
