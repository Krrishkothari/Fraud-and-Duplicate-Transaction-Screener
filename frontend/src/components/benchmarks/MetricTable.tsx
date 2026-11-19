import { formatInteger } from '../../format'
import Table from '../Table'

export interface MetricRow {
  label: string
  values: (number | undefined)[]
}

export interface MetricGroup {
  /** The measurement, with its unit, such as "Memory (KB)". */
  label: string
  format: (value: number) => string
  /** One row is a plain metric; several rows are variants measured the same way, listed under the label. */
  rows: MetricRow[]
  /** Marks the lowest value in each column, for variants that compete, such as two algorithms' times. */
  lowerWins?: boolean
}

interface MetricTableProps {
  label: string
  sizes: number[]
  groups: MetricGroup[]
}

/** The exact medians behind a section: one row per metric, one column per input size. */
function MetricTable({ label, sizes, groups }: MetricTableProps) {
  return (
    <Table label={label}>
      <thead>
        <tr>
          <th scope="col">Metric (median)</th>
          {sizes.map((size) => (
            <th key={size} scope="col" className="num">
              {formatInteger(size)} rows
            </th>
          ))}
        </tr>
      </thead>
      {groups.map((group) => (
        <tbody key={group.label} className="metric-group">
          {group.rows.length > 1 && (
            <tr className="metric-group-header">
              <th scope="colgroup" colSpan={sizes.length + 1}>
                {group.label}
              </th>
            </tr>
          )}
          {group.rows.map((row) => (
            <tr key={row.label}>
              <th
                scope="row"
                className={
                  group.rows.length > 1
                    ? 'metric-name metric-variant'
                    : 'metric-name'
                }
              >
                {group.rows.length > 1 ? (
                  <>
                    <span className="visually-hidden">{group.label}, </span>
                    {row.label}
                  </>
                ) : (
                  group.label
                )}
              </th>
              {row.values.map((value, i) => {
                const wins =
                  group.lowerWins === true &&
                  value !== undefined &&
                  isLowest(value, group.rows, i)
                return (
                  <td
                    key={sizes[i]}
                    className={wins ? 'num mono metric-win' : 'num mono'}
                  >
                    {value === undefined ? 'Not measured' : group.format(value)}
                    {wins && <span className="visually-hidden">, lowest</span>}
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      ))}
    </Table>
  )
}

/** Whether value is the lowest of the rows' measured values in column i, with at least two to compare. */
function isLowest(value: number, rows: MetricRow[], i: number): boolean {
  const measured = rows
    .map((row) => row.values[i])
    .filter((other) => other !== undefined)
  return measured.length > 1 && measured.every((other) => value <= other)
}

export default MetricTable
