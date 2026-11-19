import { useId, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, asApiError, createRun, createSampleRun } from '../api/client'
import type { RunConfigInput } from '../api/types'
import Alert, { ErrorAlert } from '../components/Alert'
import Button from '../components/Button'
import Field from '../components/Field'
import FilterPlan from '../components/FilterPlan'
import { Input, Select } from '../components/Input'
import PageHeader from '../components/PageHeader'
import SegmentedControl from '../components/SegmentedControl'
import UploadZone from '../components/upload/UploadZone'
import { formatInteger } from '../format'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useRowCount } from '../hooks/useRowCount'

type Mode = 'upload' | 'sample'

const MODES: { value: Mode; label: string }[] = [
  { value: 'upload', label: 'Upload files' },
  { value: 'sample', label: 'Generate sample data' },
]

const SAMPLE_SIZES = [1_000, 10_000, 100_000, 200_000]

const CONFIG_DEFAULTS = {
  expectedInsertions: '',
  targetFpr: '0.01',
  hashCount: '',
  bucketSeconds: '60',
  percentile: '99',
  seed: '42',
}

const SAMPLE_DEFAULTS = {
  size: '10000',
  seed: '42',
  duplicateRate: '0.02',
  blacklistRate: '0.05',
}

type ConfigForm = typeof CONFIG_DEFAULTS
type SampleForm = typeof SAMPLE_DEFAULTS
type FieldErrors = Record<string, string>

const SLOW_MESSAGE =
  'The server is starting up. On the free hosting tier this can take up to a minute.'

