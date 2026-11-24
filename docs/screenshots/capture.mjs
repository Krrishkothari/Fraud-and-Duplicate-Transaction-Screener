// Captures the screenshots for the project submission report's walkthrough from the running app.
//
// Start the app first with `npm run dev` from the repository root (API on 8080, frontend on 5173), then run:
//
//   PLAYWRIGHT_CORE=/path/to/node_modules/playwright-core/index.mjs \
//   CHROME=/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome \
//   node docs/screenshots/capture.mjs
//
// playwright-core is not a project dependency, so its location is passed in. BASE_URL defaults to
// http://localhost:5173. Every input comes from data/samples/, except the one live transaction, which is
// typed in for the demonstration. Images are written next to this script.
//
// Pages are rendered 1440 px wide at deviceScaleFactor 2. Before each capture the viewport is made as tall
// as the page and scrolled to the top, so the sticky header and the Screen page's sticky form bar sit in
// their place instead of covering content. Each image is then clipped to the part of the page it shows.

import path from 'node:path'
import { fileURLToPath } from 'node:url'

const { chromium } = await import(process.env.PLAYWRIGHT_CORE ?? 'playwright-core')

const here = path.dirname(fileURLToPath(import.meta.url))
const samples = path.resolve(here, '../../data/samples')
const baseUrl = process.env.BASE_URL ?? 'http://localhost:5173'
const WIDTH = 1440
const HEIGHT = 900
const PAD = 16
const CHART_SETTLE_MS = 2500

const browser = await chromium.launch({ executablePath: process.env.CHROME })

async function newPage() {
  const context = await browser.newContext({
    viewport: { width: WIDTH, height: HEIGHT },
    deviceScaleFactor: 2,
    timezoneId: 'Asia/Kolkata',
    locale: 'en-IN',
    colorScheme: 'light',
    reducedMotion: 'reduce',
  })
  return context.newPage()
}

/** Saves the region covering every given locator, plus a small margin, as one PNG. */
async function shot(page, file, locators, { pad = PAD } = {}) {
  const height = await page.evaluate(() => document.documentElement.scrollHeight)
  await page.setViewportSize({ width: WIDTH, height })
  await page.evaluate(() => {
    window.scrollTo(0, 0)
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur()
  })
  await page.waitForTimeout(500)
  const boxes = []
  for (const locator of locators) {
    boxes.push(await locator.boundingBox())
  }
  const x0 = Math.max(0, Math.min(...boxes.map((b) => b.x)) - pad)
  const y0 = Math.max(0, Math.min(...boxes.map((b) => b.y)) - pad)
  const x1 = Math.min(WIDTH, Math.max(...boxes.map((b) => b.x + b.width)) + pad)
  const y1 = Math.max(...boxes.map((b) => b.y + b.height)) + pad
  await page.screenshot({
    path: path.join(here, file),
    clip: { x: x0, y: y0, width: x1 - x0, height: y1 - y0 },
    animations: 'disabled',
  })
  await page.setViewportSize({ width: WIDTH, height: HEIGHT })
  console.log('saved', file)
}

async function settle(page, ms = 800) {
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(ms)
}

/** Fills the Screen page with sample files and settings, starts the run and waits for the run page. */
async function startRun(page, { transactions, blacklist, expectedInsertions }) {
  await page.goto(`${baseUrl}/screen`)
  await settle(page)
  const files = page.locator('input[type=file]')
  await files.nth(0).setInputFiles(path.join(samples, transactions))
  if (blacklist) await files.nth(1).setInputFiles(path.join(samples, blacklist))
  if (expectedInsertions) await page.getByLabel('Expected insertions').fill(String(expectedInsertions))
  await settle(page, 1500)
}

