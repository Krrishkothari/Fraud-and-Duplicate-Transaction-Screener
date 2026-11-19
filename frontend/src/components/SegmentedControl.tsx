import { useRef } from 'react'
import type { KeyboardEvent } from 'react'
import type { Tone } from './Badge'
import { rovingIndex } from './rovingIndex'

export interface SegmentedOption<T extends string> {
  value: T
  label: string
  /** Shown after the label in a quieter weight, such as a count. */
  count?: string
  /** A dot in a meaning color before the label. */
  tone?: Tone
}

interface SegmentedControlProps<T extends string> {
  label: string
  options: SegmentedOption<T>[]
  value: T
  onChange: (value: T) => void
  /** segmented joins the options in one control; chips spaces them out as filter chips. */
  appearance?: 'segmented' | 'chips'
}

/** One choice among a few options, as a radio group: the arrow keys move the choice. */
function SegmentedControl<T extends string>({
  label,
  options,
  value,
  onChange,
  appearance = 'segmented',
}: SegmentedControlProps<T>) {
  const group = useRef<HTMLDivElement>(null)

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const index = options.findIndex((option) => option.value === value)
    const next = rovingIndex(event.key, index, options.length)
    if (next === null) {
      return
    }
    event.preventDefault()
    onChange(options[next].value)
    group.current
      ?.querySelectorAll<HTMLButtonElement>('[role="radio"]')
      [next]?.focus()
  }

  return (
    <div
      ref={group}
      className={appearance}
      role="radiogroup"
      aria-label={label}
      onKeyDown={handleKeyDown}
    >
      {options.map((option) => {
        const checked = option.value === value
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={checked}
            tabIndex={checked ? 0 : -1}
            className={`${appearance}-option`}
            onClick={() => onChange(option.value)}
          >
            {option.tone && (
              <span className={`dot dot-${option.tone}`} aria-hidden="true" />
            )}
            {option.label}
            {option.count !== undefined && (
              <span className={`${appearance}-count mono`}>{option.count}</span>
            )}
          </button>
        )
      })}
    </div>
  )
}

export default SegmentedControl
