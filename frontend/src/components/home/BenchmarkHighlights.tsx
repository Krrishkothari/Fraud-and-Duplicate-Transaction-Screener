import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { medians } from '../../benchmarks/results'
import type { BenchmarkResults } from '../../benchmarks/results'
import {
  formatBytes,
  formatInteger,
  formatMeasured,
  formatPercent,
} from '../../format'

interface Highlight {
  value: string
  label: string
  /** The baseline or reference the value is read against. */
  detail: string
}

/** Three medians at the largest benchmarked size, straight from the committed results file. */
function BenchmarkHighlights({ results }: { results: BenchmarkResults }) {
  const last = results.settings.sizes.length - 1
  const size = formatInteger(results.settings.sizes[last])
  const at = (benchmark: string, variant: string, metric: string) =>
    medians(results, benchmark, variant, metric)[last]

  const bloomMemory = at('membership', 'bloom', 'memory')
  const hashSetMemory = at('membership', 'hashset', 'memoryIncludingKeys')
  const treeQuery = at('range', 'segtree', 'queryTime')
  const scanQuery = at('range', 'linearscan', 'queryTime')
  const measuredFpr = at('membership', 'bloom', 'measuredFpr')
  const theoreticalFpr = at('membership', 'bloom', 'theoreticalFpr')

  const highlights: Highlight[] = []
  if (bloomMemory !== undefined && hashSetMemory !== undefined) {
    highlights.push({
      value: formatBytes(bloomMemory),
      label: `Bloom filter memory for ${size} keys`,
      detail: `HashSet with its keys: ${formatBytes(hashSetMemory)}`,
    })
  }
  if (treeQuery !== undefined && scanQuery !== undefined) {
    highlights.push({
      value: `${formatMeasured(treeQuery)} µs`,
      label: `Segment tree window query over ${size} rows`,
      detail: `Linear scan: ${formatMeasured(scanQuery)} µs`,
    })
  }
  if (measuredFpr !== undefined && theoreticalFpr !== undefined) {
    highlights.push({
      value: formatPercent(measuredFpr),
      label: `Measured false positive rate for ${size} keys`,
      detail: `Theoretical rate: ${formatPercent(theoreticalFpr)}`,
    })
  }
  if (highlights.length === 0) {
    return null
  }

  return (
    <section className="highlights" aria-labelledby="highlights-title">
      <div className="container">
        <div className="highlights-header">
          <h2 id="highlights-title" className="highlights-title">
            Benchmark results
          </h2>
          <p className="highlights-note">
            Medians at the largest input size, from{' '}
            <code>docs/benchmarks/results.json</code>
          </p>
          <Link className="text-link highlights-link" to="/benchmarks">
            All benchmarks
            <ArrowRight aria-hidden="true" />
          </Link>
        </div>
        <dl className="highlights-grid">
          {highlights.map((item) => (
            <div key={item.label} className="highlight">
              <dt className="highlight-label">{item.label}</dt>
              <dd className="highlight-value mono">{item.value}</dd>
              <dd className="highlight-detail mono">{item.detail}</dd>
            </div>
          ))}
        </dl>
      </div>
    </section>
  )
}

export default BenchmarkHighlights
