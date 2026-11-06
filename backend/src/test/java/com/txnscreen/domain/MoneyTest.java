package com.txnscreen.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void parsesIntegerAmount() {
        assertEquals(1200L, Money.parseToPaise("12"));
    }

    @Test
    void parsesOneDecimalPlace() {
        assertEquals(1250L, Money.parseToPaise("12.5"));
    }

    @Test
    void parsesTwoDecimalPlaces() {
        assertEquals(1205L, Money.parseToPaise("12.05"));
    }

    @Test
    void rejectsThreeDecimalPlaces() {
        assertThrows(IllegalArgumentException.class, () -> Money.parseToPaise("12.505"));
    }

    @Test
    void rejectsLetters() {
        assertThrows(IllegalArgumentException.class, () -> Money.parseToPaise("12a"));
    }

    @Test
    void parsesNegativeAsNegative() {
        assertEquals(-1250L, Money.parseToPaise("-12.50"));
        assertEquals(-50L, Money.parseToPaise("-0.50"));
    }
}
