export interface LegendItem {
  label: string
  /** A color from tokens.css, the same one the chart draws this item with. */
  color: string
  mark: 'bar' | 'line' | 'dashed' | 'dot'
}

/** The key above a chart: one small mark in the item's color beside its label. */
function ChartLegend({ items }: { items: LegendItem[] }) {
  return (
    <ul className="chart-legend">
      {items.map((item) => (
        <li key={item.label} className="chart-legend-item">
          <span
            className={`chart-legend-mark chart-legend-${item.mark}`}
            style={{ color: item.color }}
            aria-hidden="true"
          />
          {item.label}
        </li>
      ))}
    </ul>
  )
}

export default ChartLegend
