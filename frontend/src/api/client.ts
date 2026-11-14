import type {
  ApiErrorBody,
  BloomPlan,
  FieldViolation,
  FlaggedTransaction,
  FlagType,
  PageResponse,
  RejectedRow,
  RunConfigInput,
  RunSummary,
  SampleRunRequest,
  SeriesResponse,
  WindowResult,
} from './types'

// Empty means same origin: in development the Vite proxy forwards /api to the backend.
const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '')

export const REQUEST_TIMEOUT_MS = 90_000
export const SLOW_AFTER_MS = 5_000

/** An error from the API, in the backend's {code, message, details} shape. */
export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly details: Record<string, unknown>

  constructor(
    code: string,
    message: string,
    status: number,
    details: Record<string, unknown> = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.details = details
  }

  /** The per-field problems of an INVALID_CONFIG error, or none. */
  get fieldErrors(): FieldViolation[] {
    const errors = this.details.fieldErrors
    return Array.isArray(errors) ? (errors as FieldViolation[]) : []
  }
}

/** Returns a caught error as an ApiError, wrapping anything else in a generic one. */
export function asApiError(caught: unknown): ApiError {
  return caught instanceof ApiError
    ? caught
    : new ApiError('UNEXPECTED_ERROR', 'Something went wrong. Try again.', 0)
}

export interface RequestOptions {
  /** Called once if the request is still running after 5 seconds. */
  onSlow?: () => void
  /** Cancels the request. */
  signal?: AbortSignal
}

/**
 * Sends a request and returns the parsed JSON body. Gives up after 90 seconds. Any failure is thrown as an
 * ApiError, except a cancel through options.signal, which throws the browser's AbortError.
 */
export async function request<T>(
  path: string,
  init: RequestInit = {},
  options: RequestOptions = {},
): Promise<T> {
  const controller = new AbortController()
  let timedOut = false
  const timeout = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, REQUEST_TIMEOUT_MS)
  const slow = options.onSlow
    ? setTimeout(options.onSlow, SLOW_AFTER_MS)
    : undefined
  const cancel = () => controller.abort()
  options.signal?.addEventListener('abort', cancel)

  try {
    let response: Response
    try {
      response = await fetch(BASE_URL + path, {
        ...init,
        signal: controller.signal,
      })
    } catch (error) {
      if (options.signal?.aborted) {
        throw error
      }
      if (timedOut) {
        throw new ApiError(
          'TIMEOUT',
          'The server did not respond within 90 seconds.',
          0,
        )
      }
      throw new ApiError(
        'NETWORK_ERROR',
        'Could not reach the server. Check your connection and try again.',
        0,
      )
    }
    const body = await readJson(response)
    if (!response.ok) {
      throw toApiError(response.status, body)
    }
    return body as T
  } finally {
    clearTimeout(timeout)
    clearTimeout(slow)
    options.signal?.removeEventListener('abort', cancel)
  }
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text) {
    return null
  }
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function toApiError(status: number, body: unknown): ApiError {
  if (isErrorBody(body)) {
    return new ApiError(body.code, body.message, status, body.details ?? {})
  }
  return new ApiError(
    'UNEXPECTED_RESPONSE',
    `The server answered with HTTP ${status}.`,
    status,
  )
}

function isErrorBody(body: unknown): body is ApiErrorBody {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as ApiErrorBody).code === 'string' &&
    typeof (body as ApiErrorBody).message === 'string'
  )
}

/** GET /api/bloom/plan: the bits, hash count, memory and theoretical FPR for n keys at rate p. */
export function getBloomPlan(
  n: number,
  p: number,
  k: number | null,
  signal?: AbortSignal,
): Promise<BloomPlan> {
  const params = new URLSearchParams({ n: String(n), p: String(p) })
  if (k !== null) {
    params.set('k', String(k))
  }
  return request<BloomPlan>(`/api/bloom/plan?${params}`, {}, { signal })
}

/** POST /api/runs: screens an uploaded transactions CSV with an optional blacklist CSV. */
export function createRun(
  transactions: File,
  blacklist: File | null,
  config: RunConfigInput,
  onSlow?: () => void,
): Promise<RunSummary> {
  const form = new FormData()
  form.append('transactions', transactions)
  if (blacklist) {
    form.append('blacklist', blacklist)
  }
  form.append(
    'config',
    new Blob([JSON.stringify(config)], { type: 'application/json' }),
  )
  return request<RunSummary>(
    '/api/runs',
    { method: 'POST', body: form },
    { onSlow },
  )
}

/** GET /api/runs/{id}: the summary of a run that has not expired. */
export function getRun(id: string, signal?: AbortSignal): Promise<RunSummary> {
  return request<RunSummary>(
    `/api/runs/${encodeURIComponent(id)}`,
    {},
    { signal },
  )
}

/** GET /api/runs/{id}/flags: one page of flagged transactions, of one type or of every type when null. */
export function getFlags(
  id: string,
  type: FlagType | null,
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<PageResponse<FlaggedTransaction>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })
  if (type !== null) {
    params.set('type', type)
  }
  return request<PageResponse<FlaggedTransaction>>(
    `/api/runs/${encodeURIComponent(id)}/flags?${params}`,
    {},
    { signal },
  )
}

/** GET /api/runs/{id}/rejected: one page of rejected rows in file order. */
export function getRejected(
  id: string,
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<PageResponse<RejectedRow>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })
  return request<PageResponse<RejectedRow>>(
    `/api/runs/${encodeURIComponent(id)}/rejected?${params}`,
    {},
    { signal },
  )
}

/**
 * GET /api/runs/{id}/window: the sum, count and largest amount for one window. Each bound is an ISO-8601
 * timestamp with offset or whole epoch seconds.
 */
export function getWindow(
  id: string,
  from: string,
  to: string,
  signal?: AbortSignal,
): Promise<WindowResult> {
  const params = new URLSearchParams({ from, to })
  return request<WindowResult>(
    `/api/runs/${encodeURIComponent(id)}/window?${params}`,
    {},
    { signal },
  )
}

/** GET /api/runs/{id}/series: the range from one epoch second to another, split into consecutive windows. */
export function getSeries(
  id: string,
  from: number,
  to: number,
  points: number,
  signal?: AbortSignal,
): Promise<SeriesResponse> {
  const params = new URLSearchParams({
    from: String(from),
    to: String(to),
    points: String(points),
  })
  return request<SeriesResponse>(
    `/api/runs/${encodeURIComponent(id)}/series?${params}`,
    {},
    { signal },
  )
}

/** POST /api/runs/sample: generates synthetic data on the server and screens it. */
export function createSampleRun(
  sample: SampleRunRequest,
  onSlow?: () => void,
): Promise<RunSummary> {
  return request<RunSummary>(
    '/api/runs/sample',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(sample),
    },
    { onSlow },
  )
}
