import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import Card from '../components/Card'
import Table from '../components/Table'

interface Cost {
  operation: string
  time: string
  space: string
  note: string
}

const SECTIONS = [
  { id: 'bloom-filter', label: 'Bloom filter' },
  { id: 'segment-tree', label: 'Segment tree' },
  { id: 'quickselect', label: 'Randomized Quickselect' },
  { id: 'limitations', label: 'Limitations' },
]

const BLOOM_COSTS: Cost[] = [
  {
    operation: 'Create an empty filter',
    time: 'O(m / 64)',
    space: 'O(m) bits',
    note: 'Allocates ceil(m / 64) 64-bit words.',
  },
  {
    operation: 'Add a key',
    time: 'O(k)',
    space: 'O(1)',
    note: 'After one O(len) hash of the key.',
  },
  {
    operation: 'Look up a key',
    time: 'O(k)',
    space: 'O(1)',
    note: 'After one O(len) hash. Stops at the first bit that is 0.',
  },
  {
    operation: 'Confirm a positive',
    time: 'O(len) expected',
    space: 'O(1)',
    note: 'One exact store lookup, only when the filter answers possibly present.',
  },
  {
    operation: 'Fill ratio and estimated rate',
    time: 'O(m / 64)',
    space: 'O(1)',
    note: 'Counts the set bits one word at a time.',
  },
]

const SEGMENT_TREE_COSTS: Cost[] = [
  {
    operation: 'Build the index',
    time: 'O(n + B)',
    space: 'O(B)',
    note: 'Buckets the transactions, then builds the tree bottom up.',
  },
  {
    operation: 'Window query',
    time: 'O(log B)',
    space: 'O(1)',
    note: 'Sum, count and maximum, from at most two nodes per level.',
  },
  {
    operation: 'Chart of c windows',
    time: 'O(c log B)',
    space: 'O(c)',
    note: 'One window query per point.',
  },
  {
    operation: 'Add a live transaction',
    time: 'O(log B)',
    space: 'O(1)',
    note: 'Updates one leaf and recomputes its ancestors.',
  },
  {
    operation: 'Grow the index',
    time: 'O(B), O(1) amortized',
    space: 'O(B)',
    note: 'Only when a timestamp lands past the last bucket.',
  },
]

const QUICKSELECT_COSTS: Cost[] = [
  {
    operation: 'Find the p-th percentile',
    time: 'O(n) expected',
    space: 'O(n)',
    note: 'Works on a copy of the amounts. The worst case is O(n^2), with vanishing probability.',
  },
  {
    operation: 'Compute the rank k',
    time: 'O(1)',
    space: 'O(1)',
    note: 'Exact decimal arithmetic.',
  },
  {
    operation: 'Percentile for a time window',
    time: 'O(log n + w) expected',
    space: 'O(w)',
    note: 'Binary search finds the window, then Quickselect runs on its w amounts.',
  },
]

