package com.txnscreen.bench;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.openjdk.jol.info.GraphLayout;

import com.txnscreen.algo.bloom.BloomFilter;
import com.txnscreen.baseline.HashSetMembership;

/**
 * Compares our Bloom filter, sized for p = 0.01 with the optimal m and k, against the {@link HashSetMembership}
 * baseline for n keys: memory, insert time, query time for present and absent keys, and the filter's measured
 * and theoretical false positive rates. Present keys are "ACC" + i and absent keys "ZZZ" + i, for i from 0 to
 * n - 1, both zero-padded to six digits like the generated account IDs, so present and absent keys have the
 * same length.
 *
 * <p>A Bloom filter does not store keys. Adding a key only sets k bits in an array of m bits that every key
 * shares, so at p = 0.01 it costs about 9.6 bits per key, however long the key is. That is why it saves
 * memory: a HashSet keeps every key string, plus a table slot and an entry node for each key.
 *
 * <p>Because the bits are shared, a key that was never added can find all k of its bits set by other keys, so
 * a "possibly present" answer is wrong with probability about p, and with no keys stored the filter has
 * nothing to check that answer against. A HashSet stores the keys, so its answers are exact. That is why the
 * screener still keeps a HashSet as the exact store and confirms every Bloom positive against it before a flag
 * is raised: the filter answers "definitely absent" for most absent keys without touching the exact store,
 * and the exact store decides the rest.
 *
 * <p>Memory is the total size of each object graph as measured by JOL's {@link GraphLayout}. The HashSet is
 * reported twice: including the key strings it holds, and excluding them, which is the first figure minus
 * the keys' own layout. Insert time builds a new, presized structure on every repeat. Query times are per
 * lookup, over all n present or all n absent keys.
 *
 * <p>Keys are inserted and queried in a random order drawn from the seed, and the present keys are queried
 * through separate String instances with the same text, as parsed transactions would be. In key order,
 * consecutive keys have consecutive String hash codes, so a HashSet would walk its table and nodes in memory
 * order, and querying the stored instances would match on reference equality without comparing any text.
 * String caches its hash code, so after the first pass a HashSet lookup does not hash the key again, while the
 * Bloom filter runs MurmurHash3 on every query.
 */
public final class BloomBench implements Benchmark {

    /** The false positive rate the filter is sized for. */
    static final double TARGET_FPR = 0.01;

    private static final String NAME = "membership";
    private static final String BLOOM = "bloom";
    private static final String HASH_SET = "hashset";
    private static final String PRESENT_PREFIX = "ACC";
    private static final String ABSENT_PREFIX = "ZZZ";
    private static final String KEY_FORMAT = "%s%06d";
    private static final double NANOS_PER_MILLI = 1_000_000;

    @Override
    public String name() {
        return NAME;
    }

    /**
     * Measures the Bloom filter and the HashSet baseline for size keys.
     *
     * <p>Time complexity: O((warmup + repeats) * n * k) for the Bloom filter and O((warmup + repeats) * n)
     * expected for the HashSet, after O(n) to build the keys. Space complexity: O(n) for the keys and the
     * HashSet, plus O(m) bits for the filter.
     *
     * @param size the number of present keys n, which is also the number of absent keys queried
     * @param cfg the repeats, warmup iterations and the seed, used for the key order and as the MurmurHash3 seed
     * @return the measurements for both variants
     * @throws IllegalStateException if the Bloom filter reports any present key as absent
     */
    @Override
    public List<Measurement> run(int size, BenchConfig cfg) {
        Random random = new Random(cfg.seed());
        Keys keys = new Keys(shuffled(keys(PRESENT_PREFIX, size), random),
                shuffled(keys(PRESENT_PREFIX, size), random), shuffled(keys(ABSENT_PREFIX, size), random));
        List<Measurement> measurements = new ArrayList<>(measureBloom(size, cfg, keys));
        measurements.addAll(measureHashSet(size, cfg, keys));
        return List.copyOf(measurements);
    }

    /**
     * Throws if the filter reports any present key as absent. A Bloom filter must never give a false negative,
     * so a benchmark that saw one is measuring a broken filter and must not write results.
     *
     * <p>Time complexity: O(n * k). Space complexity: O(1).
     *
     * @param filter the filter every present key was added to
     * @param presentKeys the keys that were added
     * @throws IllegalStateException if any present key is reported as absent
     */
    static void requireNoFalseNegatives(BloomFilter filter, String[] presentKeys) {
        int falseNegatives = presentKeys.length - countHits(filter, presentKeys);
        if (falseNegatives != 0) {
            throw new IllegalStateException("The Bloom filter reported " + falseNegatives + " of "
                    + presentKeys.length + " present keys as absent. It must never give a false negative.");
        }
    }

