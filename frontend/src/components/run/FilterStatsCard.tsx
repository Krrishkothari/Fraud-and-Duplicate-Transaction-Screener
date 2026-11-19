import type { ReactNode } from 'react'
import type { FilterStats } from '../../api/types'
import { formatBytes, formatInteger, formatPercent } from '../../format'
import Alert from '../Alert'
import Badge from '../Badge'
import Card from '../Card'
import InfoTip from '../InfoTip'

const FPR_EXPLANATION =
  "Theoretical FPR is the rate predicted from the filter's bits, hash functions and items inserted, while measured FPR is the share of this run's lookups for absent keys that the filter still marked as possibly present."

interface FilterStatsCardProps {
  title: string
  stats: FilterStats
}

/** One Bloom filter's size, fill, false-positive rates and lookup counts, as the run summary reports them. */
function FilterStatsCard({ title, stats }: FilterStatsCardProps) {
  return (
    <Card
      title={title}
      headingLevel={3}
      actions={
        stats.saturated && (
          <Badge tone="warning" dot>
            Over capacity
          </Badge>
        )
      }
    >
      {stats.saturated && stats.suggestedBits !== null && (
        <div className="filter-card-alert">
          <Alert tone="warning">
            This filter is over capacity. Results are still exact because every
            hit is confirmed, but more lookups reach the exact store. Suggested
            size: {formatInteger(stats.suggestedBits)} bits.
          </Alert>
        </div>
      )}
      <StatGroup title="Size">
        <StatRow label="Bits (m)">{formatInteger(stats.bitSize)}</StatRow>
        <StatRow label="Hash functions (k)">{stats.hashCount}</StatRow>
        <StatRow label="Memory">{formatBytes(stats.memoryBytes)}</StatRow>
        <StatRow label="Items inserted">
          {formatInteger(stats.inserted)}
        </StatRow>
        <StatRow label="Fill ratio">
          <span className="fill-bar" aria-hidden="true">
            <span
              className="fill-bar-value"
              style={{ width: `${stats.fillRatio * 100}%` }}
            />
          </span>
          {formatPercent(stats.fillRatio, 1)}
        </StatRow>
      </StatGroup>
      <StatGroup title="False positive rate">
        <StatRow label="Theoretical FPR">
          {formatPercent(stats.theoreticalFpr)}
        </StatRow>
        <StatRow
          label={
            <>
              Measured FPR
              <InfoTip label="Measured and theoretical FPR">
                {FPR_EXPLANATION}
              </InfoTip>
            </>
          }
        >
          {formatPercent(stats.measuredFpr)}
        </StatRow>
      </StatGroup>
      <StatGroup title="Lookups">
        <StatRow label="Bloom positives">
          {formatInteger(stats.bloomPositives)}
        </StatRow>
        <StatRow label="Confirmed hits">
          {formatInteger(stats.confirmedHits)}
        </StatRow>
        <StatRow label="False positives">
          {formatInteger(stats.falsePositives)}
        </StatRow>
        <StatRow label="Exact-store lookups">
          {formatInteger(stats.authoritativeLookups)}
        </StatRow>
        <StatRow label="Lookups avoided">
          {formatInteger(stats.lookupsAvoided)}
        </StatRow>
      </StatGroup>
    </Card>
  )
}

function StatGroup({
  title,
  children,
}: {
  title: string
  children: ReactNode
}) {
  return (
    <section className="stat-group">
      <h4 className="stat-group-title">{title}</h4>
      <dl className="stat-rows">{children}</dl>
    </section>
  )
}

function StatRow({
  label,
  children,
}: {
  label: ReactNode
  children: ReactNode
}) {
  return (
    <div className="stat-row">
      <dt>{label}</dt>
      <dd className="mono">{children}</dd>
    </div>
  )
}

export default FilterStatsCard
