import { useEffect, useState } from 'react'
import { ApiError, getBloomPlan } from '../api/client'
import type { BloomPlan } from '../api/types'
import { formatBytes, formatInteger, formatPercent } from '../format'
import Alert from './Alert'
import Spinner from './Spinner'

const DEBOUNCE_MS = 400

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

/** The duplicate filter's size for the current settings, from GET /api/bloom/plan. */
function FilterPlan({ n, p, k }: FilterPlanProps) {
  const key = n !== null && p !== null ? `${n}|${p}|${k}` : null
  const [result, setResult] = useState<PlanResult | null>(null)

  useEffect(() => {
    if (key === null || n === null || p === null) {
      return
    }
    const controller = new AbortController()
    const timer = setTimeout(() => {
      getBloomPlan(n, p, k, controller.signal).then(
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
    <section className="filter-plan" aria-live="polite">
      <h3 className="filter-plan-title">Filter plan</h3>
      {key === null ? (
        <p className="filter-plan-note">
          {p === null
            ? 'Enter a target false-positive rate between 0 and 1 to see the plan.'
            : 'The duplicate filter is sized to the number of valid rows once the file is read. Enter expected insertions to preview its plan.'}
        </p>
      ) : current === null ? (
        <Spinner label="Loading filter plan" />
      ) : current.error ? (
        <Alert tone="warning">
          {current.error.fieldErrors.length > 0
            ? current.error.fieldErrors.map((f) => f.message).join(' ')
            : current.error.message}
        </Alert>
      ) : (
        current.plan && <PlanValues plan={current.plan} />
      )}
    </section>
  )
}

function PlanValues({ plan }: { plan: BloomPlan }) {
  return (
    <>
      <p className="filter-plan-note">
        Duplicate filter for{' '}
        <span className="mono">{formatInteger(plan.n)}</span> expected
        insertions.
      </p>
      <dl className="plan-values">
        <div>
          <dt>Bits (m)</dt>
          <dd className="mono">{formatInteger(plan.bits)}</dd>
        </div>
        <div>
          <dt>Hash functions (k)</dt>
          <dd className="mono">{plan.hashCount}</dd>
        </div>
        <div>
          <dt>Memory</dt>
          <dd className="mono">{formatBytes(plan.memoryBytes)}</dd>
        </div>
        <div>
          <dt>Theoretical FPR</dt>
          <dd className="mono">{formatPercent(plan.theoreticalFpr)}</dd>
        </div>
      </dl>
    </>
  )
}

export default FilterPlan
