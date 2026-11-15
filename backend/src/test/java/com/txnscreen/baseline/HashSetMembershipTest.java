package com.txnscreen.baseline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HashSetMembershipTest {

    // Proves the baseline is exact: added keys are found, repeats are harmless and other keys are not found.
    @Test
    void answersAreExact() {
        HashSetMembership set = new HashSetMembership(2);
        set.add("ACC000001");
        set.add("ACC000002");
        set.add("ACC000001");

        assertTrue(set.contains("ACC000001"));
        assertTrue(set.contains("ACC000002"));
        assertFalse(set.contains("ACC000003"));
        assertFalse(set.contains("ZZZ000001"));
    }

    // Proves the expected key count is only a sizing hint, so more keys than expected still fit.
    @Test
    void holdsMoreKeysThanExpected() {
        HashSetMembership set = new HashSetMembership(0);
        for (int i = 0; i < 1_000; i++) {
            set.add("ACC" + i);
        }

        for (int i = 0; i < 1_000; i++) {
            assertTrue(set.contains("ACC" + i), "ACC" + i);
        }
    }
}
