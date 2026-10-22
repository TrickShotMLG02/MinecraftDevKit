# devkit-storage-migrations

`devkit-storage-migrations` is an optional Flyway Community Edition integration for `devkit-storage`. It uses Flyway's versioned SQL migration engine rather than splitting SQL on semicolons. Apply migrations during plugin startup, after opening Storage and before creating repositories:

```java
Storage storage = Storage.sqlite(dataFolder.resolve("players.db"));
MigrationReport report = StorageMigrations.builder(storage)
    .locations("classpath:db/migration", "classpath:db/migration/sqlite")
    .migrate();
```

By default Flyway scans `classpath:db/migration`. Migration filenames follow Flyway versioned naming such as `V1__create_players.sql`. Successful versions and checksums are stored in the configured schema history table. `validate()` checks applied migration checksums without applying pending migrations; `migrate()` validates and applies pending migrations, returning the count and current version.

The Flyway integration borrows `Storage.dataSource()` and never closes Storage. Lifecycle ordering should be `configuration -> storage -> migration component -> repositories`; reverse shutdown then closes repositories before Storage. A migration exception should fail plugin startup so lifecycle rollback can close the pool.

The module declares Flyway core and database integration modules, but it does not bundle MySQL or MariaDB JDBC drivers. Include the selected database's JDBC driver in the application runtime. SQLite JDBC is available from `devkit-storage`.

## Optional network database integration tests

Normal unit tests and root build tasks remain Docker-free. To execute the real MySQL/MariaDB migration suite, use a Docker-compatible container runtime and run:

```powershell
.\gradlew.bat :devkit-storage-migrations:integrationTest
```

The suite uses Testcontainers 2.0.5 with MySQL `8.4.11` and MariaDB `11.4.13`, and exercises packaged versioned SQL resources, Flyway history/version reporting, repeat startup, checksum validation, and invalid-SQL failure handling on each server. A missing container runtime makes this explicit task fail; no tests are skipped and reported as a successful backend verification.

Run both module suites together with:

```powershell
.\gradlew.bat :devkit-storage:integrationTest :devkit-storage-migrations:integrationTest
```

Migrations are not automatically reversible. DDL transaction support differs among SQLite, MySQL, and MariaDB, and concurrent SQLite migration attempts are not a distributed lock. Do not run two application instances against one SQLite database and assume Flyway serializes their startup. Back up production data and test migrations on the exact target backend before deployment. Migration failure leaves the database state to the backend and Flyway; this module does not delete data or reset history.
