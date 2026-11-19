import { Download, SearchX, ShieldCheck } from 'lucide-react'
import { useCallback, useState } from 'react'
import { ApiError, asApiError, getFlags } from '../../api/client'
import type { FlaggedTransaction, FlagType } from '../../api/types'
import { formatEpochSeconds, formatInteger, formatRupees } from '../../format'
import { useRun } from '../../run/RunContext'
import { downloadText, flagsAsCsv } from '../../run/exportFlags'
import { FLAG_LABELS, FLAG_TONES, FLAG_TYPES } from '../../run/flagTypes'
import { usePage } from '../../run/usePage'
import { ErrorAlert } from '../Alert'
import Badge from '../Badge'
import Button from '../Button'
import Card from '../Card'
import EmptyState from '../EmptyState'
import { SearchInput } from '../Input'
import Pagination from '../Pagination'
import SegmentedControl from '../SegmentedControl'
import type { SegmentedOption } from '../SegmentedControl'
import { TableSkeleton } from '../Skeleton'
import Table, { SortHeader } from '../Table'
import type { SortDirection } from '../Table'

const PAGE_SIZE = 50

type Filter = 'ALL' | FlagType

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

type SortKey = 'line' | 'amount' | 'time'

interface Sort {
  key: SortKey
  direction: SortDirection
}

const SORT_VALUES: Record<SortKey, (item: FlaggedTransaction) => number> = {
  line: (item) => item.lineNumber,
  amount: (item) => item.amountPaise,
  time: (item) => item.epochSeconds,
}

type ExportState =
  | { status: 'idle' }
  | { status: 'running'; fetched: number; total: number }
  | { status: 'failed'; error: ApiError }

/**
 * Flagged transactions, filtered by flag type and paged by the server. Search and sorting rearrange the page
 * in view; the export fetches every page of the current filter.
 */
