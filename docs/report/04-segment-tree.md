# 4. Segment tree for time-window analytics

Owner: Hardik. Code: `com.txnscreen.algo.segtree` (`SegmentTree`, `NodeStats`, `TimeWindowIndex`,
`WindowResult`). Syllabus: Unit 3.

## 4.1 Problem

An analyst looking at a run asks questions about time windows: how much money moved between 09:00 and
18:00 on 5 January, how many transactions there were, and what the largest single amount was. The run
page asks the same question for 120 consecutive windows at once to draw its charts, and the window form
lets the analyst ask for any other window. Live transactions can also be added to a run after it is
created, and later answers must include them.

The simple answer is a linear scan: go through every transaction and add up those inside the window. That
costs O(n) per window, so the chart alone would cost 120 full passes over up to 200,000 rows, and the
cost grows with every row. We want each window to cost the same small amount however many rows the run
has, while still accepting new transactions.

A segment tree does both. After an O(n + B) build, it answers the sum, count and maximum of any range of
B time buckets in O(log B), and records a new transaction in O(log B).

## 4.2 Time buckets

The tree's leaves are fixed-width time buckets, not individual transactions.

- **Width.** `bucketSeconds` (w) comes from the run config: default 60, allowed from 1 to 86,400.
- **Origin.** t0 is the earliest timestamp rounded down to a multiple of w:
  `t0 = floorDiv(minEpochSeconds, w) * w`.
- **Mapping.** A timestamp ts goes in bucket `(ts - t0) / w`. Since ts >= t0, this integer division rounds
  down.
- **Count.** B = `(maxEpochSeconds - t0) / w + 1`, so the buckets run from the first transaction to the
  last. Empty buckets are kept: they are leaves with sum 0, count 0 and max 0.
- **Leaf contents.** Each leaf holds the stats of its bucket: the sum of its amounts in paise, the number of
  amounts, and the largest amount.

**Worked example (the committed 1K sample, w = 60).** The first transaction is at 1767209318
(2026-01-01 00:58:38 IST), so t0 = 1767209280 (00:58:00 IST). The last is at 1769790596 (2026-01-30
21:59:56 IST), so B = (1769790596 - 1767209280) / 60 + 1 = 43,022 buckets. A live transaction at
1768456800 (2026-01-15 11:30:00 IST) goes in bucket (1768456800 - 1767209280) / 60 = 20,792, which is
the `bucketIndex` the API returned for it (see `docs/api.md`).

**Resolution.** A window query covers every bucket the window touches. A window from 09:00:30 to 09:05:10
with w = 60 includes all of the 09:00 bucket and all of the 09:05 bucket. So answers are exact to the
bucket width, not to the second. A smaller w gives finer answers but more leaves. The series endpoint
splits its range on bucket boundaries, so each chart point is exact.

**Why buckets and not transactions as leaves.** Leaves indexed by time let a window be turned into a leaf
range with two divisions, with no search. They also let a live transaction land in its bucket without
shifting any other leaf, which leaves indexed by sorted position could not do.

## 4.3 Tree layout and node merge

`SegmentTree` is an iterative, array-based tree:

- capacity C is B rounded up to a power of two, so the tree is a perfect binary tree;
- three `long[]` arrays, `sum`, `count` and `max`, each of length 2C, hold every node's stats;
- node 1 is the root, the children of node i are 2i and 2i + 1, and leaf j is at index C + j.

Each node's stats are a `NodeStats(sum, count, max)`. Two nodes are combined with the merge rule:

```
merge(a, b) = (a.sum + b.sum, a.count + b.count, max(a.max, b.max))
EMPTY       = (0, 0, 0)
```

Merge is associative and commutative, and `EMPTY` is its identity: adding 0 changes no sum or count, and
since every amount is positive, `max(x, 0) = x`. So the stats of any set of disjoint ranges can be merged
in any order and give the stats of their union. This is the property every operation below relies on.
Leaves past B, added only to reach a power of two, stay `EMPTY`.

## 4.4 Operations

### Build

```
build(leafSum, leafCount, leafMax):              // B leaves
    C <- smallest power of two >= B
    allocate sum, count, max of length 2C, all zero
    copy leaf j into index C + j, for j = 0 .. B-1
    for node <- C-1 down to 1:
        recompute(node)

recompute(node):
    sum[node]   <- sum[2node]   + sum[2node+1]
    count[node] <- count[2node] + count[2node+1]
    max[node]   <- max(max[2node], max[2node+1])
```

`TimeWindowIndex.build` first fills the leaf arrays in one pass over the n transactions, then calls this
build once. It never calls `add` n times.

### Point update (add)

```
add(j, amount):                                  // amount > 0
    leaf <- C + j
    sum[leaf]   <- sum[leaf] + amount
    count[leaf] <- count[leaf] + 1
    max[leaf]   <- max(max[leaf], amount)
    node <- leaf / 2
    while node >= 1:
        recompute(node)
        node <- node / 2
```

### Range query

