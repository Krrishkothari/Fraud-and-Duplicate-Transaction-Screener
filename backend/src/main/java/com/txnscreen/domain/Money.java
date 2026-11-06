package com.txnscreen.domain;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts between decimal rupee text and long paise using exact integer arithmetic.
 */
public final class Money {

    private static final long PAISE_PER_RUPEE = 100;
    private static final long PAISE_PER_TENTH = 10;
    private static final Pattern AMOUNT = Pattern.compile("(-)?(\\d+)(?:\\.(\\d{1,2}))?");

    private Money() {
    }

    /**
     * Parses a decimal rupee amount such as "12", "12.5" or "-12.50" into paise.
     *
     * @param amount an optional leading minus, digits, and at most 2 decimal places
     * @return the amount in paise, negative when the text has a leading minus
     * @throws IllegalArgumentException if the text is null, has any other format, or does not fit in a long
     */
    public static long parseToPaise(String amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        Matcher matcher = AMOUNT.matcher(amount);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid amount: " + amount);
        }
        long sign = matcher.group(1) == null ? 1 : -1;
        try {
            long rupeesInPaise = Math.multiplyExact(sign * Long.parseLong(matcher.group(2)), PAISE_PER_RUPEE);
            return Math.addExact(rupeesInPaise, sign * fractionInPaise(matcher.group(3)));
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("Amount out of range: " + amount, e);
        }
    }

    /**
     * Formats paise as plain decimal rupees with exactly 2 decimal places, such as "1234.50" or "-0.50".
     *
     * @param paise the amount in paise
     * @return the formatted amount, with no currency symbol or digit grouping
     */
    public static String format(long paise) {
        String sign = paise < 0 ? "-" : "";
        long rupees = Math.abs(paise / PAISE_PER_RUPEE);
        long fraction = Math.abs(paise % PAISE_PER_RUPEE);
        return String.format(Locale.ROOT, "%s%d.%02d", sign, rupees, fraction);
    }

    private static long fractionInPaise(String fraction) {
        if (fraction == null) {
            return 0;
        }
        long value = Long.parseLong(fraction);
        return fraction.length() == 1 ? value * PAISE_PER_TENTH : value;
    }
}
