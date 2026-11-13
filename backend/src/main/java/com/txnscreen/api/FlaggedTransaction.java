package com.txnscreen.api;

import com.txnscreen.domain.Flag;
import com.txnscreen.domain.FlagType;
import com.txnscreen.domain.Transaction;

/**
 * One flag with the transaction it was raised on. A transaction with two flags appears once per flag.
 *
 * @param lineNumber the transaction's line in the file
 * @param transactionId the transaction ID
 * @param accountId the account ID
 * @param amountPaise the amount in paise
 * @param epochSeconds the transaction time in epoch seconds
 * @param merchant the merchant
 * @param type the flag type
 * @param detail why the flag was raised
 */
public record FlaggedTransaction(
        int lineNumber,
        String transactionId,
        String accountId,
        long amountPaise,
        long epochSeconds,
        String merchant,
        FlagType type,
        String detail) {

    /**
     * Joins a flag to its transaction.
     *
     * @param flag the flag
     * @param transaction the transaction on the flag's line
     * @return the flagged transaction
     */
    public static FlaggedTransaction of(Flag flag, Transaction transaction) {
        return new FlaggedTransaction(transaction.lineNumber(), transaction.transactionId(),
                transaction.accountId(), transaction.amountPaise(), transaction.epochSeconds(),
                transaction.merchant(), flag.type(), flag.detail());
    }
}
