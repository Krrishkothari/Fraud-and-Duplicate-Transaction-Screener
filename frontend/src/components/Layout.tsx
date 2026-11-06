import { Link, NavLink, Outlet } from 'react-router-dom'

const REPO_URL =
  'https://github.com/Krrishkothari/Fraud-and-Duplicate-Transaction-Screener'

const NAV_ITEMS = [
  { to: '/screen', label: 'Screen' },
  { to: '/benchmarks', label: 'Benchmarks' },
  { to: '/method', label: 'Method' },
]

function Layout() {
  return (
    <div className="app">
      <header className="app-header">
        <div className="app-header-inner">
          <Link className="wordmark" to="/">
            TxnScreen
          </Link>
          <nav aria-label="Main">
            <ul className="nav-list">
              {NAV_ITEMS.map((item) => (
                <li key={item.to}>
                  <NavLink className="nav-link" to={item.to}>
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
      <footer className="app-footer">
        <div className="app-footer-inner">
          <span>© 2026 TxnScreen</span>
          <ul className="footer-links">
            <li>
              <Link to="/privacy">Privacy</Link>
            </li>
            <li>
              <Link to="/terms">Terms</Link>
            </li>
            <li>
              <a href={REPO_URL}>GitHub</a>
            </li>
          </ul>
        </div>
      </footer>
    </div>
  )
}

export default Layout
