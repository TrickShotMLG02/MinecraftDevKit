package com.trickshotmlg.devkit.examples.storage;

import com.trickshotmlg.devkit.storage.SqlParameters;
import com.trickshotmlg.devkit.storage.Storage;
import com.trickshotmlg.devkit.storage.StorageTask;

import java.util.Optional;
import java.util.UUID;

/** Small explicit-SQL repository; every gameplay operation is submitted to Storage's bounded workers. */
final class PlayerRepository {
    private static final String UPSERT_SQLITE = "INSERT INTO players(uuid, coins) VALUES (?, ?) "
            + "ON CONFLICT(uuid) DO UPDATE SET coins = coins + excluded.coins";
    private static final String UPSERT_MYSQL = "INSERT INTO players(uuid, coins) VALUES (?, ?) "
            + "ON DUPLICATE KEY UPDATE coins = coins + VALUES(coins)";
    private final Storage storage;
    private final boolean sqlite;

    PlayerRepository(Storage storage) {
        this.storage = storage;
        this.sqlite = storage.configuration().sqlite();
    }

    StorageTask<Optional<Long>> balance(UUID player) {
        return storage.async().queryOne("SELECT coins FROM players WHERE uuid = ?",
                SqlParameters.of(player.toString()), row -> row.getLong("coins"));
    }

    StorageTask<Integer> add(UUID player, long amount) {
        if (amount < 0) throw new IllegalArgumentException("amount must be non-negative");
        return storage.async().update(sqlite ? UPSERT_SQLITE : UPSERT_MYSQL,
                SqlParameters.of(player.toString(), amount));
    }

    StorageTask<Integer> set(UUID player, long amount) {
        if (amount < 0) throw new IllegalArgumentException("amount must be non-negative");
        return storage.async().update(sqlite
                        ? "INSERT INTO players(uuid, coins) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET coins = excluded.coins"
                        : "INSERT INTO players(uuid, coins) VALUES (?, ?) ON DUPLICATE KEY UPDATE coins = VALUES(coins)",
                SqlParameters.of(player.toString(), amount));
    }
}