    private static List<Measurement> measureBloom(int size, BenchConfig cfg, Keys keys) {
        long seed = cfg.seed();
        Timer.Timing insert = Timer.time(cfg, () -> filledBloom(keys.stored(), seed));
        BloomFilter filter = filledBloom(keys.stored(), seed);
        Timer.Timing presentQueries = Timer.time(cfg, () -> countHits(filter, keys.present()));
        Timer.Timing absentQueries = Timer.time(cfg, () -> countHits(filter, keys.absent()));
        requireNoFalseNegatives(filter, keys.present());
        double measuredFpr = (double) countHits(filter, keys.absent()) / size;
        long memory = GraphLayout.parseInstance(filter).totalSize();
        return List.of(
                Measurement.single(NAME, BLOOM, size, "bits", "bits", filter.bitSize()),
                Measurement.single(NAME, BLOOM, size, "hashes", "count", filter.hashCount()),
                Measurement.single(NAME, BLOOM, size, "memory", "bytes", memory),
                insert.toMeasurement(NAME, BLOOM, size, "insertTime", "ms", NANOS_PER_MILLI),
                presentQueries.toMeasurement(NAME, BLOOM, size, "queryTimePresent", "ns/query", size),
                absentQueries.toMeasurement(NAME, BLOOM, size, "queryTimeAbsent", "ns/query", size),
                Measurement.single(NAME, BLOOM, size, "measuredFpr", "fraction", measuredFpr),
                Measurement.single(NAME, BLOOM, size, "theoreticalFpr", "fraction", filter.theoreticalFpr()),
                // requireNoFalseNegatives has just confirmed there are none.
                Measurement.single(NAME, BLOOM, size, "falseNegatives", "count", 0));
    }

    private static List<Measurement> measureHashSet(int size, BenchConfig cfg, Keys keys) {
        Timer.Timing insert = Timer.time(cfg, () -> filledHashSet(keys.stored()));
        HashSetMembership set = filledHashSet(keys.stored());
        Timer.Timing presentQueries = Timer.time(cfg, () -> countHits(set, keys.present()));
        Timer.Timing absentQueries = Timer.time(cfg, () -> countHits(set, keys.absent()));
        long includingKeys = GraphLayout.parseInstance(set).totalSize();
        // Passing the array as the roots measures the key strings alone, not the array that holds them.
        long keysOnly = GraphLayout.parseInstance((Object[]) keys.stored()).totalSize();
        return List.of(
                Measurement.single(NAME, HASH_SET, size, "memoryIncludingKeys", "bytes", includingKeys),
                Measurement.single(NAME, HASH_SET, size, "memoryExcludingKeys", "bytes", includingKeys - keysOnly),
                insert.toMeasurement(NAME, HASH_SET, size, "insertTime", "ms", NANOS_PER_MILLI),
                presentQueries.toMeasurement(NAME, HASH_SET, size, "queryTimePresent", "ns/query", size),
                absentQueries.toMeasurement(NAME, HASH_SET, size, "queryTimeAbsent", "ns/query", size));
    }

    private static String[] keys(String prefix, int count) {
        String[] keys = new String[count];
        for (int i = 0; i < count; i++) {
            keys[i] = String.format(KEY_FORMAT, prefix, i);
        }
        return keys;
    }

    private static String[] shuffled(String[] keys, Random random) {
        Collections.shuffle(Arrays.asList(keys), random);
        return keys;
    }

    private static BloomFilter filledBloom(String[] keys, long seed) {
        BloomFilter filter = BloomFilter.forCapacity(keys.length, TARGET_FPR, seed);
        for (String key : keys) {
            filter.add(key);
        }
        return filter;
    }

    private static HashSetMembership filledHashSet(String[] keys) {
        HashSetMembership set = new HashSetMembership(keys.length);
        for (String key : keys) {
            set.add(key);
        }
        return set;
    }

    private static int countHits(BloomFilter filter, String[] keys) {
        int hits = 0;
        for (String key : keys) {
            if (filter.mightContain(key)) {
                hits++;
            }
        }
        return hits;
    }

    private static int countHits(HashSetMembership set, String[] keys) {
        int hits = 0;
        for (String key : keys) {
            if (set.contains(key)) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * The keys of one run, each array in its own random order.
     *
     * @param stored the present keys as inserted
     * @param present the same texts as separate String instances, for querying
     * @param absent keys that are never inserted
     */
    private record Keys(String[] stored, String[] present, String[] absent) {
    }
}
