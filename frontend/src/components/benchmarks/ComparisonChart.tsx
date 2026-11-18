import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { formatCompact, formatInteger } from '../../format'
import ChartLegend from '../run/ChartLegend'
import {
  CHART_COLORS,
  CHART_HEIGHT,
  CHART_MARGIN,
  VALUE_AXIS_PROPS,
} from '../run/chartData'

export interface ChartSeries {
  label: string
  /** A color from tokens.css. */
  color: string
  dashed?: boolean
  values: (number | undefined)[]
}

interface ComparisonChartProps {
  title: string
  /** The y axis unit, such as "ms" or "KB". */
  unit: string
  sizes: number[]
  series: ChartSeries[]
  format: (value: number) => string
}

/** Our algorithm against its baseline across the input sizes, on a log scale so every size stays readable. */
function ComparisonChart({
  title,
  unit,
  sizes,
  series,
  format,
}: ComparisonChartProps) {
  const data = sizes.map((size, i) => {
    const point: Record<string, number | undefined> = { size }
    series.forEach((s, j) => {
      point[`s${j}`] = s.values[i]
    })
    return point
  })
  const summary = series
    .map(
      (s) =>
        `${s.label}: ${sizes
          .map((size, i) =>
            s.values[i] === undefined
              ? `${formatInteger(size)} not measured`
              : `${format(s.values[i])} ${unit} at ${formatInteger(size)}`,
          )
          .join(', ')}`,
    )
    .join('. ')

  return (
    <figure className="chart">
      <figcaption className="chart-title">{title}</figcaption>
      <ChartLegend
        items={series.map((s) => ({
          label: s.label,
          color: s.color,
          mark: s.dashed ? 'dashed' : 'line',
        }))}
      />
      <div className="chart-axis-units">
        <span>{unit}, log scale</span>
      </div>
      <p className="visually-hidden">{summary}.</p>
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <LineChart data={data} margin={CHART_MARGIN}>
          <CartesianGrid stroke={CHART_COLORS.grid} vertical={false} />
          <XAxis
            dataKey="size"
            tick={{ fill: CHART_COLORS.tick, fontSize: 12 }}
            tickLine={false}
            axisLine={{ stroke: CHART_COLORS.axis }}
            tickFormatter={(size: number) => formatInteger(size)}
            padding={{ left: 24, right: 24 }}
          />
          <YAxis
            {...VALUE_AXIS_PROPS}
            scale="log"
            domain={[(min: number) => min / 2, (max: number) => max * 2]}
            allowDataOverflow
            tickFormatter={(value: number) => formatCompact(value)}
          />
          <Tooltip
            isAnimationActive={false}
            labelFormatter={(size) => `${formatInteger(Number(size))} rows`}
            formatter={(value, name) => [
              `${format(Number(value))} ${unit}`,
              series[Number(String(name).slice(1))]?.label ?? String(name),
            ]}
            contentStyle={{
              border: `1px solid ${CHART_COLORS.axis}`,
              borderRadius: 6,
              fontSize: 13,
            }}
          />
          {series.map((s, j) => (
            <Line
              key={s.label}
              dataKey={`s${j}`}
              type="linear"
              stroke={s.color}
              strokeWidth={2}
              strokeDasharray={s.dashed ? '6 4' : undefined}
              dot={{ r: 3, fill: s.color, strokeWidth: 0 }}
              isAnimationActive={false}
              connectNulls
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </figure>
  )
}

export default ComparisonChart
