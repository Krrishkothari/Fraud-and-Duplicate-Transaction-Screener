import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import ChartLegend from './ChartLegend'
import WindowTooltip from './WindowTooltip'
import {
  CHART_COLORS,
  CHART_HEIGHT,
  CHART_MARGIN,
  timeAxisProps,
  VALUE_AXIS_PROPS,
} from './chartData'
import type { ChartPoint } from './chartData'

interface VolumeChartProps {
  points: ChartPoint[]
  spanSeconds: number
}

/** Transaction count per window as bars, with the total amount per window as a line on its own axis. */
function VolumeChart({ points, spanSeconds }: VolumeChartProps) {
  return (
    <figure className="chart">
      <figcaption className="chart-title">
        Transactions and total amount per window
      </figcaption>
      <ChartLegend
        items={[
          { label: 'Transactions', color: CHART_COLORS.count, mark: 'bar' },
          {
            label: 'Total amount (INR)',
            color: CHART_COLORS.amount,
            mark: 'line',
          },
        ]}
      />
      <div className="chart-axis-units">
        <span>Transactions</span>
        <span>Total amount (INR)</span>
      </div>
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <ComposedChart data={points} margin={CHART_MARGIN}>
          <CartesianGrid
            yAxisId="count"
            stroke={CHART_COLORS.grid}
            vertical={false}
          />
          <XAxis {...timeAxisProps(spanSeconds)} />
          <YAxis {...VALUE_AXIS_PROPS} yAxisId="count" allowDecimals={false} />
          <YAxis {...VALUE_AXIS_PROPS} yAxisId="amount" orientation="right" />
          <Tooltip
            cursor={{ fill: CHART_COLORS.cursor }}
            isAnimationActive={false}
            content={({ payload }) => (
              <WindowTooltip point={payload[0]?.payload} />
            )}
          />
          <Bar
            yAxisId="count"
            dataKey="count"
            name="Transactions"
            fill={CHART_COLORS.count}
            radius={[2, 2, 0, 0]}
            maxBarSize={24}
            isAnimationActive={false}
          />
          <Line
            yAxisId="amount"
            dataKey="sumRupees"
            name="Total amount (INR)"
            type="linear"
            stroke={CHART_COLORS.amount}
            strokeWidth={2}
            dot={false}
            activeDot={{
              r: 4,
              fill: CHART_COLORS.amount,
              stroke: CHART_COLORS.surface,
              strokeWidth: 2,
            }}
            isAnimationActive={false}
          />
        </ComposedChart>
      </ResponsiveContainer>
      <p className="chart-axis-x">Window start (IST)</p>
    </figure>
  )
}

export default VolumeChart
