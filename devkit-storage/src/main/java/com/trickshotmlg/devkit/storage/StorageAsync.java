package com.trickshotmlg.devkit.storage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/** Bounded background execution facade. Callbacks run on storage workers, never implicitly on a game thread. */
public final class StorageAsync {
    private final JdbcStorage storage;
    StorageAsync(JdbcStorage storage) { this.storage = storage; }

    public <T> StorageTask<T> submit(Callable<T> operation) {
        return storage.submitAsync(Objects.requireNonNull(operation, "operation"));
    }
    public <T> StorageTask<Optional<T>> queryOne(String sql, SqlParameters parameters, RowMapper<T> mapper) {
        return submit(() -> storage.queryOne(sql, parameters, mapper));
    }
    public <T> StorageTask<List<T>> queryList(String sql, SqlParameters parameters, RowMapper<T> mapper) {
        return submit(() -> storage.queryList(sql, parameters, mapper));
    }
    public StorageTask<Integer> update(String sql, SqlParameters parameters) {
        return submit(() -> storage.update(sql, parameters));
    }
    public <T> StorageTask<T> transaction(TransactionWork<T> work) {
        return submit(() -> storage.transaction(work));
    }
}
