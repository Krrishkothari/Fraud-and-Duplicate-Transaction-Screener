import { useCallback, useState } from 'react'
import { getFlags } from '../../api/client'
import type { FlagType } from '../../api/types'
import { formatEpochSeconds, formatInteger, formatRupees } from '../../format'
import { useRun } from '../../run/RunContext'
import { FLAG_LABELS, FLAG_TYPES } from '../../run/flagTypes'
import { usePage } from '../../run/usePage'
import Alert from '../Alert'
import Badge from '../Badge'
import Card from '../Card'
import EmptyState from '../EmptyState'
import Pagination from '../Pagination'
import SegmentedControl from '../SegmentedControl'
import Spinner from '../Spinner'
import Table from '../Table'

const PAGE_SIZE = 50

type Filter = 'ALL' | FlagType

const FLAG_TONES: Record<FlagType, 'danger' | 'warning' | 'info'> = {
  BLACKLISTED: 'danger',
  DUPLICATE: 'warning',
  HIGH_AMOUNT: 'info',
}

const EMPTY_STATES: Record<Filter, { title: string; body: string }> = {
  ALL: {
    title: 'No flagged transactions',
    body: 'No transaction was blacklisted, duplicated or above the high amount threshold.',
  },
  BLACKLISTED: {
    title: 'No blacklisted transactions',
    body: 'No transaction came from an account on the blacklist.',
  },
  DUPLICATE: {
    title: 'No duplicate transactions',
    body: 'No transaction ID appeared more than once.',
  },
  HIGH_AMOUNT: {
    title: 'No high amount transactions',
    body: 'No amount was above the high amount threshold.',
  },
}

/** Flagged transactions, filtered by flag type and paged by the server. */
function FlaggedTable() {
  const { run } = useRun()
  const [filter, setFilter] = useState<Filter>('ALL')
  const [page, setPage] = useState(0)
  const type = filter === 'ALL' ? null : filter

  // Depends on the whole summary, not just its ID, so a refresh after a live transaction reloads the page.
  const load = useCallback(
    (signal: AbortSignal) => getFlags(run.id, type, page, PAGE_SIZE, signal),
    [run, type, page],
  )
  const { page: result, error } = usePage(`${filter}|${page}`, load)

  const allCount = FLAG_TYPES.reduce(
    (sum, flagType) => sum + run.flagCounts[flagType],
    0,
  )
  const options: { value: Filter; label: string }[] = [
    { value: 'ALL', label: `All (${formatInteger(allCount)})` },
    ...FLAG_TYPES.map((flagType) => ({
      value: flagType,
      label: `${FLAG_LABELS[flagType]} (${formatInteger(run.flagCounts[flagType])})`,
    })),
  ]

  function changeFilter(next: Filter) {
    setFilter(next)
    setPage(0)
  }

  return (
    <Card title="Flagged transactions">
      <div className="card-stack">
        <SegmentedControl
          label="Flag type"
          options={options}
          value={filter}
          onChange={changeFilter}
        />
        {error ? (
          <Alert tone="danger" title={error.code}>
            {error.message}
          </Alert>
        ) : result === null ? (
          <div className="panel-loading">
            <Spinner label="Loading flagged transactions" />
          </div>
        ) : result.totalItems === 0 ? (
          <EmptyState title={EMPTY_STATES[filter].title}>
            {EMPTY_STATES[filter].body}
          </EmptyState>
        ) : (
          <>
            <Table label="Flagged transactions">
              <thead>
                <tr>
                  <th scope="col" className="num">
                    Line
                  </th>
                  <th scope="col">Transaction ID</th>
                  <th scope="col">Account</th>
                  <th scope="col" className="num">
                    Amount (INR)
                  </th>
                  <th scope="col">Time (IST)</th>
                  <th scope="col">Merchant</th>
                  <th scope="col">Flag</th>
                  <th scope="col">Detail</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((item) => (
                  <tr key={`${item.lineNumber}-${item.type}`}>
                    <td className="num">{item.lineNumber}</td>
                    <td className="mono">{item.transactionId}</td>
                    <td className="mono">{item.accountId}</td>
                    <td className="num">{formatRupees(item.amountPaise)}</td>
                    <td className="mono">
                      {formatEpochSeconds(item.epochSeconds)}
                    </td>
                    <td>{item.merchant}</td>
                    <td>
                      <Badge tone={FLAG_TONES[item.type]}>
                        {FLAG_LABELS[item.type]}
                      </Badge>
                    </td>
                    <td>{item.detail}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <Pagination
              label="Flagged transaction pages"
              page={result.page}
              totalPages={result.totalPages}
              onChange={setPage}
            />
          </>
        )}
      </div>
    </Card>
  )
}

export default FlaggedTable
