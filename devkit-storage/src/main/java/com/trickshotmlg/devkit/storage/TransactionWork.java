package com.trickshotmlg.devkit.storage;

/** Work executed on one JDBC connection inside a transaction. */
@FunctionalInterface
public interface TransactionWork<T> {
    T execute(Transaction transaction) throws Exception;
}
