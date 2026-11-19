# 1. Problem

TxnScreen: a fraud and duplicate transaction screener. Advanced Algorithm course (CIA 3), ATLAS uGDX
School of Technology. Team: Krrish, Hardik and Vedant.

## 1.1 Problem statement

A payments team receives files of transactions and must find three kinds of suspicious rows before the
money moves on:

1. **Duplicates.** The same transaction ID appears more than once, for example because a batch was
   resubmitted or a payment was retried. Paying it twice loses money.
2. **Blacklisted accounts.** The transaction comes from an account on a blacklist of known fraudulent or
   blocked accounts.
3. **Unusual amounts.** The amount is far above what is normal for this data. A fixed limit does not work
   across files of very different scale, so "unusual" has to be relative to the file itself.

An analyst also needs to see how money moved over time: how much was paid, how many transactions there
were and the largest single amount, for any time window, and the same for windows that change as new
transactions arrive.

Doing this by hand, or with a spreadsheet, is slow and error prone at tens of thousands of rows. Doing it
naively in code is also slow at scale: checking each row against every earlier row is O(n^2), scanning
every row for each time window is O(n) per window, and sorting all amounts to find a percentile is
O(n log n) per question. TxnScreen answers each question with an algorithm suited to it:

| Question | Algorithm | Section |
| --- | --- | --- |
| Is this account blacklisted? Has this transaction ID been seen? | Bloom filter as a precheck, with every hit confirmed against an exact store | 3 |
| What are the total, count and maximum in this time window? | Segment tree over fixed-width time buckets, with point updates | 4 |
| What amount is the P99 (or any percentile) of this run or window? | Randomized Quickselect with a three-way partition | 5 |

All three are our own Java code in `com.txnscreen.algo`. No library Bloom filter, segment tree,
percentile or selection routine is used.

## 1.2 Users

- **Fraud analysts**, who upload a file, review what was flagged and why, and investigate time windows.
- **Payments operations staff**, who need to know which transactions to hold before settlement.

Both are expected to understand transactions but not algorithms. The app therefore explains every flag in
plain words ("Duplicate of line 15", "Account ACC000174 is on the blacklist") and shows algorithm details,
such as the Bloom filter's false positive rate, as supporting information, not as the result.

## 1.3 Constraints

- **Correctness over speed.** A flag must never be wrong. Every Bloom filter hit is therefore treated as
  "possibly present" and confirmed against an exact store before a flag is raised, so the flags are exact
  even though the filter is probabilistic.
- **Our own algorithms on the server.** The assessed algorithms run in the Java API. The React frontend
  only sends requests and shows what the API returns. Every number in the UI comes from an API response or
  from the committed benchmark results.
- **Free hosting.** The API runs on a Render free web service: 512 MB of memory, and it sleeps after 15
  minutes without traffic. Uploads are therefore limited to 200,000 data rows and 20 MB per file, and the
  1,000,000 row benchmarks run only locally.
- **No storage.** The free service has a temporary filesystem, and the app has no database. Runs live in
  server memory only: at most 10 at a time, each for at most 30 minutes, and all are lost on a restart.
- **Exact money.** Amounts are stored as whole paise in a `long`, never as floating point, so sums and
  comparisons are exact.
- **Reproducible data.** All test and benchmark data comes from a seeded generator, so anyone can recreate
  the same files.

## 1.4 Assumptions

- **A duplicate is a repeated transaction ID.** Two rows are duplicates when their `transaction_id` is the
  same, whatever their other fields. Rows with different IDs but the same account, amount and time are not
  treated as duplicates.
- **"First occurrence" is the earliest in time.** Valid rows are sorted by timestamp before screening, and
  the sort is stable. So the first occurrence of an ID is its earliest row, and rows with the same
  timestamp keep their file order. Every later copy is flagged as a duplicate of that row's line.
- **Blacklist matching is exact.** An account matches only if its ID is identical to a blacklist entry
  after trimming spaces. Matching is case sensitive.
- **One currency.** Every amount is in Indian rupees. Amounts must be positive with at most 2 decimal
  places.
- **Timestamps carry their offset.** A timestamp without an offset is ambiguous across time zones, so it is
  rejected. The UI shows times in Asia/Kolkata.
- **Data is synthetic or anonymised.** The app is not meant for real customer or card data, and the
  Privacy and Terms pages say so.

## 1.5 Input format

**Transactions CSV** (required). The first row must be exactly this header, ignoring case and surrounding
spaces:

```
transaction_id,account_id,amount,timestamp,merchant
```

| Column | Format | Example |
| --- | --- | --- |
| `transaction_id` | Any non-blank text | `TXN00000067` |
| `account_id` | Any non-blank text | `ACC000033` |
| `amount` | Rupees, an optional minus, digits, at most 2 decimal places | `729.96` |
| `timestamp` | ISO-8601 with an offset | `2026-01-01T16:05:15+05:30` |
| `merchant` | Any non-blank text | `Restaurant 163` |

Fields are trimmed. Quoted fields follow normal CSV rules. Columns after the fifth are ignored. A file may
have at most 200,000 data rows.

