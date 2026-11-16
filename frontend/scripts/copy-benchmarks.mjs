// Copies the committed benchmark results into the frontend before every dev start and build, so the
// Benchmarks page shows exactly docs/benchmarks/results.json. If that file is missing, any old copy is
// removed and the page shows its empty state instead of stale numbers.

import { copyFileSync, existsSync, mkdirSync, rmSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontend = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
)
const source = path.resolve(
  frontend,
  '..',
  'docs',
  'benchmarks',
  'results.json',
)
const target = path.join(frontend, 'src', 'data', 'results.json')

if (existsSync(source)) {
  mkdirSync(path.dirname(target), { recursive: true })
  copyFileSync(source, target)
  console.log('Copied docs/benchmarks/results.json for the Benchmarks page.')
} else {
  rmSync(target, { force: true })
  console.log(
    'No docs/benchmarks/results.json found; the Benchmarks page will show its empty state.',
  )
}
