// The rules each number field on the Screen page is checked against before a run is sent. They repeat the
// rule each field's helper text states and the server's own checks, so a mistake shows on the field right away.
// The server still checks everything again.

/** A typed setting, named by the section it belongs to and its name in the API request. */
export type FieldKey =
  | 'config.expectedInsertions'
  | 'config.targetFpr'
  | 'config.hashCount'
  | 'config.bucketSeconds'
  | 'config.percentile'
  | 'config.seed'
  | 'sample.seed'
  | 'sample.duplicateRate'
  | 'sample.blacklistRate'

interface NumberRule {
  /** Blank is allowed, and leaves the setting out so the server uses its default. */
  optional?: boolean
  whole?: boolean
  min?: number
  minExclusive?: boolean
  max?: number
  maxExclusive?: boolean
  /** What to enter instead, shown under the field. */
  message: string
}

/** The largest algorithm seed the server takes (Java's Integer.MAX_VALUE). */
const MAX_ALGORITHM_SEED = 2_147_483_647

const RULES: Record<FieldKey, NumberRule> = {
  'config.expectedInsertions': {
    optional: true,
    whole: true,
    min: 1,
    message: 'Enter a whole number of 1 or more, or leave this blank.',
  },
  'config.targetFpr': {
    min: 0,
    minExclusive: true,
    max: 1,
    maxExclusive: true,
    message: 'Enter a number between 0 and 1, such as 0.01.',
  },
  'config.hashCount': {
    optional: true,
    whole: true,
    min: 1,
    max: 30,
    message: 'Enter a whole number from 1 to 30, or leave this blank.',
  },
  'config.bucketSeconds': {
    whole: true,
    min: 1,
    max: 86_400,
    message: 'Enter a whole number from 1 to 86,400.',
  },
  'config.percentile': {
    min: 0,
    minExclusive: true,
    max: 100,
    message: 'Enter a number above 0, up to 100.',
  },
  'config.seed': {
    whole: true,
    min: 0,
    max: MAX_ALGORITHM_SEED,
    message: 'Enter a whole number from 0 to 2,147,483,647.',
  },
  'sample.seed': {
    whole: true,
    message: 'Enter a whole number.',
  },
  'sample.duplicateRate': {
    min: 0,
    max: 0.2,
    message: 'Enter a number from 0 to 0.2.',
  },
  'sample.blacklistRate': {
    min: 0,
    max: 0.2,
    message: 'Enter a number from 0 to 0.2.',
  },
}

/** The settings every run sends, in the order they appear on the page. */
export const CONFIG_FIELDS: FieldKey[] = [
  'config.expectedInsertions',
  'config.targetFpr',
  'config.hashCount',
  'config.bucketSeconds',
  'config.percentile',
  'config.seed',
]

/** The fields only a sample run sends, in page order. They come before the settings. */
export const SAMPLE_FIELDS: FieldKey[] = [
  'sample.seed',
  'sample.duplicateRate',
  'sample.blacklistRate',
]

/** The problem with a field's text, or null when it follows the field's rule. */
export function fieldError(key: FieldKey, raw: string): string | null {
  const rule = RULES[key]
  const text = raw.trim()
  if (text === '') {
    return rule.optional ? null : rule.message
  }
  const value = Number(text)
  const fits =
    Number.isFinite(value) &&
    (!rule.whole || Number.isSafeInteger(value)) &&
    (rule.min === undefined ||
      (rule.minExclusive ? value > rule.min : value >= rule.min)) &&
    (rule.max === undefined ||
      (rule.maxExclusive ? value < rule.max : value <= rule.max))
  return fits ? null : rule.message
}

/** A checked field's value: undefined when it is blank, so the server uses its default. */
export function fieldValue(raw: string): number | undefined {
  const text = raw.trim()
  return text === '' ? undefined : Number(text)
}
