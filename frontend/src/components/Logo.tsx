/** The TxnScreen mark: three rows of a table, one of them flagged. */
export function LogoMark({ size = 22 }: { size?: number }) {
  return (
    <svg
      className="logo-mark"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      aria-hidden="true"
    >
      <rect width="24" height="24" rx="6" fill="var(--color-accent-solid)" />
      <g fill="var(--color-on-accent)">
        <rect x="6" y="6.5" width="12" height="2" rx="1" />
        <rect x="6" y="11" width="7" height="2" rx="1" />
        <circle cx="16.75" cy="12" r="1.75" />
        <rect x="6" y="15.5" width="9.5" height="2" rx="1" />
      </g>
    </svg>
  )
}

/** The mark with the TxnScreen wordmark beside it. */
function Logo() {
  return (
    <span className="logo">
      <LogoMark />
      <span className="logo-wordmark">TxnScreen</span>
    </span>
  )
}

export default Logo
