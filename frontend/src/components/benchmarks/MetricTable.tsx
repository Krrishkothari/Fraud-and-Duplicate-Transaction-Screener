import { formatInteger } from '../../format'
import Table from '../Table'

export interface MetricRow {
  label: string
  values: (number | undefined)[]
  format: (value: number) => string
}

interface MetricTableProps {
  label: string
  sizes: number[]
  rows: MetricRow[]
}

/** The exact medians behind a section: one row per metric, one column per input size. */
function MetricTable({ label, sizes, rows }: MetricTableProps) {
  return (
    <div className="metric-table">
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
        <tbody>
          {rows.map((row) => (
            <tr key={row.label}>
              <th scope="row" className="metric-name">
                {row.label}
              </th>
              {row.values.map((value, i) => (
                <td key={sizes[i]} className="num">
                  {value === undefined ? 'Not measured' : row.format(value)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </Table>
    </div>
  )
}

export default MetricTable
