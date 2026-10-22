package com.trickshotmlg.devkit.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

final class JdbcStorage implements Storage {
    private final StorageConfiguration configuration;
    private volatile DataSource dataSource;
    private final boolean ownsDataSource;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ThreadLocal<Boolean> transactionActive = ThreadLocal.withInitial(() -> false);
    private final Object asyncLifecycleLock = new Object();
    private final java.util.Set<AsyncOperation<?>> asyncOperations = ConcurrentHashMap.newKeySet();
    private volatile Executor asyncExecutor;
    private final boolean ownsAsyncExecutor;
    private final StorageAsync async = new StorageAsync(this);

    JdbcStorage(StorageConfiguration configuration, DataSource external, boolean ownsExternal,
                Executor executor, boolean ownsExecutor, int asyncThreads, int asyncQueueCapacity) throws Exception {
        this.configuration = configuration;
        DataSource resolved;
        if (external != null) {
            resolved = external;
            ownsDataSource = ownsExternal;
        } else {
            if (configuration.sqliteWal()) StorageBuilder.enableWal(configuration.jdbcUrl());
            HikariConfig hikari = new HikariConfig();
            hikari.setJdbcUrl(configuration.jdbcUrl());
            if (configuration.username() != null) hikari.setUsername(configuration.username());
            char[] password = configuration.password();
            if (password != null) {
                hikari.setPassword(new String(password));
                java.util.Arrays.fill(password, '\0');
            }
            hikari.setMaximumPoolSize(configuration.maximumPoolSize());
            hikari.setConnectionTimeout(configuration.connectionTimeout().toMillis());
            if (configuration.idleTimeout() != null && !configuration.idleTimeout().isZero())
                hikari.setIdleTimeout(configuration.idleTimeout().toMillis());
            hikari.setPoolName("MinecraftDevKitStorage");
            if (configuration.sqlite()) {
                hikari.setConnectionInitSql("PRAGMA foreign_keys=ON");
                Properties properties = new Properties();
                properties.setProperty("busy_timeout", Long.toString(configuration.busyTimeout().toMillis()));
                hikari.setDataSourceProperties(properties);
            }
            try {
                resolved = new HikariDataSource(hikari);
            } catch (RuntimeException failure) {
                configuration.clearPassword();
                throw new StorageException("Could not create JDBC connection pool", failure);
            }
            ownsDataSource = true;
        }
        dataSource = resolved;
        asyncExecutor = executor == null ? new java.util.concurrent.ThreadPoolExecutor(asyncThreads, asyncThreads, 0L,
                java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(asyncQueueCapacity),
                runnable -> { Thread thread = new Thread(runnable, "MinecraftDevKit-Storage"); thread.setDaemon(true); return thread; },
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy()) : executor;
        ownsAsyncExecutor = executor == null || ownsExecutor;
    }

    @Override public StorageConfiguration configuration() { return configuration; }
    @Override public DataSource dataSource() { return openDataSource(); }
    private DataSource openDataSource() {
        ensureOpen();
        DataSource current = dataSource;
        if (current == null) throw new StorageClosedException();
        return current;
    }
    @Override public boolean isClosed() { return closed.get(); }
    void ensureOpen() { if (closed.get()) throw new StorageClosedException(); }

    @Override public StorageAsync async() { ensureOpen(); return async; }

    <T> StorageTask<T> submitAsync(java.util.concurrent.Callable<T> work) {
        synchronized (asyncLifecycleLock) {
            ensureOpen();
            AsyncOperation<T> operation = new AsyncOperation<>(work);
            asyncOperations.add(operation);
            operation.task.cancellation(operation::cancelFromHandle);
            try {
                asyncExecutor.execute(operation);
                operation.submissionReturned.set(true);
            } catch (RejectedExecutionException rejected) {
                operation.task.fail(new StorageException("Asynchronous storage queue is full or shutting down", rejected));
                operation.finish();
            } catch (RuntimeException failure) {
                operation.task.fail(new StorageException("Asynchronous storage executor rejected work", failure));
                operation.finish();
            }
            return operation.task;
        }
    }

