// Display formatting only. These never compute algorithm results.

// Standard digit grouping in threes, such as 100,000 and 1,000,000, everywhere numbers are shown.
const LOCALE = 'en-US'

const integer = new Intl.NumberFormat(LOCALE)

/** A whole number with digit grouping, such as 9585059 shown as 9,585,059. */
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

/** A positive amount in paise as rupees with 2 decimals and digit grouping, such as 1234567 shown as 12,345.67. */
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
  second: '2-digit',
  hourCycle: 'h23',
})

interface KolkataParts {
  year: string
  /** Two digits, such as 03. */
  month: string
  /** Short English name, such as Mar. */
  monthName: string
  day: string
  hour: string
  minute: string
  second: string
}

function partsInKolkata(date: Date): KolkataParts {
  const parts: Record<string, string> = {}
  for (const part of kolkataParts.formatToParts(date)) {
    parts[part.type] = part.value
  }
  return {
    year: parts.year,
    month: parts.month.padStart(2, '0'),
    monthName: MONTHS[Number(parts.month) - 1],
    day: parts.day,
    hour: parts.hour,
    minute: parts.minute,
    second: parts.second,
  }
}

/** An instant in Asia/Kolkata time, such as 14 Mar 2026, 09:26 or 04 Sep 2026, 08:36. */
export function formatDateTime(date: Date): string {
  const p = partsInKolkata(date)
  return `${p.day} ${p.monthName} ${p.year}, ${p.hour}:${p.minute}`
}

/** Epoch seconds in Asia/Kolkata time, as formatDateTime shows them. */
export function formatEpochSeconds(epochSeconds: number): string {
  return formatDateTime(new Date(epochSeconds * 1000))
}

const DAY_SECONDS = 86_400

/**
 * Epoch seconds in Asia/Kolkata time as a short chart tick, detailed enough for a chart covering
 * spanSeconds: 09:26 within a day, 14 Mar, 09:26 within a week, and 14 Mar beyond that.
 */
export function formatAxisTime(
  epochSeconds: number,
  spanSeconds: number,
): string {
  const p = partsInKolkata(new Date(epochSeconds * 1000))
  if (spanSeconds <= DAY_SECONDS) {
    return `${p.hour}:${p.minute}`
  }
  if (spanSeconds <= 7 * DAY_SECONDS) {
    return `${p.day} ${p.monthName}, ${p.hour}:${p.minute}`
  }
  return `${p.day} ${p.monthName}`
}

/** Epoch seconds as a datetime-local input value in Asia/Kolkata time, such as 2026-03-14T09:26:05. */
export function formatKolkataInput(epochSeconds: number): string {
  const p = partsInKolkata(new Date(epochSeconds * 1000))
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}:${p.second}`
}

// India has one fixed offset and no daylight saving time.
const IST_OFFSET = '+05:30'

/**
 * A datetime-local value, read as Asia/Kolkata time, as the ISO-8601 timestamp with offset the API expects,
 * such as 2026-03-14T09:26 sent as 2026-03-14T09:26:00+05:30.
 */
export function kolkataInputToIso(value: string): string {
  const withSeconds =
    value.length === 'YYYY-MM-DDTHH:mm'.length ? `${value}:00` : value
  return withSeconds + IST_OFFSET
}

/** Paise as the plain rupee text the API parses, with no grouping, such as 1234550 shown as 12345.50. */
export function formatAmountInput(paise: number): string {
  const remainder = String(paise % 100).padStart(2, '0')
  return `${Math.floor(paise / 100)}.${remainder}`
}

const compact = new Intl.NumberFormat(LOCALE, {
  notation: 'compact',
  maximumFractionDigits: 1,
})

/** A number in short form for chart axes, such as 1250000 shown as 1.3M. */
export function formatCompact(value: number): string {
  return compact.format(value)
}

/** A duration in whole microseconds, such as 1534 shown as 1,534 µs. */
export function formatMicros(micros: number): string {
  return `${integer.format(micros)} µs`
}

const millis = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/** A duration in microseconds as milliseconds with 2 decimals, such as 1534 shown as 1.53 ms. */
export function formatMillis(micros: number): string {
  return `${millis.format(micros / 1000)} ms`
}

const measured = [3, 2, 1].map(
  (digits) =>
    new Intl.NumberFormat(LOCALE, {
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    }),
)

/** A measured value with about 4 significant digits, such as 0.153, 5.01, 58.80 or 1,234.5. */
export function formatMeasured(value: number): string {
  if (value < 1) {
    return measured[0].format(value)
  }
  if (value < 100) {
    return measured[1].format(value)
  }
  return measured[2].format(value)
}
