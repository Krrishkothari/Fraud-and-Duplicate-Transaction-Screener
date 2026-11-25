import { ChartNoAxesColumn } from 'lucide-react'
import type { ReactNode } from 'react'
import { benchmarkResults, medians } from '../benchmarks/results'
import type { BenchmarkResults } from '../benchmarks/results'
import Card from '../components/Card'
import EmptyState from '../components/EmptyState'
import PageHeader from '../components/PageHeader'
import ComparisonChart from '../components/benchmarks/ComparisonChart'
import type { ChartSeries } from '../components/benchmarks/ComparisonChart'
import MetricTable from '../components/benchmarks/MetricTable'
import type { MetricGroup } from '../components/benchmarks/MetricTable'
import { formatInteger, formatMeasured, formatPercent } from '../format'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

const SECTIONS = [
  { id: 'membership', label: 'Membership' },
  { id: 'range-queries', label: 'Range queries' },
  { id: 'percentile-selection', label: 'Percentile selection' },
]

function BenchmarksPage() {
  useDocumentTitle('Benchmarks')
  return (
    <div className="container page">
      <PageHeader
        title="Benchmarks"
        description={
          <>
            Each algorithm against a simple baseline at three input sizes,
            measured once on one machine with the benchmark runner in this
            repository. Every number on this page comes from the committed file{' '}
            <code>docs/benchmarks/results.json</code>.
          </>
        }
      />
      {benchmarkResults === null ? (
        <Card>
          <EmptyState
            icon={ChartNoAxesColumn}
            title="No benchmark results yet"
          >
            Run the benchmark runner to create docs/benchmarks/results.json,
            then rebuild the site.
          </EmptyState>
        </Card>
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
      <nav className="bench-nav" aria-label="Benchmarks on this page">
        {SECTIONS.map((section) => (
          <a key={section.id} href={`#${section.id}`}>
            {section.label}
          </a>
        ))}
      </nav>

      <Section
        id={SECTIONS[0].id}
        title="Membership: Bloom filter against HashSet"
        verdict="A Bloom filter stores only bits, never the keys, so it needs a small fraction of a HashSet's memory; it is slower than a HashSet to insert and to look up, because it hashes every key and probes k bits, and it can report a key that is not there, which is why every hit is confirmed against an exact set before a flag is raised."
        layout="stacked"
        note="Why the HashSet's present-key lookups are slower than its absent-key ones: the benchmark looks up present keys through separate String objects in random order, so each hit must compare the stored key's text, which reads that key from memory, while an absent key is almost always ruled out by comparing hash codes, without reading any stored key."
        charts={
          <>
            <ComparisonChart
              title="Memory"
              unit="KB"
              sizes={sizes}
              format={formatMeasured}
              series={[
                series(
                  'Bloom filter',
                  'primary',
                  m('membership', 'bloom', 'memory').map(kb),
                ),
                series(
                  'HashSet, including keys',
                  'baseline',
                  m('membership', 'hashset', 'memoryIncludingKeys').map(kb),
                ),
              ]}
            />
            <ComparisonChart
              title="Lookup time for absent keys"
              unit="ns per lookup"
              sizes={sizes}
              format={formatMeasured}
              series={[
                series(
                  'Bloom filter',
                  'primary',
                  m('membership', 'bloom', 'queryTimeAbsent'),
                ),
                series(
                  'HashSet',
                  'baseline',
                  m('membership', 'hashset', 'queryTimeAbsent'),
                ),
              ]}
            />
          </>
        }
        groups={[
          single(
            'Bloom filter bits (m)',
            m('membership', 'bloom', 'bits'),
            formatInteger,
          ),
          single(
            'Bloom filter hash functions (k)',
            m('membership', 'bloom', 'hashes'),
            formatInteger,
          ),
          contest('Memory (KB)', formatMeasured, [
            row('Bloom filter', m('membership', 'bloom', 'memory').map(kb)),
            row(
              'HashSet, including keys',
              m('membership', 'hashset', 'memoryIncludingKeys').map(kb),
            ),
            row(
              'HashSet, excluding keys',
              m('membership', 'hashset', 'memoryExcludingKeys').map(kb),
            ),
          ]),
          contest('Insert all keys (ms)', formatMeasured, [
            row('Bloom filter', m('membership', 'bloom', 'insertTime')),
            row('HashSet', m('membership', 'hashset', 'insertTime')),
          ]),
          contest('Lookup, present key (ns)', formatMeasured, [
            row('Bloom filter', m('membership', 'bloom', 'queryTimePresent')),
            row('HashSet', m('membership', 'hashset', 'queryTimePresent')),
          ]),
          contest('Lookup, absent key (ns)', formatMeasured, [
            row('Bloom filter', m('membership', 'bloom', 'queryTimeAbsent')),
            row('HashSet', m('membership', 'hashset', 'queryTimeAbsent')),
          ]),
          {
            label: 'False-positive rate',
            format: (v) => formatPercent(v),
            rows: [
              row('Measured', m('membership', 'bloom', 'measuredFpr')),
              row('Theoretical', m('membership', 'bloom', 'theoreticalFpr')),
            ],
          },
          single(
            'False negatives',
            m('membership', 'bloom', 'falseNegatives'),
            formatInteger,
          ),
        ]}
        sizes={sizes}
      />

      <Section
        id={SECTIONS[1].id}
        title="Range queries: segment tree against linear scan"
        verdict="The segment tree answers any time window from precomputed bucket totals, so its query time barely changes as rows grow while the linear scan reads every row; for a single query on a small file it does not pay off, because building the tree takes longer than scanning once."
        layout="split"
        charts={
          <ComparisonChart
            title="Query time per window"
            unit="µs per query"
            sizes={sizes}
            format={formatMeasured}
            series={[
              series(
                'Segment tree',
                'primary',
                m('range', 'segtree', 'queryTime'),
              ),
              series(
                'Linear scan',
                'baseline',
                m('range', 'linearscan', 'queryTime'),
              ),
            ]}
          />
        }
        groups={[
          single(
            'Time buckets (B)',
            m('range', 'segtree', 'bucketCount'),
            formatInteger,
          ),
          single(
            'Segment tree build (ms)',
            m('range', 'segtree', 'buildTime'),
            formatMeasured,
          ),
          contest('Query per window (µs)', formatMeasured, [
            row('Segment tree', m('range', 'segtree', 'queryTime')),
            row('Linear scan', m('range', 'linearscan', 'queryTime')),
          ]),
          single(
            'Segment tree point update (ns)',
            m('range', 'segtree', 'updateTime'),
            formatMeasured,
          ),
          single(
            'Windows where the two disagree',
            m('range', 'segtree', 'mismatchedWindows'),
            formatInteger,
          ),
        ]}
        sizes={sizes}
      />

      <Section
        id={SECTIONS[2].id}
        title="Percentile selection: Quickselect against full sort"
        verdict="Quickselect finds one rank without ordering every amount, so it was faster than a full sort at every size on the generated amounts; when every amount is equal, Java's sort recognises input that is already in order and was faster at the larger sizes, although Quickselect still finished in one linear pass."
        layout="split"
        charts={
          <ComparisonChart
            title="P99 threshold time"
            unit="ms"
            sizes={sizes}
            format={formatMeasured}
            series={[
              series(
                'Randomized Quickselect',
                'primary',
                m('selection', 'quickselect', 'timeP99'),
              ),
              series(
                'Copy and full sort',
                'baseline',
                m('selection', 'sort', 'timeP99'),
              ),
            ]}
          />
        }
        groups={[
          contest('P95 threshold (ms)', formatMeasured, [
            row('Quickselect', m('selection', 'quickselect', 'timeP95')),
            row('Full sort', m('selection', 'sort', 'timeP95')),
          ]),
          contest('P99 threshold (ms)', formatMeasured, [
            row('Quickselect', m('selection', 'quickselect', 'timeP99')),
            row('Full sort', m('selection', 'sort', 'timeP99')),
          ]),
          contest('All amounts equal, P99 (ms)', formatMeasured, [
            row(
              'Quickselect',
              m('selection', 'quickselect', 'timeAllEqualP99'),
            ),
            row('Full sort', m('selection', 'sort', 'timeAllEqualP99')),
          ]),
          {
            label: 'Quickselect comparisons per element',
            format: formatMeasured,
            rows: [
              row('P95', m('selection', 'quickselect', 'comparisonsPerNP95')),
              row('P99', m('selection', 'quickselect', 'comparisonsPerNP99')),
              row(
                'All amounts equal, P99',
                m('selection', 'quickselect', 'comparisonsPerNAllEqualP99'),
              ),
            ],
          },
        ]}
        sizes={sizes}
      />
    </>
  )
}

interface SectionProps {
  id: string
  title: string
  verdict: string
  /** stacked puts the charts above a full-width table; split puts one chart beside the table. */
  layout: 'stacked' | 'split'
  charts: ReactNode
  groups: MetricGroup[]
  sizes: number[]
  /** A sentence under the table that explains a result which looks wrong at first sight. */
  note?: string
}

function Section({
  id,
  title,
  verdict,
  layout,
  charts,
  groups,
  sizes,
  note,
}: SectionProps) {
  return (
    <section className="bench-section" id={id} aria-labelledby={`${id}-title`}>
      <header className="bench-section-header">
        <h2 id={`${id}-title`}>{title}</h2>
        <p className="bench-verdict">{verdict}</p>
      </header>
      <div className={`bench-body bench-body-${layout}`}>
        <div className="charts">{charts}</div>
        <MetricTable label={title} sizes={sizes} groups={groups} />
      </div>
      {note && <p className="bench-note">{note}</p>}
    </section>
  )
}

function series(
  label: string,
  role: ChartSeries['role'],
  values: (number | undefined)[],
): ChartSeries {
  return { label, role, values }
}

function row(label: string, values: (number | undefined)[]) {
  return { label, values }
}

function single(
  label: string,
  values: (number | undefined)[],
  format: (value: number) => string,
): MetricGroup {
  return { label, format, rows: [{ label, values }] }
}

function contest(
  label: string,
  format: (value: number) => string,
  rows: { label: string; values: (number | undefined)[] }[],
): MetricGroup {
  return { label, format, rows, lowerWins: true }
}

function kb(bytes: number | undefined): number | undefined {
  return bytes === undefined ? undefined : bytes / 1024
}

export default BenchmarksPage
