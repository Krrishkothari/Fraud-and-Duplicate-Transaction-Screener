import type { XAxisProps, YAxisProps } from 'recharts'
import type { SeriesPoint, ThresholdResult } from '../../api/types'
import { formatAxisTime, formatCompact, formatRupees } from '../../format'

/** A series window with its amounts also in rupees, the unit the chart axes show. */
export interface ChartPoint extends SeriesPoint {
  sumRupees: number
  maxRupees: number
}

export function toChartPoints(points: SeriesPoint[]): ChartPoint[] {
  return points.map((point) => ({
    ...point,
    sumRupees: point.sumPaise / 100,
    maxRupees: point.maxPaise / 100,
  }))
}

/** Chart colors, all taken from tokens.css. */
export const CHART_COLORS = {
  count: 'var(--gray-400)',
  amount: 'var(--accent)',
  threshold: 'var(--gray-700)',
  above: 'var(--danger)',
  grid: 'var(--gray-200)',
  axis: 'var(--gray-300)',
  tick: 'var(--gray-600)',
  cursor: 'var(--gray-100)',
  surface: 'var(--white)',
}

export const CHART_HEIGHT = 260

export const CHART_MARGIN = { top: 8, right: 4, bottom: 0, left: 4 }

const TICK = { fill: CHART_COLORS.tick, fontSize: 12 }

/** The shared x axis: one category per window, labelled by its start in Asia/Kolkata time. */
export function timeAxisProps(spanSeconds: number): XAxisProps {
  return {
    dataKey: 'from',
    tick: TICK,
    tickLine: false,
    axisLine: { stroke: CHART_COLORS.axis },
    minTickGap: 32,
    tickFormatter: (epochSeconds: number) =>
      formatAxisTime(epochSeconds, spanSeconds),
  }
}

export const VALUE_AXIS_PROPS: YAxisProps = {
  tick: TICK,
  tickLine: false,
  axisLine: false,
  tickFormatter: (value: number) => formatCompact(value),
  width: 48,
}

/** The threshold as the chart labels it, such as P99 threshold 18,450.00. */
export function thresholdLabel(threshold: ThresholdResult): string {
  return `P${threshold.percentile} threshold ${formatRupees(threshold.valuePaise)}`
}