    private final class AsyncOperation<T> implements Runnable {
        private final java.util.concurrent.Callable<T> work;
        private final StorageTask<T> task = new StorageTask<>();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicBoolean submissionReturned = new AtomicBoolean();
        private final Thread submittingThread = Thread.currentThread();
        private AsyncOperation(java.util.concurrent.Callable<T> work) { this.work = work; }
        @Override public void run() {
            if (Thread.currentThread() == submittingThread && !submissionReturned.get()) {
                task.fail(new IllegalStateException("The configured async executor ran storage work inline"));
                finish();
                return;
            }
            if (!task.begin()) { finish(); return; }
            try { task.complete(work.call()); }
            catch (Throwable failure) {
                if (failure instanceof Error error) task.fail(error);
                else task.fail(failure instanceof StorageException ? failure : new StorageException("Asynchronous storage operation failed", failure));
            } finally { finish(); }
        }
        private void cancelFromHandle() { cancel(); }
        private void cancel() {
            task.cancelState();
            Executor current = asyncExecutor;
            if (current instanceof ThreadPoolExecutor pool) pool.remove(this);
            finish();
        }
        private void finish() {
            if (finished.compareAndSet(false, true)) asyncOperations.remove(this);
        }
    }

    @Override public <T> Optional<T> queryOne(String sql, SqlParameters parameters, RowMapper<T> mapper) {
        List<T> rows = executeQuery(sql, parameters, mapper, true);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override public <T> List<T> queryList(String sql, SqlParameters parameters, RowMapper<T> mapper) {
        return executeQuery(sql, parameters, mapper, false);
    }

    private <T> List<T> executeQuery(String sql, SqlParameters parameters, RowMapper<T> mapper, boolean atMostOne) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(mapper, "mapper");
        ensureOutsideTransaction();
        validateSql(sql);
        ensureOpen();
        try (var connection = openDataSource().getConnection(); var statement = connection.prepareStatement(sql)) {
            parameters.bind(statement);
            try (var results = statement.executeQuery()) {
                List<T> mapped = new ArrayList<>(atMostOne ? 1 : 8);
                while (results.next()) {
                    if (atMostOne && !mapped.isEmpty())
                        throw new StorageException("Query expected at most one result row but returned multiple rows");
                    Row row = new Row(results);
                    try {
                        T value = mapper.map(row);
                        if (value == null) throw new StorageException("Row mapper returned null");
                        mapped.add(value);
                    } catch (StorageException failure) {
                        throw failure;
                    } catch (Exception failure) {
                        throw new StorageException("Could not map SQL result row", failure);
                    } finally {
                        row.invalidate();
                    }
                }
                return List.copyOf(mapped);
            }
        } catch (StorageException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new StorageException("Database query failed", failure);
        }
    }

    @Override public int update(String sql, SqlParameters parameters) {
        Objects.requireNonNull(parameters, "parameters");
        ensureOutsideTransaction();
        validateSql(sql);
        ensureOpen();
        try (var connection = openDataSource().getConnection(); var statement = connection.prepareStatement(sql)) {
            parameters.bind(statement);
            return statement.executeUpdate();
        } catch (StorageException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new StorageException("Database update failed", failure);
        }
    }

