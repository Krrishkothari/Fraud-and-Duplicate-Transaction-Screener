import { ChevronRight } from 'lucide-react'
import { useCallback, useState } from 'react'
import { getRejected } from '../../api/client'
import type { RejectReason } from '../../api/types'
import { formatInteger } from '../../format'
import { useRun } from '../../run/RunContext'
import { usePage } from '../../run/usePage'
import Alert from '../Alert'
import Pagination from '../Pagination'
import Spinner from '../Spinner'
import Table from '../Table'

const PAGE_SIZE = 50

const REASON_LABELS: Record<RejectReason, string> = {
  MISSING_FIELD: 'Missing field',
  INVALID_AMOUNT: 'Invalid amount',
  AMOUNT_NOT_POSITIVE: 'Amount not positive',
  INVALID_TIMESTAMP: 'Invalid timestamp',
}

/** The rows that failed validation, collapsed until opened and paged by the server. */
function RejectedRowsTable() {
  const { run } = useRun()
  const [expanded, setExpanded] = useState(false)
  const [page, setPage] = useState(0)

  const load = useCallback(
    (signal: AbortSignal) => getRejected(run.id, page, PAGE_SIZE, signal),
    [run.id, page],
  )
  const { page: result, error } = usePage(expanded ? String(page) : null, load)

  if (run.rejectedRows === 0) {
    return (
      <section className="card">
        <h2 className="card-title">Rejected rows</h2>
        <p className="panel-note">Every row in the file was valid.</p>
      </section>
    )
  }

  return (
    <section className="card">
      <h2 className="card-title disclosure-title">
        <button
          type="button"
          className="disclosure"
          aria-expanded={expanded}
          onClick={() => setExpanded(!expanded)}
        >
          <ChevronRight
            className="disclosure-icon"
            size={18}
            aria-hidden="true"
          />
          Rejected rows
          <span className="disclosure-count mono">
            {formatInteger(run.rejectedRows)}
          </span>
        </button>
      </h2>
      {expanded && (
        <div className="card-stack disclosure-body">
          {error ? (
            <Alert tone="danger" title={error.code}>
              {error.message}
            </Alert>
          ) : result === null ? (
            <div className="panel-loading">
              <Spinner label="Loading rejected rows" />
            </div>
          ) : (
            <>
              <Table label="Rejected rows">
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
                      <td className="num">{row.lineNumber}</td>
                      <td>{REASON_LABELS[row.reason]}</td>
                      <td className="mono">{row.raw}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
              <Pagination
                label="Rejected row pages"
                page={result.page}
                totalPages={result.totalPages}
                onChange={setPage}
              />
            </>
          )}
        </div>
      )}
    </section>
  )
}

export default RejectedRowsTable
