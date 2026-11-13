// Display formatting only. These never compute algorithm results.

const integer = new Intl.NumberFormat('en-IN')

/** A whole number with Indian digit grouping, such as 9,585,059 shown as 95,85,059. */
export function formatInteger(value: number): string {
  return integer.format(value)
}

/** A byte count in B, KB or MB (1 KB = 1,024 bytes). */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** A fraction such as 0.01 as a percentage, with 3 decimals unless told otherwise, such as 1.000%. */
export function formatPercent(fraction: number, decimals = 3): string {
  return `${(fraction * 100).toFixed(decimals)}%`
}

/** A positive amount in paise as rupees with 2 decimals and Indian grouping, such as 1234567 shown as 12,345.67. */
export function formatRupees(paise: number): string {
  const rupees = Math.floor(paise / 100)
  const remainder = String(paise % 100).padStart(2, '0')
  return `${integer.format(rupees)}.${remainder}`
}

const MONTHS = [
  'Jan',
  'Feb',
  'Mar',
  'Apr',
  'May',
  'Jun',
  'Jul',
  'Aug',
  'Sep',
  'Oct',
  'Nov',
  'Dec',
]

const kolkataParts = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Kolkata',
  day: '2-digit',
  month: 'numeric',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** An instant in Asia/Kolkata time, such as 14 Mar 2026, 09:26 or 04 Sep 2026, 08:36. */
export function formatDateTime(date: Date): string {
  const parts: Record<string, string> = {}
  for (const part of kolkataParts.formatToParts(date)) {
    parts[part.type] = part.value
  }
  const month = MONTHS[Number(parts.month) - 1]
  return `${parts.day} ${month} ${parts.year}, ${parts.hour}:${parts.minute}`
}

/** Epoch seconds in Asia/Kolkata time, as formatDateTime shows them. */
export function formatEpochSeconds(epochSeconds: number): string {
  return formatDateTime(new Date(epochSeconds * 1000))
}

const millis = new Intl.NumberFormat('en-IN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/** A duration in microseconds as milliseconds with 2 decimals, such as 1534 shown as 1.53 ms. */
export function formatMillis(micros: number): string {
  return `${millis.format(micros / 1000)} ms`
}
