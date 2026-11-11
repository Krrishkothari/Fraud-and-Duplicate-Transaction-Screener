package com.txnscreen.data;

import com.txnscreen.domain.Transaction;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Generates realistic, reproducible transaction data with injected duplicates and blacklisted
 * accounts. The same arguments always produce the same data, because every random choice comes
 * from one {@link Random} built from the seed.
 */
public final class SyntheticDataGenerator {

    /** Offset of every generated timestamp (India Standard Time). */
    public static final ZoneOffset OFFSET = ZoneOffset.ofHoursMinutes(5, 30);

    private static final long START_EPOCH = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, OFFSET).toEpochSecond();
    private static final int DAYS = 30;
    private static final int BUSY_START_SECOND = 9 * 3600;
    private static final int BUSY_SECONDS = 13 * 3600;
    private static final int QUIET_SECONDS = 24 * 3600 - BUSY_SECONDS;
    private static final double BUSY_SHARE = 0.85;
    private static final int MAX_DUPLICATE_DELAY_SECONDS = 120;
    private static final long MIN_AMOUNT_PAISE = 100;
    private static final int MIN_ACCOUNTS = 50;
    private static final int ROWS_PER_ACCOUNT = 20;
    private static final int MERCHANT_COUNT = 200;
    private static final double EXTRA_BLACKLIST_SHARE = 0.10;

    /** Merchant categories with a typical (median) amount in rupees and the spread of amounts. */
    private static final List<Category> CATEGORIES = List.of(
            new Category("Grocery Store", 600, 0.6),
            new Category("Fuel Station", 1_500, 0.4),
            new Category("Restaurant", 900, 0.5),
            new Category("Pharmacy", 450, 0.6),
            new Category("Electronics Outlet", 15_000, 0.8),
            new Category("Clothing Store", 2_500, 0.7),
            new Category("Travel Agency", 12_000, 0.7),
            new Category("Utility Payment", 2_000, 0.5));

    private SyntheticDataGenerator() {
    }

    /**
     * Generates a dataset of exactly {@code size} rows.
     *
     * <p>Accounts are ACC000001 style, max(50, size / 20) of them. Amounts are log-normal per
     * merchant category, at least 1.00 rupee. Timestamps cover 30 days from
     * 2026-01-01T00:00:00+05:30, mostly between 09:00 and 22:00. With probability
     * {@code duplicateRate} a row re-emits an earlier transaction with the same fields, 0 to 120
     * seconds later. The blacklist holds {@code blacklistRate} of the accounts plus 10 percent
     * extra IDs that never appear in the data.
     *
     * <p>Time complexity: O(n log n) for the final sort by time. Space complexity: O(n).
     *
     * @param size the number of rows, at least 1
     * @param seed the seed for every random choice
     * @param duplicateRate the probability that a row is a duplicate, in [0, 1]
     * @param blacklistRate the share of accounts that are blacklisted, in [0, 1]
     * @return the rows sorted by time, the blacklist, and a label for every row
     * @throws IllegalArgumentException if size is below 1 or a rate is outside [0, 1]
     */
    public static GeneratedData generate(int size, long seed, double duplicateRate, double blacklistRate) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1, got " + size);
        }
        requireRate("duplicateRate", duplicateRate);
        requireRate("blacklistRate", blacklistRate);

        Random random = new Random(seed);
        int accountCount = Math.max(MIN_ACCOUNTS, size / ROWS_PER_ACCOUNT);
        List<Draft> drafts = draftRows(size, duplicateRate, accountCount, random);
        SortedSet<String> blacklist = pickBlacklist(accountCount, blacklistRate, random);

        // List.sort is stable, so a duplicate with the same timestamp stays after its original.
        drafts.sort(Comparator.comparingLong(Draft::epochSeconds));

        List<Transaction> rows = new ArrayList<>(size);
        Map<Integer, Label> labels = new LinkedHashMap<>();
        for (int i = 0; i < drafts.size(); i++) {
            Draft draft = drafts.get(i);
            int line = i + 2;
            rows.add(new Transaction(line, draft.transactionId(), draft.accountId(), draft.amountPaise(),
                    draft.epochSeconds(), draft.merchant()));
            labels.put(line, new Label(draft.duplicate(), blacklist.contains(draft.accountId())));
        }
        return new GeneratedData(List.copyOf(rows), Collections.unmodifiableSortedSet(blacklist),
                Collections.unmodifiableMap(labels));
    }

    private static List<Draft> draftRows(int size, double duplicateRate, int accountCount, Random random) {
        List<Draft> drafts = new ArrayList<>(size);
        List<Draft> originals = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (!originals.isEmpty() && random.nextDouble() < duplicateRate) {
                Draft original = originals.get(random.nextInt(originals.size()));
                long delay = random.nextInt(MAX_DUPLICATE_DELAY_SECONDS + 1);
                drafts.add(original.duplicateAt(original.epochSeconds() + delay));
            } else {
                Draft original = newRow(originals.size() + 1, accountCount, random);
                originals.add(original);
                drafts.add(original);
            }
        }
        return drafts;
    }

    private static Draft newRow(int number, int accountCount, Random random) {
        int merchantNumber = 1 + random.nextInt(MERCHANT_COUNT);
        Category category = CATEGORIES.get((merchantNumber - 1) % CATEGORIES.size());
        String merchant = String.format("%s %03d", category.name(), merchantNumber);
        String accountId = accountId(1 + random.nextInt(accountCount));
        return new Draft(String.format("TXN%08d", number), accountId, amountPaise(category, random),
                timestamp(random), merchant, false);
    }

    private static long amountPaise(Category category, Random random) {
        double logMedianPaise = Math.log(category.medianRupees() * 100);
        long paise = Math.round(Math.exp(logMedianPaise + category.sigma() * standardNormal(random)));
        return Math.max(MIN_AMOUNT_PAISE, paise);
    }

    /** One standard normal value from two uniform values, by the Box-Muller transform. */
    private static double standardNormal(Random random) {
        double u1 = 1.0 - random.nextDouble(); // in (0, 1], so the log is finite
        double u2 = random.nextDouble();
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    }

    private static long timestamp(Random random) {
        int day = random.nextInt(DAYS);
        int secondOfDay;
        if (random.nextDouble() < BUSY_SHARE) {
            secondOfDay = BUSY_START_SECOND + random.nextInt(BUSY_SECONDS);
        } else {
            int quiet = random.nextInt(QUIET_SECONDS);
            secondOfDay = quiet < BUSY_START_SECOND ? quiet : quiet + BUSY_SECONDS;
        }
        return START_EPOCH + day * 86_400L + secondOfDay;
    }

    private static SortedSet<String> pickBlacklist(int accountCount, double blacklistRate, Random random) {
        List<Integer> accountNumbers = new ArrayList<>(accountCount);
        for (int i = 1; i <= accountCount; i++) {
            accountNumbers.add(i);
        }
        Collections.shuffle(accountNumbers, random);

        int blacklisted = (int) Math.round(accountCount * blacklistRate);
        SortedSet<String> blacklist = new TreeSet<>();
        for (int i = 0; i < blacklisted; i++) {
            blacklist.add(accountId(accountNumbers.get(i)));
        }
        // Account numbers above accountCount are never used by any row. Rounding up keeps at
        // least one such ID whenever the blacklist is not empty.
        int extra = (int) Math.ceil(blacklisted * EXTRA_BLACKLIST_SHARE);
        for (int i = 1; i <= extra; i++) {
            blacklist.add(accountId(accountCount + i));
        }
        return blacklist;
    }

    private static String accountId(int number) {
        return String.format("ACC%06d", number);
    }

    private static void requireRate(String name, double rate) {
        if (!(rate >= 0 && rate <= 1)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1, got " + rate);
        }
    }

    private record Category(String name, double medianRupees, double sigma) {
    }

    private record Draft(String transactionId, String accountId, long amountPaise, long epochSeconds,
            String merchant, boolean duplicate) {

        Draft duplicateAt(long newEpochSeconds) {
            return new Draft(transactionId, accountId, amountPaise, newEpochSeconds, merchant, true);
        }
    }
}
