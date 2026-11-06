import type { ReactNode } from 'react'

interface TableProps {
  /** Names the table for screen readers. */
  label: string
  /** The thead and tbody. Give numeric cells the class "num". */
  children: ReactNode
}

function Table({ label, children }: TableProps) {
  return (
    <div className="table-wrap" role="region" aria-label={label} tabIndex={0}>
      <table className="table">{children}</table>
    </div>
  )
}

export default Table
