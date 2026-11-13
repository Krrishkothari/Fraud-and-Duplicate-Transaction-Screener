import { createContext, useContext } from 'react'
import type { RunSummary } from '../api/types'

/** A time window in epoch seconds. */
export interface TimeWindow {
  from: number
  to: number
}

export interface RunContextValue {
  run: RunSummary
  /**
   * Fetches the summary again, for example after a live transaction. Rejects with an ApiError if the fetch
   * fails, except when the run has expired, which replaces the whole page with the expired message.
   */
  refresh: () => Promise<void>
  /** The window the analytics panels share: the run's whole data range until a panel sets another. */
  selectedWindow: TimeWindow
  setSelectedWindow: (window: TimeWindow) => void
}

export const RunContext = createContext<RunContextValue | null>(null)

/** The run shown on the current run page. Only works inside RunPage. */
export function useRun(): RunContextValue {
  const value = useContext(RunContext)
  if (value === null) {
    throw new Error('useRun must be called inside RunPage.')
  }
  return value
}
