package com.trickshotmlg.devkit.examples.lifecycle;

import com.trickshotmlg.devkit.bukkit.scheduler.BukkitTaskScheduler;
import com.trickshotmlg.devkit.lifecycle.ServiceKey;

import java.nio.file.Path;
import java.util.logging.Logger;

final class LifecycleServices {
    static final ServiceKey<ExampleConfigService> CONFIG = ServiceKey.of("config", ExampleConfigService.class);
    static final ServiceKey<ExampleDatabaseService> DATABASE = ServiceKey.of("database", ExampleDatabaseService.class);
    static final ServiceKey<BukkitTaskScheduler> SCHEDULER = ServiceKey.of("scheduler", BukkitTaskScheduler.class);
    static final ServiceKey<ExampleFeatureService> FEATURE = ServiceKey.of("feature", ExampleFeatureService.class);

    private LifecycleServices() { }

    static final class ExampleConfigService {
        private final Path dataDirectory;
        private int reloadCount;

        ExampleConfigService(Path dataDirectory) { this.dataDirectory = dataDirectory; }
        Path dataDirectory() { return dataDirectory; }
        int reloadCount() { return reloadCount; }
        void reload() { reloadCount++; }
    }

    static final class ExampleDatabaseService implements AutoCloseable {
        private final ExampleConfigService config;
        private final Logger logger;
        ExampleDatabaseService(ExampleConfigService config, Logger logger) { this.config = config; this.logger = logger; }
        String status() { return "database ready (config reloads=" + config.reloadCount() + ")"; }
        @Override public void close() { logger.info("Example database service closed"); }
    }

    static final class ExampleFeatureService {
        private final ExampleDatabaseService database;
        ExampleFeatureService(ExampleDatabaseService database) { this.database = database; }
        String status() { return "feature active; " + database.status(); }
    }
}
