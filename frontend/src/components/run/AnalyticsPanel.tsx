import type { SeriesResponse } from '../../api/types'
import { formatEpochSeconds, formatInteger, formatMicros } from '../../format'
import { useRun } from '../../run/RunContext'
import { useSeries } from '../../run/useSeries'
import { ErrorAlert } from '../Alert'
import { Loading, Skeleton } from '../Skeleton'
import MaxAmountChart from './MaxAmountChart'
import VolumeChart from './VolumeChart'
import WindowQueryForm from './WindowQueryForm'
import { CHART_HEIGHT, toChartPoints } from './chartData'

/** Segment tree analytics over the selected window: two charts of its windows and a single window query. */
function AnalyticsPanel() {
  const { series, error } = useSeries()
  return (
    <div className="panel-stack">
      {error && <ErrorAlert error={error} />}
      {!series && !error && <ChartsSkeleton />}
      {series && <SeriesCharts series={series} />}
      <WindowQueryForm />
    </div>
  )
}

function SeriesCharts({ series }: { series: SeriesResponse }) {
  const { run } = useRun()
  const points = toChartPoints(series.points)
  const spanSeconds = series.effectiveTo - series.effectiveFrom
  return (
    <>
      <p className="panel-note">
        {formatInteger(series.points.length)} windows from{' '}
        {formatEpochSeconds(series.effectiveFrom)} to{' '}
        {formatEpochSeconds(series.effectiveTo)} IST. The segment tree answered
        all of them in{' '}
        <span className="mono">{formatMicros(series.algoMicros)}</span>.
      </p>
      <div className="charts">
        <VolumeChart points={points} spanSeconds={spanSeconds} />
        <MaxAmountChart
          points={points}
          spanSeconds={spanSeconds}
          threshold={run.threshold}
        />
      </div>
    </>
  )
}

function ChartsSkeleton() {
  return (
    <Loading label="Loading charts">
      <Skeleton width="60%" height={10} />
      <div className="charts">
        {[0, 1].map((index) => (
          <div key={index} className="chart">
            <Skeleton width="50%" height={12} />
            <Skeleton width="100%" height={CHART_HEIGHT} />
          </div>
        ))}
      </div>
    </Loading>
  )
}

export default AnalyticsPanel
