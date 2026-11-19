import { useState } from 'react'
import type { ThresholdResponse } from '../../api/types'
import {
  formatEpochSeconds,
  formatInteger,
  formatMicros,
  formatRupees,
} from '../../format'
import { useRun } from '../../run/RunContext'
import { useThreshold } from '../../run/useThreshold'
import { ErrorAlert } from '../Alert'
import Field from '../Field'
import { Input } from '../Input'
import SegmentedControl from '../SegmentedControl'
import { StatsSkeleton } from '../Skeleton'

const PRESETS = ['90', '95', '99', '99.9'] as const

type Choice = (typeof PRESETS)[number] | 'custom'

const OPTIONS: { value: Choice; label: string }[] = [
  ...PRESETS.map((value) => ({ value, label: `P${value}` })),
  { value: 'custom', label: 'Custom' },
]

/** Explore percentile thresholds with Quickselect on the server, over the whole run or the selected window. */
function ThresholdPanel() {
  const { run, selectedWindow } = useRun()
  const configured = run.config.percentile
  const preset = PRESETS.find((value) => Number(value) === configured)
  const [choice, setChoice] = useState<Choice>(preset ?? 'custom')
  const [custom, setCustom] = useState(preset ? '' : String(configured))
  const [useWindow, setUseWindow] = useState(false)

  const windowSelected =
    selectedWindow.from !== run.dataStart || selectedWindow.to !== run.dataEnd
  const windowed = useWindow && windowSelected
  const p = choice === 'custom' ? parsePercentile(custom) : Number(choice)
  const customError =
    choice === 'custom' && custom.trim() !== '' && p === null
      ? 'Enter a percentile above 0 and up to 100.'
      : undefined
  const { result, error } = useThreshold(p, windowed ? selectedWindow : null)

  return (
    <div className="panel-stack">
      <div className="panel-heading">
        <h3 className="panel-title">Explore a percentile</h3>
        <p className="panel-note">
          Quickselect finds the percentile on the server, over the whole run or
          the selected window.
        </p>
      </div>
      <div className="threshold-controls">
        <SegmentedControl
          label="Percentile"
          options={OPTIONS}
          value={choice}
          onChange={setChoice}
        />
        {choice === 'custom' && (
          <div className="threshold-custom">
            <Field
              label="Custom percentile"
              hint="Above 0, up to 100."
              error={customError}
            >
              <Input
                inputMode="decimal"
                value={custom}
                onChange={(e) => setCustom(e.target.value)}
              />
            </Field>
          </div>
        )}
        <div className="checkbox-field">
          <label>
            <input
              type="checkbox"
              checked={windowed}
              disabled={!windowSelected}
              onChange={(e) => setUseWindow(e.target.checked)}
            />
            Use selected window
          </label>
          <p className="panel-note">
            {windowSelected
              ? `Selected window: ${formatEpochSeconds(selectedWindow.from)} to ${formatEpochSeconds(selectedWindow.to)} IST.`
              : 'Query a window on the Time windows tab to use it here.'}
          </p>
        </div>
      </div>

      {p === null ? (
        <p className="panel-note">
          Enter a percentile to compute the threshold.
        </p>
      ) : error ? (
        <ErrorAlert error={error} />
      ) : result === null ? (
        <StatsSkeleton label="Computing threshold" />
      ) : (
        <ThresholdValues result={result} windowed={windowed} />
      )}

      <p className="panel-note">
        Flags on this run use the P{configured} threshold set when the run was
        created.
      </p>
    </div>
  )
}

interface ThresholdValuesProps {
  result: ThresholdResponse
  windowed: boolean
}

function ThresholdValues({ result, windowed }: ThresholdValuesProps) {
  return (
    <>
      <dl className="stat-inline">
        <div>
          <dt>P{result.percentile} threshold (INR)</dt>
          <dd className="mono">{formatRupees(result.valuePaise)}</dd>
        </div>
        <div>
          <dt>Amounts considered</dt>
          <dd className="mono">{formatInteger(result.n)}</dd>
        </div>
        <div>
          <dt>Strictly above</dt>
          <dd className="mono">{formatInteger(result.countAbove)}</dd>
        </div>
        <div>
          <dt>Quickselect time</dt>
          <dd className="mono">{formatMicros(result.algoMicros)}</dd>
        </div>
      </dl>
      {windowed && (
        <p className="panel-note">
          Over the amounts from {formatEpochSeconds(result.effectiveFrom)} to{' '}
          {formatEpochSeconds(result.effectiveTo)} IST
          {result.clamped ? ', narrowed to the data range.' : '.'}
        </p>
      )}
    </>
  )
}

/** A percentile typed by the user, or null unless it is above 0 and at most 100. */
function parsePercentile(raw: string): number | null {
  const text = raw.trim()
  const value = Number(text)
  return text !== '' && value > 0 && value <= 100 ? value : null
}

export default ThresholdPanel