async function submitRun(page) {
  await page.getByRole('button', { name: 'Start screening' }).click()
  await page.waitForURL(/\/runs\//)
  await page.locator('.stat-grid').waitFor()
  await settle(page, 1500)
  console.log('run', page.url())
}

async function openTab(page, name) {
  await page.getByRole('tab', { name }).click()
  await settle(page)
  return page.getByRole('tabpanel')
}

const card = (page, title) =>
  page.locator('section.card').filter({ has: page.getByRole('heading', { name: title, exact: true }) })

// 1. Home page: the header and the hero with its run preview.
{
  const page = await newPage()
  await page.goto(baseUrl)
  await settle(page, 1500)
  await shot(page, '01-home.png', [
    page.getByRole('banner'),
    page.getByRole('heading', { level: 1 }),
    page.locator('code', { hasText: 'transaction_id,account_id' }).first(),
  ])
  await page.close()
}

// 2 to 10. The committed 10,000-row sample and its blacklist, with the default settings.
{
  const page = await newPage()
  await startRun(page, {
    transactions: 'transactions_10000.csv',
    blacklist: 'blacklist_10000.csv',
    expectedInsertions: 10000,
  })
  await shot(page, '02-screen-settings-and-filter-plan.png', [
    page.getByRole('heading', { name: 'Data', exact: true }),
    page.getByText('Seeds the hash functions'),
    page.getByRole('complementary', { name: 'Filter plan' }),
  ])
  await submitRun(page)

  await page.getByRole('radio', { name: /^Duplicate/ }).click()
  await settle(page)
  await shot(page, '03-run-summary-and-duplicate-flags.png', [
    page.locator('.stat-grid'),
    card(page, 'Flagged transactions'),
  ])

  const bloom = await openTab(page, 'Bloom filters')
  await shot(page, '05-run-bloom-filter-statistics.png', [page.getByRole('tablist'), bloom])

  await openTab(page, 'Time windows')
  await page.locator('.recharts-surface').first().waitFor()
  await page.waitForTimeout(CHART_SETTLE_MS)
  await shot(page, '07-run-time-window-charts.png', [
    page.getByRole('tablist'),
    page.locator('figure.chart').first(),
    page.locator('figure.chart').last(),
  ])

  await page.getByLabel('From (IST)').fill('2026-01-15T09:00:00')
  await page.getByLabel('To (IST)').fill('2026-01-15T18:00:00')
  await page.getByRole('button', { name: 'Query window' }).click()
  await settle(page, 1200)
  await shot(page, '08-run-window-query.png', [page.locator('section.window-query')])

  const threshold = await openTab(page, 'High amount threshold')
  await shot(page, '09-run-threshold-whole-run.png', [page.getByRole('tablist'), threshold])
  await page.getByLabel('Use selected window').check()
  await settle(page, 1200)
  await shot(page, '10-run-threshold-selected-window.png', [page.getByRole('tablist'), threshold])

  // The one row typed in for the demonstration: a new ID from a blacklisted account in the sample.
  const live = await openTab(page, 'Live transactions')
  await live.getByLabel('Transaction ID').fill('TXN00009813')
  await live.getByLabel('Account ID').fill('ACC000174')
  await live.getByLabel('Amount (INR)').fill('2450.00')
  await live.getByLabel('Timestamp (IST)').fill('2026-01-15T14:30:00')
  await live.getByLabel('Merchant').fill('Fuel Station 034')
  await live.getByRole('button', { name: 'Add transaction' }).click()
  await page.locator('section.append-result').waitFor()
  await settle(page, 1200)
  await shot(page, '11-run-live-transaction.png', [page.getByRole('tablist'), live])
  await page.close()
}

// 4. The 17-row edge-case file, without a blacklist.
{
  const page = await newPage()
  await startRun(page, { transactions: 'edge_cases.csv' })
  await submitRun(page)
  const rejected = await openTab(page, /^Rejected rows/)
  await shot(page, '04-run-rejected-rows-edge-cases.png', [page.getByRole('tablist'), rejected])
  await page.close()
}

// 6. The 10,000-row sample again, with expected insertions set to 1,000 so the duplicate filter saturates.
{
  const page = await newPage()
  await startRun(page, {
    transactions: 'transactions_10000.csv',
    blacklist: 'blacklist_10000.csv',
    expectedInsertions: 1000,
  })
  await submitRun(page)
  await openTab(page, 'Bloom filters')
  await shot(page, '06-run-bloom-saturation-warning.png', [card(page, 'Duplicate ID filter')])
  await page.close()
}

// 12. Benchmarks page, from the committed results file: the environment and the first pair of charts.
{
  const page = await newPage()
  await page.goto(`${baseUrl}/benchmarks`)
  await settle(page, CHART_SETTLE_MS)
  await shot(page, '12-benchmarks.png', [
    page.getByRole('heading', { name: 'Benchmarks', level: 1 }),
    page.locator('figure.chart').nth(0),
    page.locator('figure.chart').nth(1),
  ])
  await page.close()
}

await browser.close()
