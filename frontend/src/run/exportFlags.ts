import { getFlags } from '../api/client'
import type { FlaggedTransaction, FlagType } from '../api/types'
import {
  formatAmountInput,
  formatKolkataInput,
  kolkataInputToIso,
} from '../format'

/** The largest page the server returns, so an export needs as few requests as possible. */
const EXPORT_PAGE_SIZE = 200

const COLUMNS = [
  'line_number',
  'transaction_id',
  'account_id',
  'amount',
  'timestamp',
  'merchant',
  'flag',
  'detail',
]

/**
 * Fetches every flag of one type, or of every type when type is null, page by page from the API, and returns
 * them as CSV text in screening order. Amounts are rupees with 2 decimals and timestamps are ISO-8601 with
 * the IST offset, as in an uploaded file. onProgress hears how many flags are fetched out of the total.
 */
export async function flagsAsCsv(
  runId: string,
  type: FlagType | null,
  onProgress: (fetched: number, total: number) => void,
): Promise<string> {
  const lines = [COLUMNS.join(',')]
  let page = 0
  let totalPages = 1
  while (page < totalPages) {
    const result = await getFlags(runId, type, page, EXPORT_PAGE_SIZE)
    for (const item of result.items) {
      lines.push(toCsvRow(item))
    }
    totalPages = result.totalPages
    page += 1
    onProgress(lines.length - 1, result.totalItems)
  }
  return `${lines.join('\n')}\n`
}

function toCsvRow(item: FlaggedTransaction): string {
  return [
    String(item.lineNumber),
    item.transactionId,
    item.accountId,
    formatAmountInput(item.amountPaise),
    kolkataInputToIso(formatKolkataInput(item.epochSeconds)),
    item.merchant,
    item.type,
    item.detail,
  ]
    .map(csvField)
    .join(',')
}

/** Quotes a field that holds a comma, quote or line break, doubling any quotes inside it. */
function csvField(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replaceAll('"', '""')}"` : value
}

/** Hands text to the browser as a file download. */
export function downloadText(text: string, fileName: string): void {
  const url = URL.createObjectURL(
    new Blob([text], { type: 'text/csv;charset=utf-8' }),
  )
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  // Revoking at once can cancel the download in some browsers, so the URL is released a little later.
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
