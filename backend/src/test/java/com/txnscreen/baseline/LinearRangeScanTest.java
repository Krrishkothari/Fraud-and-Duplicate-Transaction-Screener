package com.txnscreen.baseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.txnscreen.algo.segtree.NodeStats;
import com.txnscreen.domain.Transaction;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinearRangeScanTest {

    private static final List<Transaction> ROWS = List.of(
            row(2, 100, 500),
            row(3, 160, 1_200),
            row(4, 160, 300),
            row(5, 250, 900));

    // Proves both window ends are inclusive and only rows inside the window are counted.
    @Test
    void boundsAreInclusive() {
        LinearRangeScan scan = new LinearRangeScan(ROWS);

        assertEquals(new NodeStats(2_000, 3, 1_200), scan.query(100, 160));
        assertEquals(new NodeStats(1_500, 2, 1_200), scan.query(160, 160));
        assertEquals(new NodeStats(2_900, 4, 1_200), scan.query(0, 1_000));
    }

    // Proves a window with no rows, and a scan over no rows, give count 0, sum 0 and max 0.
    @Test
    void emptyWindowGivesEmptyStats() {
        assertEquals(NodeStats.EMPTY, new LinearRangeScan(ROWS).query(101, 159));
        assertEquals(NodeStats.EMPTY, new LinearRangeScan(List.of()).query(0, 1_000));
    }

    // Proves the scan does not rely on time order, since it checks every row.
    @Test
    void orderDoesNotMatter() {
        LinearRangeScan scan = new LinearRangeScan(List.of(ROWS.get(3), ROWS.get(0), ROWS.get(2), ROWS.get(1)));

        assertEquals(new NodeStats(2_400, 3, 1_200), scan.query(150, 300));
    }

    // Proves invalid input is rejected instead of giving a silently wrong answer.
    @Test
    void invalidInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LinearRangeScan(null));
        assertThrows(IllegalArgumentException.class, () -> new LinearRangeScan(ROWS).query(200, 100));
    }

    private static Transaction row(int line, long epochSeconds, long amountPaise) {
        return new Transaction(line, "TXN" + line, "ACC000001", amountPaise, epochSeconds, "Grocery Store 001");
    }
}