```
query(l, r):                                     // leaves l..r inclusive
    result <- EMPTY
    left  <- C + l
    right <- C + r + 1                           // half-open [left, right)
    while left < right:
        if left is odd:                          // left is a right child
            result <- merge(result, node(left))
            left <- left + 1
        if right is odd:                         // right - 1 is a left child
            right <- right - 1
            result <- merge(result, node(right))
        left  <- left / 2
        right <- right / 2
    return result
```

**Worked example.** Take B = 5, so C = 8 and the leaves are nodes 8 to 12. Query leaves 1 to 3: left = 9,
right = 12.

1. left = 9 is odd, so node 9 (leaf 1) is merged and left becomes 10. right = 12 is even. Move up: left = 5,
   right = 6.
2. left = 5 is odd, so node 5 is merged. Node 5 covers nodes 10 and 11, which are leaves 2 and 3. left
   becomes 6. right = 6 is even. Move up: left = 3, right = 3.
3. left = right, so the loop stops.

The result is node 9 merged with node 5, exactly leaves 1, 2 and 3, from 2 nodes instead of 3 leaves.

### Window query, clamping and series (TimeWindowIndex)

```
query(from, to):                                 // epoch seconds, inclusive
    if from > to:                         error 400 INVALID_WINDOW
    if to < dataStart or from > dataEnd:  error 400 WINDOW_OUT_OF_RANGE (both ranges in details)
    effFrom <- max(from, dataStart)
    effTo   <- min(to, dataEnd)
    clamped <- (effFrom != from) or (effTo != to)
    return tree.query(bucket(effFrom), bucket(effTo)), effFrom, effTo, clamped
```

A window that is partly outside the data is narrowed to the data and answered with `clamped: true`, and
the response carries the effective bounds, so the user can see what was actually counted.

`series(from, to, p)` clamps the window the same way, splits its buckets into min(p, number of buckets)
consecutive groups on bucket boundaries, and runs one query per group. Because the groups follow bucket
boundaries, every bucket belongs to exactly one group, so the counts of the groups add up to the count of
the whole window. A test checks that the series counts sum to the run's `validRows`.

### Live append with growth

```
add(t):
    if t.ts < t0:                         error 422 TIMESTAMP_BEFORE_RANGE
    j <- (t.ts - t0) / w
    if j >= capacity:
        newCapacity <- capacity
        while newCapacity <= j:
            newCapacity <- 2 * newCapacity
        copy the three leaf arrays into arrays of length newCapacity
        tree <- build(those arrays)              // O(newCapacity)
    tree.add(j, t.amount)
    update the leaf arrays, dataStart, dataEnd and bucket count
```

`TimeWindowIndex` keeps its own copy of the leaf values for this purpose: when a transaction lands past
the last bucket, the leaf arrays are extended with empty buckets and the tree is rebuilt from them. Every
earlier bucket keeps its value, so earlier windows give the same answers after a rebuild. A test appends a
transaction far past the end and checks this. The tree cannot grow to the left, because every bucket index
is measured from t0, so a transaction before t0 is rejected.

## 4.5 Correctness

**Invariant.** For every internal node i, `node(i) = merge(node(2i), node(2i + 1))`. With the merge
properties in section 4.3, this means every node holds the stats of exactly the leaves below it.

- **Build establishes it.** Nodes are recomputed from C - 1 down to 1. A node's children have larger
  indexes, so they are already final when the node is recomputed.
- **Add preserves it.** Changing leaf j can only change nodes whose range contains j, which are the leaf's
  ancestors. `add` recomputes exactly those, from the bottom up, so each is recomputed after its changed
  child. No other node's children change.
- **Growth preserves it.** A rebuild runs the build above on the extended leaf arrays.

**Query returns the stats of exactly leaves l to r.** At the start of each loop iteration, `result` merged
with the stats of the nodes in [left, right) at the current level equals the stats of leaves l to r. It
holds at the start, when [left, right) is the leaves themselves and `result` is `EMPTY`. In each
iteration:

- if left is odd, node `left` is a right child, so its parent also covers a leaf before the range. It is
  moved from the range into `result`, and left advances. The right end is handled the same way;
- after that, left is even and right is even, so [left, right) is a whole number of sibling pairs, and is
  covered exactly by the parents [left / 2, right / 2) one level up.

So the invariant holds after every iteration. The loop ends when the range is empty, and then `result`
alone is the answer. Every node merged into `result` is removed from the remaining range at that moment,
so the merged nodes are disjoint and no leaf is counted twice. At most 2 nodes are merged per level, and
there are log2(C) + 1 levels, so a query merges O(log B) disjoint nodes.

**Overflow.** Sums are `long` paise. The largest upload is 200,000 rows, and a test adds sums near 10^15
paise per leaf without overflow, far beyond any real total.

**Tests.** `SegmentTreeTest` (8 tests) checks a one-leaf tree, full-range and single-leaf queries, 10,000
random add and query operations against a brute-force array, build against repeated adds, ranges of empty
buckets, large sums, and invalid input. `TimeWindowIndexTest` (8 tests) checks bucket mapping, clamping,
both window errors, growth that leaves earlier windows unchanged, appends before the first bucket, and
series split on bucket boundaries. `WindowSeriesLiveAppendIntegrationTest` (11 tests) checks the same
rules through the HTTP API. The benchmark also checks every one of its 1,000 windows against the linear
scan before recording a time.

