import { Info } from 'lucide-react'
import { useEffect, useId, useState } from 'react'

interface InfoTipProps {
  /** Names the icon button for screen readers. */
  label: string
  /** The explanation, one sentence. */
  children: string
}

/**
 * An info icon that shows a short explanation on hover, keyboard focus or tap, and hides it on Escape. The
 * bubble is placed under the nearest positioned ancestor, so give the surrounding row position: relative.
 */
function InfoTip({ label, children }: InfoTipProps) {
  const id = useId()
  const [open, setOpen] = useState(false)

  useEffect(() => {
    if (!open) {
      return
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [open])

  return (
    <span
      className="info-tip"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <button
        type="button"
        className="info-tip-button"
        aria-label={label}
        aria-describedby={id}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onClick={() => setOpen(true)}
      >
        <Info size={14} aria-hidden="true" />
      </button>
      <span id={id} role="tooltip" className="info-tip-bubble" hidden={!open}>
        {children}
      </span>
    </span>
  )
}

export default InfoTip
