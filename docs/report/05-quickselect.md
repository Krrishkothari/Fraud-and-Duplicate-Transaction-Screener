# 5. Randomized Quickselect for the risk threshold

Owner: Vedant. Code: `com.txnscreen.algo.select` (`RandomizedQuickselect`, `SelectResult`) and the threshold
service in `com.txnscreen.service` (`ThresholdService`). Syllabus: Unit 6, CO5 (a randomized algorithm).

## 5.1 Problem: a dynamic risk threshold

A transaction is flagged `HIGH_AMOUNT` when its amount is unusually large for the data it arrived with. A
fixed limit, such as every amount above 50,000 rupees, does not adapt: it flags almost nothing in a file of
small retail payments and almost everything in a file of business transfers. So the threshold is a
percentile of the run's own amounts. With the default p = 99, about the top 1% of amounts are flagged
whatever their scale.

The threshold is asked for more than once. The run sets it when it is created, using the configured
percentile. After that the analyst can try other percentiles (the run page offers P90, P95, P99, P99.9 and a
custom value) and ask for the percentile of one time window only, through `GET /api/runs/{id}/threshold`.
Each of those is a new question about a different percentile or a different set of amounts.

The simple answer is to sort the amounts and read the value at the right index. That costs O(n log n) and
puts every amount in order, while the question needs only one value at one rank. Selection finds the k-th
smallest value without ordering the rest. Randomized Quickselect does it in expected O(n) time.

## 5.2 Nearest-rank percentile

For n amounts and a percentile p with 0 < p <= 100, the nearest-rank percentile is the k-th smallest amount,
where:

```
k = ceil(p / 100 * n),   clamped to [1, n]
```

The result is always one of the amounts in the data, never a value between two of them, so the threshold
the analyst sees is a real transaction amount. A transaction is flagged when its amount is **strictly**
above the threshold, so at most n - k amounts are flagged, and fewer when several amounts equal the
threshold.

**Worked example.** For the 10 amounts 10, 20, ..., 100:

- P95: k = ceil(0.95 * 10) = ceil(9.5) = 10, so the threshold is 100 and no amount is above it.
- P90: k = ceil(0.90 * 10) = 9, so the threshold is 90 and one amount, 100, is above it.

`RandomizedQuickselectTest` checks both. On the committed 1K sample, P99 gives k = 990, a threshold of
53,656.06 rupees and 10 amounts above it (see `docs/api.md`).

**Exact rank.** `nearestRank` computes k with `BigDecimal`, not `double`. In `double`,
99.9 / 100 * 1000 is 999.0000000000001, whose ceiling is 1000, one rank too high. With exact decimals,
P99.9 of 1,000 values is rank 999, and a test checks it.

## 5.3 Pseudocode

```
select(values, k, seed):                         // 1 <= k <= n
    a      <- copy of values                     // the caller's array is never changed
    target <- k - 1                              // 0-based index in sorted order
    random <- new Random(seed)
    lo <- 0
    hi <- n - 1
    loop:
        pivot <- a[lo + random.nextInt(hi - lo + 1)]
        (lt, gt) <- partition3(a, lo, hi, pivot)
        if target < lt:   hi <- lt - 1           // the answer is smaller than the pivot
        elif target > gt: lo <- gt + 1           // the answer is larger than the pivot
        else:             return pivot           // the answer equals the pivot

partition3(a, lo, hi, pivot):                    // Dijkstra's three-way partition
    lt <- lo
    i  <- lo
    gt <- hi
    // invariant: a[lo..lt-1] < pivot, a[lt..i-1] = pivot, a[gt+1..hi] > pivot,
    //            a[i..gt] not yet examined
    while i <= gt:
        if a[i] < pivot:
            swap(a, lt, i); lt <- lt + 1; i <- i + 1
        elif a[i] > pivot:
            swap(a, i, gt); gt <- gt - 1         // i stays: the swapped-in value is unexamined
        else:
            i <- i + 1
    return (lt, gt)

percentile(values, p, seed):
    return select(values, nearestRank(p, n), seed)
```

`select` also counts element comparisons: one for `a[i] < pivot`, and a second for `a[i] > pivot` when the
first is false. The run summary and the threshold endpoint report the count, and the benchmark uses it.

**Worked example.** Find P60 of 5, 1, 4, 2, 3: n = 5, k = ceil(0.6 * 5) = 3, target = 2. The pivots below
are chosen by hand to show the steps; the real ones come from the seed.

