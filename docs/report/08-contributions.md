# 8. Contributions

Each person owned one algorithm from code to tests, benchmark and report section, plus a share of the
API, frontend, deployment and documentation. The build plan numbered 45 commits, K01 to K15, H01 to H15
and V01 to V15. Some were made by a different teammate than the one first planned, so every commit below
is listed under the person who made it, as recorded in the repository's history on `main`.

## Krrish: Bloom filter and screening pipeline

**Algorithm.** Bloom filter with our own MurmurHash3 x64 128-bit hash and Kirsch-Mitzenmacher double
hashing, used as a precheck for blacklisted accounts and duplicate transaction IDs, with every hit
confirmed against an exact store (section 3).

**Built.** The repository skeleton and Spring Boot backend; the domain model, money parsing and
the shared error handling; MurmurHash3, the Bloom filter and its sizing formulas with their tests; the
streaming CSV parser and row validation; the screening pipeline with exact confirmation; the run lifecycle
API and in-memory run store; the Bloom sizing plan endpoint and config validation; the run page, flagged
transactions table and Bloom filter statistics panel; the Bloom filter benchmark against a HashSet; the
ingestion and run endpoint edge-case tests; the Home, Method and 404 pages with the favicon and meta tags;
the one-command local start; the Vercel configuration; and the Quickselect and benchmark report sections
with the README screenshots.

| Plan ID | Commit | Message |
| --- | --- | --- |
| K01 | `dfea646` | chore: initialize monorepo and Spring Boot backend |
| K02 | `c483d84` | feat: add transaction model, money parsing and error handling |
| K03 | `a26fc85` | feat: implement MurmurHash3 x64 128-bit hash |
| K04 | `a849f40` | feat(algo): add Bloom filter with double hashing and sizing math |
| K05 | `08f23da` | test(algo): add Bloom filter correctness and probabilistic tests |
| K06 | `e676602` | feat(ingest): add streaming CSV parser and row validation |
| K07 | `0178dac` | feat(service): add Bloom-based screening with exact confirmation |
| K08 | `db7d3bd` | feat(api): add run lifecycle endpoints and in-memory run store |
| K09 | `a283641` | feat(api): add Bloom sizing plan endpoint and config validation |
| K10 | `55ff6d9` | feat(frontend): add run page with summary and flagged transactions table |
| K11 | `f08804a` | feat(frontend): add Bloom filter statistics panel |
| K12 | `b0c8b3d` | feat(bench): benchmark Bloom filter against HashSet baseline |
| K13 | `c467c9b` | test(api): add ingestion and run endpoint edge-case tests |
| V13 | `944fa5c` | feat(frontend): add home, method and 404 pages, favicon and meta tags |
| V15 | `84be4b7` | docs: add Quickselect and benchmark report sections with screenshots |
| outside the plan | `4e7a5f0` | docs: add project info |
| outside the plan | `d738334` | build: start backend and frontend with one npm run dev |
| outside the plan | `bdad753` | feat(api): include each flag's transaction in flag pages |
| outside the plan | `a806073` | chore(deploy): remove Vercel and Render configuration and references |
| outside the plan | `70fe35b` | chore(deploy): restore Render blueprint and add Vercel config for the frontend |

## Hardik: segment tree and window analytics

**Algorithm.** Iterative segment tree over fixed-width time buckets, storing sum, count and maximum per
node, with O(log B) window queries, O(log B) point updates and doubling growth for live appends
(section 4).

**Built.** The project proposal; the CI workflow; the segment tree and its tests; the time-bucket
index with clamping and growth; the window and series analytics endpoints; live transaction append with
segment tree point updates; the time-window charts and window query form; the live transaction form; the
segment tree benchmark against a linear scan; the window, series and live append integration tests; the
404 response for unknown API paths; the Privacy and Terms pages; the architecture, API reference and
segment tree report sections; the Bloom filter report section and the full README; and this assembled
report with the demo script.

| Plan ID | Commit | Message |
| --- | --- | --- |
| H01 | `7f40cef` | docs: add project proposal |
| H02 | `b0f7bd7` | ci: add GitHub Actions for backend tests and frontend build |
| H03 | `d640877` | feat(algo): implement segment tree for sum, count and max |
| H04 | `9cc4209` | test(algo): add segment tree correctness tests |
| H05 | `5abace3` | feat(algo): add time-bucket index over the segment tree |
| H06 | `cc28e1d` | feat(service): add segment tree window analytics endpoints |
| H07 | `7c4c414` | feat(api): add live transaction append with point updates |
| H08 | `f836681` | feat(frontend): add time-window chart and window query form |
| H09 | `cdf6e7d` | feat(frontend): add live transaction form with point update result |
| H10 | `a1be64b` | feat(bench): benchmark segment tree against linear scan |
| H11 | `2eae0d0` | test(api): add window, series and live append integration tests |
| H13 | `103c729` | feat(frontend): add privacy policy and terms pages |
| H14 | `3b78cd9` | docs(report): add architecture, API diagram and segment tree sections |
| K15 | `6422e76` | docs: add Bloom filter report section and complete README |
| H15 | this commit | docs(report): assemble final technical report and demo script |
| outside the plan | `bb52ce6` | fix(api): return 404 NOT_FOUND for unknown paths instead of 500 |

## Vedant: Quickselect, synthetic data and benchmarks

**Algorithm.** Randomized Quickselect with a seeded random pivot and a three-way partition, for the
nearest-rank percentile that sets the `HIGH_AMOUNT` threshold, over a whole run or one time window
(section 5).

**Built.** The frontend scaffold, design tokens and app layout; Quickselect and its tests; the
seeded synthetic data generator and the committed samples; the risk threshold service and high-amount
flags; the typed API client and the Screen page; the percentile threshold panel; the benchmark harness
with JSON output; the Quickselect benchmark against a full sort; the Benchmarks page and the committed
measured results; the threshold endpoint tests; the Dockerfile, Render blueprint and CORS configuration;
and the responsive, accessibility and loading-state pass.

| Plan ID | Commit | Message |
| --- | --- | --- |
| V01 | `0b1da16` | chore(frontend): scaffold React, Vite and TypeScript app |
| V02 | `99b46e8` | feat(frontend): add design tokens and app layout |
| V03 | `39d948d` | feat(algo): implement randomized Quickselect with three-way partition |
| V04 | `e261fe9` | test(algo): add Quickselect correctness tests |
| V05 | `2be1fc9` | feat(data): add seeded synthetic transaction generator and samples |
| V06 | `f366a1d` | feat(service): add Quickselect risk threshold and high-amount flags |
| V07 | `e1cb303` | feat(frontend): add typed API client and Screen page |
| V08 | `c1cad07` | feat(frontend): add percentile threshold panel |
| V09 | `053cd96` | feat(bench): add benchmark harness with JSON output |
| V10 | `a6c6ba6` | feat(bench): benchmark Quickselect against full sort |
| V11 | `d1b7fdf` | feat(frontend): add benchmarks page from measured results |
| V12 | `9e97a8e` | test(api): add threshold endpoint tests |
| K14 | `5f00030` | chore(deploy): add Dockerfile, Render blueprint and CORS config |
| V14 | `bcda16e` | fix(frontend): responsive, accessibility and loading-state pass |

Vedant also merged pull request #4 (`827f473`), which brought in V03.

## Not done

**H12, deploying the frontend to Vercel with a custom domain.** The Vercel configuration
(`frontend/vercel.json`) and the Vercel half of `docs/deployment.md` are in commit `70fe35b`. The custom
domain, the `api.` subdomain on Render and the DNS records are not set up yet, so the README has no live
site link.