## 4.6 Complexity

B is the number of buckets, C < 2B is the capacity, n is the number of transactions and p is the number of
series points.

| Operation | Time | Extra space |
| --- | --- | --- |
| `TimeWindowIndex.build` | O(n + B) | O(B) |
| `SegmentTree.build` | O(B) | O(B) |
| `add` (point update) | O(log B) | O(1) |
| `add` when the timestamp is past the last bucket | O(B') to rebuild at the new capacity B', O(1) amortized (below) | O(B') |
| `query` | O(log B) | O(1) |
| `series` | O(p log B) | O(p) |
| Linear scan baseline, per window | O(n) | O(1) |

The tree takes 3 arrays of 2C longs, and `TimeWindowIndex` keeps 3 leaf arrays of at most C longs, so the
index uses at most 9C longs. Since C < 2B, that is O(B) space. It does not depend on n: every transaction
is folded into its bucket.

**Amortized growth on append.** Capacity only grows by doubling. Suppose live transactions arrive in time
order and the capacity grows through C0, 2C0, 4C0, up to a final capacity Cf. The rebuilds cost
O(2C0 + 4C0 + ... + Cf), and that sum is less than 2Cf. Since Cf is at most twice the last bucket index
reached, the total rebuild work is linear in the number of buckets the index grew by. So when live
transactions arrive in time order with at least one per bucket on average, the rebuild cost is O(1)
amortized per append, and every other append is a plain O(log B) point update. A single transaction far
in the future still pays one O(B') rebuild, and the index rejects any growth beyond 4,194,304 buckets with
422 `TOO_MANY_BUCKETS`.

## 4.7 Results

From `docs/benchmarks/results.json`: commit `a6c6ba6`, measured on 16 Nov 2026 on an Intel Core
i7-13650HX laptop with 24 GB of RAM, OpenJDK 25.0.4.1, seed 42, 3 warmup runs and 7 timed runs, median
reported. The machine and command are in `docs/benchmarks/machine.md`.

Each size uses n rows from the synthetic generator with w = 60. Query times are the average over 1,000
random bucket-aligned windows. Update time is the average over 10,000 point updates inside the existing
buckets.

| n (rows) | Buckets B | Build (ms) | Segment tree query (µs) | Linear scan query (µs) | Point update (ns) | Mismatched windows |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10,000 | 43,138 | 0.7204 | 0.3101 | 5.4096 | 121.57 | 0 |
| 100,000 | 43,196 | 3.1349 | 0.3402 | 226.3171 | 192.19 | 0 |
| 1,000,000 | 43,200 | 42.2908 | 0.4981 | 2,390.6988 | 281.49 | 0 |

Derived from the medians above:

| n (rows) | Scan time / tree time | Queries before the build pays for itself |
| ---: | ---: | ---: |
| 10,000 | 17.4 | 142 |
| 100,000 | 665.2 | 14 |
| 1,000,000 | 4,799.6 | 18 |

The second column is build time divided by the time each query saves, (scan - tree), rounded up.

**What the results show.**

- **Same answers.** Every one of the 1,000 windows gave the same sum, count and maximum from the segment
  tree and from the linear scan, at every size. The benchmark stops without writing results if any window
  differs.
- **Query time does not follow n.** From 10,000 to 1,000,000 rows, n grows 100 times and the linear scan
  grows about 442 times, from 5.4096 to 2,390.6988 µs. The segment tree query grows from 0.3101 to 0.4981 µs.
- **Build time follows n.** Build is O(n + B), and with B almost fixed it grows with n, from 0.7204 to
  42.2908 ms.
- **When it does not help.** At 10,000 rows, the build costs as much as about 142 linear scans. One or two
  window queries on a small run are faster with a plain scan. The tree pays off on a run page, which asks
  for 120 windows for the chart, and on every later window query.

**Limitation of this benchmark.** The generated data always spans 30 days, so B is about 43,200 at every
size, and log2(B) is about 15.4 for all three. The benchmark therefore cannot show query time growing with
log2(B), which the proposal listed as a success criterion. Measuring that needs runs with different time
spans or bucket widths. Because the tree has the same shape at all three sizes, the rise in query time
from 0.3101 to 0.4981 µs, and in update time from 121.57 to 281.49 ns, does not come from extra tree
levels. We did not measure its cause.

## 4.8 Limitations

- Answers are exact to the bucket width, not to the second, as explained in section 4.2.
- The index cannot grow to the left. A live transaction before the first bucket is rejected with 422
  `TIMESTAMP_BEFORE_RANGE`.
- Only sum, count and maximum are kept. A minimum or an average needs no new structure: the average is sum
  divided by count, and a minimum would be a fourth array with the same merge pattern. A median or
  percentile cannot be merged this way, which is why thresholds use Quickselect instead.
- A run's index lives in memory and is lost when the run expires or the server restarts.
