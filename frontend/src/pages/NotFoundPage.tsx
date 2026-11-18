import { Link, useLocation } from 'react-router-dom'

function NotFoundPage() {
  const { pathname } = useLocation()
  return (
    <div className="page">
      <header className="page-header">
        <h1>Page not found</h1>
        <p>
          There is no page at <span className="mono">{pathname}</span>. The link
          may be wrong, or the page may have moved.
        </p>
      </header>
      <div>
        <Link className="button button-secondary" to="/">
          Go to the home page
        </Link>
      </div>
    </div>
  )
}

export default NotFoundPage
