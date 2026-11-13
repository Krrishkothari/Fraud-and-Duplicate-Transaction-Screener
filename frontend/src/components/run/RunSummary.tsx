import { formatEpochSeconds, formatInteger, formatMillis } from '../../format'
import { useRun } from '../../run/RunContext'
import { FLAG_LABELS, FLAG_TYPES } from '../../run/flagTypes'
import Alert from '../Alert'
import Card from '../Card'

/** Row counts, flag counts, the data time range and the screening time, all from the run summary. */
function RunSummary() {
  const { run } = useRun()
  return (
    <Card title="Summary">
      <div className="card-stack">
        <dl className="summary-values">
          <div>
            <dt>Valid rows</dt>
            <dd className="mono">{formatInteger(run.validRows)}</dd>
          </div>
          <div>
            <dt>Rejected rows</dt>
            <dd className="mono">{formatInteger(run.rejectedRows)}</dd>
          </div>
          {FLAG_TYPES.map((type) => (
            <div key={type}>
              <dt>{FLAG_LABELS[type]} flags</dt>
              <dd className="mono">{formatInteger(run.flagCounts[type])}</dd>
            </div>
          ))}
          <div>
            <dt>Screening time</dt>
            <dd className="mono">{formatMillis(run.algoMicros)}</dd>
          </div>
          <div className="summary-wide">
            <dt>Data time range (IST)</dt>
            <dd className="mono">
              {formatEpochSeconds(run.dataStart)} to{' '}
              {formatEpochSeconds(run.dataEnd)}
            </dd>
          </div>
        </dl>
        {run.warnings.map((warning) => (
          <Alert key={warning} tone="warning">
            {warning}
          </Alert>
        ))}
      </div>
    </Card>
  )
}

export default RunSummary
