import { Info } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import type { ChangeEvent, FormEvent, ReactNode } from 'react'
import { flushSync } from 'react-dom'
import { useNavigate } from 'react-router-dom'
import {
  ApiError,
  asApiError,
  createRun,
  createSampleRun,
  warmUpServer,
} from '../api/client'
import type { RunConfigInput } from '../api/types'
import { ErrorAlert } from '../components/Alert'
import Button from '../components/Button'
import Field from '../components/Field'
import FilterPlan from '../components/FilterPlan'
import { Input, Select } from '../components/Input'
import PageHeader from '../components/PageHeader'
import SegmentedControl from '../components/SegmentedControl'
import UploadZone from '../components/upload/UploadZone'
import WakingNotice from '../components/WakingNotice'
import { BLACKLIST_COLUMNS, TRANSACTION_COLUMNS } from '../csv'
import { formatInteger, formatPlainPercent } from '../format'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useRowCount } from '../hooks/useRowCount'
import {
  CONFIG_FIELDS,
  SAMPLE_FIELDS,
  fieldError,
  fieldValue,
} from '../screen/fieldRules'
import type { FieldKey } from '../screen/fieldRules'

type Mode = 'upload' | 'sample'

const MODES: { value: Mode; label: string }[] = [
  { value: 'upload', label: 'Upload files' },
  { value: 'sample', label: 'Generate sample data' },
]

const SAMPLE_SIZES = [1_000, 10_000, 100_000, 200_000]

const DEFAULT_VALUES: Record<FieldKey, string> = {
  'config.expectedInsertions': '',
  'config.targetFpr': '0.01',
  'config.hashCount': '',
  'config.bucketSeconds': '60',
  'config.percentile': '99',
  'config.seed': '42',
  'sample.seed': '42',
  'sample.duplicateRate': '0.02',
  'sample.blacklistRate': '0.05',
}

/** The key of the transactions file's error, and the name of its file input. */
const TRANSACTIONS_FILE = 'upload.transactions'

type FieldErrors = Record<string, string>

