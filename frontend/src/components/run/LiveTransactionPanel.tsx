import { useState } from 'react'
import type { FormEvent } from 'react'
import {
  ApiError,
  appendTransaction,
  asApiError,
  getFlags,
} from '../../api/client'
import type { AppendRequest, AppendResponse } from '../../api/types'
import {
  formatAmountInput,
  formatInteger,
  formatKolkataInput,
  kolkataInputToIso,
} from '../../format'
import { useRun } from '../../run/RunContext'
import { appendFieldErrors } from '../../run/appendFieldErrors'
import type {
  AppendField,
  AppendFieldErrors,
} from '../../run/appendFieldErrors'
import Alert from '../Alert'
import Button from '../Button'
import Card from '../Card'
import Field from '../Field'
import AppendResult from './AppendResult'

const EMPTY: AppendRequest = {
  transactionId: '',
  accountId: '',
  amount: '',
  timestamp: '',
  merchant: '',
}

type SubmitState =
  | { status: 'idle' }
  | { status: 'submitting' }
  | { status: 'done'; result: AppendResponse; refreshError: ApiError | null }
  | { status: 'failed'; error: ApiError }

type PrefillState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'copied'; lineNumber: number }
  | { status: 'none' }
  | { status: 'failed'; error: ApiError }

function LiveTransactionPanel() {
  const { run, refresh } = useRun()
  const [values, setValues] = useState<AppendRequest>(EMPTY)
  const [fieldErrors, setFieldErrors] = useState<AppendFieldErrors>({})
  const [submit, setSubmit] = useState<SubmitState>({ status: 'idle' })
  const [prefill, setPrefill] = useState<PrefillState>({ status: 'idle' })

  function change(field: AppendField, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => ({ ...current, [field]: undefined }))
    setPrefill({ status: 'idle' })
  }

  async function copyFirstFlagged() {
    setPrefill({ status: 'loading' })
    try {
      const page = await getFlags(run.id, null, 0, 1)
      const row = page.items[0]
      if (row === undefined) {
        setPrefill({ status: 'none' })
        return
      }
      setValues({
        transactionId: row.transactionId,
        accountId: row.accountId,
        amount: formatAmountInput(row.amountPaise),
        timestamp: formatKolkataInput(row.epochSeconds),
        merchant: row.merchant,
      })
      setFieldErrors({})
      setPrefill({ status: 'copied', lineNumber: row.lineNumber })
    } catch (caught) {
      setPrefill({ status: 'failed', error: asApiError(caught) })
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmit({ status: 'submitting' })
    setFieldErrors({})
    let result: AppendResponse
    try {
      result = await appendTransaction(run.id, {
        ...values,
        timestamp: values.timestamp && kolkataInputToIso(values.timestamp),
      })
    } catch (caught) {
      const error = asApiError(caught)
      const errors = appendFieldErrors(error)
      setFieldErrors(errors)
      setSubmit(
        Object.keys(errors).length > 0
          ? { status: 'idle' }
          : { status: 'failed', error },
      )
      return
    }
    setSubmit({ status: 'done', result, refreshError: null })
    try {
      await refresh()
    } catch (caught) {
      setSubmit({ status: 'done', result, refreshError: asApiError(caught) })
    }
  }

  const submitting = submit.status === 'submitting'

  return (
    <Card title="Live transactions">
      <div className="card-stack">
        <p className="panel-note">
          Add one transaction to this run. The server screens it with the same
          Bloom filters and exact checks as the file rows, then updates its time
          bucket in the segment tree.
        </p>
        <form className="card-stack" onSubmit={handleSubmit} noValidate>
          <div className="form-grid">
            <Field label="Transaction ID" error={fieldErrors.transactionId}>
              <input
                value={values.transactionId}
                onChange={(e) => change('transactionId', e.target.value)}
              />
            </Field>
            <Field label="Account ID" error={fieldErrors.accountId}>
              <input
                value={values.accountId}
                onChange={(e) => change('accountId', e.target.value)}
              />
            </Field>
            <Field
              label="Amount (INR)"
              hint="Up to 2 decimal places, such as 1234.50."
              error={fieldErrors.amount}
            >
              <input
                inputMode="decimal"
                value={values.amount}
                onChange={(e) => change('amount', e.target.value)}
              />
            </Field>
            <Field label="Timestamp (IST)" error={fieldErrors.timestamp}>
              <input
                type="datetime-local"
                step={1}
                value={values.timestamp}
                onChange={(e) => change('timestamp', e.target.value)}
              />
            </Field>
            <Field label="Merchant" error={fieldErrors.merchant}>
              <input
                value={values.merchant}
                onChange={(e) => change('merchant', e.target.value)}
              />
            </Field>
          </div>
          <PrefillNote state={prefill} />
          <div className="form-actions">
            <Button
              variant="secondary"
              onClick={copyFirstFlagged}
              disabled={prefill.status === 'loading' || submitting}
            >
              Prefill from an existing transaction
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? 'Adding' : 'Add transaction'}
            </Button>
          </div>
        </form>
        {submit.status === 'failed' && (
          <Alert tone="danger" title={submit.error.code}>
            {submit.error.message}
          </Alert>
        )}
        {submit.status === 'done' && (
          <>
            <AppendResult result={submit.result} />
            {submit.refreshError && (
              <Alert tone="warning" title="The page did not refresh">
                The transaction was added, but the run could not be reloaded:{' '}
                {submit.refreshError.message}
              </Alert>
            )}
          </>
        )}
      </div>
    </Card>
  )
}

function PrefillNote({ state }: { state: PrefillState }) {
  switch (state.status) {
    case 'copied':
      return (
        <p className="panel-note">
          Copied line {formatInteger(state.lineNumber)}, the first flagged
          transaction. Its transaction ID is already in this run.
        </p>
      )
    case 'none':
      return (
        <p className="panel-note">
          This run has no flagged transaction to copy.
        </p>
      )
    case 'failed':
      return <Alert tone="danger">{state.error.message}</Alert>
    default:
      return null
  }
}

export default LiveTransactionPanel
