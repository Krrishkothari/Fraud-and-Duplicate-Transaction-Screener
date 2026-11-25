package com.txnscreen.api;

import java.util.List;

import com.txnscreen.domain.FlagType;
import com.txnscreen.domain.Transaction;

/**
 * One valid transaction with every flag raised on it, which is an empty list for a transaction that passed
 * screening.
 *
 * @param lineNumber the transaction's line in the file
 * @param transactionId the transaction ID
 * @param accountId the account ID
 * @param amountPaise the amount in paise
 * @param epochSeconds the transaction time in epoch seconds
 * @param merchant the merchant
 * @param flags the flags raised on this transaction, in screening order
 */
public record ScreenedTransaction(
        int lineNumber,
        String transactionId,
        String accountId,
        long amountPaise,
        long epochSeconds,
        String merchant,
        List<FlagNote> flags) {

    /**
     * One flag on a transaction.
     *
     * @param type the flag type
     * @param detail why the flag was raised
     */
    public record FlagNote(FlagType type, String detail) {
    }

    /**
     * Joins a transaction to its flags.
     *
     * @param transaction the transaction
     * @param flags the flags raised on its line
     * @return the screened transaction
     */
    public static ScreenedTransaction of(Transaction transaction, List<FlagNote> flags) {
        return new ScreenedTransaction(transaction.lineNumber(), transaction.transactionId(),
                transaction.accountId(), transaction.amountPaise(), transaction.epochSeconds(),
                transaction.merchant(), List.copyOf(flags));
    }
}
