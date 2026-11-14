import type { SeriesResponse } from '../../api/types'
import { formatEpochSeconds, formatInteger, formatMicros } from '../../format'
import { useRun } from '../../run/RunContext'
import { useSeries } from '../../run/useSeries'
import Alert from '../Alert'
import Card from '../Card'
import Spinner from '../Spinner'
import MaxAmountChart from './MaxAmountChart'
import VolumeChart from './VolumeChart'
import WindowQueryForm from './WindowQueryForm'
import { toChartPoints } from './chartData'

/** Segment tree analytics over the selected window: two charts of its windows and a single window query. */
function AnalyticsPanel() {
  const { series, error } = useSeries()
  return (
    <Card title="Time window analytics">
      <div className="card-stack">
        {error && <Alert tone="danger">{error.message}</Alert>}
        {!series && !error && (
          <div className="panel-loading">
            <Spinner label="Loading charts" />
          </div>
        )}
        {series && <SeriesCharts series={series} />}
        <WindowQueryForm />
      </div>
    </Card>
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
        all of them in {formatMicros(series.algoMicros)}.
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

export default AnalyticsPanel
