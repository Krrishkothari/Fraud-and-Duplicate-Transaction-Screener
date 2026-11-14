import { useEffect, useState } from 'react'
import { ApiError, asApiError, getThreshold } from '../api/client'
import type { ThresholdResponse } from '../api/types'
import { useRun } from './RunContext'
import type { TimeWindow } from './RunContext'

const DEBOUNCE_MS = 300

interface Loaded {
  key: string
  result?: ThresholdResponse
  error?: ApiError
}

export interface ThresholdState {
  /** The threshold for the current percentile and window, or null while it loads or after an error. */
  result: ThresholdResponse | null
  error: ApiError | null
}

/**
 * Fetches the p-th percentile of the run's amounts, over one window or, when window is null, the whole run.
 * Waits briefly after a change, so typing a percentile sends one request. A null p fetches nothing. After a
 * refresh of the run, the old result stays in view until the new one replaces it.
 */
export function useThreshold(
  p: number | null,
  window: TimeWindow | null,
): ThresholdState {
  const { run } = useRun()
  const key = p === null ? null : `${p}|${window?.from}|${window?.to}`
  const [loaded, setLoaded] = useState<Loaded | null>(null)

  useEffect(() => {
    if (key === null || p === null) {
      return
    }
    const controller = new AbortController()
    const timer = setTimeout(() => {
      getThreshold(run.id, p, window, controller.signal).then(
        (result) => setLoaded({ key, result }),
        (caught: unknown) => {
          if (!controller.signal.aborted) {
            setLoaded({ key, error: asApiError(caught) })
          }
        },
      )
    }, DEBOUNCE_MS)
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [run, key, p, window])

  const current = loaded !== null && loaded.key === key ? loaded : null
  return { result: current?.result ?? null, error: current?.error ?? null }
}
