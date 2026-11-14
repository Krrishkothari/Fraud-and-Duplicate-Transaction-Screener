import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { ThresholdResult } from '../../api/types'
import ChartLegend from './ChartLegend'
import WindowTooltip from './WindowTooltip'
import {
  CHART_COLORS,
  CHART_HEIGHT,
  CHART_MARGIN,
  thresholdLabel,
  timeAxisProps,
  VALUE_AXIS_PROPS,
} from './chartData'
import type { ChartPoint } from './chartData'

interface MaxAmountChartProps {
  points: ChartPoint[]
  spanSeconds: number
  threshold: ThresholdResult
}

interface MarkerProps {
  cx?: number | null
  cy?: number | null
  color: string
}

/** One point on the line, ringed in the surface color so it stands clear of the line under it. */
function Marker({ cx, cy, color }: MarkerProps) {
  if (cx == null || cy == null) {
    return null
  }
  return (
    <circle
      cx={cx}
      cy={cy}
      r={4}
      fill={color}
      stroke={CHART_COLORS.surface}
      strokeWidth={2}
    />
  )
}

/**
 * The largest single amount per window, against the run's high amount threshold. Each window whose largest
 * amount is above the threshold gets a point in the danger color.
 */
function MaxAmountChart({
  points,
  spanSeconds,
  threshold,
}: MaxAmountChartProps) {
  // The same rule the server flags by: an amount strictly greater than the threshold value.
  const isAbove = (point: ChartPoint) => point.maxPaise > threshold.valuePaise
  const colorOf = (point: ChartPoint) =>
    isAbove(point) ? CHART_COLORS.above : CHART_COLORS.amount

  return (
    <figure className="chart">
      <figcaption className="chart-title">
        Largest single amount per window
      </figcaption>
      <ChartLegend
        items={[
          {
            label: 'Largest amount (INR)',
            color: CHART_COLORS.amount,
            mark: 'line',
          },
          {
            label: `P${threshold.percentile} threshold`,
            color: CHART_COLORS.threshold,
            mark: 'dashed',
          },
          {
            label: 'Above the threshold',
            color: CHART_COLORS.above,
            mark: 'dot',
          },
        ]}
      />
      <div className="chart-axis-units">
        <span>Largest amount (INR)</span>
      </div>
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <LineChart data={points} margin={CHART_MARGIN}>
          <CartesianGrid stroke={CHART_COLORS.grid} vertical={false} />
          <XAxis {...timeAxisProps(spanSeconds)} />
          <YAxis {...VALUE_AXIS_PROPS} />
          <Tooltip
            cursor={{ stroke: CHART_COLORS.axis }}
            isAnimationActive={false}
            content={({ payload }) => (
              <WindowTooltip
                point={payload[0]?.payload}
                threshold={threshold}
              />
            )}
          />
          <ReferenceLine
            y={threshold.valuePaise / 100}
            ifOverflow="extendDomain"
            // Recharts puts lines at z-index 400; one above keeps the threshold and its label in front.
            zIndex={401}
            stroke={CHART_COLORS.threshold}
            strokeDasharray="4 4"
            label={{
              value: thresholdLabel(threshold),
              position: 'insideTopRight',
              fill: CHART_COLORS.threshold,
              fontSize: 12,
              fontWeight: 500,
              // A halo in the surface color keeps the label readable where the line runs behind it.
              stroke: CHART_COLORS.surface,
              strokeWidth: 4,
              paintOrder: 'stroke',
            }}
          />
          <Line
            dataKey="maxRupees"
            name="Largest amount (INR)"
            type="linear"
            stroke={CHART_COLORS.amount}
            strokeWidth={2}
            dot={({ cx, cy, payload }) =>
              isAbove(payload) ? (
                <Marker cx={cx} cy={cy} color={CHART_COLORS.above} />
              ) : null
            }
            activeDot={({ cx, cy, payload }) => (
              <Marker cx={cx} cy={cy} color={colorOf(payload)} />
            )}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
      <p className="chart-axis-x">Window start (IST)</p>
    </figure>
  )
}

export default MaxAmountChart
