package com.trickshotmlg.devkit.localization;

import com.trickshotmlg.devkit.text.MissingTranslationException;
import com.trickshotmlg.devkit.text.MissingTranslationPolicy;
import com.trickshotmlg.devkit.text.TextFormatter;
import com.trickshotmlg.devkit.text.TextProvider;
import com.trickshotmlg.devkit.text.TextRef;
import net.kyori.adventure.text.Component;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Thread-safe, reloadable text provider backed by atomically replaced immutable catalogs. */
public final class LocalizationService implements TextProvider, AutoCloseable {
    private final List<TranslationSource> sources;
    private final Locale defaultFallbackLocale;
    private final boolean defaultFallbackEnabled;
    private final MissingTranslationPolicy missingKeyPolicy;
    private final TextDiagnosticSink diagnosticSink;
    private final AtomicReference<TranslationSnapshot> active = new AtomicReference<>();
    private final AtomicLong generation = new AtomicLong();
    private final Object reloadLock = new Object();
    private final Set<DiagnosticIdentity> reported = ConcurrentHashMap.newKeySet();
    private final Executor reloadExecutor;
    private final Consumer<Throwable> watcherFailureHandler;
    private final ExecutorService ownedExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Watcher> watcher = new AtomicReference<>();

    private LocalizationService(Builder builder) {
        sources = List.copyOf(builder.sources);
        defaultFallbackLocale = builder.defaultFallbackLocale == null ? null
                : LocaleFallbackResolver.normalize(builder.defaultFallbackLocale);
        defaultFallbackEnabled = builder.defaultFallbackEnabled;
        missingKeyPolicy = builder.missingKeyPolicy;
        diagnosticSink = builder.diagnosticSink;
        watcherFailureHandler = builder.watcherFailureHandler;
        if (builder.reloadExecutor == null) {
            ownedExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(1), daemonFactory("minecraftdevkit-localization-reload"),
                    new ThreadPoolExecutor.AbortPolicy());
            reloadExecutor = ownedExecutor;
        } else {
            ownedExecutor = null;
            reloadExecutor = builder.reloadExecutor;
        }
        active.set(TranslationSnapshot.bundled(Map.of()));
    }

    /** Starts configuring a localization service. */
    public static Builder builder() { return new Builder(); }

    /** Loads every configured source and atomically publishes the complete candidate snapshot. */
    public ReloadResult reload() {
        ensureOpen();
        synchronized (reloadLock) {
            ensureOpen();
            try {
                TranslationSnapshot candidate = TranslationSnapshot.load(sources);
                TranslationSnapshot previous = active.get();
                boolean changed = generation.get() == 0 || !candidate.equals(previous);
                active.set(candidate);
                long current = generation.incrementAndGet();
                return result(true, changed, current, candidate, Optional.empty());
            } catch (IOException | RuntimeException exception) {
                TranslationSnapshot existing = active.get();
                return result(false, false, generation.get(), existing,
                        Optional.of(describe(exception)));
            }
        }
    }

    /**
     * Schedules a serialized reload on the configured executor. The default executor allows one
     * active and one queued reload; if both slots are occupied, the returned stage completes
     * exceptionally with a rejection instead of growing an unbounded work queue.
     */
    public CompletionStage<ReloadResult> reloadAsync() {
        ensureOpen();
        CompletableFuture<ReloadResult> future = new CompletableFuture<>();
        try {
            reloadExecutor.execute(() -> {
                try { future.complete(reload()); }
                catch (RuntimeException exception) { future.completeExceptionally(exception); }
            });
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }

    /** Whether at least one successful initial load has occurred. */
    public boolean isLoaded() { return generation.get() > 0; }

    /** Current successful snapshot generation, starting at zero before initial load. */
    public long generation() { return generation.get(); }

    /** Resolves against one consistent immutable snapshot and returns resolution provenance. */
    public TextResolution resolveWithTrace(TextRef reference, Locale locale, Map<String, ?> placeholders) {
        return resolveWithTrace(reference, locale, placeholders, Optional.empty());
    }

    /** Resolves against one consistent immutable snapshot and associates diagnostic context. */
    public TextResolution resolveWithTrace(TextRef reference, Locale locale, Map<String, ?> placeholders,
                                           Optional<String> sourceContext) {
        Objects.requireNonNull(reference, "reference");
        Locale requested = LocaleFallbackResolver.normalize(locale);
        Objects.requireNonNull(sourceContext, "sourceContext");
        TextFormatter.validate(placeholders);
        TranslationSnapshot snapshot = active.get();
        String template;
        boolean markup = false;
        TextResolution.Fallback fallback;
        Optional<Locale> resolvedLocale;
        Optional<String> source;
        Optional<String> key = reference.translationKey();
        if (reference instanceof TextRef.Literal literal) {
            template = literal.value();
            fallback = TextResolution.Fallback.NONE;
            resolvedLocale = Optional.empty();
            source = Optional.empty();
        } else {
            String translationKey = key.orElseThrow();
            TranslationSnapshot.Lookup lookup = snapshot.lookup(translationKey, requested,
                    defaultFallbackLocale, defaultFallbackEnabled);
            if (lookup != null) {
                template = lookup.value();
                markup = true;
                fallback = lookup.fallback();
                resolvedLocale = Optional.of(lookup.locale());
                source = lookup.sources().stream().findFirst()
                        .map(name -> lookup.layer().name().toLowerCase(Locale.ROOT) + ":" + name);
                if (fallback != TextResolution.Fallback.NONE) {
                    report(new TextDiagnostic(TextDiagnostic.Kind.MISSING_TRANSLATION, translationKey, requested,
                            reference.fallback().isPresent(), outcome(fallback), Optional.empty(), sourceContext));
                }
            } else if (reference.fallback().isPresent()) {
                template = reference.fallback().orElseThrow();
                fallback = TextResolution.Fallback.TEXT_REF;
                resolvedLocale = Optional.empty();
                source = Optional.empty();
                report(new TextDiagnostic(TextDiagnostic.Kind.MISSING_TRANSLATION, translationKey, requested,
                        true, TextDiagnostic.Outcome.REFERENCE_FALLBACK, Optional.empty(), sourceContext));
            } else {
                TextDiagnostic.Outcome missing = switch (missingKeyPolicy) {
                    case SHOW_KEY -> TextDiagnostic.Outcome.SHOW_KEY;
                    case SHOW_MARKER -> TextDiagnostic.Outcome.SHOW_MARKER;
                    case ERROR -> TextDiagnostic.Outcome.ERROR;
                };
                report(new TextDiagnostic(TextDiagnostic.Kind.MISSING_TRANSLATION, translationKey, requested,
                        false, missing, Optional.empty(), sourceContext));
                if (missingKeyPolicy == MissingTranslationPolicy.ERROR) {
                    throw new MissingTranslationException(translationKey, requested);
                }
                template = missingKeyPolicy == MissingTranslationPolicy.SHOW_KEY
                        ? translationKey : "<missing:" + translationKey + ">";
                fallback = TextResolution.Fallback.MISSING_KEY_POLICY;
                resolvedLocale = Optional.empty();
                source = Optional.empty();
            }
        }
        if (key.isPresent()) {
            for (String placeholder : TextFormatter.placeholders(template)) {
                if (!placeholders.containsKey(placeholder)) {
                    report(new TextDiagnostic(TextDiagnostic.Kind.MISSING_PLACEHOLDER, key.orElseThrow(), requested,
                            reference.fallback().isPresent(), TextDiagnostic.Outcome.PLACEHOLDER_UNRESOLVED,
                            Optional.of(placeholder), sourceContext));
                }
            }
        }
        return new TextResolution(TranslationTemplateRenderer.render(template, placeholders, markup), requested,
                resolvedLocale, fallback, source);
    }

    @Override
    public Component resolve(TextRef reference, Locale locale, Map<String, ?> placeholders) {
        return resolveWithTrace(reference, locale, placeholders).component();
    }

    @Override
    public Component resolve(TextRef reference, Locale locale, Map<String, ?> placeholders,
                             Optional<String> sourceContext) {
        return resolveWithTrace(reference, locale, placeholders, sourceContext).component();
    }

    /** Starts an opt-in watcher for external YAML source directories. */
    public synchronized void startWatching() throws IOException { startWatching(Duration.ofMillis(300)); }

    /** Starts an opt-in watcher with a quiet-period debounce. */
    public synchronized void startWatching(Duration debounce) throws IOException {
        ensureOpen();
        Objects.requireNonNull(debounce, "debounce");
        if (debounce.isNegative() || debounce.isZero()) throw new IllegalArgumentException("debounce must be positive");
        if (watcher.get() != null) return;
        List<Path> directories = sources.stream().flatMap(source -> source.watchDirectories().stream()).distinct().toList();
        if (directories.isEmpty()) throw new IllegalStateException("No external translation directories are configured");
        Watcher created = new Watcher(directories, debounce);
        watcher.set(created);
        created.start();
    }

    /** Stops the optional filesystem watcher without closing the service. */
    public synchronized void stopWatching() {
        Watcher existing = watcher.getAndSet(null);
        if (existing != null) existing.close();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        // Wait for an already-running synchronous load to finish before releasing resources.
        synchronized (reloadLock) { }
        stopWatching();
        // Queued reloads now observe the closed state and complete their futures exceptionally.
        if (ownedExecutor != null) ownedExecutor.shutdown();
    }

    private void report(TextDiagnostic diagnostic) {
        DiagnosticIdentity identity = new DiagnosticIdentity(diagnostic.kind(), diagnostic.translationKey(),
                diagnostic.requestedLocale(), diagnostic.placeholder());
        if (reported.add(identity)) diagnosticSink.report(diagnostic);
    }

    private static TextDiagnostic.Outcome outcome(TextResolution.Fallback fallback) {
        return switch (fallback) {
            case PARENT_LOCALE -> TextDiagnostic.Outcome.PARENT_LOCALE_FALLBACK;
            case DEFAULT_LOCALE -> TextDiagnostic.Outcome.DEFAULT_LOCALE_FALLBACK;
            case TEXT_REF -> TextDiagnostic.Outcome.REFERENCE_FALLBACK;
            case MISSING_KEY_POLICY -> TextDiagnostic.Outcome.SHOW_KEY;
            case NONE -> throw new IllegalArgumentException("No fallback outcome for exact locale");
        };
    }

    private ReloadResult result(boolean success, boolean changed, long current, TranslationSnapshot snapshot,
                               Optional<String> error) {
        List<String> localeTags = snapshot.locales().stream().map(Locale::toLanguageTag).toList();
        int count = snapshot.locales().stream().mapToInt(locale -> snapshot.translations(locale).size()).sum();
        return new ReloadResult(success, changed, current, localeTags, count,
                sources.stream().map(TranslationSource::description).toList(), error);
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("LocalizationService is closed");
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static ThreadFactory daemonFactory(String name) {
        return runnable -> { Thread thread = new Thread(runnable, name); thread.setDaemon(true); return thread; };
    }

    /** Mutable configuration used only to construct an immutable service. */
    public static final class Builder {
        private final List<TranslationSource> sources = new ArrayList<>();
        private Locale defaultFallbackLocale;
        private boolean defaultFallbackEnabled;
        private MissingTranslationPolicy missingKeyPolicy = MissingTranslationPolicy.SHOW_KEY;
        private TextDiagnosticSink diagnosticSink = TextDiagnosticSink.NO_OP;
        private Executor reloadExecutor;
        private Consumer<Throwable> watcherFailureHandler = ignored -> { };

        private Builder() { }
        public Builder source(TranslationSource source) { sources.add(Objects.requireNonNull(source, "source")); return this; }
        public Builder defaultFallbackLocale(Locale locale) { defaultFallbackLocale = LocaleFallbackResolver.normalize(locale); return this; }
        public Builder defaultFallbackEnabled(boolean enabled) { defaultFallbackEnabled = enabled; return this; }
        public Builder missingKeyPolicy(MissingTranslationPolicy policy) { missingKeyPolicy = Objects.requireNonNull(policy); return this; }
        public Builder diagnosticSink(TextDiagnosticSink sink) { diagnosticSink = Objects.requireNonNull(sink); return this; }
        /** Receives watcher and automatic-reload failures; the default handler ignores them. */
        public Builder watcherFailureHandler(Consumer<Throwable> handler) {
            watcherFailureHandler = Objects.requireNonNull(handler); return this;
        }
        /** Supplies a caller-owned executor; the service does not shut it down. */
        public Builder reloadExecutor(Executor executor) { reloadExecutor = Objects.requireNonNull(executor); return this; }
        public LocalizationService build() { return new LocalizationService(this); }
    }

    private record DiagnosticIdentity(TextDiagnostic.Kind kind, String key, Locale locale,
                                      Optional<String> placeholder) { }

    private final class Watcher implements AutoCloseable, Runnable {
        private final Duration debounce;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final WatchService watchService;
        private final Map<WatchKey, Path> directories = new ConcurrentHashMap<>();
        private final Set<Path> registeredDirectories = ConcurrentHashMap.newKeySet();
        private final Thread thread;

        Watcher(List<Path> roots, Duration debounce) throws IOException {
            this.debounce = debounce;
            watchService = FileSystems.getDefault().newWatchService();
            try {
                for (Path root : roots) {
                    Files.createDirectories(root);
                    registerTree(root);
                }
                thread = daemonFactory("minecraftdevkit-localization-watch").newThread(this);
            } catch (IOException | RuntimeException failure) {
                try { watchService.close(); }
                catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
                throw failure;
            }
        }

        void start() { thread.start(); }

        private void registerTree(Path root) throws IOException {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(candidate -> Files.isDirectory(candidate,
                        java.nio.file.LinkOption.NOFOLLOW_LINKS)).toList()) register(path);
            }
        }

        private void register(Path directory) throws IOException {
            Path normalized = directory.toAbsolutePath().normalize();
            if (!registeredDirectories.add(normalized)) return;
            try {
                WatchKey key = directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                directories.put(key, normalized);
            } catch (IOException | RuntimeException failure) {
                registeredDirectories.remove(normalized);
                throw failure;
            }
        }

        @Override public void run() {
            boolean dirty = false;
            long lastChange = 0;
            while (running.get()) {
                try {
                    WatchKey key = watchService.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (key != null) {
                        Path directory = directories.get(key);
                        for (WatchEvent<?> event : key.pollEvents()) {
                            if (event.kind() == StandardWatchEventKinds.OVERFLOW || directory == null) continue;
                            Path changed = directory.resolve((Path) event.context()).toAbsolutePath().normalize();
                            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                                try { registerTree(changed); }
                                catch (IOException failure) { reportWatcherFailure(failure); }
                                // Files may have been written before the new directory was registered.
                                try (var nested = Files.walk(changed)) {
                                    if (nested.anyMatch(path -> Files.isRegularFile(path,
                                            java.nio.file.LinkOption.NOFOLLOW_LINKS)
                                            && path.getFileName().toString().endsWith(".yml"))) {
                                        dirty = true;
                                        lastChange = System.nanoTime();
                                    }
                                } catch (IOException failure) { reportWatcherFailure(failure); }
                            }
                            if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                                boolean removedDirectory = directories.entrySet().stream()
                                        .anyMatch(entry -> entry.getValue().startsWith(changed));
                                if (removedDirectory) {
                                    // Cancel watches below a removed locale directory and reload its removed keys.
                                    for (Map.Entry<WatchKey, Path> entry : directories.entrySet()) {
                                        if (entry.getValue().startsWith(changed)) {
                                            entry.getKey().cancel();
                                            directories.remove(entry.getKey(), entry.getValue());
                                            registeredDirectories.remove(entry.getValue());
                                        }
                                    }
                                    dirty = true;
                                    lastChange = System.nanoTime();
                                }
                            }
                            if (changed.getFileName().toString().endsWith(".yml")) {
                                dirty = true;
                                lastChange = System.nanoTime();
                            }
                        }
                        if (!key.reset()) {
                            Path removed = directories.remove(key);
                            if (removed != null) registeredDirectories.remove(removed);
                        }
                    }
                    if (dirty && System.nanoTime() - lastChange >= debounce.toNanos()) {
                        reloadAsync().thenAccept(result -> {
                            if (!result.successful()) reportWatcherFailure(new IOException(
                                    "Automatic localization reload failed: " + result.error().orElse("unknown error")));
                        }).exceptionally(failure -> {
                            reportWatcherFailure(failure);
                            return null;
                        });
                        dirty = false;
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (RuntimeException failure) {
                    if (running.get()) reportWatcherFailure(failure);
                }
            }
        }

        private void reportWatcherFailure(Throwable failure) {
            try { watcherFailureHandler.accept(failure); }
            catch (RuntimeException ignored) { }
        }

        @Override public void close() {
            running.set(false);
            try { watchService.close(); } catch (IOException ignored) { }
            thread.interrupt();
            if (Thread.currentThread() != thread) {
                try { thread.join(1000); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            }
        }
    }
}
