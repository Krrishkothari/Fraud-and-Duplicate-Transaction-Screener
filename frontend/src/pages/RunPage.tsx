import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, asApiError, getRun } from '../api/client'
import type { RunSummary as RunSummaryData } from '../api/types'
import Alert from '../components/Alert'
import Button from '../components/Button'
import EmptyState from '../components/EmptyState'
import Spinner from '../components/Spinner'
import AnalyticsPanel from '../components/run/AnalyticsPanel'
import BloomStatsPanel from '../components/run/BloomStatsPanel'
import FlaggedTable from '../components/run/FlaggedTable'
import LiveTransactionPanel from '../components/run/LiveTransactionPanel'
import RejectedRowsTable from '../components/run/RejectedRowsTable'
import RunSummary from '../components/run/RunSummary'
import ThresholdPanel from '../components/run/ThresholdPanel'
import { formatDateTime } from '../format'
import { RunContext } from '../run/RunContext'
import type { RunContextValue, TimeWindow } from '../run/RunContext'

type LoadState =
  | { status: 'loading' }
  | { status: 'loaded'; run: RunSummaryData }
  | { status: 'expired' }
  | { status: 'failed'; error: ApiError }

function RunPage() {
  const { id = '' } = useParams()
  // A new key gives each run ID fresh state, so one run's results never show under another's URL.
  return <RunView key={id} id={id} />
}

function RunView({ id }: { id: string }) {
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getRun(id, controller.signal).then(
      (run) => setState({ status: 'loaded', run }),
      (caught: unknown) => {
        if (!controller.signal.aborted) {
          setState(failure(asApiError(caught)))
        }
      },
    )
    return () => controller.abort()
  }, [id, attempt])

  const refresh = useCallback(async () => {
    try {
      const run = await getRun(id)
      setState({ status: 'loaded', run })
    } catch (caught) {
      const error = asApiError(caught)
      if (isExpired(error)) {
        setState({ status: 'expired' })
      }
      throw error
    }
  }, [id])

  function retry() {
    setState({ status: 'loading' })
    setAttempt((current) => current + 1)
  }

  switch (state.status) {
    case 'loading':
      return (
        <div className="page-loading">
          <Spinner label="Loading run" />
        </div>
      )
    case 'expired':
      return (
        <EmptyState
          title="This run has expired or does not exist."
          action={
            <Link className="button button-primary" to="/screen">
              Start a new screening
            </Link>
          }
        >
          Runs are kept for 30 minutes.
        </EmptyState>
      )
    case 'failed':
      return (
        <div className="page">
          <h1>Screening run</h1>
          <Alert tone="danger" title={state.error.code}>
            {state.error.message}
          </Alert>
          <div>
            <Button variant="secondary" onClick={retry}>
              Try again
            </Button>
          </div>
        </div>
      )
    case 'loaded':
      return <RunResults run={state.run} refresh={refresh} />
  }
}

interface RunResultsProps {
  run: RunSummaryData
  refresh: () => Promise<void>
}

function RunResults({ run, refresh }: RunResultsProps) {
  const [selectedWindow, setSelectedWindow] = useState<TimeWindow>({
    from: run.dataStart,
    to: run.dataEnd,
  })
  const context = useMemo<RunContextValue>(
    () => ({ run, refresh, selectedWindow, setSelectedWindow }),
    [run, refresh, selectedWindow],
  )

  return (
    <RunContext value={context}>
      <div className="page">
        <header className="page-header">
          <h1>Screening run</h1>
          <p>
            Run <span className="mono">{run.id}</span>, created{' '}
            {formatDateTime(new Date(run.createdAt))} IST.
          </p>
        </header>
        <RunSummary />
        <BloomStatsPanel />
        <AnalyticsPanel />
        <ThresholdPanel />
        <LiveTransactionPanel />
        <FlaggedTable />
        <RejectedRowsTable />
      </div>
    </RunContext>
  )
}

function isExpired(error: ApiError): boolean {
  return error.code === 'RUN_NOT_FOUND'
}

function failure(error: ApiError): LoadState {
  return isExpired(error) ? { status: 'expired' } : { status: 'failed', error }
}

export default RunPage