**Blacklist CSV** (optional). One column with the header `account_id`, one account ID per row. Blank rows
are skipped. An empty or missing blacklist is allowed, and the run then warns that no blacklist check was
made.

**Run config** (optional JSON): `expectedInsertions`, `targetFpr`, `hashCount`, `bucketSeconds`,
`percentile` and `seed`, each with a default (section 2.4).

**Sample data.** Instead of uploading, the analyst can ask the server to generate 1 to 200,000 rows from a
seed, with chosen duplicate and blacklist rates.

## 1.6 Output

Every output is JSON from the API, shown by the frontend:

| Output | Contents |
| --- | --- |
| Run summary | Valid and rejected row counts, flag counts by type, both Bloom filters' statistics, the data's time range, the risk threshold, warnings, and algorithm timings |
| Flags | Every `BLACKLISTED`, `DUPLICATE` and `HIGH_AMOUNT` flag with its transaction and a plain reason, in pages |
| Rejected rows | Every invalid row with its line number, reason and raw text, in pages |
| Window | Sum, count and maximum amount for one time window, with the bounds actually used |
| Series | Up to 500 consecutive windows, for the charts |
| Threshold | Any percentile of the run's amounts, or of one window's, and how many amounts are above it |
| Live append | The flags raised for one new transaction and its time bucket's new totals |

[docs/api.md](../api.md) has an example of every request and response.

## 1.7 Failure cases

No input is silently ignored or silently wrong. A problem with the whole request fails the request with one
error shape, `{code, message, details}`. A problem with one row rejects that row, lists it with its line
number and reason, and screens the rest.

| Case | Behaviour | HTTP | Tested in |
| --- | --- | --- | --- |
| Empty file, or header only | Rejected with `EMPTY_FILE` | 400 | `IngestionEdgeCaseTest`, `CsvTransactionParserTest` |
| Wrong or missing header columns | Rejected with `BAD_HEADER`; `details` gives the expected and actual header | 400 | `IngestionEdgeCaseTest` |
| Broken CSV quoting | Rejected with `MALFORMED_CSV` | 400 | `CsvTransactionParserTest` |
| Missing or blank field in a row | Row rejected, `MISSING_FIELD` | 200, row listed | `IngestionEdgeCaseTest`, `RowValidatorTest` |
| Amount not a number, or more than 2 decimals | Row rejected, `INVALID_AMOUNT` | 200, row listed | `RowValidatorTest`, `MoneyTest` |
| Zero or negative amount | Row rejected, `AMOUNT_NOT_POSITIVE`, so it never reaches the analytics | 200, row listed | `IngestionEdgeCaseTest` |
| Invalid timestamp, or no offset | Row rejected, `INVALID_TIMESTAMP` | 200, row listed | `IngestionEdgeCaseTest` |
| Every row invalid | Run refused with `NO_VALID_ROWS` and the first 50 rejected rows | 422 | `IngestionEdgeCaseTest` |
| Duplicate transaction ID | Flagged `DUPLICATE`, confirmed exactly, naming the first occurrence's line | 200 | `RunEdgeCaseTest`, `ScreenerTest` |
| Empty blacklist | Run continues, blacklist check skipped, warning returned | 200 | `RunEdgeCaseTest` |
| Bloom filter saturated | Results still exact; `saturated: true`, a warning and a suggested size | 200 | `ScreeningServiceTest`, `BloomFilterTest` |
| Invalid config (for example p not in (0, 1), k above 30, percentile not in (0, 100]) | Rejected with `INVALID_CONFIG` naming each field | 400 | `RunControllerTest`, `BloomControllerTest` |
| Upload over 200,000 rows or 20 MB | Rejected with `FILE_TOO_LARGE` | 413 | `CsvTransactionParserTest` |
| Window start after end | Rejected with `INVALID_WINDOW` | 400 | `WindowSeriesLiveAppendIntegrationTest` |
| Window entirely outside the data | Rejected with `WINDOW_OUT_OF_RANGE`; `details` gives both ranges | 400 | `WindowSeriesLiveAppendIntegrationTest` |
| Window partly outside the data | Narrowed to the data, `clamped: true` with the bounds used | 200 | `WindowSeriesLiveAppendIntegrationTest` |
| Threshold window with no transactions | Rejected with `EMPTY_WINDOW` | 422 | `ThresholdControllerTest` |
| Live transaction before the first time bucket | Rejected with `TIMESTAMP_BEFORE_RANGE` | 422 | `WindowSeriesLiveAppendIntegrationTest` |
| Unknown or expired run ID | Rejected with `RUN_NOT_FOUND` | 404 | `RunEdgeCaseTest` |
| Any unexpected error | `INTERNAL_ERROR`, logged on the server, no stack trace in the response | 500 | No dedicated test |

The hand-written sample `data/samples/edge_cases.csv` covers the row-level cases in one file. Uploading it
gives 12 valid rows, 5 rejected rows (a negative amount, a zero amount, a bad timestamp, a missing field
and an amount with 3 decimals) and 2 `DUPLICATE` flags.
