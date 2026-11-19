import { Moon, Sun } from 'lucide-react'
import { useSyncExternalStore } from 'react'
import { chooseTheme, currentTheme, subscribeToTheme } from '../theme'

/** Switches between the light and dark theme. */
function ThemeToggle() {
  const theme = useSyncExternalStore(subscribeToTheme, currentTheme)
  const next = theme === 'dark' ? 'light' : 'dark'
  const label = `Switch to ${next} theme`
  return (
    <button
      type="button"
      className="icon-button"
      aria-label={label}
      title={label}
      onClick={() => chooseTheme(next)}
    >
      {theme === 'dark' ? (
        <Sun aria-hidden="true" />
      ) : (
        <Moon aria-hidden="true" />
      )}
    </button>
  )
}

export default ThemeToggle
