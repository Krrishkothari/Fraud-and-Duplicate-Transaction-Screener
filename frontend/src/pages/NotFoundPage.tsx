import { Compass } from 'lucide-react'
import { useLocation } from 'react-router-dom'
import { ButtonLink } from '../components/Button'
import Card from '../components/Card'
import EmptyState from '../components/EmptyState'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

function NotFoundPage() {
  const { pathname } = useLocation()
  useDocumentTitle('Page not found')
  return (
    <div className="container page">
      <Card>
        <EmptyState
          icon={Compass}
          title="Page not found"
          action={
            <ButtonLink to="/" variant="secondary">
              Go to the home page
            </ButtonLink>
          }
        >
          There is no page at <code>{pathname}</code>. The link may be wrong, or
          the page may have moved.
        </EmptyState>
      </Card>
    </div>
  )
}

export default NotFoundPage
