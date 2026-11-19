# 7. Limitations

This section collects the limits of TxnScreen in one place. Sections 3.9, 4.8, 5.9 and 6.8 explain the
algorithm-specific ones in more detail.

## 7.1 The application

- **Runs live in memory only.** There is no database and nothing is written to disk. A run is deleted 30
  minutes after it is created, when it is the least recently used of 10 runs and an 11th is created, or
  when the server restarts. On the Render free tier the server stops after 15 minutes without traffic, so
  every run is lost then too. An analyst cannot save a run or come back to it the next day.
- **One server, one copy of each run.** Runs are kept in the memory of a single server process, so the API
  cannot be scaled to several instances without moving runs into a shared store.
- **Upload limits.** Files are limited to 200,000 data rows and 20 MB, to fit the free tier's 512 MB of
  memory. The 1,000,000 row results in section 6 come from local benchmarks, not from the app.
- **The first request after a sleep is slow.** A free Render service that has stopped takes about a minute
  to start again. The frontend shows a notice when a request is slow, but the wait remains.
- **No accounts or access control.** Anyone with a run's ID can read it until it expires. This is
  acceptable only because the app is meant for synthetic or anonymised data.

## 7.2 What counts as suspicious

- **Duplicates are exact ID matches.** Two rows are duplicates only when their transaction IDs are equal.
  A resubmitted payment with a new ID but the same account, amount and time is not caught. Detecting near
  duplicates would need a different method, such as grouping by account and amount within a short window.
- **Blacklist matching is exact.** An account ID that differs from a blacklist entry in case or in a
  single character is not matched.
- **The amount threshold is one percentile of one run.** `HIGH_AMOUNT` compares every amount with one
  percentile of the whole run. It does not take the account's own history, the merchant or the time of
  day into account, so a normal large payment for one account can be flagged and an unusual one for a
  small account can be missed.
- **The threshold is fixed when the run is created.** A live transaction is flagged against that
  threshold. The threshold is not recomputed on append, and the append response says so.
- **Rupees only, timestamps with an offset only.** Other currencies and timestamps without an offset are
  rejected, not converted.

## 7.3 Bloom filter (section 3)

- **No deletion.** A Bloom filter cannot remove a key without risking false negatives. Removing accounts
  from the blacklist during a run would need a counting Bloom filter.
- **Fixed size.** A filter sized for fewer keys than it receives saturates. Results stay exact because
  every hit is confirmed, but more lookups reach the exact store. The run reports a suggested size.
- **No gain inside this app.** The exact stores are in-memory `HashSet` and `HashMap` objects, so the
  filters add memory here rather than save it, and they are not faster than an in-memory set (section
  3.8). The design pays off when the exact store is slow, such as a database or a remote blacklist
  service. TxnScreen models that setup and reports the lookups the filters avoided.
- **Measured rates on small runs.** The same key always gets the same answer, so repeated transactions
  from one account repeat its false positive. With a very small blacklist the measured rate can be far
  from the theoretical one: 4.97% against 0.93% on the 1K sample.

## 7.4 Segment tree (section 4)

- **Answers are exact to the bucket width.** A window that starts or ends inside a bucket includes the
  whole bucket. The default width is 60 seconds.
- **No growth to the left.** A live transaction earlier than the run's first bucket is rejected.
- **Bucket limit.** An index holds at most 4,194,304 buckets. A long time span with a narrow bucket is
  rejected with `TOO_MANY_BUCKETS`.
- **Only sum, count and maximum.** A median or percentile cannot be combined from two halves this way,
  which is why thresholds use Quickselect instead.

## 7.5 Quickselect (section 5)

- **Repeated percentiles of the same amounts.** Each percentile runs Quickselect again in O(n). For about
  7 or more percentiles of the same unchanged amounts, one sort would be cheaper. TxnScreen does not keep a
  sorted copy.
- **Ordered input.** Java's sort detects input that is already in order and returns after one scan, so it
  beat Quickselect when every amount was equal.
- **Predictable pivots.** The pivot seed is the run's `seed`, shown in the config. Someone who knows it and
  controls the file could choose amounts that make that run slow, though never wrong.
- **Extra memory.** Every call copies the amounts.

## 7.6 Measurements (section 6)

- **One machine, one run.** Every benchmark number comes from one run on one laptop, so the absolute
  times are for comparing methods with each other, not for predicting other machines.
- **A simple harness.** `Timer` warms up 3 times and reports the median of 7 runs. It is not a harness
  such as JMH, and all benchmarks share one JVM.
- **One time span for range queries.** The generated data always spans 30 days, so the range benchmark
  has about 43,200 buckets at every size. It shows the effect of n but not of the bucket count, so the
  proposal's criterion of query time growing logarithmically with bucket count is not shown.
- **Unexplained points.** Some results are not explained by the analysis, such as the segment tree query
  time rising from 0.3101 to 0.4981 µs with the tree the same shape at every size. We did not measure their
  causes.

## 7.7 Data

- **Synthetic data only.** Every sample and benchmark uses data from our seeded generator: log-normal
  amounts by merchant category, timestamps weighted towards the daytime, and injected duplicates and
  blacklisted accounts. Real transaction data has patterns the generator does not model, so the results
  show how the algorithms behave, not how well the screen would catch real fraud.
