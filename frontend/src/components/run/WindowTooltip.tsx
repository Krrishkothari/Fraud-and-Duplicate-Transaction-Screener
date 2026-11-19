import type { ThresholdResult } from '../../api/types'
import { formatEpochSeconds, formatInteger, formatRupees } from '../../format'
import type { ChartPoint } from './chartData'

interface WindowTooltipProps {
  point: ChartPoint | undefined
  /** When given, the tooltip says whether the window's largest amount is above it. */
  threshold?: ThresholdResult
}

/** The hover details for one chart window: its time range and all three totals. */
function WindowTooltip({ point, threshold }: WindowTooltipProps) {
  if (point === undefined) {
    return null
  }
  const above = threshold !== undefined && point.maxPaise > threshold.valuePaise
  return (
    <div className="chart-tooltip">
      <p className="chart-tooltip-title">
        {formatEpochSeconds(point.from)} to {formatEpochSeconds(point.to)} IST
      </p>
      <dl className="chart-tooltip-values">
        <div>
          <dt>Transactions</dt>
          <dd className="mono">{formatInteger(point.count)}</dd>
        </div>
        <div>
          <dt>Total amount (INR)</dt>
          <dd className="mono">{formatRupees(point.sumPaise)}</dd>
        </div>
        <div>
          <dt>Largest amount (INR)</dt>
          <dd className="mono">{formatRupees(point.maxPaise)}</dd>
        </div>
      </dl>
      {above && (
        <p className="chart-tooltip-flag">
          <span className="dot dot-danger" aria-hidden="true" />
          Above the P{threshold.percentile} threshold
        </p>
      )}
    </div>
  )
}

export default WindowTooltip
