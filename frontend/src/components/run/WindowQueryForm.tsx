import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, asApiError, getWindow } from '../../api/client'
import type { WindowResult } from '../../api/types'
import {
  formatEpochSeconds,
  formatInteger,
  formatKolkataInput,
  formatMicros,
  formatRupees,
} from '../../format'
import { useRun } from '../../run/RunContext'
import Alert from '../Alert'
import Button from '../Button'
import Field from '../Field'

// India has one fixed offset and no daylight saving time.
const IST_OFFSET = '+05:30'

type QueryState =
  | { status: 'idle' }
  | { status: 'querying' }
  | { status: 'done'; result: WindowResult }
  | { status: 'failed'; error: ApiError }

interface FieldErrors {
  from?: string
  to?: string
}

/** A datetime-local value, read as IST, as the ISO-8601 timestamp with offset the API expects. */
function kolkataInputToIso(value: string): string {
  const withSeconds =
    value.length === 'YYYY-MM-DDTHH:mm'.length ? `${value}:00` : value
  return withSeconds + IST_OFFSET
}

/**
 * Queries the totals of one window, entered as IST date and time. A window the server answers becomes the
 * run's selected window, which the charts then show.
 */
function WindowQueryForm() {
  const { run, selectedWindow, setSelectedWindow } = useRun()
  const [fromInput, setFromInput] = useState(() =>
    formatKolkataInput(selectedWindow.from),
  )
  const [toInput, setToInput] = useState(() =>
    formatKolkataInput(selectedWindow.to),
  )
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [state, setState] = useState<QueryState>({ status: 'idle' })

  async function query(from: string, to: string) {
    setState({ status: 'querying' })
    try {
      const result = await getWindow(run.id, from, to)
      setState({ status: 'done', result })
      setSelectedWindow({ from: result.effectiveFrom, to: result.effectiveTo })
    } catch (caught) {
      setState({ status: 'failed', error: asApiError(caught) })
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const errors: FieldErrors = {
      from: fromInput ? undefined : 'Enter a start date and time.',
      to: toInput ? undefined : 'Enter an end date and time.',
    }
    setFieldErrors(errors)
    if (errors.from || errors.to) {
      return
    }
    void query(kolkataInputToIso(fromInput), kolkataInputToIso(toInput))
  }

  function showFullRange() {
    setFromInput(formatKolkataInput(run.dataStart))
    setToInput(formatKolkataInput(run.dataEnd))
    setFieldErrors({})
    void query(String(run.dataStart), String(run.dataEnd))
  }

  const querying = state.status === 'querying'

  return (
    <section className="window-query">
      <h3 className="window-query-title">Query a window</h3>
      <form className="card-stack" onSubmit={handleSubmit} noValidate>
        <div className="form-grid">
          <Field label="From (IST)" error={fieldErrors.from}>
            <input
              type="datetime-local"
              step={1}
              value={fromInput}
              onChange={(event) => setFromInput(event.target.value)}
            />
          </Field>
          <Field label="To (IST)" error={fieldErrors.to}>
            <input
              type="datetime-local"
              step={1}
              value={toInput}
              onChange={(event) => setToInput(event.target.value)}
            />
          </Field>
        </div>
        <p className="panel-note">
          Both ends are included. Totals cover every{' '}
          {formatInteger(run.config.bucketSeconds)} second bucket the window
          touches.
        </p>
        <div className="form-actions">
          <Button
            variant="secondary"
            onClick={showFullRange}
            disabled={querying}
          >
            Full data range
          </Button>
          <Button type="submit" disabled={querying}>
            {querying ? 'Querying' : 'Query window'}
          </Button>
        </div>
      </form>
      {state.status === 'failed' && (
        <Alert tone="danger">{state.error.message}</Alert>
      )}
      {state.status === 'done' && <WindowTotals result={state.result} />}
    </section>
  )
}

function WindowTotals({ result }: { result: WindowResult }) {
  const range = `${formatEpochSeconds(result.effectiveFrom)} to ${formatEpochSeconds(result.effectiveTo)} IST`
  return (
    <div className="card-stack">
      {result.clamped && (
        <Alert tone="info" title="Window narrowed to the data range">
          The window reached past the run&apos;s data, so the query used {range}
          .
        </Alert>
      )}
      <dl className="summary-values">
        <div>
          <dt>Total amount (INR)</dt>
          <dd className="mono">{formatRupees(result.sumPaise)}</dd>
        </div>
        <div>
          <dt>Transactions</dt>
          <dd className="mono">{formatInteger(result.count)}</dd>
        </div>
        <div>
          <dt>Largest amount (INR)</dt>
          <dd className="mono">{formatRupees(result.maxPaise)}</dd>
        </div>
        <div>
          <dt>Query time</dt>
          <dd className="mono">{formatMicros(result.algoMicros)}</dd>
        </div>
        <div className="summary-wide">
          <dt>Window queried</dt>
          <dd>{range}</dd>
        </div>
      </dl>
    </div>
  )
}

export default WindowQueryForm
