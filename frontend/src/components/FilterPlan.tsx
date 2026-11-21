import { ArrowRight } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, getBloomPlan } from '../api/client'
import type { BloomPlan } from '../api/types'
import { formatBytes, formatInteger, formatPercent } from '../format'
import Alert from './Alert'
import { Loading, Skeleton } from './Skeleton'
import WakingNotice from './WakingNotice'

const DEBOUNCE_MS = 400

const PLAN_LABELS = [
  'Expected insertions (n)',
  'Bits (m)',
  'Hash functions (k)',
  'Memory',
  'Theoretical FPR',
]

interface FilterPlanProps {
  /** Expected insertions, or null when the row count is not known yet. */
  n: number | null
  /** Target false-positive rate, or null when the input is not a rate. */
  p: number | null
  /** Hash count, or null for the optimal count. */
  k: number | null
}

interface PlanResult {
  key: string
  plan?: BloomPlan
  error?: ApiError
}

/** The duplicate filter's size for the current settings, from GET /api/bloom/plan, updated as the settings change. */
function FilterPlan({ n, p, k }: FilterPlanProps) {
  const key = n !== null && p !== null ? `${n}|${p}|${k}` : null
  const [result, setResult] = useState<PlanResult | null>(null)
  // The settings whose plan request has run for 3 seconds without an answer.
  const [slowKey, setSlowKey] = useState<string | null>(null)

  useEffect(() => {
    if (key === null || n === null || p === null) {
      return
    }
    const controller = new AbortController()
    const timer = setTimeout(() => {
      getBloomPlan(n, p, k, {
        signal: controller.signal,
        onSlow: () => setSlowKey(key),
      }).then(
        (plan) => setResult({ key, plan }),
        (error: unknown) => {
          if (error instanceof ApiError) {
            setResult({ key, error })
          }
        },
      )
    }, DEBOUNCE_MS)
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [key, n, p, k])

  const current = result?.key === key ? result : null

  return (
    <section className="plan" aria-labelledby="plan-title">
      <header className="plan-header">
        <h2 id="plan-title" className="plan-title">
          Filter plan
        </h2>
        <p className="plan-description">
          The duplicate ID filter for these settings, sized by the server.
        </p>
      </header>
      <div className="plan-body" aria-live="polite">
        {key === null ? (
          <p className="plan-note">
            {p === null
              ? 'Enter a target false-positive rate between 0 and 1 to see the plan.'
              : 'The duplicate filter is sized to the number of valid rows once the file is read. Enter expected insertions to preview its plan.'}
          </p>
        ) : current === null ? (
          <>
            {slowKey === key && <WakingNotice />}
            <Loading label="Loading filter plan">
              <dl className="plan-values">
                {PLAN_LABELS.map((label) => (
                  <PlanRow key={label} label={label}>
                    <Skeleton width={64} height={10} />
                  </PlanRow>
                ))}
              </dl>
            </Loading>
          </>
        ) : current.error ? (
          <Alert tone="warning">
            {current.error.fieldErrors.length > 0
              ? current.error.fieldErrors.map((f) => f.message).join(' ')
              : current.error.message}
          </Alert>
        ) : (
          current.plan && <PlanValues plan={current.plan} />
        )}
      </div>
      <footer className="plan-footer">
        <code className="plan-formula">m = ceil(-n * ln(p) / (ln 2)^2)</code>
        <Link className="text-link" to="/method#bloom-filter">
          How the filter is sized
          <ArrowRight aria-hidden="true" />
        </Link>
      </footer>
    </section>
  )
}

function PlanValues({ plan }: { plan: BloomPlan }) {
  return (
    <dl className="plan-values">
      <PlanRow label={PLAN_LABELS[0]}>{formatInteger(plan.n)}</PlanRow>
      <PlanRow label={PLAN_LABELS[1]}>{formatInteger(plan.bits)}</PlanRow>
      <PlanRow label={PLAN_LABELS[2]}>{plan.hashCount}</PlanRow>
      <PlanRow label={PLAN_LABELS[3]}>{formatBytes(plan.memoryBytes)}</PlanRow>
      <PlanRow label={PLAN_LABELS[4]}>
        {formatPercent(plan.theoreticalFpr)}
      </PlanRow>
    </dl>
  )
}

function PlanRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="plan-row">
      <dt>{label}</dt>
      <dd className="mono">{children}</dd>
    </div>
  )
}

export default FilterPlan
