import { useEffect, useState } from 'react'

/** Files larger than this are not read in the browser, so their row count is not shown. */
const MAX_BYTES_TO_COUNT = 64 * 1024 * 1024

interface Counted {
  file: File
  rows: number
}

/**
 * The data rows in a chosen CSV file: its non-blank lines after the header. Shown beside the file name
 * before upload. Null with no file, while counting, or for a file too large to read here.
 */
export function useRowCount(file: File | null): number | null {
  const [counted, setCounted] = useState<Counted | null>(null)

  useEffect(() => {
    if (file === null || file.size > MAX_BYTES_TO_COUNT) {
      return
    }
    let current = true
    file.text().then(
      (text) => {
        if (current) {
          setCounted({ file, rows: countDataRows(text) })
        }
      },
      () => {
        // An unreadable file simply shows no row count; the server reports any real problem on upload.
      },
    )
    return () => {
      current = false
    }
  }, [file])

  return counted !== null && counted.file === file ? counted.rows : null
}

function countDataRows(text: string): number {
  let lines = 0
  for (const line of text.split('\n')) {
    if (line.trim() !== '') {
      lines += 1
    }
  }
  return Math.max(0, lines - 1)
}
