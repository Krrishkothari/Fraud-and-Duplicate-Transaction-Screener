# Demo script

An 8-minute walkthrough of TxnScreen. Each part names a presenter, by who owns that part of the app, and
lists what to click, what to say and what the screen should show.

The expected values below come from running the backend locally on 19 Nov 2026, at commit `84be4b7`, with
the Screen page's default sample settings (10,000 rows, seed 42, duplicate rate 0.02, blacklist rate
0.05). They are the same on every run with these settings, apart from the timings in milliseconds and
microseconds, which change a little each time.

| Part | Time | Presenter | Total |
| --- | --- | --- | --- |
| 1. Problem | 1 min | Hardik | 1:00 |
| 2. Upload and flags | 2 min | Krrish | 3:00 |
| 3. Bloom panel and saturation | 1 min | Krrish | 4:00 |
| 4. Window chart and live append | 1.5 min | Hardik | 5:30 |
| 5. Threshold | 1 min | Vedant | 6:30 |
| 6. Benchmarks | 1 min | Vedant | 7:30 |
| 7. Error handling | 0.5 min | Krrish | 8:00 |

## Before the demo

- [ ] Open the site **2 minutes early** and load the Screen page once. The free API host sleeps after 15
      minutes without traffic and takes about a minute to wake.
- [ ] Have the app running locally as a backup: `npm run dev` from the repository root, then
      http://localhost:5173. Faculty accept a working local app.
- [ ] Keep `data/samples/edge_cases.csv` ready to upload, in a folder that is easy to reach.
- [ ] Close other tabs and set the browser zoom so the run page's tables are readable from the back.
- [ ] Create a **second run in advance** with expected insertions set to 1,000 (part 3), and keep it open
      in a second tab. Runs expire after 30 minutes, so create it within 30 minutes of the demo.

## 1. Problem (1 min, Hardik)

**Show:** the Home page.

**Say:**

- A payments team gets files of transactions and must catch three things before money moves on:
  duplicate transaction IDs, transactions from blacklisted accounts, and amounts unusually large for that
  file.
- Doing it naively is slow at scale: comparing every row with every earlier row, scanning every row for
  each time window, or sorting all amounts for each percentile.
- TxnScreen uses three algorithms we wrote in Java, which run on the server: a Bloom filter as a
  precheck, a segment tree for time windows, and randomized Quickselect for the risk threshold. The
  browser only shows what the API returns.
- One rule matters most: a Bloom filter hit only means "possibly present", so every hit is checked
  against the exact list before a flag is raised. The flags are always exact.

## 2. Upload and flags (2 min, Krrish)

**Do:** go to **Screen**, choose **Generate sample data**, keep 10,000 rows and seed 42, and leave the
config fields blank. Point at the **Filter plan** box, which updates as the config changes. Press the
submit button.

**Say:** the same seed always produces the same data, with duplicates and blacklisted accounts injected,
and the generator also writes the true labels, which our tests check the flags against.

**Expect on the run page summary:**

| Item | Value |
| --- | --- |
| Valid rows | 10,000 |
| Rejected rows | 0 |
| Blacklisted | 528 |
| Duplicate | 188 |
| High amount | 100 |
| Data range | 1 Jan 2026, 00:10 to 30 Jan 2026, 23:07 |

**Do:** in the flagged transactions table, click the **Duplicate** tab, then **Blacklisted**.

**Say:** every flag says why. A duplicate names the line of its first occurrence, which is its earliest
row in time. The table pages on the server, 50 rows at a time.

## 3. Bloom panel and saturation (1 min, Krrish)

**Show:** the Bloom filter statistics panel of the run from part 2.

| | Blacklist filter | Duplicate ID filter |
| --- | --- | --- |
| Bits (m) | 269 | 95,851 |
| Hash functions (k) | 7 | 7 |
| Items inserted | 28 | 9,812 |
| Bloom positives | 528 | 201 |
| Confirmed hits | 528 | 188 |
| False positives | 0 | 13 |
| Lookups avoided | 9,472 | 9,799 |

**Say:** of 10,000 checks, the duplicate filter sent only 201 to the exact store. 13 of those were false
positives, and the exact store dropped them, so none became a flag.

**Do:** switch to the second tab, the run created with expected insertions set to 1,000.

**Expect:** the saturation warning on the duplicate ID filter, with a suggested size of 94,049 bits. The
filter has 9,586 bits and is 99.9% full. It now gives 6,479 positives, of which 6,291 are false positives,
and avoids only 3,521 lookups. The summary still shows **528, 188 and 100**, exactly as before.

**Say:** an overfilled filter only costs speed, never correctness, because every hit is confirmed.

