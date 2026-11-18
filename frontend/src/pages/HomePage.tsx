import { ArrowRight, ListChecks, SlidersHorizontal, Upload } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { Link } from 'react-router-dom'
import Card from '../components/Card'

interface Step {
  icon: LucideIcon
  title: string
  text: string
}

const STEPS: Step[] = [
  {
    icon: Upload,
    title: 'Upload',
    text: 'Upload a transactions CSV with the columns transaction_id, account_id, amount, timestamp and merchant, and optionally a list of blacklisted account IDs. Or generate sample data instead.',
  },
  {
    icon: SlidersHorizontal,
    title: 'Configure',
    text: 'Set the false positive rate the Bloom filters are sized for, the width of a time bucket and the amount percentile used as the high amount threshold, or keep the defaults.',
  },
  {
    icon: ListChecks,
    title: 'Review',
    text: 'Review every flagged transaction with its reason, the rows that could not be read, the filter statistics, and the total, count and largest amount in any time window.',
  },
]

const ALGORITHMS = [
  {
    name: 'Bloom filter',
    text: 'checks each account against the blacklist and each transaction ID against the IDs already seen. Every possible match is confirmed against an exact set before a flag is raised.',
  },
  {
    name: 'Segment tree',
    text: 'answers the total, count and largest amount for any time window, and takes new transactions as point updates.',
  },
  {
    name: 'Randomized Quickselect',
    text: 'finds the amount percentile used as the high amount threshold without sorting the amounts.',
  },
]

function HomePage() {
  return (
    <div className="page home">
      <header className="home-hero">
        <h1>
          Screen transaction files for duplicates, blacklisted accounts and
          unusual amounts
        </h1>
        <p>
          TxnScreen is for fraud analysts who review batches of transactions.
          Upload a transactions file and a blacklist, and the server returns
          every flagged transaction with the reason it was flagged.
        </p>
        <Link className="button button-primary" to="/screen">
          Start screening
          <ArrowRight size={16} aria-hidden="true" />
        </Link>
      </header>

      <section className="home-section" aria-labelledby="how-it-works">
        <h2 id="how-it-works" className="section-title">
          How it works
        </h2>
        <ol className="steps">
          {STEPS.map(({ icon: Icon, title, text }) => (
            <li key={title} className="step">
              <span className="step-icon">
                <Icon size={20} aria-hidden="true" />
              </span>
              <h3 className="step-title">{title}</h3>
              <p className="step-text">{text}</p>
            </li>
          ))}
        </ol>
      </section>

      <Card title="What runs on the server">
        <div className="card-stack">
          <ul className="algorithm-list">
            {ALGORITHMS.map(({ name, text }) => (
              <li key={name}>
                <strong>{name}</strong> {text}
              </li>
            ))}
          </ul>
          <p className="panel-note">
            All three are written for this project in Java. The browser only
            shows what the API returns.{' '}
            <Link to="/method">How each algorithm works</Link>
          </p>
        </div>
      </Card>
    </div>
  )
}

export default HomePage
