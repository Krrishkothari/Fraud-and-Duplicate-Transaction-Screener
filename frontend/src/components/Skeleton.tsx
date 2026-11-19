import type { CSSProperties, ReactNode } from 'react'

interface SkeletonProps {
  width?: CSSProperties['width']
  height?: CSSProperties['height']
  className?: string
}

/** A gray placeholder block in the shape of content that is still loading. */
export function Skeleton({ width, height, className }: SkeletonProps) {
  return (
    <span
      className={className ? `skeleton ${className}` : 'skeleton'}
      style={{ width, height }}
      aria-hidden="true"
    />
  )
}

interface LoadingProps {
  /** Tells screen readers what is loading, such as "Loading flagged transactions". */
  label: string
  children: ReactNode
}

/** Wraps skeletons so screen readers hear what is loading instead of empty shapes. */
export function Loading({ label, children }: LoadingProps) {
  return (
    <div className="loading" role="status">
      <span className="visually-hidden">{label}</span>
      {children}
    </div>
  )
}

const CELL_WIDTHS = ['40%', '72%', '56%', '64%', '48%', '80%', '36%', '60%']

/** Placeholder rows for a table that is loading. */
export function TableSkeleton({
  label,
  rows = 6,
  columns = 6,
}: {
  label: string
  rows?: number
  columns?: number
}) {
  const grid = { gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }
  return (
    <Loading label={label}>
      <div className="table-skeleton">
        <div className="table-skeleton-head" style={grid}>
          {Array.from({ length: columns }, (_, column) => (
            <Skeleton key={column} width="48%" height={8} />
          ))}
        </div>
        {Array.from({ length: rows }, (_, row) => (
          <div key={row} className="table-skeleton-row" style={grid}>
            {Array.from({ length: columns }, (_, column) => (
              <Skeleton
                key={column}
                width={CELL_WIDTHS[(row + column) % CELL_WIDTHS.length]}
                height={10}
              />
            ))}
          </div>
        ))}
      </div>
    </Loading>
  )
}

/** Placeholder label and value pairs for a row of stats that is loading. */
export function StatsSkeleton({
  label,
  count = 4,
}: {
  label: string
  count?: number
}) {
  return (
    <Loading label={label}>
      <div className="stats-skeleton">
        {Array.from({ length: count }, (_, index) => (
          <div key={index} className="stats-skeleton-item">
            <Skeleton width="56%" height={10} />
            <Skeleton width="72%" height={20} />
          </div>
        ))}
      </div>
    </Loading>
  )
}
