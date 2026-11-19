# TxnScreen API

Every endpoint is under `/api`, takes and returns JSON (except the multipart upload), and is called by the
React app with `fetch`. This page has one `curl` example per endpoint and the response the running app
returned for it.

The examples were captured on 19 Nov 2026 from the backend running locally at commit `70fe35b`
(`./mvnw spring-boot:run` in `backend/`), using the committed samples in `data/samples/`. Each request ran
once, in the order shown. IDs, `createdAt` and the timing fields (`algoMicros`, `analyticsBuildMicros`,
`screenMicros`, `updateMicros`) change on every run. Every other value is reproducible from the same files
and seed.

## Conventions

- **Base URL.** Locally `http://localhost:8080`. The examples use a shell variable:

  ```bash
  API=http://localhost:8080
  ```

- **Money** is a whole number of paise (1 rupee = 100 paise) in fields ending in `Paise`. Amounts sent *to*
  the API are rupee strings with at most 2 decimal places, such as `"729.96"`.
- **Time** in responses is epoch seconds. Window bounds sent to the API may be epoch seconds or ISO-8601
  timestamps with an offset, such as `2026-01-05T09:00:00+05:30`. In a query string, encode `+` as `%2B`,
  or let `curl -G --data-urlencode` do it as the examples below do.
- **Timings.** `algoMicros` is the time the algorithm step took on the server, in microseconds, measured
  with `System.nanoTime`. It does not include parsing or network time.
- **Runs expire.** A run is kept in server memory for 30 minutes, and at most 10 runs are kept. After that
  its ID returns 404 `RUN_NOT_FOUND`.
