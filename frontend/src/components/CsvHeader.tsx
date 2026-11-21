import { Fragment } from 'react'

interface CsvHeaderProps {
  columns: string[]
  className?: string
}

/**
 * A CSV header row in monospace. A <wbr> after each comma lets a narrow screen wrap it between columns,
 * never inside a column name such as timestamp.
 */
function CsvHeader({ columns, className }: CsvHeaderProps) {
  return (
    <code className={className}>
      {columns.map((column, index) => (
        <Fragment key={column}>
          {column}
          {index < columns.length - 1 && (
            <>
              ,<wbr />
            </>
          )}
        </Fragment>
      ))}
    </code>
  )
}

export default CsvHeader