function ScreenPage() {
  useDocumentTitle('Screen transactions')
  const navigate = useNavigate()
  const [mode, setMode] = useState<Mode>('upload')
  const [transactionsFile, setTransactionsFile] = useState<File | null>(null)
  const [blacklistFile, setBlacklistFile] = useState<File | null>(null)
  const [sample, setSample] = useState<SampleForm>(SAMPLE_DEFAULTS)
  const [config, setConfig] = useState<ConfigForm>(CONFIG_DEFAULTS)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [error, setError] = useState<ApiError | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [slow, setSlow] = useState(false)
  const transactionRows = useRowCount(transactionsFile)
  const blacklistRows = useRowCount(blacklistFile)

  const updateConfig = (key: keyof ConfigForm, value: string) =>
    setConfig((current) => ({ ...current, [key]: value }))
  const updateSample = (key: keyof SampleForm, value: string) =>
    setSample((current) => ({ ...current, [key]: value }))

  /** A chosen file answers the missing file error, so it goes as soon as the file does. */
  function chooseTransactionsFile(file: File | null) {
    setTransactionsFile(file)
    setFieldErrors((current) => {
      const next = { ...current }
      delete next['upload.transactions']
      return next
    })
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const errors: FieldErrors = {}
    const runConfig = readConfig(config, errors)
    if (mode === 'upload' && !transactionsFile) {
      errors['upload.transactions'] = 'Choose a transactions CSV file.'
    }
    const sampleSeed = readNumber(sample.seed, 'sample.seed', errors, true)
    const duplicateRate = readNumber(
      sample.duplicateRate,
      'sample.duplicateRate',
      errors,
    )
    const blacklistRate = readNumber(
      sample.blacklistRate,
      'sample.blacklistRate',
      errors,
    )
    const relevant = Object.keys(errors).filter(
      (key) => !key.startsWith(mode === 'upload' ? 'sample.' : 'upload.'),
    )
    setFieldErrors(errors)
    setError(null)
    if (relevant.length > 0) {
      return
    }

    setSubmitting(true)
    setSlow(false)
    try {
      const onSlow = () => setSlow(true)
      const summary =
        mode === 'upload'
          ? await createRun(transactionsFile!, blacklistFile, runConfig, onSlow)
          : await createSampleRun(
              {
                size: Number(sample.size),
                seed: sampleSeed,
                duplicateRate,
                blacklistRate,
                config: runConfig,
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
    positiveInteger(config.expectedInsertions) ??
    (mode === 'sample' ? Number(sample.size) : null)
  const planP = rateBetweenZeroAndOne(config.targetFpr)
  const planK = positiveInteger(config.hashCount)
  const rows = mode === 'upload' ? transactionRows : Number(sample.size)
  const screeningLabel =
    rows === null ? 'Screening…' : `Screening ${formatInteger(rows)} rows…`

  return (
    <div className="container page">
      <PageHeader
        title="Screen transactions"
        description="Upload a transactions file and a blacklist, or generate sample data, then choose the screening settings."
      />

      <form className="screen-layout" onSubmit={handleSubmit} noValidate>
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
              <div key="upload" className="upload-grid">
                <UploadZone
                  label="Transactions CSV"
                  header="transaction_id,account_id,amount,timestamp,merchant"
                  note="Up to 200,000 rows."
                  file={transactionsFile}
                  rows={transactionRows}
                  error={fieldErrors['upload.transactions']}
                  onChange={chooseTransactionsFile}
                />
                <UploadZone
                  label="Blacklist CSV"
                  optional
                  header="account_id"
                  note="One column of blacklisted account IDs."
                  file={blacklistFile}
                  rows={blacklistRows}
                  onChange={setBlacklistFile}
                />
              </div>
            ) : (
              <div key="sample" className="field-grid">
                <Field
                  label="Rows"
                  hint="Generated on the server with injected duplicates and blacklisted accounts."
                  error={fieldErrors['sample.size']}
                >
                  <Select
                    value={sample.size}
                    onChange={(e) => updateSample('size', e.target.value)}
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
                  <Input
                    inputMode="numeric"
                    value={sample.seed}
                    onChange={(e) => updateSample('seed', e.target.value)}
                  />
                </Field>
                <Field
                  label="Duplicate rate"
                  hint="Share of rows that repeat an earlier transaction ID, 0 to 0.2."
                  error={fieldErrors['sample.duplicateRate']}
                >
                  <Input
                    inputMode="decimal"
                    value={sample.duplicateRate}
                    onChange={(e) =>
                      updateSample('duplicateRate', e.target.value)
                    }
                  />
                </Field>
                <Field
                  label="Blacklist rate"
                  hint="Share of accounts on the blacklist, 0 to 0.2."
                  error={fieldErrors['sample.blacklistRate']}
                >
                  <Input
                    inputMode="decimal"
                    value={sample.blacklistRate}
                    onChange={(e) =>
                      updateSample('blacklistRate', e.target.value)
                    }
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
                  value={config.expectedInsertions}
                  onChange={(e) =>
                    updateConfig('expectedInsertions', e.target.value)
                  }
                />
              </Field>
              <Field
                label="Target false-positive rate"
                hint="Between 0 and 1, for example 0.01 for 1 percent."
                error={fieldErrors['config.targetFpr']}
              >
                <Input
                  inputMode="decimal"
                  value={config.targetFpr}
                  onChange={(e) => updateConfig('targetFpr', e.target.value)}
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
                  value={config.hashCount}
                  onChange={(e) => updateConfig('hashCount', e.target.value)}
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
                  value={config.bucketSeconds}
                  onChange={(e) =>
                    updateConfig('bucketSeconds', e.target.value)
                  }
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
                  value={config.percentile}
                  onChange={(e) => updateConfig('percentile', e.target.value)}
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
                <Input
                  inputMode="numeric"
                  value={config.seed}
                  onChange={(e) => updateConfig('seed', e.target.value)}
                />
              </Field>
            </SettingsGroup>
          </section>

          {error && <ErrorAlert error={error} />}
          {submitting && slow && <Alert tone="info">{SLOW_MESSAGE}</Alert>}

          <div className="form-footer">
            <p className="form-footer-note">Runs are kept for 30 minutes.</p>
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

/** Reads the settings, leaving out blank fields so the server uses its defaults. */
function readConfig(form: ConfigForm, errors: FieldErrors): RunConfigInput {
  const config: RunConfigInput = {}
  const set = <K extends keyof RunConfigInput>(
    key: K,
    value: RunConfigInput[K] | undefined,
  ) => {
    if (value !== undefined) {
      config[key] = value
    }
  }
  set(
    'expectedInsertions',
    readNumber(
      form.expectedInsertions,
      'config.expectedInsertions',
      errors,
      true,
    ),
  )
  set('targetFpr', readNumber(form.targetFpr, 'config.targetFpr', errors))
  set('hashCount', readNumber(form.hashCount, 'config.hashCount', errors, true))
  set(
    'bucketSeconds',
    readNumber(form.bucketSeconds, 'config.bucketSeconds', errors, true),
  )
  set('percentile', readNumber(form.percentile, 'config.percentile', errors))
  set('seed', readNumber(form.seed, 'config.seed', errors, true))
  return config
}

/** Parses a number field. Blank gives undefined; anything else that is not a number records an error. */
function readNumber(
  raw: string,
  key: string,
  errors: FieldErrors,
  wholeNumber = false,
): number | undefined {
  const text = raw.trim()
  if (text === '') {
    return undefined
  }
  const value = Number(text)
  if (!Number.isFinite(value)) {
    errors[key] = 'Enter a number.'
    return undefined
  }
  if (wholeNumber && !Number.isInteger(value)) {
    errors[key] = 'Enter a whole number.'
    return undefined
  }
  return value
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
