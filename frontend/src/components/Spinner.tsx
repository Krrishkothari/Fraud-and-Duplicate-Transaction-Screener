import { LoaderCircle } from 'lucide-react'

interface SpinnerProps {
  /** Announced to screen readers. Leave it out when nearby text already says what is running. */
  label?: string
}

/** A small spinner for buttons. Content areas use skeletons instead. */
function Spinner({ label }: SpinnerProps) {
  if (label === undefined) {
    return (
      <span className="spinner" aria-hidden="true">
        <LoaderCircle />
      </span>
    )
  }
  return (
    <span className="spinner" role="status">
      <LoaderCircle aria-hidden="true" />
      <span className="visually-hidden">{label}</span>
    </span>
  )
}

export default Spinner
