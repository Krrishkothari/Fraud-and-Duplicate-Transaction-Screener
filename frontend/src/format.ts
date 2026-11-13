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

/** A fraction such as 0.01 as a percentage with 3 decimals, such as 1.000%. */
export function formatPercent(fraction: number): string {
  return `${(fraction * 100).toFixed(3)}%`
}