- **Errors** all use one shape. See [Errors](#errors) at the end.

## GET /api/health

Liveness check. Render calls it as the service's health check, and the frontend uses it to tell when the
free service has woken up.

```bash
curl "$API/api/health"
```

`200 OK`

```json
{
  "status": "ok"
}
```

## GET /api/bloom/plan

Plans a Bloom filter without building one. The Screen page calls it as the user edits the config, to show
the filter's size before a run is created.

| Parameter | Required | Meaning |
| --- | --- | --- |
| `n` | yes | Expected insertions, at least 1 |
| `p` | yes | Target false positive rate, greater than 0 and less than 1 |
| `k` | no | Hash function count, 1 to 30. Without it, the optimal k is used |

```bash
curl -G "$API/api/bloom/plan" --data-urlencode "n=1000000" --data-urlencode "p=0.01"
```

`200 OK`

```json
{
  "n": 1000000,
  "p": 0.01,
  "bits": 9585059,
  "hashCount": 7,
  "memoryBytes": 1198136,
  "theoreticalFpr": 0.010039214559253868
}
```

`bits` is ceil(-n ln p / (ln 2)^2), `hashCount` is round(bits / n * ln 2), and `theoreticalFpr` is
(1 - e^(-k n / m))^k. A plan above 67,108,864 bits (8 MB) is rejected with 400 `INVALID_CONFIG`.

## POST /api/runs

Uploads a transactions CSV, screens it, and creates a run. The request is `multipart/form-data`:

| Part | Required | Content |
| --- | --- | --- |
| `transactions` | yes | CSV with the header `transaction_id,account_id,amount,timestamp,merchant`, at most 200,000 data rows |
| `blacklist` | no | CSV with the header `account_id`. Without it, the blacklist check is skipped with a warning |
| `config` | no | JSON run config, sent with content type `application/json`. Every field is optional |

Config fields: `expectedInsertions` (1 to 10,000,000, default the number of valid rows), `targetFpr`
(between 0 and 1, default 0.01), `hashCount` (1 to 30, default optimal), `bucketSeconds` (1 to 86,400,
default 60), `percentile` (above 0, at most 100, default 99) and `seed` (default 42).

```bash
curl -i "$API/api/runs" \
  -F "transactions=@data/samples/transactions_1000.csv" \
  -F "blacklist=@data/samples/blacklist_1000.csv" \
  -F 'config={"expectedInsertions":1000,"targetFpr":0.01,"bucketSeconds":60,"percentile":99,"seed":42};type=application/json'
```

`201 Created`, with a `Location: /api/runs/{id}` header.

```json
{
  "id": "4449f134-d794-4977-93cb-42510d55483d",
  "createdAt": "2026-11-19T07:59:54.589465500Z",
  "validRows": 1000,
  "rejectedRows": 0,
  "flagCounts": {
    "BLACKLISTED": 54,
    "DUPLICATE": 16,
    "HIGH_AMOUNT": 10
  },
  "blacklistStats": {
    "bitSize": 39,
    "hashCount": 7,
    "memoryBytes": 8,
    "inserted": 4,
    "fillRatio": 0.5128205128205128,
    "checks": 1000,
    "bloomPositives": 101,
    "confirmedHits": 54,
    "falsePositives": 47,
    "measuredFpr": 0.049682875264270614,
    "theoreticalFpr": 0.009254722522666913,
    "saturated": false,
    "suggestedBits": null,
    "authoritativeLookups": 101,
    "lookupsAvoided": 899
  },
  "duplicateStats": {
    "bitSize": 9586,
    "hashCount": 7,
    "memoryBytes": 1200,
    "inserted": 984,
    "fillRatio": 0.5161694137283539,
    "checks": 1000,
    "bloomPositives": 16,
    "confirmedHits": 16,
    "falsePositives": 0,
    "measuredFpr": 0.0,
    "theoreticalFpr": 0.009291734205655313,
    "saturated": false,
    "suggestedBits": null,
    "authoritativeLookups": 16,
    "lookupsAvoided": 984
  },
  "dataStart": 1767209318,
  "dataEnd": 1769790596,
  "config": {
    "expectedInsertions": 1000,
    "targetFpr": 0.01,
    "hashCount": null,
    "bucketSeconds": 60,
    "percentile": 99.0,
    "seed": 42
  },
  "warnings": [],
  "algoMicros": 9010,
  "analyticsBuildMicros": 4147,
  "threshold": {
    "percentile": 99.0,
    "valuePaise": 5365606,
    "n": 1000,
    "countAbove": 10,
    "comparisons": 2805,
    "algoMicros": 1752
  }
}
```

Fields of the run summary:

- `blacklistStats` and `duplicateStats` describe the two Bloom filters. `bloomPositives` are the keys the
  filter called possibly present, `confirmedHits` are those the exact store confirmed, and
  `falsePositives` are the rest. Only confirmed hits become flags. `lookupsAvoided` counts Bloom negatives,
  which never reach the exact store. `measuredFpr` is falsePositives / (checks - confirmedHits).
- `saturated` is true when a filter holds more keys than it was sized for, or its estimated rate is above
  twice the target. `suggestedBits` is then the size it needs, and `warnings` says so.
- `algoMicros` is the screening time, `analyticsBuildMicros` the time to build the segment tree index, and
  `threshold` the Quickselect result whose `valuePaise` sets the `HIGH_AMOUNT` flags.

The rows of a file that fail validation do not fail the run. They are counted in `rejectedRows` and listed
by `GET /api/runs/{id}/rejected`. If every row fails, the response is 422 `NO_VALID_ROWS` with the first 50
rejected rows in `details`.

The examples below use the ID from this response:

```bash
RUN=4449f134-d794-4977-93cb-42510d55483d
```

## POST /api/runs/sample

Generates seeded synthetic data on the server, screens it, and creates a run. It needs no files, which
makes it the quickest way to try the app.

| Field | Required | Meaning |
| --- | --- | --- |
| `size` | yes | Rows to generate, 1 to 200,000 |
| `seed` | no | Generator seed, default 42 |
| `duplicateRate` | no | Share of rows that repeat an earlier transaction ID, 0 to 0.2, default 0.02 |
| `blacklistRate` | no | Share of accounts on the blacklist, 0 to 0.2, default 0.05 |
| `config` | no | Run config, as for `POST /api/runs` |

```bash
curl "$API/api/runs/sample" \
  -H "Content-Type: application/json" \
  -d '{"size":1000,"seed":42,"duplicateRate":0.02,"blacklistRate":0.05,"config":{"percentile":95}}'
```

`201 Created`

```json
{
  "id": "abc6b4b9-1666-48b7-ba98-dee373135374",
  "createdAt": "2026-11-19T07:59:54.837924900Z",
  "validRows": 1000,
  "rejectedRows": 0,
  "flagCounts": {
    "BLACKLISTED": 54,
    "DUPLICATE": 16,
    "HIGH_AMOUNT": 50
  },
  "blacklistStats": {
    "bitSize": 39,
    "hashCount": 7,
    "memoryBytes": 8,
    "inserted": 4,
    "fillRatio": 0.5128205128205128,
    "checks": 1000,
    "bloomPositives": 101,
    "confirmedHits": 54,
    "falsePositives": 47,
    "measuredFpr": 0.049682875264270614,
    "theoreticalFpr": 0.009254722522666913,
    "saturated": false,
    "suggestedBits": null,
    "authoritativeLookups": 101,
    "lookupsAvoided": 899
  },
  "duplicateStats": {
    "bitSize": 9586,
    "hashCount": 7,
    "memoryBytes": 1200,
    "inserted": 984,
    "fillRatio": 0.5161694137283539,
    "checks": 1000,
    "bloomPositives": 16,
    "confirmedHits": 16,
    "falsePositives": 0,
    "measuredFpr": 0.0,
    "theoreticalFpr": 0.009291734205655313,
    "saturated": false,
    "suggestedBits": null,
    "authoritativeLookups": 16,
    "lookupsAvoided": 984
  },
  "dataStart": 1767209318,
  "dataEnd": 1769790596,
  "config": {
    "expectedInsertions": null,
    "targetFpr": 0.01,
    "hashCount": null,
    "bucketSeconds": 60,
    "percentile": 95.0,
    "seed": 42
  },
  "warnings": [],
  "algoMicros": 2070,
  "analyticsBuildMicros": 2150,
  "threshold": {
    "percentile": 95.0,
    "valuePaise": 2406350,
    "n": 1000,
    "countAbove": 50,
    "comparisons": 2745,
    "algoMicros": 255
  }
}
```

Size 1,000 with seed 42 generates the same data as the committed `transactions_1000.csv` and
`blacklist_1000.csv`, so the screening results match the upload above. Only the percentile differs: P95
here, so 50 amounts are above the threshold instead of 10.

## GET /api/runs/{id}

Returns the summary of a run that has not expired.

```bash
curl "$API/api/runs/$RUN"
```

`200 OK`. The body is the same run summary as the `POST /api/runs` response above. Called right after
that request, it returned exactly the same JSON. After live appends, `validRows`, `flagCounts`, the filter
stats and `dataEnd` include the appended transactions.

## GET /api/runs/{id}/flags

Returns one page of flags in screening order, each with its transaction.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `type` | all types | `BLACKLISTED`, `DUPLICATE` or `HIGH_AMOUNT` |
| `page` | 0 | Page number, from 0 |
| `size` | 50 | Page size, 1 to 200 |

```bash
curl -G "$API/api/runs/$RUN/flags" --data-urlencode "type=DUPLICATE" --data-urlencode "page=0" --data-urlencode "size=2"
```

`200 OK`

```json
{
  "items": [
    {
      "lineNumber": 16,
      "transactionId": "TXN00000067",
      "accountId": "ACC000033",
      "amountPaise": 72996,
      "epochSeconds": 1767263715,
      "merchant": "Restaurant 163",
      "type": "DUPLICATE",
      "detail": "Duplicate of line 15"
    },
    {
      "lineNumber": 181,
      "transactionId": "TXN00000314",
      "accountId": "ACC000048",
      "amountPaise": 386376,
      "epochSeconds": 1767694556,
      "merchant": "Travel Agency 167",
      "type": "DUPLICATE",
      "detail": "Duplicate of line 180"
    }
  ],
  "page": 0,
  "size": 2,
  "totalItems": 16,
  "totalPages": 8
}
```

## GET /api/runs/{id}/rejected

Returns one page of rejected rows in file order, with the 1-based line number (the header is line 1), the
reason and the raw row. It takes `page` and `size` like `/flags`.

The 1K sample has no invalid rows, so this example uploads `data/samples/edge_cases.csv`, which has 5:

```bash
EDGE=$(curl -s "$API/api/runs" -F "transactions=@data/samples/edge_cases.csv" | python -c "import json,sys; print(json.load(sys.stdin)['id'])")
curl -G "$API/api/runs/$EDGE/rejected" --data-urlencode "page=0" --data-urlencode "size=2"
```

`200 OK`

```json
{
  "items": [
    {
      "lineNumber": 5,
      "reason": "AMOUNT_NOT_POSITIVE",
      "raw": "EDG0004,ACC000004,-250.00,2026-01-05T10:30:00+05:30,Pharmacy 004"
    },
    {
      "lineNumber": 6,
      "reason": "AMOUNT_NOT_POSITIVE",
      "raw": "EDG0005,ACC000005,0.00,2026-01-05T11:02:19+05:30,Clothing Store 006"
    }
  ],
  "page": 0,
  "size": 2,
  "totalItems": 5,
  "totalPages": 3
}
```

That run has 12 valid rows, 5 rejected rows and 2 `DUPLICATE` flags. With no blacklist uploaded, its
`warnings` is `["The blacklist is empty, so no transaction was checked against blacklisted accounts."]`.

## GET /api/runs/{id}/window

Returns the sum, count and largest amount for one time window, from one segment tree query. Both bounds
are inclusive, and the query covers every time bucket the window touches.

| Parameter | Required | Meaning |
| --- | --- | --- |
| `from` | yes | Window start: epoch seconds or ISO-8601 with offset |
| `to` | yes | Window end, the same formats |

```bash
curl -G "$API/api/runs/$RUN/window" \
  --data-urlencode "from=2026-01-05T09:00:00+05:30" \
  --data-urlencode "to=2026-01-05T18:00:00+05:30"
```

`200 OK`

```json
{
  "from": 1767583800,
  "to": 1767616200,
  "effectiveFrom": 1767583800,
  "effectiveTo": 1767616200,
  "clamped": false,
  "sumPaise": 23310727,
  "count": 18,
  "maxPaise": 12385310,
  "algoMicros": 1125
}
```

A window that reaches outside the run's data is narrowed to it. `clamped` is then true, and
`effectiveFrom` and `effectiveTo` show what was counted. This window starts a day before the data:

```bash
curl -G "$API/api/runs/$RUN/window" \
  --data-urlencode "from=2025-12-31T00:00:00+05:30" \
  --data-urlencode "to=2026-01-01T12:00:00+05:30"
```

`200 OK`

```json
{
  "from": 1767119400,
  "to": 1767249000,
  "effectiveFrom": 1767209318,
  "effectiveTo": 1767249000,
  "clamped": true,
  "sumPaise": 2357832,
  "count": 8,
  "maxPaise": 1054574,
  "algoMicros": 7
}
```

A start after the end is 400 `INVALID_WINDOW`, and a window entirely outside the data is 400
`WINDOW_OUT_OF_RANGE`. Both are shown under [Errors](#errors).

## GET /api/runs/{id}/series

Splits a range into consecutive windows for the run page's charts and answers each with one segment tree
query. The split follows bucket boundaries, so the counts of the windows add up to the count of the whole
range.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `from` | start of the run's data | Range start: epoch seconds or ISO-8601 with offset |
| `to` | end of the run's data | Range end, the same formats |
| `points` | 120 | Number of windows, 1 to 500. Fewer are returned if the range has fewer buckets |

```bash
curl -G "$API/api/runs/$RUN/series" --data-urlencode "points=3"
```

`200 OK`

```json
{
  "effectiveFrom": 1767209318,
  "effectiveTo": 1769790596,
  "clamped": false,
  "points": [
    {
      "from": 1767209318,
      "to": 1768069679,
      "sumPaise": 191802498,
      "count": 331,
      "maxPaise": 12385310
    },
    {
      "from": 1768069680,
      "to": 1768930139,
      "sumPaise": 175058436,
      "count": 350,
      "maxPaise": 6047643
    },
    {
      "from": 1768930140,
      "to": 1769790596,
      "sumPaise": 179727875,
      "count": 319,
      "maxPaise": 9237566
    }
  ],
  "algoMicros": 52
}
```

The three counts add up to 1,000, the run's `validRows`.

## GET /api/runs/{id}/threshold

Finds the p-th percentile amount with randomized Quickselect (nearest-rank: the k-th smallest amount with
k = ceil(p / 100 * n)), over the whole run or one time window, and counts the amounts strictly above it.
It is for exploring thresholds only. A run's `HIGH_AMOUNT` flags always use the threshold set when the run
was created.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `p` | the run's configured percentile | Percentile, greater than 0 and at most 100 |
| `from` | start of the run's data | Window start: epoch seconds or ISO-8601 with offset |
| `to` | end of the run's data | Window end, the same formats |

```bash
curl -G "$API/api/runs/$RUN/threshold" --data-urlencode "p=95"
```

`200 OK`

```json
{
  "percentile": 95.0,
  "valuePaise": 2406350,
  "n": 1000,
  "countAbove": 50,
  "comparisons": 2745,
  "algoMicros": 259,
  "effectiveFrom": 1767209318,
  "effectiveTo": 1769790596,
  "clamped": false
}
```

With a window, Quickselect runs only on the amounts of the transactions inside it:

```bash
curl -G "$API/api/runs/$RUN/threshold" \
  --data-urlencode "p=99" \
  --data-urlencode "from=2026-01-05T00:00:00+05:30" \
  --data-urlencode "to=2026-01-11T23:59:59+05:30"
```

`200 OK`

```json
{
  "percentile": 99.0,
  "valuePaise": 5695091,
  "n": 255,
  "countAbove": 2,
  "comparisons": 1070,
  "algoMicros": 154,
  "effectiveFrom": 1767551400,
  "effectiveTo": 1768156199,
  "clamped": false
}
```

`p` outside (0, 100] is 400 `INVALID_CONFIG`, and a window with no transactions is 422 `EMPTY_WINDOW`. The
window rules are the same as for `/window`.

## POST /api/runs/{id}/transactions

Adds one live transaction to a run. The server validates it with the same rules as a CSV row, screens it
with the run's Bloom filters (each hit confirmed in the exact store), adds `HIGH_AMOUNT` if it is above the
run's threshold, and records it in the segment tree with a point update. If its timestamp is past the last
time bucket, the index grows first.

| Field | Meaning |
| --- | --- |
| `transactionId` | Transaction ID |
| `accountId` | Account ID |
| `amount` | Rupees as a string, at most 2 decimal places, greater than 0 |
| `timestamp` | ISO-8601 with offset, not before the run's first time bucket |
| `merchant` | Merchant name |

This example sends a transaction ID that is already in the file, line 15:

```bash
curl "$API/api/runs/$RUN/transactions" \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"TXN00000067","accountId":"ACC000033","amount":"729.96","timestamp":"2026-01-01T16:05:30+05:30","merchant":"Restaurant 163"}'
```

`200 OK`

```json
{
  "transaction": {
    "lineNumber": 1002,
    "transactionId": "TXN00000067",
    "accountId": "ACC000033",
    "amountPaise": 72996,
    "epochSeconds": 1767263730,
    "merchant": "Restaurant 163"
  },
  "flags": [
    {
      "transactionId": "TXN00000067",
      "lineNumber": 1002,
      "type": "DUPLICATE",
      "detail": "Duplicate of line 15"
    }
  ],
  "bucketIndex": 907,
  "bucketStats": {
    "sumPaise": 145992,
    "count": 2,
    "maxPaise": 72996
  },
  "screenMicros": 152,
  "updateMicros": 15,
  "thresholdNote": "The P99 threshold of 53656.06 was set when the run was created and is not recomputed when a transaction is appended."
}
```

The file has 1,001 lines, so the appended transaction gets line 1002. `bucketStats` are the stats of its
bucket after the update: it already held the 729.96 transaction at line 16, so the count is now 2.

A new transaction ID raises no flag:

```bash
curl "$API/api/runs/$RUN/transactions" \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"TXN-LIVE-0001","accountId":"ACC000010","amount":"1250.00","timestamp":"2026-01-15T11:30:00+05:30","merchant":"Grocery Store 017"}'
```

`200 OK`

```json
{
  "transaction": {
    "lineNumber": 1003,
    "transactionId": "TXN-LIVE-0001",
    "accountId": "ACC000010",
    "amountPaise": 125000,
    "epochSeconds": 1768456800,
    "merchant": "Grocery Store 017"
  },
  "flags": [],
  "bucketIndex": 20792,
  "bucketStats": {
    "sumPaise": 125000,
    "count": 1,
    "maxPaise": 125000
  },
  "screenMicros": 13,
  "updateMicros": 11,
  "thresholdNote": "The P99 threshold of 53656.06 was set when the run was created and is not recomputed when a transaction is appended."
}
```

An invalid field is 400 with the reject reason as the code (`MISSING_FIELD`, `INVALID_AMOUNT`,
`AMOUNT_NOT_POSITIVE` or `INVALID_TIMESTAMP`) and the field in `details`. A timestamp before the run's first
bucket is 422 `TIMESTAMP_BEFORE_RANGE`.

## Errors

Every error has the same body: a stable `code`, a readable `message`, and `details` with the values that
caused it.

```bash
curl "$API/api/runs/00000000-0000-0000-0000-000000000000"
```

`404 Not Found`

```json
{
  "code": "RUN_NOT_FOUND",
  "message": "No run with this ID exists. Runs expire 30 minutes after they are created.",
  "details": {
    "runId": "00000000-0000-0000-0000-000000000000"
  }
}
```

```bash
curl -G "$API/api/runs/$RUN/window" --data-urlencode "from=1769000000" --data-urlencode "to=1768000000"
```

`400 Bad Request`

```json
{
  "code": "INVALID_WINDOW",
  "message": "Window start is after window end",
  "details": {
    "from": 1769000000,
    "to": 1768000000
  }
}
```

```bash
curl -G "$API/api/runs/$RUN/window" --data-urlencode "from=1700000000" --data-urlencode "to=1700003600"
```

`400 Bad Request`

```json
{
  "code": "WINDOW_OUT_OF_RANGE",
  "message": "Window lies entirely outside the data range",
  "details": {
    "requestedFrom": 1700000000,
    "requestedTo": 1700003600,
    "dataStart": 1767209318,
    "dataEnd": 1769790596
  }
}
```

```bash
curl -G "$API/api/bloom/plan" --data-urlencode "n=1000" --data-urlencode "p=0"
```

`400 Bad Request`

```json
{
  "code": "INVALID_CONFIG",
  "message": "The configuration is invalid.",
  "details": {
    "fieldErrors": [
      {
        "field": "p",
        "message": "must be greater than 0"
      }
    ]
  }
}
```

The full list of codes and statuses is in the API section of the report,
[docs/report/02-architecture.md](report/02-architecture.md#23-api).