    @Override public OptionalLong insert(String sql, SqlParameters parameters) {
        Objects.requireNonNull(parameters, "parameters");
        ensureOutsideTransaction();
        validateSql(sql);
        ensureOpen();
        try (var connection = openDataSource().getConnection();
             var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            parameters.bind(statement);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                return keys.next() ? OptionalLong.of(keys.getLong(1)) : OptionalLong.empty();
            }
        } catch (StorageException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new StorageException("Database insert failed", failure);
        }
    }

    private static void validateSql(String sql) {
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("SQL must not be empty");
    }

    private void ensureOutsideTransaction() {
        if (transactionActive.get())
            throw new IllegalStateException("Use the supplied Transaction for all SQL inside its callback");
    }

    private static void suppress(Throwable primary, Throwable secondary) {
        if (primary != secondary) primary.addSuppressed(secondary);
    }

    @Override public <T> T transaction(TransactionWork<T> work) {
        Objects.requireNonNull(work, "work");
        ensureOpen();
        if (transactionActive.get())
            throw new IllegalStateException("Nested transactions are not supported; reuse the current Transaction");
        try (var connection = openDataSource().getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            TransactionImpl transaction = null;
            Throwable primaryFailure = null;
            boolean discardConnection = false;
            T value = null;
            transactionActive.set(true);
            try {
                connection.setAutoCommit(false);
                transaction = new TransactionImpl(connection);
                value = work.execute(transaction);
                transaction.ensureUsable();
                connection.commit();
            } catch (Throwable failure) {
                primaryFailure = failure;
                try { connection.rollback(); }
                catch (Throwable rollbackFailure) {
                    suppress(failure, rollbackFailure);
                    discardConnection = true;
                }
            } finally {
                if (transaction != null) transaction.invalidate();
                if (!discardConnection) {
                    try { connection.setAutoCommit(originalAutoCommit); }
                    catch (Throwable restoreFailure) {
                        if (primaryFailure != null) suppress(primaryFailure, restoreFailure);
                        else primaryFailure = restoreFailure;
                        discardConnection = true;
                    }
                }
                if (discardConnection) {
                    try { connection.abort(Runnable::run); }
                    catch (Throwable abortFailure) {
                        if (primaryFailure != null) suppress(primaryFailure, abortFailure);
                        else primaryFailure = abortFailure;
                    }
                }
                transactionActive.remove();
            }
            if (primaryFailure != null) {
                if (primaryFailure instanceof Error error) throw error;
                if (primaryFailure instanceof StorageException storageFailure) throw storageFailure;
                throw new StorageException("Database transaction failed", primaryFailure);
            }
            return value;
        } catch (StorageException failure) {
            throw failure;
        } catch (Throwable failure) {
            if (failure instanceof Error error) throw error;
            throw new StorageException("Could not acquire or release transaction connection", failure);
        }
    }

    private final class TransactionImpl implements Transaction {
        private final java.sql.Connection connection;
        private final Thread owner = Thread.currentThread();
        private boolean active = true;

        private TransactionImpl(java.sql.Connection connection) { this.connection = connection; }
        private void ensureUsable() {
            if (!active) throw new IllegalStateException("Transaction callback has completed");
            if (Thread.currentThread() != owner) throw new IllegalStateException("Transaction is confined to its callback thread");
            JdbcStorage.this.ensureOpen();
        }
        private void invalidate() { active = false; }

        @Override public <T> Optional<T> queryOne(String sql, SqlParameters parameters, RowMapper<T> mapper) {
            List<T> rows = query(sql, parameters, mapper, true);
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        }
        @Override public <T> List<T> queryList(String sql, SqlParameters parameters, RowMapper<T> mapper) {
            return query(sql, parameters, mapper, false);
        }
        private <T> List<T> query(String sql, SqlParameters parameters, RowMapper<T> mapper, boolean atMostOne) {
            ensureUsable(); Objects.requireNonNull(parameters, "parameters");
            Objects.requireNonNull(mapper, "mapper"); validateSql(sql);
            try (var statement = connection.prepareStatement(sql)) {
                parameters.bind(statement);
                try (var results = statement.executeQuery()) {
                    List<T> mapped = new ArrayList<>();
                    while (results.next()) {
                        if (atMostOne && !mapped.isEmpty())
                            throw new StorageException("Query expected at most one result row but returned multiple rows");
                        Row row = new Row(results);
                        try {
                            T item = mapper.map(row);
                            if (item == null) throw new StorageException("Row mapper returned null");
                            mapped.add(item);
                        } catch (StorageException failure) { throw failure; }
                        catch (Exception failure) { throw new StorageException("Could not map SQL result row", failure); }
                        finally { row.invalidate(); }
                    }
                    return List.copyOf(mapped);
                }
            } catch (StorageException failure) { throw failure; }
            catch (Exception failure) { throw new StorageException("Database transaction query failed", failure); }
        }
        @Override public int update(String sql, SqlParameters parameters) {
            ensureUsable(); Objects.requireNonNull(parameters, "parameters"); validateSql(sql);
            try (var statement = connection.prepareStatement(sql)) {
                parameters.bind(statement);
                return statement.executeUpdate();
            } catch (StorageException failure) { throw failure; }
            catch (Exception failure) { throw new StorageException("Database transaction update failed", failure); }
        }
        @Override public OptionalLong insert(String sql, SqlParameters parameters) {
            ensureUsable(); Objects.requireNonNull(parameters, "parameters"); validateSql(sql);
            try (var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                parameters.bind(statement); statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) {
                    return keys.next() ? OptionalLong.of(keys.getLong(1)) : OptionalLong.empty();
                }
            } catch (StorageException failure) { throw failure; }
            catch (Exception failure) { throw new StorageException("Database transaction insert failed", failure); }
        }
    }

    @Override public void close() {
        synchronized (asyncLifecycleLock) {
            if (!closed.compareAndSet(false, true)) return;
            for (AsyncOperation<?> operation : List.copyOf(asyncOperations)) operation.cancel();
        }
        Executor executor = asyncExecutor;
        if (ownsAsyncExecutor && executor instanceof ExecutorService service) service.shutdownNow();
        asyncExecutor = null;
        configuration.clearPassword();
        DataSource owned = dataSource;
        if (!ownsDataSource) {
            dataSource = null;
            return;
        }
        try {
            if (owned instanceof AutoCloseable closeable) closeable.close();
        } catch (Exception failure) {
            throw new StorageException("Could not close storage resources", failure);
        } finally {
            dataSource = null;
        }
    }
}
