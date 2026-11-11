package com.txnscreen.api;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.txnscreen.domain.Flag;
import com.txnscreen.domain.FlagType;
import com.txnscreen.domain.Transaction;
import com.txnscreen.service.FilterStats;
import com.txnscreen.service.Run;
import com.txnscreen.service.RunConfig;
import com.txnscreen.service.ScreeningResult;

/**
 * The response for one run: row counts, flag counts, filter stats and the screening time.
 *
 * @param id the run ID
 * @param createdAt when the run was created
 * @param validRows the number of valid transactions
 * @param rejectedRows the number of rejected rows
 * @param flagCounts the number of flags of each type, including types with none
 * @param blacklistStats the blacklist filter after screening
 * @param duplicateStats the duplicate filter after screening
 * @param dataStart the earliest transaction time, in epoch seconds
 * @param dataEnd the latest transaction time, in epoch seconds
 * @param config the run settings
 * @param warnings plain sentences about the run an analyst should know
 * @param algoMicros how long screening took, in microseconds
 */
public record RunSummary(
        UUID id,
        Instant createdAt,
        int validRows,
        int rejectedRows,
        Map<FlagType, Long> flagCounts,
        FilterStats blacklistStats,
        FilterStats duplicateStats,
        long dataStart,
        long dataEnd,
        RunConfig config,
        List<String> warnings,
        long algoMicros) {

    /**
     * Summarises a run, which always has at least one valid transaction.
     *
     * @param run the run
     * @return the summary
     */
    public static RunSummary of(Run run) {
        ScreeningResult screening = run.screening();
        List<Transaction> transactions = run.transactions();
        return new RunSummary(run.id(), run.createdAt(), transactions.size(), run.rejected().size(),
                countByType(screening.flags()), screening.blacklistStats(), screening.duplicateStats(),
                transactions.getFirst().epochSeconds(), transactions.getLast().epochSeconds(), run.config(),
                screening.warnings(), run.screeningMicros());
    }

    private static Map<FlagType, Long> countByType(List<Flag> flags) {
        Map<FlagType, Long> counts = new EnumMap<>(FlagType.class);
        for (FlagType type : FlagType.values()) {
            counts.put(type, 0L);
        }
        for (Flag flag : flags) {
            counts.merge(flag.type(), 1L, Long::sum);
        }
        return counts;
    }
}
