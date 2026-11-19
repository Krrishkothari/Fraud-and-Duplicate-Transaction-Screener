import { useRun } from '../../run/RunContext'
import FilterStatsCard from './FilterStatsCard'

/** Both Bloom filters of the run, side by side. */
function BloomStatsPanel() {
  const { run } = useRun()
  return (
    <div className="panel-stack">
      <p className="panel-note">
        A Bloom filter hit only marks a candidate. Every candidate was checked
        against the exact list before it was flagged.
      </p>
      <div className="filter-cards">
        <FilterStatsCard title="Blacklist filter" stats={run.blacklistStats} />
        <FilterStatsCard
          title="Duplicate ID filter"
          stats={run.duplicateStats}
        />
      </div>
    </div>
  )
}

export default BloomStatsPanel