1. lo = 0, hi = 4, pivot 4. The partition gives 3, 1, 2 | 4 | 5, so lt = 3 and gt = 3. target = 2 < 3,
   so hi = 2.
2. lo = 0, hi = 2, pivot 2. The partition gives 1 | 2 | 3, so lt = 1 and gt = 1. target = 2 > 1, so lo = 2.
3. lo = 2, hi = 2, pivot 3. lt = gt = 2 = target, so the answer is 3, which is the 3rd smallest of the
   five values.

The three rounds made 7, 5 and 2 comparisons. Each round examined only the part of the array that could
still hold the answer: 5, then 3, then 1 values.

## 5.4 Correctness

**Partition invariant.** At the top of each pass of the `while` loop, a[lo..lt-1] < pivot,
a[lt..i-1] = pivot, a[gt+1..hi] > pivot, and a[i..gt] is unexamined. It holds at the start, when the first
three parts are empty. Each branch moves a[i] into the part it belongs to and shrinks the unexamined part by
one. When a[i] > pivot, the value swapped in from position gt has not been examined, so i does not move. The
loop ends when the unexamined part is empty, so every value in [lo, hi] is in exactly one of the three
parts. The pivot is one of the values, so the equal part is never empty: lt <= gt.

**Outer invariant: the k-th smallest stays inside [lo, hi].** At the start of each round:

1. lo <= target <= hi;
2. every value at an index below lo is smaller than every value in a[lo..hi];
3. every value at an index above hi is larger than every value in a[lo..hi].

Parts 2 and 3 say the values outside [lo, hi] are already where a full sort would put them, so the value of
sorted rank target + 1, which is the k-th smallest, is inside a[lo..hi]. All three parts hold at the start,
when [lo, hi] is the whole array. After a partition of [lo, hi]:

- if target < lt, the new range is [lo, lt - 1]. Its values are below the pivot, and everything that now lies
  above it, the equal block and the values above the pivot, is larger, so part 3 holds. Part 2 was already
  true, and lo <= target <= lt - 1;
- if target > gt, the new range is [gt + 1, hi], and the same argument holds the other way round;
- otherwise lt <= target <= gt. Exactly lt values in the whole array are smaller than the pivot: lo of them
  below lo, by part 2, and lt - lo inside the range. The next gt - lt + 1 values in sorted order equal the
  pivot. So sorted positions lt to gt all hold the pivot, and since target is one of them, the pivot is the
  k-th smallest.

**Termination.** Every round that does not return removes the equal block, which has at least one value, from
the range. So there are at most n rounds, and the loop always returns.

**Tests.**

- `RandomizedQuickselectTest` (11 tests): 500 random arrays of up to 5,000 values with a random rank, each
  checked against sorting and indexing; all-equal input for every k from 1 to 1,000; one element; P100 is the
  maximum and a tiny p the minimum; sorted and reverse-sorted arrays of 100,000 values; 50,000 values drawn
  from only 3 amounts; the input array is unchanged; invalid input is rejected; the hand examples in
  section 5.2; exact ranks for decimal percentiles; and a comparison count between n and 20n.
- `ThresholdServiceTest` (3 tests): P99 of the 10K sample equals the sorted value, the count above is
  strict, and `HIGH_AMOUNT` flags keep transaction order.
- `ThresholdControllerTest` (7 tests): P95 and P99 through the API equal the sorted nearest-rank values,
  P100 is the maximum, a percentile outside (0, 100] is rejected, a window's threshold equals sorting only
  that window's amounts, an empty window is 422 `EMPTY_WINDOW`, a one-transaction run returns its amount for
  any percentile, and the `HIGH_AMOUNT` flag count equals `countAbove`.
- `SelectBenchTest` (4 tests): the sort baseline uses the same nearest rank and matches sorting on 200
  random inputs, the benchmark records both variants for every case, and comparisons per value at 200,000
  amounts are less than twice those at 10,000, with at most 2 per value on all-equal input.

## 5.5 Expected O(n) time

Each round costs time linear in the size of its range, s = hi - lo + 1: every value is examined once, with
one or two comparisons. So the total cost is proportional to the sum of the range sizes over all rounds.

Call a pivot **good** when its rank within the range falls in the middle half, between s/4 and 3s/4. A pivot
is chosen uniformly from the range, so it is good with probability at least 1/2. Whichever side the
answer is on, a good pivot leaves a range of at most 3s/4 values. The expected number of rounds until a good
pivot is at most 2, and a bad round never makes the range larger. So the expected total work is at most:

