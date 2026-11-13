import { useEffect, useState } from 'react'
import { ApiError, asApiError } from '../api/client'
import type { PageResponse } from '../api/types'

type PageLoader<T> = (signal: AbortSignal) => Promise<PageResponse<T>>

interface Loaded<T> {
  key: string
  page?: PageResponse<T>
  error?: ApiError
}

export interface PageState<T> {
  /** The page for the current key, or null while it loads or after an error. */
  page: PageResponse<T> | null
  error: ApiError | null
}

/**
 * Calls load whenever it changes, and returns what it gave for the current key. key names the page asked
 * for, so a new key shows nothing until its page arrives, while a new load with the same key, such as after
 * a refresh, keeps the old page in view until the new one replaces it. A null key loads nothing.
 */
export function usePage<T>(
  key: string | null,
  load: PageLoader<T>,
): PageState<T> {
  const [loaded, setLoaded] = useState<Loaded<T> | null>(null)

  useEffect(() => {
    if (key === null) {
      return
    }
    const controller = new AbortController()
    load(controller.signal).then(
      (page) => setLoaded({ key, page }),
      (caught: unknown) => {
        if (!controller.signal.aborted) {
          setLoaded({ key, error: asApiError(caught) })
        }
      },
    )
    return () => controller.abort()
  }, [key, load])

  const current = loaded !== null && loaded.key === key ? loaded : null
  return { page: current?.page ?? null, error: current?.error ?? null }
}