function MethodPage() {
  return (
    <div className="page method">
      <header className="page-header">
        <h1>Method</h1>
        <p>
          How the server screens a run. Each algorithm below is the
          project&apos;s own Java code, not a library, and the browser only
          shows what the API returns. Formulas are written as text: ln is the
          natural logarithm and ceil rounds up to a whole number.
        </p>
      </header>

      <nav className="method-contents" aria-label="On this page">
        <ul>
          {SECTIONS.map((section) => (
            <li key={section.id}>
              <a href={`#${section.id}`}>{section.label}</a>
            </li>
          ))}
        </ul>
      </nav>

      <Card
        id="bloom-filter"
        title="Bloom filter: blacklist and duplicate checks"
      >
        <div className="method-body">
          <p>
            Each run builds two Bloom filters. One holds the blacklisted account
            IDs. The other starts empty and collects transaction IDs as the
            transactions are screened in time order, so the first appearance of
            an ID is never flagged and each later one is flagged as a duplicate
            of the first.
          </p>

          <h3>How it works</h3>
          <p>
            A Bloom filter is an array of m bits that all start at 0. To add a
            key, the server hashes it once with 128-bit MurmurHash3, which gives
            two values h1 and h2, and sets k bits by double hashing:
          </p>
          <Formulas>
            <Formula>bit i = (h1 + i * h2) mod m, for i = 0 to k - 1</Formula>
          </Formulas>
          <p>
            To look a key up, it reads the same k bits. If any of them is 0, the
            key was never added. If all k are 1, the key is possibly present.
          </p>

          <h3>Sizing</h3>
          <p>
            For n keys and a target false positive rate p, the filter uses the
            bit count and hash count that keep false positives lowest:
          </p>
          <Formulas>
            <Formula>m = ceil(-n * ln(p) / (ln 2)^2)</Formula>
            <Formula>k = max(1, round(m / n * ln 2))</Formula>
          </Formulas>
          <p>
            The run page shows two estimates of the false positive rate: the
            rate expected after n insertions, and the rate implied by the
            fraction of bits that are set.
          </p>
          <Formulas>
            <Formula>expected rate = (1 - e^(-k * n / m))^k</Formula>
            <Formula>rate from fill = (bits set / m)^k</Formula>
          </Formulas>
          <p>
            A filter is marked saturated when it holds more keys than it was
            sized for, or its rate from fill is more than twice the target. The
            run page then suggests a larger m.
          </p>

          <h3>Why every hit is confirmed</h3>
          <p>
            A Bloom filter never gives a false negative: bits are only ever set,
            so a key that was added always reads as possibly present. It can
            give a false positive, because the k bits of a key that was never
            added may all have been set by other keys. A flag raised on a filter
            hit alone could name an account that is not on the blacklist, or
            call a new transaction a duplicate.
          </p>
          <p>
            So the filter only decides which keys need an exact check. When it
            answers possibly present, the server looks the key up in an exact
            store: a hash set of the blacklisted accounts, or a map from each
            transaction ID to the line where it first appeared. A flag is raised
            only when the exact store confirms the match. When the filter
            answers not present, the exact lookup is skipped. The run page
            counts the positives, the confirmed hits, the false positives and
            the exact lookups the filter avoided.
          </p>

          <h3>Complexity</h3>
          <p>
            m is the bit count, k the hash count and len the length of a key.
            The filter stores bits only, never the keys, so its memory is O(m)
            bits.
          </p>
          <ComplexityTable
            label="Bloom filter complexity"
            costs={BLOOM_COSTS}
          />
        </div>
      </Card>

      <Card id="segment-tree" title="Segment tree: time window totals">
        <div className="method-body">
          <p>
            Each run groups its transactions into time buckets w seconds wide,
            where w is the bucket width set on the Screen page. The first bucket
            starts at t0, the earliest timestamp rounded down to a multiple of
            w:
          </p>
          <Formulas>
            <Formula>bucket = floor((timestamp - t0) / w)</Formula>
          </Formulas>
          <p>
            Each bucket is a leaf of a segment tree that stores three values:
            the sum of the amounts, how many there are and the largest one. Each
            internal node stores the merge of its two children: sums and counts
            add, and the maximum is the larger of the two.
          </p>

          <h3>How it works</h3>
          <p>
            The tree is a perfect binary tree held in arrays, with no pointers.
            Node 1 is the root, the children of node i are 2i and 2i + 1, and
            the leaves start at the bucket count rounded up to a power of two.
            It is built bottom up in one pass over the nodes.
          </p>
          <p>
            A window query turns the window into a range of buckets and walks up
            the tree from both ends of the range, merging at most one node from
            each end per level. However wide the window, it touches O(log B)
            nodes. A window that reaches past the data is clamped to the data
            range first. The charts on the run page split a window into
            consecutive parts along bucket boundaries and answer each part with
            one query.
          </p>
          <p>
            A transaction added on the run page is a point update: its amount
            goes into its bucket&apos;s leaf and every ancestor of that leaf is
            recomputed. When the timestamp lands past the last bucket, the
            bucket capacity doubles until it fits and the tree is rebuilt. Each
            rebuild at least doubles the capacity, so growth costs O(1)
            amortized per append. A timestamp before the first bucket is
            rejected.
          </p>

          <h3>Complexity</h3>
          <p>
            n is the number of transactions, B the number of buckets and c the
            number of windows in a chart.
          </p>
          <ComplexityTable
            label="Segment tree complexity"
            costs={SEGMENT_TREE_COSTS}
          />
        </div>
      </Card>

      <Card
        id="quickselect"
        title="Randomized Quickselect: the high amount threshold"
      >
        <div className="method-body">
          <p>
            A run&apos;s high amount threshold is the p-th percentile of its n
            amounts by the nearest-rank method: the k-th smallest amount, where
          </p>
          <Formulas>
            <Formula>k = ceil(p / 100 * n)</Formula>
          </Formulas>
          <p>
            Every transaction with an amount strictly above the threshold is
            flagged as a high amount. The rank is computed with exact decimal
            arithmetic, so a fractional percentile never rounds to the wrong
            rank.
          </p>

          <h3>How it works</h3>
          <p>
            Each round picks a random pivot from the remaining range and splits
            the range into three parts: amounts less than the pivot, equal to it
            and greater than it. Only the part that holds rank k is kept. The
            search ends when rank k falls in the equal part, and that pivot is
            the answer. The equal part matters for transaction data, where many
            amounts repeat: a block of equal amounts is settled in one round
            instead of shrinking a little at a time.
          </p>
          <p>
            Pivots come from a random generator seeded with the run&apos;s seed,
            so the same file and settings always give the same result.
            Quickselect works on a copy, so the run&apos;s amounts keep their
            order.
          </p>
          <p>
            The run page can also find a percentile for one time window.
            Transactions are kept sorted by time, so binary search finds the
            window&apos;s transactions and Quickselect runs on their amounts
            only.
          </p>
          <p>
            Sorting the amounts would also give the k-th smallest, in O(n log
            n). Quickselect does only the work needed to place one rank. The{' '}
            <Link to="/benchmarks">Benchmarks</Link> page measures the two
            against each other.
          </p>

          <h3>Complexity</h3>
          <p>
            n is the number of amounts in the run and w the number in a time
            window.
          </p>
          <ComplexityTable
            label="Randomized Quickselect complexity"
            costs={QUICKSELECT_COSTS}
          />
        </div>
      </Card>

      <Card id="limitations" title="Limitations">
        <ul className="method-limits">
          <li>
            <strong>No deletion from a Bloom filter.</strong> Bits are shared
            between keys, so clearing the bits of one key could clear a bit
            another key needs, and that key would then read as never added. Keys
            can only be added. To screen against a smaller blacklist, create a
            new run.
          </li>
          <li>
            <strong>Runs live in memory only.</strong> The server keeps each run
            in memory, with no database, so runs expire and every run is lost
            when the server restarts. The{' '}
            <Link to="/privacy">privacy policy</Link> says how long a run is
            kept. A file also has to fit in the server&apos;s memory.
          </li>
          <li>
            <strong>The threshold is not recomputed on live append.</strong> The
            high amount threshold is computed once, when the run is created. A
            transaction added on the run page is compared with that threshold,
            and the threshold does not move. A percentile for a time window is
            computed when you ask for it, so it does include added transactions.
          </li>
          <li>
            <strong>Window totals are exact to the bucket width.</strong> A
            window query covers every bucket the window touches, so a window
            that starts or ends inside a bucket counts the whole bucket. The
            percentile for a window uses exact timestamps, so its count can
            differ from the window total&apos;s count. Use a narrower bucket for
            finer windows.
          </li>
        </ul>
      </Card>
    </div>
  )
}

function Formulas({ children }: { children: ReactNode }) {
  return <div className="formulas">{children}</div>
}

function Formula({ children }: { children: ReactNode }) {
  return <code className="formula">{children}</code>
}

function ComplexityTable({ label, costs }: { label: string; costs: Cost[] }) {
  return (
    <div className="complexity-table">
      <Table label={label}>
        <thead>
          <tr>
            <th scope="col">Operation</th>
            <th scope="col">Time</th>
            <th scope="col">Space</th>
            <th scope="col">Notes</th>
          </tr>
        </thead>
        <tbody>
          {costs.map((cost) => (
            <tr key={cost.operation}>
              <td>{cost.operation}</td>
              <td className="mono">{cost.time}</td>
              <td className="mono">{cost.space}</td>
              <td>{cost.note}</td>
            </tr>
          ))}
        </tbody>
      </Table>
    </div>
  )
}

export default MethodPage
