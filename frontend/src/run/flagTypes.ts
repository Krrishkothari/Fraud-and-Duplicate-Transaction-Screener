import type { FlagType } from '../api/types'

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

export const FLAG_TONES: Record<FlagType, 'danger' | 'warning' | 'info'> = {
  BLACKLISTED: 'danger',
  DUPLICATE: 'warning',
  HIGH_AMOUNT: 'info',
}