function ScreenPage() {
  useDocumentTitle('Screen transactions')
  const navigate = useNavigate()
  const form = useRef<HTMLFormElement>(null)
  const [mode, setMode] = useState<Mode>('upload')
  const [transactionsFile, setTransactionsFile] = useState<File | null>(null)
  const [blacklistFile, setBlacklistFile] = useState<File | null>(null)
  const [sampleSize, setSampleSize] = useState('10000')
  const [values, setValues] = useState(DEFAULT_VALUES)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [error, setError] = useState<ApiError | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [slow, setSlow] = useState(false)
  const transactionRows = useRowCount(transactionsFile)
  const blacklistRows = useRowCount(blacklistFile)

  // Starts a sleeping server now, so it is likely awake by the time the form is sent.
  useEffect(() => {
    warmUpServer()
  }, [])

  function setFieldError(key: string, message: string | null) {
    setFieldErrors((current) => {
      if ((current[key] ?? null) === message) {
        return current
      }
      const next = { ...current }
      if (message === null) {
        delete next[key]
      } else {
        next[key] = message
      }
      return next
    })
  }

  /**
   * The props that wire a number input to its value and rule. A field is checked when it loses focus, and
   * again on every change once it shows an error, so the error goes as soon as the value is fixed.
   */
  function numberInput(key: FieldKey) {
    return {
      name: key,
      value: values[key],
      onChange: (event: ChangeEvent<HTMLInputElement>) => {
        const raw = event.target.value
        setValues((current) => ({ ...current, [key]: raw }))
        if (fieldErrors[key] !== undefined) {
          setFieldError(key, fieldError(key, raw))
        }
      },
      onBlur: () => setFieldError(key, fieldError(key, values[key])),
    }
  }

  /** The percent equivalent under a fraction field, while its value is valid. */
  function percentNote(key: FieldKey): string | undefined {
    const value = fieldValue(values[key])
    return value === undefined || fieldError(key, values[key]) !== null
      ? undefined
      : `= ${formatPlainPercent(value)}`
  }

  /** A chosen file answers the missing file error, so it goes as soon as the file does. */
  function chooseTransactionsFile(file: File | null) {
    setTransactionsFile(file)
    setFieldError(TRANSACTIONS_FILE, null)
  }

  /** Every problem the form can see before sending, in page order. */
  function checkForm(): FieldErrors {
    const errors: FieldErrors = {}
    if (mode === 'upload' && !transactionsFile) {
      errors[TRANSACTIONS_FILE] = 'Choose a transactions CSV file.'
    }
    const keys =
      mode === 'sample' ? [...SAMPLE_FIELDS, ...CONFIG_FIELDS] : CONFIG_FIELDS
    for (const key of keys) {
      const message = fieldError(key, values[key])
      if (message !== null) {
        errors[key] = message
      }
    }
    return errors
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) {
      return
    }
    const errors = checkForm()
    const firstInvalid = Object.keys(errors)[0]
    setError(null)
    if (firstInvalid !== undefined) {
      // Renders the errors first, so a screen reader hears the message when focus lands on the field.
      flushSync(() => setFieldErrors(errors))
      const control = form.current?.elements.namedItem(firstInvalid)
      if (control instanceof HTMLElement) {
        control.focus()
      }
      return
    }
    setFieldErrors({})

    setSubmitting(true)
    setSlow(false)
    try {
      const onSlow = () => setSlow(true)
      const config = runConfig(values)
      const summary =
        mode === 'upload'
          ? await createRun(transactionsFile!, blacklistFile, config, onSlow)
          : await createSampleRun(
              {
                size: Number(sampleSize),
                seed: fieldValue(values['sample.seed']),
                duplicateRate: fieldValue(values['sample.duplicateRate']),
                blacklistRate: fieldValue(values['sample.blacklistRate']),
                config,
              },
              onSlow,
            )
      navigate(`/runs/${summary.id}`)
    } catch (caught) {
      const apiError = asApiError(caught)
      setError(apiError)
      setFieldErrors(serverFieldErrors(apiError, mode))
    } finally {
      setSubmitting(false)
    }
  }

  const planN =
    positiveInteger(values['config.expectedInsertions']) ??
    (mode === 'sample' ? Number(sampleSize) : null)
  const planP = rateBetweenZeroAndOne(values['config.targetFpr'])
  const planK = positiveInteger(values['config.hashCount'])
  const rows = mode === 'upload' ? transactionRows : Number(sampleSize)
  const screeningLabel =
    rows === null ? 'Screening…' : `Screening ${formatInteger(rows)} rows…`

  return (
    <div className="container page">
      <PageHeader
        title="Screen transactions"
        description="Upload a transactions file and a blacklist, or generate sample data, then choose the screening settings."
      />

      <form
        ref={form}
        className="screen-layout"
        onSubmit={handleSubmit}
        noValidate
      >
        <div className="screen-form">
          <section className="form-section" aria-labelledby="data-title">
            <div className="form-section-header">
              <h2 id="data-title" className="form-section-title">
                Data
              </h2>
              <SegmentedControl
                label="Data source"
                options={MODES}
                value={mode}
                onChange={setMode}
              />
            </div>

            {mode === 'upload' ? (
              <div key="upload" className="upload-section">
                <div className="upload-grid">
                  <UploadZone
                    label="Transactions CSV"
                    name={TRANSACTIONS_FILE}
                    columns={TRANSACTION_COLUMNS}
                    note="Up to 200,000 rows."
                    file={transactionsFile}
                    rows={transactionRows}
                    error={fieldErrors[TRANSACTIONS_FILE]}
                    onChange={chooseTransactionsFile}
                  />
                  <UploadZone
                    label="Blacklist CSV"
                    optional
                    columns={BLACKLIST_COLUMNS}
                    note="One column of blacklisted account IDs."
                    file={blacklistFile}
                    rows={blacklistRows}
                    onChange={setBlacklistFile}
                  />
                </div>
                <p className="upload-notice">
                  <Info aria-hidden="true" />
                  <span>
                    Use synthetic or anonymised data only, never real customer
                    data, account numbers or card details. Or use{' '}
                    <button
                      type="button"
                      className="link-button"
                      onClick={() => setMode('sample')}
                    >
                      Generate sample data
                    </button>
                    .
                  </span>
                </p>
              </div>
            ) : (
              <div key="sample" className="field-grid">
                <Field
                  label="Rows"
                  hint="Generated on the server with injected duplicates and blacklisted accounts."
                  error={fieldErrors['sample.size']}
                >
                  <Select
                    value={sampleSize}
                    onChange={(e) => setSampleSize(e.target.value)}
                  >
                    {SAMPLE_SIZES.map((size) => (
                      <option key={size} value={size}>
                        {formatInteger(size)}
                      </option>
                    ))}
                  </Select>
                </Field>
                <Field
                  label="Data seed"
                  hint="The same seed always generates the same data."
                  error={fieldErrors['sample.seed']}
                >
                  <Input inputMode="numeric" {...numberInput('sample.seed')} />
                </Field>
                <Field
                  label="Duplicate rate"
                  valueNote={percentNote('sample.duplicateRate')}
                  hint="Share of rows that repeat an earlier transaction ID, 0 to 0.2."
                  error={fieldErrors['sample.duplicateRate']}
                >
                  <Input
                    inputMode="decimal"
                    {...numberInput('sample.duplicateRate')}
                  />
                </Field>
                <Field
                  label="Blacklist rate"
                  valueNote={percentNote('sample.blacklistRate')}
                  hint="Share of accounts on the blacklist, 0 to 0.2."
                  error={fieldErrors['sample.blacklistRate']}
                >
                  <Input
                    inputMode="decimal"
                    {...numberInput('sample.blacklistRate')}
                  />
                </Field>
              </div>
            )}
          </section>

          <section className="form-section" aria-labelledby="settings-title">
            <div className="form-section-header">
              <h2 id="settings-title" className="form-section-title">
                Screening settings
              </h2>
            </div>

            <SettingsGroup
              title="Duplicate filter"
              description="Expected insertions and hash functions size the duplicate ID filter. The target rate applies to both Bloom filters."
            >
              <Field
                label="Expected insertions"
                hint="Transaction IDs the duplicate filter is sized for. Leave blank to use the row count."
                error={fieldErrors['config.expectedInsertions']}
              >
                <Input
                  inputMode="numeric"
                  placeholder="Row count"
                  {...numberInput('config.expectedInsertions')}
                />
              </Field>
              <Field
                label="Target false-positive rate"
                valueNote={percentNote('config.targetFpr')}
                hint="Between 0 and 1, for example 0.01 for 1 percent."
                error={fieldErrors['config.targetFpr']}
              >
                <Input
                  inputMode="decimal"
                  {...numberInput('config.targetFpr')}
                />
              </Field>
              <Field
                label="Hash functions"
                hint="From 1 to 30. Leave blank to use the optimal number."
                error={fieldErrors['config.hashCount']}
              >
                <Input
                  inputMode="numeric"
                  placeholder="Optimal"
                  {...numberInput('config.hashCount')}
                />
              </Field>
            </SettingsGroup>

            <SettingsGroup
              title="Time windows"
              description="Sets the bucket width of the segment tree."
            >
              <Field
                label="Bucket width (seconds)"
                hint="Time-window resolution for analytics, from 1 to 86,400."
                error={fieldErrors['config.bucketSeconds']}
              >
                <Input
                  inputMode="numeric"
                  {...numberInput('config.bucketSeconds')}
                />
              </Field>
            </SettingsGroup>

            <SettingsGroup
              title="Thresholds"
              description="Sets the high amount percentile that Quickselect finds."
            >
              <Field
                label="Percentile"
                hint="Amounts above this percentile are flagged as high. Above 0, up to 100."
                error={fieldErrors['config.percentile']}
              >
                <Input
                  inputMode="decimal"
                  {...numberInput('config.percentile')}
                />
              </Field>
            </SettingsGroup>

            <SettingsGroup
              title="Reproducibility"
              description="The same file, settings and seed always give the same result."
            >
              <Field
                label="Algorithm seed"
                hint="Seeds the hash functions and Quickselect, so results repeat."
                error={fieldErrors['config.seed']}
              >
                <Input inputMode="numeric" {...numberInput('config.seed')} />
              </Field>
            </SettingsGroup>
          </section>

          {error && <ErrorAlert error={error} />}
          {submitting && slow && <WakingNotice />}

          <div className="form-footer">
            <p className="form-footer-note">
              Runs are kept for up to 30 minutes.
            </p>
            <Button type="submit" loading={submitting}>
              {submitting ? screeningLabel : 'Start screening'}
            </Button>
            <p className="visually-hidden" role="status">
              {submitting ? screeningLabel : ''}
            </p>
          </div>
        </div>

        <aside className="screen-aside" aria-label="Filter plan">
          <FilterPlan n={planN} p={planP} k={planK} />
        </aside>
      </form>
    </div>
  )
}

