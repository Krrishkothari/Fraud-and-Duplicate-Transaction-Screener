import { ArrowRight } from 'lucide-react'
import { benchmarkResults } from '../benchmarks/results'
import { ButtonLink } from '../components/Button'
import CsvHeader from '../components/CsvHeader'
import BenchmarkHighlights from '../components/home/BenchmarkHighlights'
import ProductPreview from '../components/home/ProductPreview'
import { TRANSACTION_COLUMNS } from '../csv'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

const STEPS = [
  {
    title: 'Upload',
    text: 'Upload a transactions CSV with the columns transaction_id, account_id, amount, timestamp and merchant, and optionally a list of blacklisted account IDs. Or generate sample data instead.',
  },
  {
    title: 'Configure',
    text: 'Set the false positive rate the Bloom filters are sized for, the width of a time bucket and the amount percentile used as the high amount threshold, or keep the defaults.',
  },
  {
    title: 'Review',
    text: 'Review every flagged transaction with its reason, the rows that could not be read, the filter statistics, and the total, count and largest amount in any time window.',
  },
]

const ALGORITHMS = [
  {
    name: 'Bloom filter',
    pkg: 'com.txnscreen.algo.bloom',
    cost: 'O(k) per lookup',
    text: 'Checks each account against the blacklist and each transaction ID against the IDs already seen. Every possible match is confirmed against an exact set before a flag is raised.',
  },
  {
    name: 'Segment tree',
    pkg: 'com.txnscreen.algo.segtree',
    cost: 'O(log B) per window',
    text: 'Answers the total, count and largest amount for any time window, and takes new transactions as point updates.',
  },
  {
    name: 'Randomized Quickselect',
    pkg: 'com.txnscreen.algo.select',
    cost: 'O(n) expected',
    text: 'Finds the amount percentile used as the high amount threshold without sorting the amounts.',
  },
]

function HomePage() {
  useDocumentTitle(null)
  return (
    <div className="home">
      <section className="container hero">
        <div className="hero-copy">
          <h1 className="hero-title">
            Screen transaction files for duplicates, blacklisted accounts and
            unusual amounts
          </h1>
          <p className="hero-lede">
            TxnScreen is for fraud analysts who review batches of transactions.
            Upload a transactions file and a blacklist, and the server returns
            every flagged transaction with the reason it was flagged.
          </p>
          <div className="hero-actions">
            <ButtonLink to="/screen">
              Start screening
              <ArrowRight aria-hidden="true" />
            </ButtonLink>
            <ButtonLink to="/benchmarks" variant="ghost">
              View benchmarks
            </ButtonLink>
          </div>
          <p className="hero-format">
            <span>Reads CSV files with the header</span>
            <CsvHeader columns={TRANSACTION_COLUMNS} />
          </p>
        </div>
        <ProductPreview />
      </section>

      {benchmarkResults && <BenchmarkHighlights results={benchmarkResults} />}

      <section
        className="container home-section"
        aria-labelledby="how-it-works"
      >
        <div className="section-header">
          <h2 id="how-it-works">How it works</h2>
        </div>
        <ol className="steps">
          {STEPS.map((step, index) => (
            <li key={step.title} className="step">
              <span className="step-marker mono" aria-hidden="true">
                {String(index + 1).padStart(2, '0')}
              </span>
              <h3 className="step-title">{step.title}</h3>
              <p className="step-text">{step.text}</p>
            </li>
          ))}
        </ol>
      </section>

      <section
        className="container home-section"
        aria-labelledby="server-title"
      >
        <div className="section-header">
          <h2 id="server-title">What runs on the server</h2>
          <p>
            All three are written for this project in Java. The browser only
            shows what the API returns.
          </p>
        </div>
        <ul className="features">
          {ALGORITHMS.map((algorithm) => (
            <li key={algorithm.name} className="feature">
              <p className="feature-pkg mono">{algorithm.pkg}</p>
              <h3 className="feature-title">{algorithm.name}</h3>
              <p className="feature-text">{algorithm.text}</p>
              <p className="feature-cost mono">{algorithm.cost}</p>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}

export default HomePage
