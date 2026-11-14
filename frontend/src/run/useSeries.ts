import { useEffect, useState } from 'react'
import { ApiError, asApiError, getSeries } from '../api/client'
import type { SeriesResponse } from '../api/types'
import { useRun } from './RunContext'

/** The number of windows each analytics chart shows. */
const SERIES_POINTS = 120

interface Loaded {
  key: string
  series?: SeriesResponse
  error?: ApiError
}

export interface SeriesState {
  /** The series for the selected window, or null while it loads or after an error. */
  series: SeriesResponse | null
  error: ApiError | null
}

/**
 * Loads the series for the run's selected window. A new window shows nothing until its series arrives, while
 * a refresh of the run, such as after a live transaction, keeps the old series in view until the new one
 * replaces it.
 */
export function useSeries(): SeriesState {
  const { run, selectedWindow } = useRun()
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const key = `${selectedWindow.from}|${selectedWindow.to}`

  // Depends on the whole summary, not just its ID, so a refresh after a live transaction reloads the series.
  useEffect(() => {
    const controller = new AbortController()
    getSeries(
      run.id,
      selectedWindow.from,
      selectedWindow.to,
      SERIES_POINTS,
      controller.signal,
    ).then(
      (series) => setLoaded({ key, series }),
      (caught: unknown) => {
        if (!controller.signal.aborted) {
          setLoaded({ key, error: asApiError(caught) })
        }
      },
    )
    return () => controller.abort()
  }, [run, selectedWindow, key])

  const current = loaded !== null && loaded.key === key ? loaded : null
  return { series: current?.series ?? null, error: current?.error ?? null }
}