```
2n + 2(3/4)n + 2(3/4)^2 n + ... = 2n / (1 - 3/4) = 8n
```

That is a loose bound, but it shows the expected time is O(n). Knuth's exact analysis of the same algorithm
on distinct values gives an expected 2n(1 + H(a)) values examined to find rank k = a * n, where
H(a) = -a ln a - (1 - a) ln(1 - a). That is about 2.40n at P95 and 2.11n at P99. Section 5.8 compares this
with the measured counts.

The worst case is O(n^2): a pivot that is always the smallest or largest value in the range removes only one
value per round. With random pivots, that needs about n unlucky choices in a row, and its probability shrinks
very quickly as n grows.

**Space.** The copy takes O(n). The partition works in place, so beyond the copy the algorithm needs O(1).

## 5.6 Why random pivots

A fixed pivot rule can be beaten by the input. With "always take the last value", an array that is already
sorted gives the worst case: every pivot is the largest value in its range, so each round removes only one
value and the time is O(n^2). Sorted or nearly sorted amounts are not unusual, for example a file exported
in amount order. Any other fixed rule, such as the middle value, has its own bad inputs.

A random pivot changes what "expected" means. The expected O(n) holds for **every** input, averaged over
the algorithm's own random choices, not over some assumed distribution of inputs. No order of amounts is bad
in general, so an input chosen to be slow, without knowledge of the pivot sequence, is no slower in
expectation than any other. `sortedAndReverseSortedLargeArrays` runs sorted and reverse-sorted arrays of
100,000 values at ranks 1, 2, n/2, n - 1 and n.

**Seeded on purpose.** The pivots come from `java.util.Random` seeded with the run's `seed`, so the same run
config always gives the same comparison count and the same timings to compare. The value found does not
depend on the seed: every seed returns the k-th smallest. The cost is that the pivot sequence is
predictable to anyone who knows the seed, which section 5.9 covers.

## 5.7 Why a three-way partition

Amounts repeat. Many payments are round values, and a run can hold thousands of transactions of the same
amount. A two-way partition, which splits the range into values less than the pivot and values greater than
or equal to it, handles repeats badly. If every value in the range equals the pivot, they all land on one
side, the range shrinks by one value per round, and the time is O(n^2).

The three-way partition puts every value equal to the pivot in the middle block, and the next round leaves
the whole block out, so each distinct value is handled at most once as a pivot. For the extreme case, an
array where every amount is equal, the first pivot's equal block is the whole array. The target is inside
it, so Quickselect returns after one pass. Each value then costs exactly 2 comparisons, neither less nor
greater, so the count is exactly 2n. The benchmark measures exactly 2.00 comparisons per value at every size
and with every one of the 25 pivot seeds.

`heavyDuplicatesFromThreeAmounts` checks 200 random ranks on 50,000 values drawn from only 3 amounts, and
`allElementsEqualReturnsThatValueForAnyK` checks every rank of 1,000 equal values.

## 5.8 Results

From `docs/benchmarks/results.json`: commit `a6c6ba6`, measured on 16 Nov 2026 on an Intel Core
i7-13650HX laptop with 24 GB of RAM, OpenJDK 25.0.4.1, seed 42, 3 warmup runs and 7 timed runs, median
reported. Section 6 and `docs/benchmarks/machine.md` describe the method.

The input is the amounts of n rows from the synthetic generator with seed 42. The baseline copies the
amounts, sorts the copy with `Arrays.sort(long[])` and reads the same nearest-rank index. Both times include
the copy. Before timing anything, the benchmark checks that both methods return the same value, and it stops
if they do not.

### Time against a full sort

| n (amounts) | Quickselect P95 (ms) | Sort P95 (ms) | Sort / Quickselect | Quickselect P99 (ms) | Sort P99 (ms) | Sort / Quickselect |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10,000 | 0.3172 | 1.4094 | 4.4 | 0.2377 | 1.5948 | 6.7 |
| 100,000 | 3.0117 | 10.3991 | 3.5 | 2.8391 | 10.2225 | 3.6 |
| 1,000,000 | 18.2942 | 124.9191 | 6.8 | 19.9412 | 125.3417 | 6.3 |

The ratio columns are computed from the medians above. Quickselect was 3.5 to 6.8 times faster than sorting
at every size and both percentiles.

### Comparisons per value