function FlaggedTable() {
  const { run } = useRun()
  const [filter, setFilter] = useState<Filter>('ALL')
  const [page, setPage] = useState(0)
  const [attempt, setAttempt] = useState(0)
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<Sort | null>(null)
  const [exportState, setExportState] = useState<ExportState>({
    status: 'idle',
  })
  const type = filter === 'ALL' ? null : filter

  // Depends on the whole summary, not just its ID, so a refresh after a live transaction reloads the page.
  const load = useCallback(
    (signal: AbortSignal) => getFlags(run.id, type, page, PAGE_SIZE, signal),
    [run, type, page],
  )
  const { page: result, error } = usePage(`${filter}|${page}|${attempt}`, load)

  const allCount = FLAG_TYPES.reduce(
    (sum, flagType) => sum + run.flagCounts[flagType],
    0,
  )
  const filterCount = type === null ? allCount : run.flagCounts[type]
  const options: SegmentedOption<Filter>[] = [
    { value: 'ALL', label: 'All', count: formatInteger(allCount) },
    ...FLAG_TYPES.map((flagType) => ({
      value: flagType,
      label: FLAG_LABELS[flagType],
      count: formatInteger(run.flagCounts[flagType]),
      tone: FLAG_TONES[flagType],
    })),
  ]

  function changeFilter(next: Filter) {
    setFilter(next)
    setPage(0)
  }

  function toggleSort(key: SortKey) {
    setSort((current) => {
      if (current?.key !== key) {
        return { key, direction: 'ascending' }
      }
      return current.direction === 'ascending'
        ? { key, direction: 'descending' }
        : null
    })
  }

  async function exportCsv() {
    setExportState({ status: 'running', fetched: 0, total: filterCount })
    try {
      const csv = await flagsAsCsv(run.id, type, (fetched, total) =>
        setExportState({ status: 'running', fetched, total }),
      )
      const suffix = type === null ? '' : `-${type.toLowerCase()}`
      downloadText(csv, `txnscreen-${run.id}-flags${suffix}.csv`)
      setExportState({ status: 'idle' })
    } catch (caught) {
      setExportState({ status: 'failed', error: asApiError(caught) })
    }
  }

  const exporting = exportState.status === 'running'
  const rows = result === null ? [] : arrange(result.items, query, sort)
  const direction = (key: SortKey) =>
    sort?.key === key ? sort.direction : null

  return (
    <Card
      title="Flagged transactions"
      description="Every flag in screening order. A transaction with two flags appears once for each."
      flush
      actions={
        <Button
          variant="secondary"
          size="sm"
          loading={exporting}
          disabled={filterCount === 0}
          onClick={exportCsv}
        >
          {!exporting && <Download aria-hidden="true" />}
          {exporting
            ? `Exporting ${formatInteger(exportState.fetched)} of ${formatInteger(exportState.total)}`
            : 'Export CSV'}
        </Button>
      }
    >
      <div className="table-toolbar">
        <SegmentedControl
          label="Flag type"
          appearance="chips"
          options={options}
          value={filter}
          onChange={changeFilter}
        />
        <SearchInput
          label="Search the flagged transactions on this page"
          placeholder="Search this page"
          value={query}
          onChange={setQuery}
        />
      </div>
      {exportState.status === 'failed' && (
        <div className="card-inset">
          <ErrorAlert error={exportState.error} onRetry={exportCsv} />
        </div>
      )}
      {error ? (
        <div className="card-inset">
          <ErrorAlert
            error={error}
            onRetry={() => setAttempt((current) => current + 1)}
          />
        </div>
      ) : result === null ? (
        <TableSkeleton label="Loading flagged transactions" columns={7} />
      ) : result.totalItems === 0 ? (
        <EmptyState icon={ShieldCheck} title={EMPTY_STATES[filter].title}>
          {EMPTY_STATES[filter].body}
        </EmptyState>
      ) : (
        <>
          {rows.length === 0 ? (
            <EmptyState
              icon={SearchX}
              title="No rows on this page match"
              action={
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => setQuery('')}
                >
                  Clear search
                </Button>
              }
            >
              Nothing among the {formatInteger(result.items.length)} rows on
              this page matches &ldquo;{query}&rdquo;.
            </EmptyState>
          ) : (
            <Table label="Flagged transactions" scroll>
              <thead>
                <tr>
                  <SortHeader
                    label="Line"
                    numeric
                    direction={direction('line')}
                    onSort={() => toggleSort('line')}
                  />
                  <th scope="col">Transaction ID</th>
                  <th scope="col">Account</th>
                  <SortHeader
                    label="Amount (INR)"
                    numeric
                    direction={direction('amount')}
                    onSort={() => toggleSort('amount')}
                  />
                  <SortHeader
                    label="Time (IST)"
                    direction={direction('time')}
                    onSort={() => toggleSort('time')}
                  />
                  <th scope="col">Merchant</th>
                  <th scope="col">Flag</th>
                  <th scope="col">Detail</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((item) => (
                  <tr key={`${item.lineNumber}-${item.type}`}>
                    <td className="num mono">{item.lineNumber}</td>
                    <td className="mono">{item.transactionId}</td>
                    <td className="mono">{item.accountId}</td>
                    <td className="num mono">
                      {formatRupees(item.amountPaise)}
                    </td>
                    <td className="mono">
                      {formatEpochSeconds(item.epochSeconds)}
                    </td>
                    <td>{item.merchant}</td>
                    <td>
                      <Badge tone={FLAG_TONES[item.type]} dot>
                        {FLAG_LABELS[item.type]}
                      </Badge>
                    </td>
                    <td className="cell-muted">{item.detail}</td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
          <Pagination
            label="Flagged transaction pages"
            page={result.page}
            pageSize={result.size}
            totalItems={result.totalItems}
            totalPages={result.totalPages}
            noun="flags"
            onChange={setPage}
          />
        </>
      )}
    </Card>
  )
}

/** The page's rows that match the search, in the chosen order, or in screening order when there is none. */
function arrange(
  items: FlaggedTransaction[],
  query: string,
  sort: Sort | null,
): FlaggedTransaction[] {
  const needle = query.trim().toLowerCase()
  const matching =
    needle === ''
      ? items
      : items.filter((item) =>
          [
            item.transactionId,
            item.accountId,
            item.merchant,
            item.detail,
            FLAG_LABELS[item.type],
          ].some((text) => text.toLowerCase().includes(needle)),
        )
  if (sort === null) {
    return matching
  }
  const value = SORT_VALUES[sort.key]
  const sign = sort.direction === 'ascending' ? 1 : -1
  return matching.toSorted((a, b) => sign * (value(a) - value(b)))
}

export default FlaggedTable
