// The light or dark theme. Until someone picks one in the header, the page follows the system setting. A
// script in index.html applies a saved choice before the first paint, so the page never flashes.

export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'txnscreen-theme'
const CHANGE_EVENT = 'txnscreen-theme-change'
const DARK_QUERY = '(prefers-color-scheme: dark)'

/** The theme in use: the one picked in the header, or else the system setting. */
export function currentTheme(): Theme {
  const chosen = document.documentElement.dataset.theme
  if (chosen === 'light' || chosen === 'dark') {
    return chosen
  }
  return window.matchMedia(DARK_QUERY).matches ? 'dark' : 'light'
}

/** Applies a theme and remembers it for later visits where the browser allows storage. */
export function chooseTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // Storage is blocked, as in some private windows: the theme still applies until the page closes.
  }
  window.dispatchEvent(new Event(CHANGE_EVENT))
}

/** Calls onChange whenever the theme in use may have changed. Returns the unsubscribe function. */
export function subscribeToTheme(onChange: () => void): () => void {
  const query = window.matchMedia(DARK_QUERY)
  query.addEventListener('change', onChange)
  window.addEventListener(CHANGE_EVENT, onChange)
  return () => {
    query.removeEventListener('change', onChange)
    window.removeEventListener(CHANGE_EVENT, onChange)
  }
}
