package com.txnscreen.service;

import java.util.List;

import com.txnscreen.domain.Flag;

/**
 * The outcome of screening one run.
 *
 * @param screener the screener that ran, holding both filters and stores
 * @param flags every flag raised, in screening order
 * @param blacklistStats the blacklist filter after the run
 * @param duplicateStats the duplicate filter after the run
 * @param warnings plain sentences about the run an analyst should know, such as an empty blacklist
 */
public record ScreeningResult(
        Screener screener,
        List<Flag> flags,
        FilterStats blacklistStats,
        FilterStats duplicateStats,
        List<String> warnings) {

    public ScreeningResult {
        flags = List.copyOf(flags);
        warnings = List.copyOf(warnings);
    }
}
