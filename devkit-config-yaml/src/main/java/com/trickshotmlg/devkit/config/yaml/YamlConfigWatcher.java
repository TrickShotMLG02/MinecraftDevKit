package com.trickshotmlg.devkit.config.yaml;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One-file opt-in watcher; the parent directory observes atomic-save replacement events. */
final class YamlConfigWatcher implements AutoCloseable {
    private final YamlConfigHandle handle;
    private final Duration debounce;
    private final Consumer<YamlReloadResult> resultConsumer;
    private final Consumer<Throwable> failureConsumer;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledThreadPoolExecutor worker;
    private WatchService watchService;
    private Thread watchThread;
    private volatile ScheduledFuture<?> pending;

    YamlConfigWatcher(YamlConfigHandle handle, Duration debounce,
                      Consumer<YamlReloadResult> resultConsumer, Consumer<Throwable> failureConsumer) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.debounce = Objects.requireNonNull(debounce, "debounce");
        if (debounce.isNegative()) throw new IllegalArgumentException("debounce must not be negative");
        this.resultConsumer = Objects.requireNonNull(resultConsumer, "resultConsumer");
        this.failureConsumer = Objects.requireNonNull(failureConsumer, "failureConsumer");
        ThreadFactory factory = task -> { Thread thread = new Thread(task, "minecraftdevkit-config-reload"); thread.setDaemon(true); return thread; };
        worker = new ScheduledThreadPoolExecutor(1, factory);
        worker.setRemoveOnCancelPolicy(true);
        worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    void start() throws IOException {
        Path parent = handle.file().getParent();
        if (parent == null) throw new IOException("Configuration path has no parent directory");
        java.nio.file.Files.createDirectories(parent);
        watchService = FileSystems.getDefault().newWatchService();
        parent.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        watchThread = new Thread(this::run, "minecraftdevkit-config-watch");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    private void run() {
        try {
            while (!closed.get()) {
                WatchKey key = watchService.take();
                boolean targetChanged = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW
                            || event.context() instanceof Path path && path.equals(handle.file().getFileName())) targetChanged = true;
                }
                if (!key.reset()) throw new IOException("Configuration parent directory watch key became invalid");
                if (targetChanged) scheduleReload();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable failure) {
            if (!closed.get()) failureConsumer.accept(failure);
        }
    }

    private synchronized void scheduleReload() {
        if (closed.get()) return;
        if (pending != null) pending.cancel(false);
        pending = worker.schedule(() -> {
            if (closed.get()) return;
            try {
                YamlReloadResult result = handle.reload();
                resultConsumer.accept(result);
            } catch (Throwable failure) { failureConsumer.accept(failure); }
        }, debounce.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ScheduledFuture<?> task = pending;
        if (task != null) task.cancel(true);
        worker.shutdownNow();
        try { if (watchService != null) watchService.close(); }
        catch (IOException exception) { failureConsumer.accept(exception); }
        if (watchThread != null) watchThread.interrupt();
    }
}
