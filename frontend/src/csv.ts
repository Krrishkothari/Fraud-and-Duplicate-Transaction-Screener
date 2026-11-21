/** The header row every transactions CSV must start with, column by column. */
export const TRANSACTION_COLUMNS = [
  'transaction_id',
  'account_id',
  'amount',
  'timestamp',
  'merchant',
]

/** The header row of a blacklist CSV. */
export const BLACKLIST_COLUMNS = ['account_id']
