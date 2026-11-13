import { ChevronLeft, ChevronRight } from 'lucide-react'
import { formatInteger } from '../format'
import Button from './Button'

interface PaginationProps {
  /** Names the controls for screen readers. */
  label: string
  /** The 0-based page number. */
  page: number
  totalPages: number
  onChange: (page: number) => void
}

/** Previous and next controls for a server-paged list. Shows nothing when there is one page or none. */
function Pagination({ label, page, totalPages, onChange }: PaginationProps) {
  if (totalPages <= 1) {
    return null
  }
  return (
    <nav className="pagination" aria-label={label}>
      <Button
        variant="secondary"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
      >
        <ChevronLeft size={16} aria-hidden="true" />
        Previous
      </Button>
      <span className="pagination-status">
        Page {formatInteger(page + 1)} of {formatInteger(totalPages)}
      </span>
      <Button
        variant="secondary"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        Next
        <ChevronRight size={16} aria-hidden="true" />
      </Button>
    </nav>
  )
}

export default Pagination
