import type { ApiError } from '../api/client'
import type { AppendRequest } from '../api/types'

export type AppendField = keyof AppendRequest

export const APPEND_FIELDS: AppendField[] = [
  'transactionId',
  'accountId',
  'amount',
  'timestamp',
  'merchant',
]

/** Codes the time index raises about the timestamp, which carry no field name in their details. */
const TIMESTAMP_CODES = ['TIMESTAMP_BEFORE_RANGE', 'TOO_MANY_BUCKETS']

export type AppendFieldErrors = Partial<Record<AppendField, string>>

/**
 * The server's message placed on each field an append error names: details.field for one field,
 * details.fields for blank fields, or the timestamp for the time index codes. Empty when the error belongs
 * to no field, such as a network failure.
 */
export function appendFieldErrors(error: ApiError): AppendFieldErrors {
  const named = [error.details.field, error.details.fields].flat()
  const fields = APPEND_FIELDS.filter(
    (field) =>
      named.includes(field) ||
      (field === 'timestamp' && TIMESTAMP_CODES.includes(error.code)),
  )
  return Object.fromEntries(fields.map((field) => [field, error.message]))
}
