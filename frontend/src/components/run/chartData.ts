import type { XAxisProps, YAxisProps } from 'recharts'
import type { SeriesPoint, ThresholdResult } from '../../api/types'
import {
  formatAxisTime,
  formatCompact,
  formatEpochSeconds,
  formatInteger,
  formatRupees,
} from '../../format'

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

/** Chart colors, all taken from tokens.css, so they follow the light and dark theme. */
export const CHART_COLORS = {
  primary: 'var(--chart-primary)',
  baseline: 'var(--chart-baseline)',
  count: 'var(--chart-bar)',
  amount: 'var(--chart-primary)',
  threshold: 'var(--color-text-secondary)',
  above: 'var(--color-danger)',
  grid: 'var(--chart-grid)',
  axis: 'var(--chart-axis)',
  tick: 'var(--chart-tick)',
  cursor: 'var(--chart-cursor)',
  surface: 'var(--color-surface)',
}

export const CHART_HEIGHT = 260

export const CHART_MARGIN = { top: 8, right: 4, bottom: 0, left: 4 }

/** Axis label text. Tabular figures for the ticks are set in components.css. */
export const TICK = { fill: CHART_COLORS.tick, fontSize: 12 }

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

/** What the volume chart shows, in words: its range and its busiest window. points is never empty. */
export function volumeSummary(points: ChartPoint[]): string {
  const busiest = points.reduce((a, b) => (b.count > a.count ? b : a))
  return `${points.length} windows from ${formatEpochSeconds(points[0].from)} to ${formatEpochSeconds(points[points.length - 1].to)} IST. The busiest window starts ${formatEpochSeconds(busiest.from)} IST, with ${formatInteger(busiest.count)} transactions totalling ${formatRupees(busiest.sumPaise)} INR.`
}

/** What the largest amount chart shows, in words: windows above the threshold and the largest amount. */
export function maxAmountSummary(
  points: ChartPoint[],
  threshold: ThresholdResult,
): string {
  const above = points.filter(
    (point) => point.maxPaise > threshold.valuePaise,
  ).length
  const largest = points.reduce((a, b) => (b.maxPaise > a.maxPaise ? b : a))
  return `${above} of ${points.length} windows have a largest amount above the ${thresholdLabel(threshold)} INR. The largest single amount, ${formatRupees(largest.maxPaise)} INR, is in the window starting ${formatEpochSeconds(largest.from)} IST.`
}

/** The threshold as the chart labels it, such as P99 threshold 18,450.00. */
export function thresholdLabel(threshold: ThresholdResult): string {
  return `P${threshold.percentile} threshold ${formatRupees(threshold.valuePaise)}`
}
