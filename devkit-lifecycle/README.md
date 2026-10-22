# devkit-lifecycle

`devkit-lifecycle` is a Java 17, platform-independent manager for one explicit service graph. It is a small startup/ownership utility, not a dependency injection container: registration is ordinary Java, dependencies are declared directly, factories run sequentially on the thread calling `start()`, and the manager does not create threads.

## Register and start services

```java
import com.trickshotmlg.devkit.lifecycle.LifecycleManager;
import com.trickshotmlg.devkit.lifecycle.ServiceKey;

ServiceKey<Settings> SETTINGS = ServiceKey.of("settings", Settings.class);
ServiceKey<Database> DATABASE = ServiceKey.of("database", Database.class);
ServiceKey<Friends> FRIENDS = ServiceKey.of("friends", Friends.class);

LifecycleManager lifecycle = LifecycleManager.builder()
    .service(SETTINGS, context -> Settings.load(dataDirectory))
    .service(DATABASE, context -> new Database(context.require(SETTINGS)), SETTINGS)
    .service(FRIENDS, context -> new Friends(context.require(DATABASE)), DATABASE)
    .build();

lifecycle.start();
Friends friends = lifecycle.get(FRIENDS);
```

Keys are identified by logical name and runtime class. Names must be unique within a graph. A key for a parameterized type cannot retain its generic arguments because Java erases them; use a concrete service class or wrapper type for such services. Missing keys throw an actionable exception; `find(key)` returns an `Optional` for an optional lookup. Only declared dependencies are visible through a factory's `LifecycleContext.require`. A service reload hook can use `context.service(itsKey)` to access its own published instance.

## Order and lifecycle

Dependencies start before dependents. Independent nodes use registration order as the stable tie-breaker. Missing dependencies, incompatible keys, self-dependencies, duplicate node names, and cycles are rejected while building the immutable definition. Shutdown visits started nodes in reverse dependency order, so dependents can close while their dependencies are still available. A manager is a one-shot lifecycle and cannot be restarted after it stops or fails.

Callbacks run synchronously on the calling thread. A successful `start()` ends in `RUNNING`; `close()` moves through `STOPPING` to `STOPPED`. A factory or startup callback failure stops further startup, cleans resources acquired by that callback, rolls back successfully started nodes in reverse order, then leaves the manager `FAILED`. `LifecycleException` reports the phase and node and preserves the original exception as its cause; rollback errors are attached as suppressed exceptions. Normal shutdown continues after cleanup errors and then throws a lifecycle exception containing the cleanup failures. Repeated close is safe. Concurrent lifecycle operations serialize; a callback attempting to re-enter start, reload, or close on the same manager is rejected.

`get` and `find` expose services only when the whole manager is `RUNNING`. A service is published only after its factory succeeds and its result passes runtime type validation. Factories should declare dependencies rather than reaching out to unrelated application globals.

## Resource ownership and rollback

Services implementing `AutoCloseable` are closed automatically. Register resources acquired before a factory completes immediately with the context so a later exception can roll them back:

```java
ServiceKey<WatcherService> WATCHER = ServiceKey.of("watcher", WatcherService.class);

LifecycleManager lifecycle = LifecycleManager.builder()
    .service(WATCHER, context -> {
        FileWatcher watcher = context.manage(FileWatcher.open(path));
        TaskScope tasks = context.manage(scheduler.scope());
        return new WatcherService(watcher, tasks); // both resources transfer to this service
    })
    .build();
```

Resources are closed in reverse acquisition order, after a service's own automatic or explicit cleanup action. `context.onClose(...)` registers an action; `context.manage(...)` registers an `AutoCloseable`. Explicitly registering the same identity twice is rejected. When an automatically closed service is also registered with `context.manage(...)` in its own factory, the returned service is recognized as already owned and is closed once. Resources remain owned by the lifecycle after successful startup and are released during rollback or shutdown. Cleanup is best-effort for resources the framework owns: external effects that a resource cannot reverse are not transactional.

Use `serviceWithCleanup` when the service has a close operation that is not represented by `AutoCloseable`:

```java
LifecycleManager lifecycle = LifecycleManager.builder()
    .serviceWithCleanup(
    DATABASE,
    context -> Database.open(context.require(SETTINGS)),
    (database, context) -> database.shutdown(),
    SETTINGS
    )
    .build();
```

The explicit cleanup callback takes ownership of that service's cleanup. A wrong runtime type returned through unchecked/raw code is rejected; if the returned value is `AutoCloseable`, the manager still closes it during startup rollback.

## Components and reload hooks

Use a component for an ordered startup action that does not publish a service:

```java
ComponentKey LISTENERS = ComponentKey.of("listeners");
LifecycleManager.builder()
    .component(LISTENERS,
        context -> context.onClose(() -> unregisterListeners()),
        null,
        FRIENDS)
    .build();
```

Reload hooks are opt-in and run in dependency order. They do not recreate services and do not provide a multi-service transaction:

```java
LifecycleManager lifecycle = LifecycleManager.builder()
    .service(SETTINGS, context -> Settings.load(dataDirectory))
    .reload(SETTINGS, context -> context.service(SETTINGS).reload())
    .build();

lifecycle.start();
var result = lifecycle.reload();
if (!result.successful()) {
    result.failures().forEach(error -> logger.log(Level.SEVERE, error.getMessage(), error));
}
```

The result lists attempted hooks and failures. Reload continues after an individual hook fails, so earlier changes are not implicitly rolled back. Reload is serialized against start/close and is only available while the manager is running. `diagnostics()` returns an immutable snapshot of lifecycle and per-node states plus startup and shutdown order.

## Threading and module boundary

The core runs callbacks on the caller's thread and makes manager state and service publication safe for concurrent lookups. It does not make services themselves thread-safe. Do not call it from a worker if service startup or cleanup touches thread-bound platform APIs. For Bukkit/Paper, use `devkit-bukkit-lifecycle` from `onEnable`/`onDisable` on the server thread. Early Paper bootstrap and special command/registry lifecycle registrations remain owned by Paper's own APIs.
