import { CircleAlert, FileSpreadsheet, Upload, X } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import type { ChangeEvent, DragEvent } from 'react'
import { formatBytes, formatInteger } from '../../format'
import Button from '../Button'
import CsvHeader from '../CsvHeader'

interface UploadZoneProps {
  label: string
  optional?: boolean
  /** Names the file input in the form, so a failed submit can move focus to it. */
  name?: string
  /** The columns of the header row the file must start with, shown in monospace. */
  columns: string[]
  /** Limits, such as the row cap. */
  note?: string
  file: File | null
  /** The data rows in the chosen file, or null while counting or when the count is not available. */
  rows: number | null
  error?: string
  onChange: (file: File | null) => void
}

/**
 * A drop zone for one CSV file that becomes a file chip once a file is chosen. The real file input stays in
 * the page, visually hidden, so the zone works from the keyboard like any file input.
 */
function UploadZone({
  label,
  optional = false,
  name,
  columns,
  note,
  file,
  rows,
  error,
  onChange,
}: UploadZoneProps) {
  const id = useId()
  const input = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const [typeError, setTypeError] = useState<string | null>(null)
  const shownError = typeError ?? error
  const inputId = `${id}-input`
  const hintId = `${id}-hint`
  const errorId = `${id}-error`

  function choose(next: File | undefined) {
    if (next === undefined) {
      return
    }
    if (!isCsv(next)) {
      setTypeError(
        `${next.name} is not a CSV file. Choose a file that ends in .csv.`,
      )
      return
    }
    setTypeError(null)
    onChange(next)
  }

  function handleInput(event: ChangeEvent<HTMLInputElement>) {
    choose(event.target.files?.[0])
    // Clearing the input lets the same file be chosen again after it is removed.
    event.target.value = ''
  }

  function handleDragOver(event: DragEvent<HTMLElement>) {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'copy'
    setDragging(true)
  }

  function handleDragLeave(event: DragEvent<HTMLElement>) {
    if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
      setDragging(false)
    }
  }

  function handleDrop(event: DragEvent<HTMLElement>) {
    event.preventDefault()
    setDragging(false)
    choose(event.dataTransfer.files[0])
  }

  function remove() {
    setTypeError(null)
    onChange(null)
    input.current?.focus()
  }

  const dropHandlers = {
    onDragOver: handleDragOver,
    onDragLeave: handleDragLeave,
    onDrop: handleDrop,
  }

  return (
    <div className="upload">
      <span className="field-label" aria-hidden="true">
        {label}
        {optional && <span className="field-optional">Optional</span>}
      </span>
      <input
        ref={input}
        id={inputId}
        name={name}
        type="file"
        accept=".csv,text/csv"
        className="upload-input visually-hidden"
        // Its own name: the label element around the drop zone would otherwise name it with all of its text.
        aria-label={optional ? `${label} (optional)` : label}
        aria-describedby={
          [file === null && hintId, shownError && errorId]
            .filter(Boolean)
            .join(' ') || undefined
        }
        aria-invalid={shownError ? true : undefined}
        tabIndex={file === null ? 0 : -1}
        onChange={handleInput}
      />
      {file === null ? (
        <label
          htmlFor={inputId}
          className="upload-zone"
          data-dragging={dragging || undefined}
          data-invalid={shownError ? true : undefined}
          {...dropHandlers}
        >
          <span className="upload-zone-icon">
            <Upload aria-hidden="true" />
          </span>
          {/* Touch screens cannot drop files, so they get the second title instead (components.css) */}
          <span className="upload-zone-title upload-zone-title-pointer">
            Drop your CSV here or{' '}
            <span className="upload-zone-browse">browse</span>
          </span>
          <span className="upload-zone-title upload-zone-title-touch">
            Choose a CSV file
          </span>
          <span className="upload-zone-hint" id={hintId}>
            <CsvHeader className="upload-zone-header" columns={columns} />
            {note && <span>{note}</span>}
          </span>
        </label>
      ) : (
        <div
          className="file-chip"
          data-dragging={dragging || undefined}
          {...dropHandlers}
        >
          <span className="file-chip-icon">
            <FileSpreadsheet aria-hidden="true" />
          </span>
          <div className="file-chip-text">
            <p className="file-chip-name">{file.name}</p>
            <p className="file-chip-meta mono">
              {formatBytes(file.size)}
              {rows !== null &&
                ` · ${formatInteger(rows)} ${rows === 1 ? 'row' : 'rows'}`}
            </p>
          </div>
          <div className="file-chip-actions">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => input.current?.click()}
            >
              Replace
            </Button>
            <button
              type="button"
              className="icon-button"
              aria-label={`Remove ${file.name}`}
              onClick={remove}
            >
              <X aria-hidden="true" />
            </button>
          </div>
        </div>
      )}
      {shownError && (
        <p className="field-error" id={errorId}>
          <CircleAlert aria-hidden="true" />
          {shownError}
        </p>
      )}
    </div>
  )
}

function isCsv(file: File): boolean {
  return /\.csv$/i.test(file.name) || file.type === 'text/csv'
}

export default UploadZone
