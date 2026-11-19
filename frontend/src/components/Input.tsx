import { ChevronDown, Search, X } from 'lucide-react'
import type { InputHTMLAttributes, SelectHTMLAttributes } from 'react'

function withClass(base: string, extra?: string): string {
  return extra ? `${base} ${extra}` : base
}

/** A text input. Put it inside a Field, which gives it a label and messages. */
export function Input({
  className,
  ...props
}: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={withClass('input', className)} {...props} />
}

/** A native select with the input styling. Put it inside a Field. */
export function Select({
  className,
  children,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <span className="select">
      <select className={withClass('input select-input', className)} {...props}>
        {children}
      </select>
      <ChevronDown className="select-icon" aria-hidden="true" />
    </span>
  )
}

interface SearchInputProps {
  /** Names the input for screen readers, since it has no visible label. */
  label: string
  placeholder?: string
  value: string
  onChange: (value: string) => void
}

/** A search box with a clear button that shows once something is typed. */
export function SearchInput({
  label,
  placeholder,
  value,
  onChange,
}: SearchInputProps) {
  return (
    <div className="search-input">
      <Search className="search-input-icon" aria-hidden="true" />
      <input
        type="search"
        className="input"
        aria-label={label}
        placeholder={placeholder}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Escape' && value !== '') {
            onChange('')
          }
        }}
      />
      {value !== '' && (
        <button
          type="button"
          className="search-input-clear"
          aria-label="Clear search"
          onClick={() => onChange('')}
        >
          <X aria-hidden="true" />
        </button>
      )}
    </div>
  )
}
