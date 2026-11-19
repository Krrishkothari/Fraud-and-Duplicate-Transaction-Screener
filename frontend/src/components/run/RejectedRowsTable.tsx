import { CircleCheck } from 'lucide-react'
import { useCallback, useState } from 'react'
import { getRejected } from '../../api/client'
import type { RejectReason } from '../../api/types'
import { useRun } from '../../run/RunContext'
import { usePage } from '../../run/usePage'
import { ErrorAlert } from '../Alert'
import Badge from '../Badge'
import Card from '../Card'
import EmptyState from '../EmptyState'
import Pagination from '../Pagination'
import { TableSkeleton } from '../Skeleton'
import Table from '../Table'

const PAGE_SIZE = 50

const REASON_LABELS: Record<RejectReason, string> = {
  MISSING_FIELD: 'Missing field',
  INVALID_AMOUNT: 'Invalid amount',
  AMOUNT_NOT_POSITIVE: 'Amount not positive',
  INVALID_TIMESTAMP: 'Invalid timestamp',
}

/** The rows that failed validation, loaded when their tab first opens and paged by the server. */
function RejectedRowsTable() {
  const { run } = useRun()
  const [page, setPage] = useState(0)
  const [attempt, setAttempt] = useState(0)

  const load = useCallback(
    (signal: AbortSignal) => getRejected(run.id, page, PAGE_SIZE, signal),
    [run.id, page],
  )
  const { page: result, error } = usePage(
    run.rejectedRows === 0 ? null : `${page}|${attempt}`,
    load,
  )

  if (run.rejectedRows === 0) {
    return (
      <Card>
        <EmptyState
          icon={CircleCheck}
          title="Every row in the file was valid."
        />
      </Card>
    )
  }

  return (
    <Card
      title="Rejected rows"
      description="Rows that failed validation, in file order."
      headingLevel={3}
      flush
    >
      {error ? (
        <div className="card-inset">
          <ErrorAlert
            error={error}
            onRetry={() => setAttempt((current) => current + 1)}
          />
        </div>
      ) : result === null ? (
        <TableSkeleton label="Loading rejected rows" columns={3} />
      ) : (
        <>
          <Table label="Rejected rows" scroll>
            <thead>
              <tr>
                <th scope="col" className="num">
                  Line
                </th>
                <th scope="col">Reason</th>
                <th scope="col">Raw row</th>
              </tr>
            </thead>
            <tbody>
              {result.items.map((row) => (
                <tr key={row.lineNumber}>
                  <td className="num mono">{row.lineNumber}</td>
                  <td>
                    <Badge tone="neutral" dot>
                      {REASON_LABELS[row.reason]}
                    </Badge>
                  </td>
                  <td className="mono cell-raw">{row.raw}</td>
                </tr>
              ))}
            </tbody>
          </Table>
          <Pagination
            label="Rejected row pages"
            page={result.page}
            pageSize={result.size}
            totalItems={result.totalItems}
            totalPages={result.totalPages}
            noun="rows"
            onChange={setPage}
          />
        </>
      )}
    </Card>
  )
}

export default RejectedRowsTable
