import type { AppendResponse } from '../../api/types'
import { formatInteger, formatMicros, formatRupees } from '../../format'
import { FLAG_LABELS, FLAG_TONES } from '../../run/flagTypes'
import Badge from '../Badge'

function AppendResult({ result }: { result: AppendResponse }) {
  const { transaction, flags, bucketStats } = result
  return (
    <section className="append-result" aria-live="polite">
      <h3 className="append-result-title">
        Added <span className="mono">{transaction.transactionId}</span> as line{' '}
        {formatInteger(transaction.lineNumber)}
      </h3>
      {flags.length === 0 ? (
        <p className="panel-note">No flags</p>
      ) : (
        <ul className="append-flags">
          {flags.map((flag) => (
            <li key={flag.type}>
              <Badge tone={FLAG_TONES[flag.type]}>
                {FLAG_LABELS[flag.type]}
              </Badge>
              <span>{flag.detail}</span>
            </li>
          ))}
        </ul>
      )}
      <dl className="summary-values">
        <div>
          <dt>Bucket updated</dt>
          <dd className="mono">{formatInteger(result.bucketIndex)}</dd>
        </div>
        <div>
          <dt>Bucket transactions</dt>
          <dd className="mono">{formatInteger(bucketStats.count)}</dd>
        </div>
        <div>
          <dt>Bucket total (INR)</dt>
          <dd className="mono">{formatRupees(bucketStats.sumPaise)}</dd>
        </div>
        <div>
          <dt>Bucket largest (INR)</dt>
          <dd className="mono">{formatRupees(bucketStats.maxPaise)}</dd>
        </div>
        <div>
          <dt>Screening time</dt>
          <dd className="mono">{formatMicros(result.screenMicros)}</dd>
        </div>
        <div>
          <dt>Point update time</dt>
          <dd className="mono">{formatMicros(result.updateMicros)}</dd>
        </div>
      </dl>
      <p className="panel-note">{result.thresholdNote}</p>
    </section>
  )
}

export default AppendResult
