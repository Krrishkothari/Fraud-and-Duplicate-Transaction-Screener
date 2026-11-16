import type {
  BenchmarkEnvironment,
  BenchmarkSettings,
} from '../../benchmarks/results'
import { formatBytes, formatDateTime } from '../../format'

interface EnvironmentStripProps {
  environment: BenchmarkEnvironment
  settings: BenchmarkSettings
}

/** Where and how the results were measured, straight from the results file. */
function EnvironmentStrip({ environment, settings }: EnvironmentStripProps) {
  return (
    <dl className="environment-strip">
      <div>
        <dt>Machine</dt>
        <dd>{environment.machine}</dd>
      </div>
      <div>
        <dt>JVM</dt>
        <dd>
          {environment.javaVmName} {environment.javaVersion}, max heap{' '}
          {formatBytes(environment.maxMemory)}
        </dd>
      </div>
      <div>
        <dt>OS</dt>
        <dd>
          {environment.osName} ({environment.osArch}),{' '}
          {environment.availableProcessors} processors
        </dd>
      </div>
      <div>
        <dt>Measured</dt>
        <dd>{formatDateTime(new Date(environment.timestamp))} IST</dd>
      </div>
      <div>
        <dt>Commit</dt>
        <dd className="mono">{environment.commit}</dd>
      </div>
      <div>
        <dt>Method</dt>
        <dd>
          Median of {settings.repeats} timed runs after {settings.warmup} warmup
          runs, seed {settings.seed}
        </dd>
      </div>
    </dl>
  )
}

export default EnvironmentStrip
