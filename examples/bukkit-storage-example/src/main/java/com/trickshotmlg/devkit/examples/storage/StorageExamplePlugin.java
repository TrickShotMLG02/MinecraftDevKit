package com.trickshotmlg.devkit.examples.storage;

import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandAdapter;
import com.trickshotmlg.devkit.bukkit.lifecycle.BukkitLifecycle;
import com.trickshotmlg.devkit.bukkit.scheduler.BukkitTaskScheduler;
import com.trickshotmlg.devkit.config.yaml.YamlConfigHandle;
import com.trickshotmlg.devkit.lifecycle.ComponentKey;
import com.trickshotmlg.devkit.lifecycle.LifecycleManager;
import com.trickshotmlg.devkit.lifecycle.ServiceKey;
import com.trickshotmlg.devkit.scheduler.TaskScope;
import com.trickshotmlg.devkit.storage.Storage;
import com.trickshotmlg.devkit.storage.migrations.StorageMigrations;
import com.trickshotmlg.devkit.text.StaticTextProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Locale;

/** Standalone Paper example using lifecycle, typed config, JDBC storage, migrations, commands and scheduler scopes. */
public final class StorageExamplePlugin extends JavaPlugin {
    static final ServiceKey<YamlConfigHandle> CONFIG = ServiceKey.of("config", YamlConfigHandle.class);
    static final ServiceKey<Storage> STORAGE = ServiceKey.of("storage", Storage.class);
    static final ServiceKey<BukkitTaskScheduler> SCHEDULER = ServiceKey.of("scheduler", BukkitTaskScheduler.class);
    static final ServiceKey<TaskScope> TASKS = ServiceKey.of("tasks", TaskScope.class);
    static final ServiceKey<PlayerRepository> PLAYERS = ServiceKey.of("players", PlayerRepository.class);
    static final ComponentKey MIGRATIONS = ComponentKey.of("database-migrations");

    private LifecycleManager lifecycle;
    private BukkitLifecycle bukkitLifecycle;

    @Override public void onEnable() {
        lifecycle = createLifecycle(this);
        bukkitLifecycle = BukkitLifecycle.create(this, lifecycle);
        try {
            bukkitLifecycle.start();
            new BukkitCommandAdapter(this, new StaticTextProvider(), Locale.ENGLISH)
                    .register(StorageCommands.create(this));
            getLogger().info("Storage example ready; startup order=" + lifecycle.startupOrder());
        } catch (RuntimeException failure) {
            try { bukkitLifecycle.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            bukkitLifecycle = null;
            lifecycle = null;
            throw failure;
        }
    }

    static LifecycleManager createLifecycle(StorageExamplePlugin plugin) {
        Path dataDirectory = plugin.getDataFolder().toPath();
        return LifecycleManager.builder()
                .service(CONFIG, context -> YamlConfigHandle.createIfMissing(dataDirectory.resolve("config.yml"), StorageConfig.SCHEMA))
                .service(STORAGE, context -> createStorage(context.require(CONFIG), dataDirectory), CONFIG)
                .service(SCHEDULER, context -> BukkitTaskScheduler.create(plugin))
                // Storage outlives the scope because callbacks from database work are handed off through it.
                .service(TASKS, context -> context.require(SCHEDULER).scope(), SCHEDULER, STORAGE)
                .component(MIGRATIONS, context -> {
                    StorageMigrations.builder(context.require(STORAGE))
                            .locations("classpath:db/migration")
                            .migrate();
                }, null, STORAGE)
                .service(PLAYERS, context -> new PlayerRepository(context.require(STORAGE)), STORAGE, MIGRATIONS)
                .build();
    }

    private static Storage createStorage(YamlConfigHandle config, Path directory) {
        var values = config.store();
        return switch (values.get(StorageConfig.BACKEND)) {
            case SQLITE -> Storage.builder().sqlite(directory.resolve(values.get(StorageConfig.SQLITE_FILE))).build();
            case MYSQL -> Storage.builder().mysql(values.get(StorageConfig.HOST), values.get(StorageConfig.PORT),
                    values.get(StorageConfig.DATABASE)).credentials(values.get(StorageConfig.USERNAME),
                    values.get(StorageConfig.PASSWORD)).maximumPoolSize(values.get(StorageConfig.POOL_SIZE)).build();
            case MARIADB -> Storage.builder().mariadb(values.get(StorageConfig.HOST), values.get(StorageConfig.PORT),
                    values.get(StorageConfig.DATABASE)).credentials(values.get(StorageConfig.USERNAME),
                    values.get(StorageConfig.PASSWORD)).maximumPoolSize(values.get(StorageConfig.POOL_SIZE)).build();
        };
    }

    LifecycleManager lifecycle() { return lifecycle; }

    @Override public void onDisable() {
        try { if (bukkitLifecycle != null) bukkitLifecycle.close(); }
        finally { bukkitLifecycle = null; lifecycle = null; }
    }
}
