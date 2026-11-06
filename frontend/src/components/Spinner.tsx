import { LoaderCircle } from 'lucide-react'

interface SpinnerProps {
  label?: string
  size?: number
}

function Spinner({ label = 'Loading', size = 20 }: SpinnerProps) {
  return (
    <span className="spinner" role="status">
      <LoaderCircle size={size} aria-hidden="true" />
      <span className="visually-hidden">{label}</span>
    </span>
  )
}

export default Spinner
