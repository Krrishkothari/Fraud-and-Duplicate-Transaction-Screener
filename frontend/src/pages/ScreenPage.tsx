import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, asApiError, createRun, createSampleRun } from '../api/client'
import type { RunConfigInput } from '../api/types'
import Alert from '../components/Alert'
import Button from '../components/Button'
import Card from '../components/Card'
import Field from '../components/Field'
import FilterPlan from '../components/FilterPlan'
import SegmentedControl from '../components/SegmentedControl'
import Spinner from '../components/Spinner'
import { formatBytes, formatInteger } from '../format'

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

  const updateConfig = (key: keyof ConfigForm, value: string) =>
    setConfig((current) => ({ ...current, [key]: value }))
  const updateSample = (key: keyof SampleForm, value: string) =>
    setSample((current) => ({ ...current, [key]: value }))

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

  return (
    <div className="page">
      <header className="page-header">
        <h1>Screen transactions</h1>
        <p>
          Upload a transactions file and a blacklist, or generate sample data,
          then choose the screening settings.
        </p>
      </header>

      <form className="stack" onSubmit={handleSubmit} noValidate>
        <SegmentedControl
          label="Data source"
          options={MODES}
          value={mode}
          onChange={setMode}
        />

        {mode === 'upload' ? (
          <Card key="upload" title="Files">
            <div className="form-grid">
              <Field
                label="Transactions CSV"
                hint={
                  transactionsFile
                    ? fileLabel(transactionsFile)
                    : 'Header: transaction_id,account_id,amount,timestamp,merchant. Up to 200,000 rows.'
                }
                error={fieldErrors['upload.transactions']}
              >
                <input
                  type="file"
                  accept=".csv,text/csv"
                  onChange={(e) =>
                    setTransactionsFile(e.target.files?.[0] ?? null)
                  }
                />
              </Field>
              <Field
                label="Blacklist CSV (optional)"
                hint={
                  blacklistFile
                    ? fileLabel(blacklistFile)
                    : 'One column with the header account_id.'
                }
              >
                <input
                  type="file"
                  accept=".csv,text/csv"
                  onChange={(e) =>
                    setBlacklistFile(e.target.files?.[0] ?? null)
                  }
                />
              </Field>
            </div>
          </Card>
        ) : (
          <Card key="sample" title="Sample data">
            <div className="form-grid">
              <Field
                label="Rows"
                hint="Generated on the server with injected duplicates and blacklisted accounts."
                error={fieldErrors['sample.size']}
              >
                <select
                  value={sample.size}
                  onChange={(e) => updateSample('size', e.target.value)}
                >
                  {SAMPLE_SIZES.map((size) => (
                    <option key={size} value={size}>
                      {formatInteger(size)}
                    </option>
                  ))}
                </select>
              </Field>
              <Field
                label="Data seed"
                hint="The same seed always generates the same data."
                error={fieldErrors['sample.seed']}
              >
                <input
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
                <input
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
                <input
                  inputMode="decimal"
                  value={sample.blacklistRate}
                  onChange={(e) =>
                    updateSample('blacklistRate', e.target.value)
                  }
                />
              </Field>
            </div>
          </Card>
        )}

        <Card title="Screening settings">
          <div className="settings-layout">
            <div className="form-grid">
              <Field
                label="Expected insertions"
                hint="Transaction IDs the duplicate filter is sized for. Leave blank to use the row count."
                error={fieldErrors['config.expectedInsertions']}
              >
                <input
                  inputMode="numeric"
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
                <input
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
                <input
                  inputMode="numeric"
                  value={config.hashCount}
                  onChange={(e) => updateConfig('hashCount', e.target.value)}
                />
              </Field>
              <Field
                label="Bucket width (seconds)"
                hint="Time-window resolution for analytics, from 1 to 86,400."
                error={fieldErrors['config.bucketSeconds']}
              >
                <input
                  inputMode="numeric"
                  value={config.bucketSeconds}
                  onChange={(e) =>
                    updateConfig('bucketSeconds', e.target.value)
                  }
                />
              </Field>
              <Field
                label="Percentile"
                hint="Amounts above this percentile are flagged as high. Above 0, up to 100."
                error={fieldErrors['config.percentile']}
              >
                <input
                  inputMode="decimal"
                  value={config.percentile}
                  onChange={(e) => updateConfig('percentile', e.target.value)}
                />
              </Field>
              <Field
                label="Algorithm seed"
                hint="Seeds the hash functions and Quickselect, so results repeat."
                error={fieldErrors['config.seed']}
              >
                <input
                  inputMode="numeric"
                  value={config.seed}
                  onChange={(e) => updateConfig('seed', e.target.value)}
                />
              </Field>
            </div>
            <FilterPlan n={planN} p={planP} k={planK} />
          </div>
        </Card>

        {error && (
          <Alert tone="danger" title={error.code}>
            {error.message}
          </Alert>
        )}
        {submitting && slow && <Alert tone="info">{SLOW_MESSAGE}</Alert>}

        <div className="form-actions">
          <Button type="submit" disabled={submitting}>
            {submitting && <Spinner label="Screening" size={16} />}
            {submitting ? 'Screening' : 'Start screening'}
          </Button>
        </div>
      </form>
    </div>
  )
}

function fileLabel(file: File): string {
  return `${file.name}, ${formatBytes(file.size)}`
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
