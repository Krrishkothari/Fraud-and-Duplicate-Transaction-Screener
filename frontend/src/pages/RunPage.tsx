import { Clock, Plus } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError, asApiError, getRun } from '../api/client'
import type { RunSummary as RunSummaryData } from '../api/types'
import { ErrorAlert } from '../components/Alert'
import { ButtonLink } from '../components/Button'
import Card from '../components/Card'
import CopyButton from '../components/CopyButton'
import EmptyState from '../components/EmptyState'
import PageHeader from '../components/PageHeader'
import { Loading, Skeleton, TableSkeleton } from '../components/Skeleton'
import Tabs from '../components/Tabs'
import WakingNotice from '../components/WakingNotice'
import AnalyticsPanel from '../components/run/AnalyticsPanel'
import BloomStatsPanel from '../components/run/BloomStatsPanel'
import TransactionTable from '../components/run/TransactionTable'
import LiveTransactionPanel from '../components/run/LiveTransactionPanel'
import RejectedRowsTable from '../components/run/RejectedRowsTable'
import RunSummary from '../components/run/RunSummary'
import ThresholdPanel from '../components/run/ThresholdPanel'
import { formatDateTime, formatEpochSeconds, formatInteger } from '../format'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { RunContext } from '../run/RunContext'
import type { RunContextValue, TimeWindow } from '../run/RunContext'

type LoadState =
  | { status: 'loading' }
  | { status: 'loaded'; run: RunSummaryData }
  | { status: 'expired' }
  | { status: 'failed'; error: ApiError }

function RunPage() {
  const { id = '' } = useParams()
  useDocumentTitle('Screening run')
  // A new key gives each run ID fresh state, so one run's results never show under another's URL.
  return <RunView key={id} id={id} />
}

function RunView({ id }: { id: string }) {
  const [state, setState] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [slow, setSlow] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    getRun(id, {
      signal: controller.signal,
      onSlow: () => setSlow(true),
    }).then(
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
    setSlow(false)
    setAttempt((current) => current + 1)
  }

  switch (state.status) {
    case 'loading':
      return <RunSkeleton slow={slow} />
    case 'expired':
      return (
        <div className="container page">
          <PageHeader title="Screening run" />
          <Card>
            <EmptyState
              icon={Clock}
              title="This run has expired or does not exist."
              action={
                <ButtonLink to="/screen">Start a new screening</ButtonLink>
              }
            >
              <p>Runs are kept for up to 30 minutes.</p>
              <p>
                A run can also be cleared earlier, when the server restarts or
                newer runs push it out.
              </p>
            </EmptyState>
          </Card>
        </div>
      )
    case 'failed':
      return (
        <div className="container page">
          <PageHeader title="Screening run" />
          <ErrorAlert error={state.error} onRetry={retry} />
        </div>
      )
    case 'loaded':
      return <RunResults run={state.run} refresh={refresh} />
  }
}

/** The page's shape while the run summary loads, with a note once the load is slow. */
function RunSkeleton({ slow }: { slow: boolean }) {
  return (
    <div className="container page">
      <PageHeader
        title="Screening run"
        meta={<Skeleton width={320} height={10} />}
      />
      {slow && <WakingNotice />}
      <Loading label="Loading run">
        <div className="stat-grid">
          {Array.from({ length: 4 }, (_, index) => (
            <div key={index} className="stat-card">
              <Skeleton width="48%" height={10} />
              <Skeleton width="64%" height={24} />
            </div>
          ))}
        </div>
      </Loading>
      <Card title="Transactions" flush>
        <TableSkeleton label="Loading transactions" columns={7} />
      </Card>
    </div>
  )
}

interface RunResultsProps {
  run: RunSummaryData
  refresh: () => Promise<void>
}

function RunResults({ run, refresh }: RunResultsProps) {
  // Until a panel chooses a window, the selection follows the data range, so it grows with live appends.
  const [chosenWindow, setSelectedWindow] = useState<TimeWindow | null>(null)
  const { dataStart, dataEnd } = run
  const selectedWindow = useMemo<TimeWindow>(
    () => chosenWindow ?? { from: dataStart, to: dataEnd },
    [chosenWindow, dataStart, dataEnd],
  )
  const context = useMemo<RunContextValue>(
    () => ({ run, refresh, selectedWindow, setSelectedWindow }),
    [run, refresh, selectedWindow],
  )

  return (
    <RunContext value={context}>
      <div className="container page">
        <PageHeader
          title="Screening run"
          meta={
            <>
              <span className="meta-item">
                Run <code>{run.id}</code>
                <CopyButton value={run.id} label="Copy run ID" />
              </span>
              <span className="meta-item">
                Created {formatDateTime(new Date(run.createdAt))} IST
              </span>
              <span className="meta-item">
                Data from{' '}
                <span className="mono">
                  {formatEpochSeconds(run.dataStart)}
                </span>{' '}
                to{' '}
                <span className="mono">{formatEpochSeconds(run.dataEnd)}</span>{' '}
                IST
              </span>
            </>
          }
          actions={
            <ButtonLink to="/screen" variant="secondary">
              <Plus aria-hidden="true" />
              New screening
            </ButtonLink>
          }
        />
        <RunSummary />
        <TransactionTable />
        <Tabs
          label="Run details"
          tabs={[
            {
              id: 'windows',
              label: 'Time windows',
              content: <AnalyticsPanel />,
            },
            {
              id: 'threshold',
              label: 'High amount threshold',
              content: <ThresholdPanel />,
            },
            {
              id: 'filters',
              label: 'Bloom filters',
              content: <BloomStatsPanel />,
            },
            {
              id: 'live',
              label: 'Live transactions',
              content: <LiveTransactionPanel />,
            },
            {
              id: 'rejected',
              label: 'Rejected rows',
              count: formatInteger(run.rejectedRows),
              content: <RejectedRowsTable />,
            },
          ]}
        />
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
