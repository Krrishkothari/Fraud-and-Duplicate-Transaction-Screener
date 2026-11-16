// The committed benchmark results, copied from docs/benchmarks/results.json by scripts/copy-benchmarks.mjs.
// Every number on the Benchmarks page comes from this file.

export interface BenchmarkEnvironment {
  javaVersion: string
  javaVmName: string
  osName: string
  osArch: string
  availableProcessors: number
  /** The largest heap the JVM could use, in bytes. */
  maxMemory: number
  machine: string
  commit: string
  /** When the run started, as an ISO-8601 instant. */
  timestamp: string
}

export interface BenchmarkSettings {
  sizes: number[]
  repeats: number
  warmup: number
  seed: number
}

export interface Measurement {
  benchmark: string
  variant: string
  size: number
  metric: string
  unit: string
  median: number
  min: number
  max: number
  repeats: number
}

export interface BenchmarkResults {
  environment: BenchmarkEnvironment
  settings: BenchmarkSettings
  measurements: Measurement[]
}

// A glob, not a plain import, so a missing file gives an empty object instead of a failed build.
const files = import.meta.glob<BenchmarkResults>('../data/results.json', {
  eager: true,
  import: 'default',
})

/** The results, or null when the file has not been generated. */
export const benchmarkResults: BenchmarkResults | null =
  files['../data/results.json'] ?? null

/** The median of one metric at each of the sizes, undefined where it was not measured. */
export function medians(
  results: BenchmarkResults,
  benchmark: string,
  variant: string,
  metric: string,
): (number | undefined)[] {
  return results.settings.sizes.map(
    (size) =>
      results.measurements.find(
        (m) =>
          m.benchmark === benchmark &&
          m.variant === variant &&
          m.metric === metric &&
          m.size === size,
      )?.median,
  )
}
