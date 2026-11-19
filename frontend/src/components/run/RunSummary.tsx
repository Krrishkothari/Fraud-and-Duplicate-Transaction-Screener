import { formatInteger, formatMillis } from '../../format'
import { useRun } from '../../run/RunContext'
import { FLAG_LABELS, FLAG_TONES, FLAG_TYPES } from '../../run/flagTypes'
import Alert from '../Alert'
import StatCard from '../StatCard'

/** Row counts, flag counts and the screening time, all from the run summary, with any server warnings. */
function RunSummary() {
  const { run } = useRun()
  const flags = FLAG_TYPES.reduce((sum, type) => sum + run.flagCounts[type], 0)
  return (
    <>
      {run.warnings.map((warning) => (
        <Alert key={warning} tone="warning">
          {warning}
        </Alert>
      ))}
      <dl className="stat-grid">
        <StatCard
          label="Valid rows"
          value={formatInteger(run.validRows)}
          detail="Screened in time order"
        />
        <StatCard
          label="Flags raised"
          tone="danger"
          value={formatInteger(flags)}
          detail={
            <ul className="flag-breakdown">
              {FLAG_TYPES.map((type) => (
                <li key={type}>
                  <span
                    className={`dot dot-${FLAG_TONES[type]}`}
                    aria-hidden="true"
                  />
                  {FLAG_LABELS[type]} flags
                  <span className="mono">
                    {formatInteger(run.flagCounts[type])}
                  </span>
                </li>
              ))}
            </ul>
          }
        />
        <StatCard
          label="Rejected rows"
          tone="neutral"
          value={formatInteger(run.rejectedRows)}
          detail="Rows that could not be read"
        />
        <StatCard
          label="Screening time"
          value={formatMillis(run.algoMicros)}
          detail="Bloom filters and exact checks"
        />
      </dl>
    </>
  )
}

export default RunSummary
