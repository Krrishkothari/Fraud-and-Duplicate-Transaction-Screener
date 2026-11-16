import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { benchmarkResults, medians } from '../benchmarks/results'
import type { BenchmarkResults } from '../benchmarks/results'
import Card from '../components/Card'
import EmptyState from '../components/EmptyState'
import ComparisonChart from '../components/benchmarks/ComparisonChart'
import type { ChartSeries } from '../components/benchmarks/ComparisonChart'
import EnvironmentStrip from '../components/benchmarks/EnvironmentStrip'
import MetricTable from '../components/benchmarks/MetricTable'
import type { MetricRow } from '../components/benchmarks/MetricTable'
import { CHART_COLORS } from '../components/run/chartData'
import { formatInteger, formatPercent } from '../format'

const OURS = 'var(--accent)'
const BASELINE = CHART_COLORS.count

function BenchmarksPage() {
  return (
    <div className="page">
      <header className="page-header">
        <h1>Benchmarks</h1>
        <p>
          Each algorithm against a simple baseline at three input sizes,
          measured once on one machine with the benchmark runner in this
          repository. Every number on this page comes from the committed file
          docs/benchmarks/results.json.
        </p>
      </header>
      {benchmarkResults === null ? (
        <EmptyState
          title="No benchmark results yet"
          action={
            <Link className="button button-secondary" to="/method">
              Read how each algorithm works
            </Link>
          }
        >
          Run the benchmark runner to create docs/benchmarks/results.json, then
          rebuild the site.
        </EmptyState>
      ) : (
        <Results results={benchmarkResults} />
      )}
    </div>
  )
}

