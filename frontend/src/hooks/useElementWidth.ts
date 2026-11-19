import { useCallback, useState } from 'react'
import type { RefCallback } from 'react'

/** Tracks an element's content width. Attach the returned ref to the element. The width is 0 until it mounts. */
export function useElementWidth<T extends HTMLElement>(): [
  RefCallback<T>,
  number,
] {
  const [width, setWidth] = useState(0)
  const ref = useCallback((node: T | null) => {
    if (node === null) {
      return
    }
    setWidth(node.getBoundingClientRect().width)
    const observer = new ResizeObserver(([entry]) =>
      setWidth(entry.contentRect.width),
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [])
  return [ref, width]
}
