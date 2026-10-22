package com.trickshotmlg.devkit.storage;

/** Maps the current ResultSet row to an application value. The supplied row is valid only during this call. */
@FunctionalInterface
public interface RowMapper<T> {
    T map(Row row) throws Exception;
}