function Results({ results }: { results: BenchmarkResults }) {
  const sizes = results.settings.sizes
  const m = (benchmark: string, variant: string, metric: string) =>
    medians(results, benchmark, variant, metric)

  return (
    <>
      <EnvironmentStrip
        environment={results.environment}
        settings={results.settings}
      />

      <Section
        title="Membership: Bloom filter against HashSet"
        verdict="A Bloom filter stores only bits, never the keys, so it needs a small fraction of a HashSet's memory; it is slower than a HashSet to insert and to look up, because it hashes every key and probes k bits, and it can report a key that is not there, which is why every hit is confirmed against an exact set before a flag is raised."
        charts={
          <>
            <ComparisonChart
              title="Memory"
              unit="KB"
              sizes={sizes}
              format={decimal}
              series={[
                series(
                  'Bloom filter',
                  OURS,
                  m('membership', 'bloom', 'memory').map(kb),
                ),
                series(
                  'HashSet, including keys',
                  BASELINE,
                  m('membership', 'hashset', 'memoryIncludingKeys').map(kb),
                  true,
                ),
              ]}
            />
            <ComparisonChart
              title="Lookup time for absent keys"
              unit="ns per lookup"
              sizes={sizes}
              format={decimal}
              series={[
                series(
                  'Bloom filter',
                  OURS,
                  m('membership', 'bloom', 'queryTimeAbsent'),
                ),
                series(
                  'HashSet',
                  BASELINE,
                  m('membership', 'hashset', 'queryTimeAbsent'),
                  true,
                ),
              ]}
            />
          </>
        }
        rows={[
          row(
            'Bloom filter bits (m)',
            m('membership', 'bloom', 'bits'),
            formatInteger,
          ),
          row(
            'Bloom filter hash functions (k)',
            m('membership', 'bloom', 'hashes'),
            formatInteger,
          ),
          row(
            'Bloom filter memory (KB)',
            m('membership', 'bloom', 'memory').map(kb),
            decimal,
          ),
          row(
            'HashSet memory, including keys (KB)',
            m('membership', 'hashset', 'memoryIncludingKeys').map(kb),
            decimal,
          ),
          row(
            'HashSet memory, excluding keys (KB)',
            m('membership', 'hashset', 'memoryExcludingKeys').map(kb),
            decimal,
          ),
          row(
            'Bloom filter insert all keys (ms)',
            m('membership', 'bloom', 'insertTime'),
            decimal,
          ),
          row(
            'HashSet insert all keys (ms)',
            m('membership', 'hashset', 'insertTime'),
            decimal,
          ),
          row(
            'Bloom filter lookup, present key (ns)',
            m('membership', 'bloom', 'queryTimePresent'),
            decimal,
          ),
          row(
            'HashSet lookup, present key (ns)',
            m('membership', 'hashset', 'queryTimePresent'),
            decimal,
          ),
          row(
            'Bloom filter lookup, absent key (ns)',
            m('membership', 'bloom', 'queryTimeAbsent'),
            decimal,
          ),
          row(
            'HashSet lookup, absent key (ns)',
            m('membership', 'hashset', 'queryTimeAbsent'),
            decimal,
          ),
          row(
            'Measured false-positive rate',
            m('membership', 'bloom', 'measuredFpr'),
            (v) => formatPercent(v),
          ),
          row(
            'Theoretical false-positive rate',
            m('membership', 'bloom', 'theoreticalFpr'),
            (v) => formatPercent(v),
          ),
          row(
            'False negatives',
            m('membership', 'bloom', 'falseNegatives'),
            formatInteger,
          ),
        ]}
        sizes={sizes}
      />

      <Section
        title="Range queries: segment tree against linear scan"
        verdict="The segment tree answers any time window from precomputed bucket totals, so its query time barely changes as rows grow while the linear scan reads every row; for a single query on a small file it does not pay off, because building the tree takes longer than scanning once."
        charts={
          <ComparisonChart
            title="Query time per window"
            unit="µs per query"
            sizes={sizes}
            format={decimal}
            series={[
              series('Segment tree', OURS, m('range', 'segtree', 'queryTime')),
              series(
                'Linear scan',
                BASELINE,
                m('range', 'linearscan', 'queryTime'),
                true,
              ),
            ]}
          />
        }
        rows={[
          row(
            'Time buckets (B)',
            m('range', 'segtree', 'bucketCount'),
            formatInteger,
          ),
          row(
            'Segment tree build (ms)',
            m('range', 'segtree', 'buildTime'),
            decimal,
          ),
          row(
            'Segment tree query (µs)',
            m('range', 'segtree', 'queryTime'),
            decimal,
          ),
          row(
            'Linear scan query (µs)',
            m('range', 'linearscan', 'queryTime'),
            decimal,
          ),
          row(
            'Segment tree point update (ns)',
            m('range', 'segtree', 'updateTime'),
            decimal,
          ),
          row(
            'Windows where the two disagree',
            m('range', 'segtree', 'mismatchedWindows'),
            formatInteger,
          ),
        ]}
        sizes={sizes}
      />

      <Section
        title="Percentile selection: Quickselect against full sort"
        verdict="Quickselect finds one rank without ordering every amount, so it was faster than a full sort at every size on the generated amounts; when every amount is equal, Java's sort recognises input that is already in order and was faster at the larger sizes, although Quickselect still finished in one linear pass."
        charts={
          <ComparisonChart
            title="P99 threshold time"
            unit="ms"
            sizes={sizes}
            format={decimal}
            series={[
              series(
                'Randomized Quickselect',
                OURS,
                m('selection', 'quickselect', 'timeP99'),
              ),
              series(
                'Copy and full sort',
                BASELINE,
                m('selection', 'sort', 'timeP99'),
                true,
              ),
            ]}
          />
        }
        rows={[
          row(
            'Quickselect P95 (ms)',
            m('selection', 'quickselect', 'timeP95'),
            decimal,
          ),
          row('Full sort P95 (ms)', m('selection', 'sort', 'timeP95'), decimal),
          row(
            'Quickselect P99 (ms)',
            m('selection', 'quickselect', 'timeP99'),
            decimal,
          ),
          row('Full sort P99 (ms)', m('selection', 'sort', 'timeP99'), decimal),
          row(
            'Comparisons per element, P95',
            m('selection', 'quickselect', 'comparisonsPerNP95'),
            decimal,
          ),
          row(
            'Comparisons per element, P99',
            m('selection', 'quickselect', 'comparisonsPerNP99'),
            decimal,
          ),
          row(
            'Quickselect, all amounts equal (ms)',
            m('selection', 'quickselect', 'timeAllEqualP99'),
            decimal,
          ),
          row(
            'Full sort, all amounts equal (ms)',
            m('selection', 'sort', 'timeAllEqualP99'),
            decimal,
          ),
          row(
            'Comparisons per element, all amounts equal',
            m('selection', 'quickselect', 'comparisonsPerNAllEqualP99'),
            decimal,
          ),
        ]}
        sizes={sizes}
      />
    </>
  )
}

interface SectionProps {
  title: string
  verdict: string
  charts: ReactNode
  rows: MetricRow[]
  sizes: number[]
}

function Section({ title, verdict, charts, rows, sizes }: SectionProps) {
  return (
    <Card title={title}>
      <div className="card-stack">
        <p className="section-verdict">{verdict}</p>
        <div className="charts">{charts}</div>
        <MetricTable label={title} sizes={sizes} rows={rows} />
      </div>
    </Card>
  )
}

function series(
  label: string,
  color: string,
  values: (number | undefined)[],
  dashed = false,
): ChartSeries {
  return { label, color, values, dashed }
}

function row(
  label: string,
  values: (number | undefined)[],
  format: (value: number) => string,
): MetricRow {
  return { label, values, format }
}

function kb(bytes: number | undefined): number | undefined {
  return bytes === undefined ? undefined : bytes / 1024
}

const decimals = [3, 2, 1].map(
  (digits) =>
    new Intl.NumberFormat('en-IN', {
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    }),
)

/** A measured value with about 4 significant digits: 0.153, 5.01, 58.8, 1,234.5. */
function decimal(value: number): string {
  if (value < 1) {
    return decimals[0].format(value)
  }
  if (value < 100) {
    return decimals[1].format(value)
  }
  return decimals[2].format(value)
}

export default BenchmarksPage