interface SettingsGroupProps {
  title: string
  description: string
  children: ReactNode
}

/** A titled group of settings: the title and a line about it on the left, the fields on the right. */
function SettingsGroup({ title, description, children }: SettingsGroupProps) {
  const id = useId()
  return (
    <div
      className="settings-group"
      role="group"
      aria-labelledby={`${id}-title`}
      aria-describedby={`${id}-description`}
    >
      <div className="settings-group-text">
        <h3 className="settings-group-title" id={`${id}-title`}>
          {title}
        </h3>
        <p className="settings-group-description" id={`${id}-description`}>
          {description}
        </p>
      </div>
      <div className="field-grid">{children}</div>
    </div>
  )
}

/** The settings to send, leaving out blank fields so the server uses its defaults. Call after checkForm. */
function runConfig(values: Record<FieldKey, string>): RunConfigInput {
  const config: RunConfigInput = {}
  const set = (name: keyof RunConfigInput, key: FieldKey) => {
    const value = fieldValue(values[key])
    if (value !== undefined) {
      config[name] = value
    }
  }
  set('expectedInsertions', 'config.expectedInsertions')
  set('targetFpr', 'config.targetFpr')
  set('hashCount', 'config.hashCount')
  set('bucketSeconds', 'config.bucketSeconds')
  set('percentile', 'config.percentile')
  set('seed', 'config.seed')
  return config
}

function positiveInteger(raw: string): number | null {
  const value = Number(raw.trim())
  return raw.trim() !== '' && Number.isInteger(value) && value >= 1
    ? value
    : null
}

function rateBetweenZeroAndOne(raw: string): number | null {
  const value = Number(raw.trim())
  return raw.trim() !== '' && value > 0 && value < 1 ? value : null
}

/**
 * Maps the server's INVALID_CONFIG field errors to form fields. For a sample run the server names
 * sample fields plainly (size, seed) and settings as config.name; for an upload every field is a setting.
 */
function serverFieldErrors(error: ApiError, mode: Mode): FieldErrors {
  const errors: FieldErrors = {}
  for (const violation of error.fieldErrors) {
    const key =
      mode === 'upload' || violation.field.startsWith('config.')
        ? `config.${violation.field.replace(/^config\./, '')}`
        : `sample.${violation.field}`
    errors[key] = capitalize(violation.message)
  }
  return errors
}

function capitalize(message: string): string {
  return message.charAt(0).toUpperCase() + message.slice(1)
}

export default ScreenPage
