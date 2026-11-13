// Types matching the JSON the backend returns. Times are epoch seconds and amounts are paise unless a
// name says otherwise.

export type FlagType = 'BLACKLISTED' | 'DUPLICATE' | 'HIGH_AMOUNT'

export type RejectReason =
  | 'MISSING_FIELD'
  | 'INVALID_AMOUNT'
  | 'AMOUNT_NOT_POSITIVE'
  | 'INVALID_TIMESTAMP'

export interface RunConfig {
  expectedInsertions: number | null
  targetFpr: number
  hashCount: number | null
  bucketSeconds: number
  percentile: number
  seed: number
}

/** A config to send: any field left out takes the server default. */
export type RunConfigInput = Partial<RunConfig>

export interface FilterStats {
  bitSize: number
  hashCount: number
  memoryBytes: number
  inserted: number
  fillRatio: number
  checks: number
  bloomPositives: number
  confirmedHits: number
  falsePositives: number
  measuredFpr: number
  theoreticalFpr: number
  saturated: boolean
  suggestedBits: number | null
  authoritativeLookups: number
  lookupsAvoided: number
}

export interface ThresholdResult {
  percentile: number
  valuePaise: number
  n: number
  countAbove: number
  comparisons: number
  algoMicros: number
}

export interface RunSummary {
  id: string
  /** ISO-8601 instant. */
  createdAt: string
  validRows: number
  rejectedRows: number
  flagCounts: Record<FlagType, number>
  blacklistStats: FilterStats
  duplicateStats: FilterStats
  dataStart: number
  dataEnd: number
  config: RunConfig
  warnings: string[]
  algoMicros: number
  analyticsBuildMicros: number
  threshold: ThresholdResult
}

export interface Transaction {
  lineNumber: number
  transactionId: string
  accountId: string
  amountPaise: number
  epochSeconds: number
  merchant: string
}

export interface Flag {
  transactionId: string
  lineNumber: number
  type: FlagType
  detail: string
}

export interface RejectedRow {
  lineNumber: number
  reason: RejectReason
  raw: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface BloomPlan {
  n: number
  p: number
  bits: number
  hashCount: number
  memoryBytes: number
  theoreticalFpr: number
}

export interface SampleRunRequest {
  size: number
  seed?: number
  duplicateRate?: number
  blacklistRate?: number
  config?: RunConfigInput
}

export interface WindowResult {
  from: number
  to: number
  effectiveFrom: number
  effectiveTo: number
  clamped: boolean
  sumPaise: number
  count: number
  maxPaise: number
  algoMicros: number
}

export interface SeriesPoint {
  from: number
  to: number
  sumPaise: number
  count: number
  maxPaise: number
}

export interface SeriesResponse {
  effectiveFrom: number
  effectiveTo: number
  clamped: boolean
  points: SeriesPoint[]
  algoMicros: number
}

export interface ThresholdResponse extends ThresholdResult {
  effectiveFrom: number
  effectiveTo: number
  clamped: boolean
}

/** A live transaction. Amount and timestamp are text, exactly as a CSV row would hold them. */
export interface AppendRequest {
  transactionId: string
  accountId: string
  amount: string
  timestamp: string
  merchant: string
}

export interface AppendResponse {
  transaction: Transaction
  flags: Flag[]
  bucketIndex: number
  bucketStats: { sumPaise: number; count: number; maxPaise: number }
  screenMicros: number
  updateMicros: number
  thresholdNote: string
}

export interface FieldViolation {
  field: string
  message: string
}

export interface ApiErrorBody {
  code: string
  message: string
  details: Record<string, unknown>
}
