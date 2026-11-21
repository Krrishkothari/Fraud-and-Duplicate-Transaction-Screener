import { LucideProvider, Menu, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import {
  Link,
  NavLink,
  Outlet,
  useLocation,
  useNavigationType,
} from 'react-router-dom'
import { ButtonLink } from './Button'
import Logo from './Logo'
import { REPO_URL, SITE_URL } from '../links'
import ThemeToggle from './ThemeToggle'

const NAV_ITEMS = [
  { to: '/screen', label: 'Screen' },
  { to: '/benchmarks', label: 'Benchmarks' },
  { to: '/method', label: 'Method' },
]

/** The screening form. The header's Start screening button is hidden there, since it would link to itself. */
const SCREEN_PATH = '/screen'

function Layout() {
  const [menuOpen, setMenuOpen] = useState(false)
  const { pathname } = useLocation()
  const onScreenPage = pathname === SCREEN_PATH
  useScrollOnNavigate()
  useCanonicalLink(pathname)

  useEffect(() => {
    if (!menuOpen) {
      return
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMenuOpen(false)
      }
    }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [menuOpen])

  const closeMenu = () => setMenuOpen(false)

  return (
    <LucideProvider size={16} strokeWidth={1.75}>
      <div className="app">
        <a className="skip-link" href="#main">
          Skip to content
        </a>
        <header className="site-header">
          <div className="container site-header-inner">
            <Link
              className="brand"
              to="/"
              aria-label="TxnScreen home"
              onClick={closeMenu}
            >
              <Logo />
            </Link>
            <nav className="site-nav" aria-label="Main">
              <ul>
                {NAV_ITEMS.map((item) => (
                  <li key={item.to}>
                    <NavLink className="nav-link" to={item.to}>
                      {item.label}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </nav>
            <div className="site-header-actions">
              <ThemeToggle />
              {!onScreenPage && (
                <ButtonLink
                  to={SCREEN_PATH}
                  size="sm"
                  className="site-header-cta"
                >
                  Start screening
                </ButtonLink>
              )}
              <button
                type="button"
                className="icon-button menu-button"
                aria-expanded={menuOpen}
                aria-controls="mobile-menu"
                aria-label={menuOpen ? 'Close menu' : 'Open menu'}
                onClick={() => setMenuOpen(!menuOpen)}
              >
                {menuOpen ? (
                  <X aria-hidden="true" />
                ) : (
                  <Menu aria-hidden="true" />
                )}
              </button>
            </div>
          </div>
          <nav
            id="mobile-menu"
            className="mobile-menu"
            aria-label="Main"
            hidden={!menuOpen}
          >
            <ul className="container">
              {NAV_ITEMS.map((item) => (
                <li key={item.to}>
                  <NavLink
                    className="mobile-menu-link"
                    to={item.to}
                    onClick={closeMenu}
                  >
                    {item.label}
                  </NavLink>
                </li>
              ))}
              {!onScreenPage && (
                <li className="mobile-menu-cta">
                  <ButtonLink to={SCREEN_PATH} onClick={closeMenu}>
                    Start screening
                  </ButtonLink>
                </li>
              )}
            </ul>
          </nav>
        </header>
        <main id="main" className="site-main" tabIndex={-1}>
          <Outlet />
        </main>
        <footer className="site-footer">
          <div className="container site-footer-inner">
            <p className="site-footer-name">
              <span className="site-footer-brand">TxnScreen</span>
              <span>© 2026</span>
            </p>
            <nav aria-label="Footer">
              <ul className="site-footer-links">
                <li>
                  <Link to="/method">Method</Link>
                </li>
                <li>
                  <Link to="/benchmarks">Benchmarks</Link>
                </li>
                <li>
                  <a href={REPO_URL}>GitHub</a>
                </li>
                <li>
                  <Link to="/privacy">Privacy</Link>
                </li>
                <li>
                  <Link to="/terms">Terms</Link>
                </li>
              </ul>
            </nav>
            <p className="site-footer-credit">
              A course project for Advanced Algorithms (CIA 3), ATLAS uGDX
              School of Technology, by Krrish, Hardik and Vedant.
            </p>
          </div>
        </footer>
      </div>
    </LucideProvider>
  )
}

/**
 * Points the canonical link and og:url in index.html at the production address of the page shown, since
 * index.html serves every route and would otherwise name the home page for all of them.
 */
function useCanonicalLink(pathname: string) {
  useEffect(() => {
    const url =
      SITE_URL + (pathname === '/' ? '/' : pathname.replace(/\/+$/, ''))
    document.querySelector('link[rel="canonical"]')?.setAttribute('href', url)
    document
      .querySelector('meta[property="og:url"]')
      ?.setAttribute('content', url)
  }, [pathname])
}

/**
 * On a new page, starts at the top, or at the element a #hash names. Back and forward leave the scroll
 * position to the browser.
 */
function useScrollOnNavigate() {
  const { pathname, hash } = useLocation()
  const navigationType = useNavigationType()

  useEffect(() => {
    if (navigationType === 'POP') {
      return
    }
    const target = hash
      ? document.getElementById(decodeURIComponent(hash.slice(1)))
      : null
    if (target) {
      target.scrollIntoView()
    } else {
      window.scrollTo(0, 0)
    }
  }, [pathname, hash, navigationType])
}

export default Layout