## 4. Window chart and live append (1.5 min, Hardik)

**Show:** the two charts of the run from part 2: count and total per window, and the largest amount per
window against the P99 threshold line at 50,503.95.

**Say:** each of the 120 chart points is one segment tree query over 60-second buckets, O(log B) each,
instead of a scan over all 10,000 rows.

**Do:** in the window form, query from **5 Jan 2026, 09:00** to **5 Jan 2026, 18:00**.

**Expect:** count 207, sum 13,08,273.59 rupees, maximum 1,23,853.10 rupees, and a query time of a few
microseconds to under a millisecond.

**Do:** query from **31 Dec 2025, 00:00** to **1 Jan 2026, 12:00**.

**Expect:** an info notice that the window was clamped to the data, starting at 1 Jan 2026, 00:10, with
count 114.

**Do:** in the live transaction form, press **Prefill from an existing transaction**, then submit.

**Expect:** the form copies line 26 (`TXN00009454`, account `ACC000174`, 3,193.46 rupees, 1 Jan 2026,
05:15:57). The result shows two badges, **Blacklisted** and **Duplicate** ("Duplicate of line 26"), bucket
305 with count 2, and the screen and update times in microseconds. The summary updates to 10,001 valid
rows, 529 blacklisted and 189 duplicates.

**Say:** the new transaction was screened by the same Bloom filters and added to the tree with one point
update. If it landed past the last bucket, the tree would double its capacity and rebuild.

## 5. Threshold (1 min, Vedant)

**Show:** the threshold panel.

**Do:** choose **P95**.

**Expect:** 24,452.14 rupees over 10,000 amounts, with 500 above it.

**Do:** set the window form to **5 Jan 2026, 00:00** to **11 Jan 2026, 23:59**, query it, then in the
threshold panel turn on **Use selected window** and choose **P99**.

**Expect:** 50,511.42 rupees over 2,337 amounts, with 23 above it.

**Say:** nearest rank, k = ceil(p / 100 × n). Quickselect finds the k-th smallest amount in expected
linear time without sorting, and the three-way partition keeps it fast when many amounts are equal. The
note under the result says this run's flags still use the P99 threshold set when it was created.

## 6. Benchmarks (1 min, Vedant)

**Show:** the Benchmarks page and its environment strip: the laptop, the JVM and commit `a6c6ba6`.

**Say**, one point per section, all from `docs/benchmarks/results.json`:

- **Membership.** At 1,000,000 keys, the Bloom filter used 1,198,200 bytes against 96,388,720 for a
  HashSet with its keys, about 80 times less. Its measured false positive rate, 0.999%, was within 1% of
  the theoretical 1.004%. It was **not** faster than an in-memory HashSet; its benefit is memory and the
  exact lookups it avoids.
- **Range queries.** At 1,000,000 rows, a segment tree query took 0.4981 µs against 2,390.6988 µs for a
  linear scan. At 10,000 rows the build costs as much as about 142 scans, so one query on a small run does
  not need a tree.
- **Percentile selection.** At 1,000,000 amounts, P99 took 19.9412 ms with Quickselect against
  125.3417 ms with a full sort, and comparisons per value stayed near 3 at every size, which is the linear
  behaviour the analysis predicts.

## 7. Error handling (0.5 min, Krrish)

**Do:** on **Screen**, choose **Upload files** and upload `data/samples/edge_cases.csv` with no blacklist.

**Expect:** 12 valid rows, 5 rejected rows, 2 duplicate flags (line 9 duplicates line 3, line 16
duplicates line 7), and a warning that the blacklist is empty. Open the rejected rows table:

| Line | Reason |
| --- | --- |
| 5 | AMOUNT_NOT_POSITIVE (-250.00) |
| 6 | AMOUNT_NOT_POSITIVE (0.00) |
| 8 | INVALID_TIMESTAMP (no offset) |
| 10 | MISSING_FIELD (blank account ID) |
| 11 | INVALID_AMOUNT (3 decimal places) |

**Say:** a bad row is rejected and listed with its line and reason, and the rest of the file is still
screened. A bad file is refused with a clear code: for example, uploading a blacklist file as the
transactions file gives `BAD_HEADER` with the expected header.

## If something goes wrong

| Problem | What to do |
| --- | --- |
| The first request hangs | The API is waking up. The page says so after 5 seconds. Wait up to a minute, or switch to the local app |
| "This run has expired" | Runs last 30 minutes. Generate the sample again; the numbers will be the same |
| The saturation run has expired | Create it again on Screen: 10,000 rows, seed 42, expected insertions 1,000 |
| The live site is down | Switch to the local app at http://localhost:5173 |
