import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react'
import type { ReactNode } from 'react'

interface TableProps {
  /** Names the table for screen readers. */
  label: string
  /** Caps the height so a long table scrolls inside, with its header staying in view. */
  scroll?: boolean
  /** The thead and tbody. Give numeric cells the class "num". */
  children: ReactNode
}

/** A table that scrolls sideways inside its container on narrow screens. */
function Table({ label, scroll = false, children }: TableProps) {
  return (
    <div
      className={scroll ? 'table-wrap table-wrap-scroll' : 'table-wrap'}
      role="region"
      aria-label={label}
      tabIndex={0}
    >
      <table className="table">{children}</table>
    </div>
  )
}

export type SortDirection = 'ascending' | 'descending'

interface SortHeaderProps {
  label: string
  /** The direction when the table is sorted by this column, otherwise null. */
  direction: SortDirection | null
  onSort: () => void
  numeric?: boolean
}

/** A column header that sorts the table by its column when pressed. */
export function SortHeader({
  label,
  direction,
  onSort,
  numeric = false,
}: SortHeaderProps) {
  const Icon =
    direction === 'ascending'
      ? ArrowUp
      : direction === 'descending'
        ? ArrowDown
        : ArrowUpDown
  return (
    <th
      scope="col"
      className={numeric ? 'num' : undefined}
      aria-sort={direction ?? 'none'}
    >
      <button
        type="button"
        className={direction ? 'sort-button sort-button-active' : 'sort-button'}
        onClick={onSort}
      >
        {label}
        <Icon className="sort-icon" aria-hidden="true" />
      </button>
    </th>
  )
}

export default Table
