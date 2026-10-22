package com.trickshotmlg.devkit.storage;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** Thread-confined transaction view. It is invalid as soon as its callback returns. */
public interface Transaction {
    <T> Optional<T> queryOne(String sql, SqlParameters parameters, RowMapper<T> mapper);
    <T> List<T> queryList(String sql, SqlParameters parameters, RowMapper<T> mapper);
    int update(String sql, SqlParameters parameters);
    OptionalLong insert(String sql, SqlParameters parameters);
}
