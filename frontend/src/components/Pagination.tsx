import { ChevronLeft, ChevronRight } from 'lucide-react'
import { formatInteger } from '../format'
import Button from './Button'

interface PaginationProps {
  /** Names the controls for screen readers. */
  label: string
  /** The 0-based page number. */
  page: number
  pageSize: number
  totalItems: number
  totalPages: number
  /** What the items are, in the plural, such as "flags". */
  noun: string
  onChange: (page: number) => void
}

/** Which items of a server-paged list are in view, with previous and next controls when there is more than one page. */
function Pagination({
  label,
  page,
  pageSize,
  totalItems,
  totalPages,
  noun,
  onChange,
}: PaginationProps) {
  const first = page * pageSize + 1
  const last = Math.min((page + 1) * pageSize, totalItems)
  return (
    <div className="pagination">
      <p className="pagination-status">
        Showing <span className="mono">{formatInteger(first)}</span> to{' '}
        <span className="mono">{formatInteger(last)}</span> of{' '}
        <span className="mono">{formatInteger(totalItems)}</span> {noun}
      </p>
      {totalPages > 1 && (
        <nav className="pagination-controls" aria-label={label}>
          <Button
            variant="secondary"
            size="sm"
            disabled={page === 0}
            onClick={() => onChange(page - 1)}
          >
            <ChevronLeft aria-hidden="true" />
            Previous
          </Button>
          <span className="pagination-page">
            Page <span className="mono">{formatInteger(page + 1)}</span> of{' '}
            <span className="mono">{formatInteger(totalPages)}</span>
          </span>
          <Button
            variant="secondary"
            size="sm"
            disabled={page >= totalPages - 1}
            onClick={() => onChange(page + 1)}
          >
            Next
            <ChevronRight aria-hidden="true" />
          </Button>
        </nav>
      )}
    </div>
  )
}

export default Pagination
