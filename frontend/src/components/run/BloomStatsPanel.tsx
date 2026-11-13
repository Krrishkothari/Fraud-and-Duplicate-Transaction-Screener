import { useRun } from '../../run/RunContext'
import Card from '../Card'
import FilterStatsCard from './FilterStatsCard'

/** Both Bloom filters of the run, side by side. */
function BloomStatsPanel() {
  const { run } = useRun()
  return (
    <Card title="Bloom filters">
      <div className="card-stack">
        <div className="filter-cards">
          <FilterStatsCard
            title="Blacklist filter"
            stats={run.blacklistStats}
          />
          <FilterStatsCard
            title="Duplicate ID filter"
            stats={run.duplicateStats}
          />
        </div>
        <p className="panel-note">
          A Bloom filter hit only marks a candidate. Every candidate was checked
          against the exact list before it was flagged.
        </p>
      </div>
    </Card>
  )
}

export default BloomStatsPanel
