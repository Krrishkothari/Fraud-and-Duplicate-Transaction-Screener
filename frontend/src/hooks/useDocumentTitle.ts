import { useEffect } from 'react'

const APP_NAME = 'TxnScreen'

/** Sets the browser tab title to "page · TxnScreen", or to TxnScreen alone when page is null. */
export function useDocumentTitle(page: string | null): void {
  useEffect(() => {
    document.title = page === null ? APP_NAME : `${page} · ${APP_NAME}`
  }, [page])
}
