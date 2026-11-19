import { Download } from 'lucide-react'
import type { FlagType } from '../../api/types'
import { FLAG_LABELS, FLAG_TONES, FLAG_TYPES } from '../../run/flagTypes'
import Badge from '../Badge'
import { Skeleton } from '../Skeleton'

interface PreviewRow {
  type: FlagType
  /** Widths of the placeholder values, so the rows do not look stamped out. */
  widths: [string, string, string]
}

const ROWS: PreviewRow[] = [
  { type: 'DUPLICATE', widths: ['74%', '58%', '46%'] },
  { type: 'BLACKLISTED', widths: ['66%', '70%', '54%'] },
  { type: 'HIGH_AMOUNT', widths: ['80%', '52%', '72%'] },
  { type: 'DUPLICATE', widths: ['62%', '64%', '40%'] },
  { type: 'BLACKLISTED', widths: ['70%', '56%', '60%'] },
  { type: 'HIGH_AMOUNT', widths: ['58%', '68%', '66%'] },
]

const STATS = ['Valid rows', 'Flags raised', 'Rejected rows']

/**
 * A drawing of the run page, built from the app's own components. Values are placeholder bars: every
 * number in the app comes from the API, and this page has no run to show.
 */
function ProductPreview() {
  return (
    <figure className="preview">
      <div className="preview-window" aria-hidden="true">
        <div className="preview-header">
          <div>
            <p className="preview-title">Screening run</p>
            <Skeleton width={148} height={8} />
          </div>
          <span className="preview-button">
            <Download />
            Export CSV
          </span>
        </div>
        <div className="preview-stats">
          {STATS.map((label) => (
            <div key={label} className="preview-stat">
              <span className="preview-stat-label">{label}</span>
              <Skeleton width="64%" height={14} />
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
          {ROWS.map((row, index) => (
            <div key={index} className="preview-row">
              <Skeleton width={row.widths[0]} height={8} />
              <Skeleton width={row.widths[1]} height={8} />
              <span className="preview-num">
                <Skeleton width={row.widths[2]} height={8} />
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
