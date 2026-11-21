import { Download } from 'lucide-react'
import type { FlagType } from '../../api/types'
import { formatInteger, formatRupees } from '../../format'
import { FLAG_LABELS, FLAG_TONES, FLAG_TYPES } from '../../run/flagTypes'
import Badge from '../Badge'

/*
 * Every value below is copied from a real API response: POST /api/runs with data/samples/transactions_1000.csv,
 * data/samples/blacklist_1000.csv and the default settings, then two rows of each type from its flags. So the
 * drawing shows what that run really returned, not invented numbers.
 */
const FILES = 'transactions_1000.csv with blacklist_1000.csv'

const STATS = [
  { label: 'Valid rows', value: 1_000 },
  { label: 'Flags raised', value: 80 },
  { label: 'Rejected rows', value: 0 },
]

interface PreviewRow {
  transactionId: string
  accountId: string
  amountPaise: number
  type: FlagType
}

const ROWS: PreviewRow[] = [
  {
    transactionId: 'TXN00000067',
    accountId: 'ACC000033',
    amountPaise: 72_996,
    type: 'DUPLICATE',
  },
  {
    transactionId: 'TXN00000639',
    accountId: 'ACC000040',
    amountPaise: 51_116,
    type: 'BLACKLISTED',
  },
  {
    transactionId: 'TXN00000318',
    accountId: 'ACC000021',
    amountPaise: 5_491_610,
    type: 'HIGH_AMOUNT',
  },
  {
    transactionId: 'TXN00000314',
    accountId: 'ACC000048',
    amountPaise: 386_376,
    type: 'DUPLICATE',
  },
  {
    transactionId: 'TXN00000339',
    accountId: 'ACC000034',
    amountPaise: 165_787,
    type: 'BLACKLISTED',
  },
  {
    transactionId: 'TXN00000758',
    accountId: 'ACC000021',
    amountPaise: 12_385_310,
    type: 'HIGH_AMOUNT',
  },
]

/** A drawing of the run page, built from the app's own components and filled with one real run's results. */
function ProductPreview() {
  return (
    <figure className="preview">
      <div className="preview-window" aria-hidden="true">
        <div className="preview-header">
          <div>
            <p className="preview-title">Screening run</p>
            <p className="preview-meta">{FILES}</p>
          </div>
          <span className="preview-button">
            <Download />
            Export CSV
          </span>
        </div>
        <div className="preview-stats">
          {STATS.map((stat) => (
            <div key={stat.label} className="preview-stat">
              <span className="preview-stat-label">{stat.label}</span>
              <span className="preview-stat-value mono">
                {formatInteger(stat.value)}
              </span>
            </div>
          ))}
        </div>
        <div className="preview-chips">
          <span className="preview-chip preview-chip-active">All</span>
          {FLAG_TYPES.map((type) => (
            <span key={type} className="preview-chip">
              <span className={`dot dot-${FLAG_TONES[type]}`} />
              {FLAG_LABELS[type]}
            </span>
          ))}
        </div>
        <div className="preview-table">
          <div className="preview-row preview-row-head">
            <span>Transaction ID</span>
            <span>Account</span>
            <span className="preview-num">Amount (INR)</span>
            <span>Flag</span>
          </div>
          {ROWS.map((row) => (
            <div key={row.transactionId} className="preview-row">
              <span className="preview-cell mono">{row.transactionId}</span>
              <span className="preview-cell mono">{row.accountId}</span>
              <span className="preview-num mono">
                {formatRupees(row.amountPaise)}
              </span>
              <span>
                <Badge tone={FLAG_TONES[row.type]} dot>
                  {FLAG_LABELS[row.type]}
                </Badge>
              </span>
            </div>
          ))}
        </div>
      </div>
      <figcaption className="visually-hidden">
        A drawing of the run page, where every flagged transaction is listed
        with the reason it was flagged.
      </figcaption>
    </figure>
  )
}

export default ProductPreview
