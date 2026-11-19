import { CircleAlert } from 'lucide-react'
import { cloneElement, useId } from 'react'
import type { ReactElement } from 'react'

interface FieldControlProps {
  id?: string
  'aria-describedby'?: string
  'aria-invalid'?: boolean
}

interface FieldProps {
  label: string
  /** Marks the field as optional beside its label. */
  optional?: boolean
  hint?: string
  error?: string
  /** A single Input, Select or textarea. Field links it to the label and messages. */
  children: ReactElement<FieldControlProps>
}

/** A label above its control, with helper text and any error below. */
function Field({ label, optional = false, hint, error, children }: FieldProps) {
  const id = useId()
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy =
    [hint && hintId, error && errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className="field">
      <label className="field-label" htmlFor={id}>
        {label}
        {optional && <span className="field-optional">Optional</span>}
      </label>
      {cloneElement(children, {
        id,
        'aria-describedby': describedBy,
        'aria-invalid': error ? true : undefined,
      })}
      {hint && (
        <p className="field-hint" id={hintId}>
          {hint}
        </p>
      )}
      {error && (
        <p className="field-error" id={errorId}>
          <CircleAlert aria-hidden="true" />
          {error}
        </p>
      )}
    </div>
  )
}

export default Field
