import type { ButtonHTMLAttributes } from 'react'
import { Link } from 'react-router-dom'
import type { LinkProps } from 'react-router-dom'
import Spinner from './Spinner'

interface Appearance {
  variant?: 'primary' | 'secondary' | 'ghost'
  size?: 'sm' | 'md'
}

function buttonClass(
  { variant = 'primary', size = 'md' }: Appearance,
  extra?: string,
): string {
  return ['button', `button-${variant}`, `button-${size}`, extra]
    .filter(Boolean)
    .join(' ')
}

interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>, Appearance {
  /** Shows a spinner and disables the button while its action runs. The label should say what is running. */
  loading?: boolean
}

function Button({
  variant,
  size,
  loading = false,
  type = 'button',
  className,
  disabled,
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={buttonClass({ variant, size }, className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading && <Spinner />}
      {children}
    </button>
  )
}

type ButtonLinkProps = LinkProps & Appearance

/** A router link that looks like a button, for actions that open another page. */
export function ButtonLink({
  variant,
  size,
  className,
  ...props
}: ButtonLinkProps) {
  return (
    <Link className={buttonClass({ variant, size }, className)} {...props} />
  )
}

export default Button
