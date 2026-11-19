import type { FlagType } from '../api/types'
import type { Tone } from '../components/Badge'

export const FLAG_TYPES: FlagType[] = [
  'BLACKLISTED',
  'DUPLICATE',
  'HIGH_AMOUNT',
]

export const FLAG_LABELS: Record<FlagType, string> = {
  BLACKLISTED: 'Blacklisted',
  DUPLICATE: 'Duplicate',
  HIGH_AMOUNT: 'High amount',
}

/** Red for a blacklisted account, amber for a high amount and blue for a duplicate ID. */
export const FLAG_TONES: Record<FlagType, Tone> = {
  BLACKLISTED: 'danger',
  DUPLICATE: 'info',
  HIGH_AMOUNT: 'warning',
}