Each cell is the median over 25 pivot seeds (42 to 66), with the fewest and most in brackets.

| n (amounts) | Comparisons / n, P95 | Comparisons / n, P99 |
| ---: | ---: | ---: |
| 10,000 | 3.35 (1.64 to 7.24) | 2.89 (1.10 to 6.80) |
| 100,000 | 3.28 (1.61 to 8.64) | 2.95 (1.38 to 8.35) |
| 1,000,000 | 3.09 (1.29 to 6.20) | 2.96 (1.17 to 5.78) |

**What the results show.**

- **Linear work.** n grows 100 times from the first row to the last, and the median comparisons per value
  stay between 2.89 and 3.35. If the work grew like n log n, this column would grow too. This is the
  expected O(n) of section 5.5, measured directly rather than through time.
- **It agrees with the analysis.** Section 5.5 gives about 2.40n values examined at P95 and 2.11n at P99.
  Each examined value costs 1 or 2 comparisons in our count, so the expected count lies between about
  2.40n and 4.80n at P95, and between 2.11n and 4.22n at P99. Every measured median is inside those ranges.
- **Spread between seeds.** The most expensive of the 25 seeds made up to 8.64 comparisons per value, about
  2.6 times the median, at 100,000 values. No seed came near quadratic behaviour, which at 100,000 values
  would mean tens of thousands of comparisons per value.
- **Time per value.** Quickselect took 18 to 32 ns per value, and the sort 102 to 159 ns. The sort's
  O(n log n) does not show as a steady rise in time per value across these three sizes, so the sizes alone
  do not separate n from n log n here. The comparison counts above do.

### All amounts equal

| n (amounts) | Quickselect (ms) | Sort (ms) | Comparisons / n |
| ---: | ---: | ---: | ---: |
| 10,000 | 0.0689 | 0.2042 | 2.00 |
| 100,000 | 0.6292 | 0.3186 | 2.00 |
| 1,000,000 | 8.6129 | 5.7738 | 2.00 |

The three-way partition finished in one pass with exactly 2n comparisons, as section 5.7 predicts. At
100,000 and 1,000,000 values the sort was faster: 0.3186 against 0.6292 ms, and 5.7738 against 8.6129 ms.
Java's sort for `long[]` checks first whether the array is already made of ordered runs, and an array of
equal values is one such run, so it returns after one scan, while our partition makes two comparisons per
value. At 10,000 values the sort took about 20 ns per value against 3 ns at 100,000, and was slower than
Quickselect. We did not measure why.

### Values found

| n (amounts) | P95 (rupees) | P99 (rupees) |
| ---: | ---: | ---: |
| 10,000 | 24,452.14 | 50,503.95 |
| 100,000 | 25,266.64 | 51,874.08 |
| 1,000,000 | 25,271.35 | 51,232.29 |

Quickselect and the sort returned the same value in every case. The 10,000-row benchmark data comes from
the same generator, seed and rates as the app's default 10,000-row sample run, and the run page in the
README screenshots shows the same P99 threshold of 50,503.95 rupees, with 100 amounts above it.

## 5.9 Limitations

- **Repeated questions on the same amounts.** Each new percentile runs Quickselect again in O(n). Sorting once
  costs O(n log n), after which every percentile is a single array read. At 1,000,000 amounts one sort cost
  about 6.3 P99 selections, so an analyst who asks for 7 or more percentiles of the same unchanged amounts
  would spend less time with one sorted copy. TxnScreen does not keep one: a window threshold selects from a
  different set of amounts each time, and the run's own threshold is computed once.
- **Already ordered input.** As the all-equal results show, Java's sort can beat Quickselect on input that is
  already in order. Quickselect still takes linear time there, but it does not detect order.
- **Predictable pivots.** The pivot seed is the run's `seed`, which defaults to 42 and is shown in the run
  config. Someone who knows the seed and controls the uploaded file could in principle arrange the amounts so
  that this pivot sequence picks bad pivots. That would make one run slower, never wrong. A service exposed
  to untrusted uploads could draw the pivot seed from `SecureRandom` instead and give up repeatable
  comparison counts.
- **Memory.** Every call copies the amounts, so it needs O(n) extra memory, the same as the sort baseline.
  Selecting in place would save the copy but reorder the caller's array.
- **The threshold is fixed per run.** A live transaction appended to a run is flagged against the threshold
  set when the run was created. The threshold is not recomputed, and the append response says so.
