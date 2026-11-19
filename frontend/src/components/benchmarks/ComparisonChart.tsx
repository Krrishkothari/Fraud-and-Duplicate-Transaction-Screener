import {
  CartesianGrid,
  LabelList,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { formatCompact, formatInteger } from '../../format'
import { useElementWidth } from '../../hooks/useElementWidth'
import ChartLegend from '../run/ChartLegend'
import {
  CHART_COLORS,
  CHART_HEIGHT,
  CHART_MARGIN,
  TICK,
  VALUE_AXIS_PROPS,
} from '../run/chartData'

export interface ChartSeries {
  label: string
  /** primary is this project's algorithm, drawn in the accent color; baseline is the simple alternative, in gray. */
  role: 'primary' | 'baseline'
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

/** Room right of the plot for the label at the end of each line. */
const END_LABEL_SPACE = 156

/** Below this width the labels would squeeze the plot, so a legend above the chart names the lines instead. */
const END_LABEL_MIN_WIDTH = 460

const STYLE = {
  primary: {
    stroke: CHART_COLORS.primary,
    dash: undefined,
    text: 'var(--color-accent)',
  },
  baseline: {
    stroke: CHART_COLORS.baseline,
    dash: '5 4',
    text: 'var(--color-text-secondary)',
  },
}

/** Our algorithm against its baseline across the input sizes, on a log scale so every size stays readable. */
function ComparisonChart({
  title,
  unit,
  sizes,
  series,
  format,
}: ComparisonChartProps) {
  const [figure, width] = useElementWidth<HTMLElement>()
  const endLabels = width >= END_LABEL_MIN_WIDTH

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
  const labelOffsets = endLabelOffsets(series)

  return (
    <figure className="chart" ref={figure}>
      <div className="chart-header">
        <figcaption className="chart-title">{title}</figcaption>
        <p className="chart-subtitle">{unit}, log scale</p>
      </div>
      {!endLabels && (
        <ChartLegend
          items={series.map((s) => ({
            label: s.label,
            color: STYLE[s.role].stroke,
            mark: s.role === 'baseline' ? 'dashed' : 'line',
          }))}
        />
      )}
      <p className="visually-hidden">{summary}.</p>
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <LineChart
          data={data}
          margin={{
            ...CHART_MARGIN,
            top: 16,
            right: endLabels ? END_LABEL_SPACE : CHART_MARGIN.right + 8,
          }}
        >
          <CartesianGrid stroke={CHART_COLORS.grid} vertical={false} />
          <XAxis
            dataKey="size"
            tick={TICK}
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
            cursor={{ stroke: CHART_COLORS.axis }}
            isAnimationActive={false}
            content={({ active, payload, label }) =>
              active && payload.length > 0 ? (
                <div className="chart-tooltip">
                  <p className="chart-tooltip-title">
                    {formatInteger(Number(label))} rows
                  </p>
                  <dl className="chart-tooltip-values">
                    {series.map((s, j) => {
                      const value = payload[0].payload[`s${j}`]
                      return (
                        <div key={s.label}>
                          <dt>
                            <span
                              className={`chart-tooltip-swatch chart-tooltip-swatch-${s.role}`}
                              aria-hidden="true"
                            />
                            {s.label}
                          </dt>
                          <dd className="mono">
                            {value === undefined
                              ? 'Not measured'
                              : `${format(value)} ${unit}`}
                          </dd>
                        </div>
                      )
                    })}
                  </dl>
                </div>
              ) : null
            }
          />
          {series.map((s, j) => (
            <Line
              key={s.label}
              dataKey={`s${j}`}
              type="linear"
              stroke={STYLE[s.role].stroke}
              strokeWidth={s.role === 'primary' ? 2 : 1.75}
              strokeDasharray={STYLE[s.role].dash}
              dot={{ r: 3, fill: STYLE[s.role].stroke, strokeWidth: 0 }}
              activeDot={{
                r: 4.5,
                fill: STYLE[s.role].stroke,
                stroke: CHART_COLORS.surface,
                strokeWidth: 2,
              }}
              isAnimationActive={false}
              connectNulls
            >
              {endLabels && (
                <LabelList
                  dataKey={`s${j}`}
                  content={({ x, y, index }) =>
                    index === lastIndex(s.values) ? (
                      <text
                        x={Number(x) + 10}
                        y={Number(y) + labelOffsets[j]}
                        dominantBaseline="middle"
                        className="chart-end-label"
                        fill={STYLE[s.role].text}
                      >
                        {s.label}
                      </text>
                    ) : null
                  }
                />
              )}
            </Line>
          ))}
        </LineChart>
      </ResponsiveContainer>
    </figure>
  )
}

function lastIndex(values: (number | undefined)[]): number {
  return values.findLastIndex((value) => value !== undefined)
}

/**
 * Vertical nudges for the end labels: the line that ends highest gets its label lifted, the next one lowered,
 * so two lines that end close together never print their names on top of each other.
 */
function endLabelOffsets(series: ChartSeries[]): number[] {
  const order = series
    .map((s, j) => ({ j, end: s.values[lastIndex(s.values)] ?? 0 }))
    .sort((a, b) => b.end - a.end)
  const offsets = new Array<number>(series.length).fill(0)
  order.forEach(({ j }, rank) => {
    offsets[j] = rank === 0 ? -7 : 9 + (rank - 1) * 16
  })
  return offsets
}

export default ComparisonChart
