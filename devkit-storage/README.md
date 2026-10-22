# devkit-storage

`devkit-storage` is a Java 17 JDBC utility for explicit SQL. It provides a HikariCP-backed `Storage`, prepared parameters, eagerly mapped rows, connection-scoped transactions, bounded background operations, and explicit DataSource/executor ownership. It is not an ORM and has no Bukkit dependency.

## SQLite

SQLite JDBC is a runtime dependency of this module so the local-file path works without a separate database server:

```java
try (Storage storage = Storage.sqlite(pluginDataFolder.resolve("players.db"))) {
    storage.update("CREATE TABLE IF NOT EXISTS players (uuid TEXT PRIMARY KEY, coins INTEGER NOT NULL)",
        SqlParameters.none());
}
```

File-backed SQLite creates parent directories, enables foreign keys on each pooled connection, sets a five-second busy timeout, and enables WAL. SQLite uses one pooled connection by default because writes remain serialized. `Storage.sqliteMemory()` also uses one connection because an ordinary SQLite `:memory:` database is connection-local. Increasing the pool size is explicit and does not make SQLite a multi-writer server.

## Queries and parameter binding

```java
UUID id = UUID.randomUUID();
storage.update("INSERT INTO players(uuid, coins) VALUES (?, ?)", SqlParameters.of(id, 25L));

Optional<Long> balance = storage.queryOne(
    "SELECT coins FROM players WHERE uuid = ?",
    SqlParameters.of(id),
    row -> row.getLong("coins")
);

List<PlayerData> players = storage.queryList(
    "SELECT uuid, coins FROM players ORDER BY uuid",
    SqlParameters.none(),
    row -> new PlayerData(row.getUuid("uuid"), row.getLong("coins"))
);
```

All data values are JDBC-bound; supported values include strings, primitive wrappers, `BigDecimal`, byte arrays, UUID (canonical text), and `Instant` (ISO-8601 text). Use `SqlParameter.nullOf(JDBCType.VARCHAR)` for a typed SQL NULL. Arrays are snapshotted when parameters are created. Unsupported values are rejected rather than stringified. Identifiers cannot be bound; dynamic identifiers must come from a fixed allowlist. `queryOne` returns empty for no row and throws if a second row exists. Row views are valid only during their mapper callback; mapper exceptions still close the result set, statement, and connection.

## Network databases

The core does not force a network driver on SQLite users. Add a maintained JDBC driver to the application runtime:

```java
Storage storage = Storage.builder()
    .mysql(host, port, database)
    .credentials(username, passwordChars)
    .maximumPoolSize(8)
    .build();
```

For MariaDB use `.mariadb(host, port, database)`. The builder creates a JDBC URL without embedding credentials. Add MySQL Connector/J or MariaDB Connector/J, respectively, to the plugin runtime. TLS and certificate verification use the driver's documented properties; the framework does not disable verification. Do not log URLs with authentication parameters. Generic `jdbcUrl` rejects common embedded password properties and network URL user-info.

HikariCP pool defaults are one connection for SQLite and eight for network URLs. Connection acquisition timeout defaults to 10 seconds. Explicit pool sizing is honored independent of the order in which builder methods are called. Applications supplying a `DataSource` or executor retain ownership unless they explicitly transfer it to Storage. `dataSource()` returns a borrowed DataSource intended for integrations such as migration tools; do not close it.

## Transactions

Each transaction callback uses one JDBC connection:

```java
storage.transaction(tx -> {
    tx.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", SqlParameters.of(amount, from));
    tx.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", SqlParameters.of(amount, to));
    return null;
});
```

Success commits; callback or SQL failure attempts rollback. Rollback and connection-reset failures are suppressed onto the primary exception. Nested transactions are rejected. The `Transaction` is thread-confined and invalid after its callback; use it for all statements in the transaction instead of calling `Storage` methods. If commit fails because the server connection is lost, the final server-side outcome may be uncertain even though Storage reports failure.

## Background database work

Do not run blocking database access on the Paper server thread. `Storage.async()` uses a bounded fixed-size executor and a bounded queue by default; saturation rejects and exceptionally settles work instead of running SQL on the submitting thread.

```java
UUID id = player.getUniqueId();
TaskScope scope = lifecycle.get(TASK_SCOPE);
storage.async().queryOne("SELECT coins FROM players WHERE uuid = ?", SqlParameters.of(id),
        row -> row.getLong("coins"))
    .result().whenComplete((coins, failure) -> {
        if (scope.isClosed()) return;
        scope.sync().run(() -> {
            Player online = Bukkit.getPlayer(id);
            if (online != null && failure == null) showBalance(online, coins.orElse(0L));
        });
    });
```

The storage callback runs on the storage executor, not on Bukkit's server thread. Use the MinecraftDevKit scheduler or the platform's native scheduler for thread handoff. Capture stable identifiers such as UUIDs, then revalidate online state in the synchronous callback. `StorageTask.cancel()` cancels the logical result and removes queued work where supported; it does not guarantee interruption of JDBC work already running. Closing Storage rejects new work, settles outstanding logical handles as cancelled, shuts down internally owned workers without an unbounded wait, and preserves caller-owned executors/DataSources.

## Lifecycle ownership

Storage implements `AutoCloseable` and can be returned directly from a lifecycle service factory. Declare config and migration dependencies before repositories so migrations finish before gameplay queries begin. On reverse shutdown, repositories/features stop before Storage closes its pool and executor. Flyway is optional and lives in `devkit-storage-migrations`.

## Limits

SQL syntax and DDL transaction behavior vary between SQLite, MySQL and MariaDB. SQLite allows only one writer at a time and filesystem locking may be unsuitable on network mounts. No MySQL/MariaDB server is required by unit tests; driver presence, TLS settings, server version, and backend-specific SQL must be verified against the deployment. Storage does not implement retries, ORM mapping, schema generation, or automatic live pool replacement on configuration reload.
