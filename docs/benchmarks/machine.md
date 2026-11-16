# Benchmark machine

The results in [results.json](results.json) were measured once, on this machine, on 16 Nov 2026.

| Item | Value |
| --- | --- |
| Laptop | Lenovo 83DV, on AC power, other apps closed |
| CPU | 13th Gen Intel Core i7-13650HX, 14 cores, 20 threads |
| RAM | 24 GB |
| OS | Windows 11 Home Single Language, version 10.0.26200 |
| JDK | Microsoft Build of OpenJDK 25.0.4.1 (OpenJDK 64-Bit Server VM), compiling for Java 21 |
| Heap | `MAVEN_OPTS=-Xmx3g` |
| Code | commit `a6c6ba6` |

## Command

Run from `backend/` in Git Bash:

```bash
export MAVEN_OPTS=-Xmx3g
./mvnw -q compile exec:java -Dexec.mainClass=com.txnscreen.bench.BenchmarkRunner \
  -Dexec.args="--sizes 10000,100000,1000000 --repeats 7 --warmup 3 --seed 42 \
  --machine 'Intel Core i7-13650HX (14 cores, 20 threads), 24 GB RAM, Lenovo 83DV laptop on AC power' \
  --commit $(git rev-parse --short HEAD) --out ../docs/benchmarks/results.json"
```

In PowerShell, set the heap with `$env:MAVEN_OPTS="-Xmx3g"` instead of `export`.

## Method

- Each benchmark runs at 10,000, 100,000 and 1,000,000 rows or keys.
- Every timing is the median of 7 timed runs after 3 untimed warmup runs, so the JIT has compiled the code
  first. The minimum and maximum are kept in the file too.
- All data comes from seed 42, so every run measures the same inputs.
- Quickselect's comparison counts are the median over 25 pivot seeds.
- Each benchmark checks that our algorithm and its baseline give the same answers before it records
  anything, and stops if they do not.
- JOL measures memory. On this JDK it prints warnings that it cannot attach its agent, and still reports
  the object sizes.

Timings depend on the machine, so compare the algorithms with each other, not with numbers measured
elsewhere.
