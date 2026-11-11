package com.txnscreen.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.txnscreen.algo.bloom.BloomMath;
import com.txnscreen.domain.Flag;
import com.txnscreen.domain.FlagType;
import com.txnscreen.domain.Transaction;

class ScreenerTest {

    private static final RunConfig DEFAULTS = RunConfig.defaults();

    // Proves a blacklisted account is flagged with its ID, and a clean account is not.
    @Test
    void blacklistedAccountIsFlagged() {
        Screener screener = new Screener(Set.of("ACC9"), 10, DEFAULTS);

        assertAll(
                () -> assertEquals(List.of(new Flag("T1", 2, FlagType.BLACKLISTED, "Account ACC9 is on the blacklist")),
                        screener.screenOne(txn("T1", "ACC9", 2))),
                () -> assertEquals(List.of(), screener.screenOne(txn("T2", "ACC1", 3))));
    }

    // Proves the first occurrence of an ID is clean and every repeat points at the first line.
    @Test
    void repeatsAreFlaggedAgainstFirstLine() {
        Screener screener = new Screener(Set.of(), 10, DEFAULTS);

        assertAll(
                () -> assertEquals(List.of(), screener.screenOne(txn("T1", "ACC1", 4))),
                () -> assertEquals(List.of(new Flag("T1", 7, FlagType.DUPLICATE, "Duplicate of line 4")),
                        screener.screenOne(txn("T1", "ACC2", 7))),
                () -> assertEquals(List.of(new Flag("T1", 9, FlagType.DUPLICATE, "Duplicate of line 4")),
                        screener.screenOne(txn("T1", "ACC3", 9))));
    }

    // Proves one transaction can raise both flags, blacklist first.
    @Test
    void blacklistFlagComesBeforeDuplicateFlag() {
        Screener screener = new Screener(Set.of("ACC9"), 10, DEFAULTS);
        screener.screenOne(txn("T1", "ACC1", 2));

        List<FlagType> types = screener.screenOne(txn("T1", "ACC9", 3)).stream().map(Flag::type).toList();

        assertEquals(List.of(FlagType.BLACKLISTED, FlagType.DUPLICATE), types);
    }

    // Proves Bloom false positives are confirmed away: an overfull 2-bit filter raises no flags for unique IDs,
    // yet every positive goes to the exact store and every negative skips it.
    @Test
    void falsePositivesAreCountedButNeverFlagged() {
        int count = 200;
        RunConfig config = new RunConfig(null, 0.5, null, null, null, null);
        Screener screener = new Screener(Set.of(), 1, config);

        List<Flag> flags = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            flags.addAll(screener.screenOne(txn("T" + i, "ACC1", i + 2)));
        }
        FilterStats stats = screener.duplicateStats();

        assertAll(
                () -> assertEquals(List.of(), flags),
                () -> assertEquals(count, stats.checks()),
                () -> assertEquals(0, stats.confirmedHits()),
                () -> assertTrue(stats.falsePositives() > 0, "false positives " + stats.falsePositives()),
                () -> assertEquals(stats.bloomPositives(), stats.falsePositives()),
                () -> assertEquals(stats.bloomPositives(), stats.authoritativeLookups()),
                () -> assertEquals(stats.checks() - stats.bloomPositives(), stats.lookupsAvoided()),
                () -> assertEquals((double) stats.falsePositives() / count, stats.measuredFpr()),
                () -> assertEquals(count, stats.inserted()),
                () -> assertTrue(stats.saturated()),
                () -> assertEquals(BloomMath.optimalBits(count, 0.5), stats.suggestedBits()));
    }

    // Proves measured FPR leaves confirmed hits out of the denominator, and an unsaturated filter has no
    // suggested size.
    @Test
    void measuredFprExcludesConfirmedHits() {
        Screener screener = new Screener(Set.of(), 1_000, DEFAULTS);
        screener.screenOne(txn("T1", "ACC1", 2));
        screener.screenOne(txn("T1", "ACC1", 3));
        screener.screenOne(txn("T1", "ACC1", 4));
        FilterStats stats = screener.duplicateStats();

        assertAll(
                () -> assertEquals(3, stats.checks()),
                () -> assertEquals(2, stats.confirmedHits()),
                () -> assertEquals((double) stats.falsePositives() / (3 - 2), stats.measuredFpr()),
                () -> assertEquals(1, stats.inserted()),
                () -> assertNull(stats.suggestedBits()));
    }

    // Proves measured FPR is 0, not NaN, before any check.
    @Test
    void measuredFprIsZeroWithoutChecks() {
        assertEquals(0.0, new Screener(Set.of(), 10, DEFAULTS).duplicateStats().measuredFpr());
    }

    // Proves the blacklist filter is sized for and filled with the blacklist.
    @Test
    void blacklistFilterIsSizedForBlacklist() {
        Set<String> blacklist = Set.of("ACC1", "ACC2", "ACC3");
        FilterStats stats = new Screener(blacklist, 10, DEFAULTS).blacklistStats();

        assertAll(
                () -> assertEquals(BloomMath.optimalBits(3, 0.01), stats.bitSize()),
                () -> assertEquals(3, stats.inserted()));
    }

    // Proves an explicit hash count overrides the optimal one while the bit count stays optimal.
    @Test
    void explicitHashCountIsUsed() {
        RunConfig config = new RunConfig(null, null, 3, null, null, null);
        FilterStats stats = new Screener(Set.of(), 50_000, config).duplicateStats();

        assertAll(
                () -> assertEquals(3, stats.hashCount()),
                () -> assertEquals(BloomMath.optimalBits(50_000, 0.01), stats.bitSize()));
    }

    // Proves an empty blacklist never reaches the exact store.
    @Test
    void emptyBlacklistAvoidsEveryLookup() {
        Screener screener = new Screener(Set.of(), 10, DEFAULTS);
        for (int i = 0; i < 100; i++) {
            screener.screenOne(txn("T" + i, "ACC" + i, i + 2));
        }
        FilterStats stats = screener.blacklistStats();

        assertAll(
                () -> assertEquals(100, stats.checks()),
                () -> assertEquals(0, stats.authoritativeLookups()),
                () -> assertEquals(100, stats.lookupsAvoided()));
    }

    private static Transaction txn(String transactionId, String accountId, int lineNumber) {
        return new Transaction(lineNumber, transactionId, accountId, 100, 1_767_225_600L + lineNumber, "Shop");
    }
}
