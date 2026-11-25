import { getFlags, getTransactions } from '../api/client'
import type { FlagType, PageResponse, ScreenedTransaction } from '../api/types'

/**
 * One page of table rows: every valid transaction with its flags when type is null, or the flags of one type,
 * each as its transaction with that one flag.
 */
export async function getScreenedRows(
  runId: string,
  type: FlagType | null,
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<PageResponse<ScreenedTransaction>> {
  if (type === null) {
    return getTransactions(runId, page, size, signal)
  }
  const result = await getFlags(runId, type, page, size, signal)
  return {
    ...result,
    items: result.items.map(({ type: flagType, detail, ...transaction }) => ({
      ...transaction,
      flags: [{ type: flagType, detail }],
    })),
  }
}
