import { Check, Copy } from 'lucide-react'
import { useEffect, useState } from 'react'

const CONFIRM_MS = 1500

interface CopyButtonProps {
  value: string
  /** Names the button for screen readers, such as "Copy run ID". */
  label: string
}

/** Copies a value to the clipboard and shows a check mark for a moment. */
function CopyButton({ value, label }: CopyButtonProps) {
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!copied) {
      return
    }
    const timer = setTimeout(() => setCopied(false), CONFIRM_MS)
    return () => clearTimeout(timer)
  }, [copied])

  async function copy() {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
    } catch {
      // The browser refused clipboard access; the value is still on screen to select by hand.
    }
  }

  return (
    <button
      type="button"
      className="icon-button icon-button-sm"
      aria-label={copied ? 'Copied' : label}
      title={label}
      onClick={copy}
    >
      {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
    </button>
  )
}

export default CopyButton
