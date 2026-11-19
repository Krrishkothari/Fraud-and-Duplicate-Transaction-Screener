/**
 * The index a key moves to in a row of count items, wrapping at the ends, or null for a key that does not
 * move. Home and End jump to the ends. Up and Down move too unless the row is horizontal only, as tabs are.
 */
export function rovingIndex(
  key: string,
  index: number,
  count: number,
  horizontalOnly = false,
): number | null {
  const forward =
    key === 'ArrowRight' || (!horizontalOnly && key === 'ArrowDown')
  const back = key === 'ArrowLeft' || (!horizontalOnly && key === 'ArrowUp')
  if (forward) {
    return (index + 1) % count
  }
  if (back) {
    return (index - 1 + count) % count
  }
  if (key === 'Home') {
    return 0
  }
  if (key === 'End') {
    return count - 1
  }
  return null
}
