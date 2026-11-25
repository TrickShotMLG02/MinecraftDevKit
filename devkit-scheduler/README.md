# devkit-scheduler

`devkit-scheduler` defines Java 17, platform-independent tick scheduling contracts and task lifecycle ownership. It contains no Bukkit, Paper, Minecraft, command, GUI, localization, or configuration dependency. Platform adapters implement `TaskBackend` and map `SYNC` and `ASYNC` schedules to their native execution facilities.

The module schedules in server ticks. Ticks are not wall-clock time: 20 ticks is nominally one second at 20 TPS but can take longer under server load. `TaskRunner` also offers `Duration` convenience overloads; positive durations round up to the next tick at 20 TPS, zero remains zero for a delay, and invalid/overflow periods are rejected. These overloads do not promise real-time deadlines.

## Schedules and handles

```java
TaskHandle delayed = scheduler.sync().later(TickDelay.of(20), this::refresh);
TaskHandle byDuration = scheduler.sync().later(Duration.ofMillis(51), this::refresh); // two ticks

TaskHandle repeating = scheduler.sync().repeat(
    TickDelay.ZERO,
    TickPeriod.of(20),
    context -> {
        refresh();
        if (shouldStop()) context.cancel();
    }
);

repeating.cancel();
```

Repeating schedules also accept `(Duration initialDelay, Duration period, ...)`; the period must be positive and is rounded up to at least one tick.

`run` dispatches as soon as the backend permits. For Bukkit synchronous tasks this means a later server tick; `nextTick` explicitly requests a one-tick delay. Zero-delay repeating schedules start as soon as the backend permits. All delays and periods are validated and fit Bukkit's supported integer tick range.

One-shot tasks transition through `PENDING`, `RUNNING`, then `COMPLETED`, `CANCELLED`, or `FAILED`. Repeating tasks become `ACTIVE` between invocations and remain owned until cancellation/failure. Managed callbacks do not overlap: if an async repeat is still running at another native dispatch, that dispatch is ignored. The backend's native tick timing controls when later invocations are offered.

Cancellation is idempotent through `TaskHandle.cancel()`. It prevents callbacks that have not begun, but does not interrupt a callback already running. `completion()` completes normally for successful one-shot tasks and exceptionally on cancellation or failure; a repeat's completion remains pending until it terminates. A backend that rejects scheduling returns a failed handle and reports a failure with execution count zero.

## Scopes

```java
TaskScope feature = scheduler.scope();
TaskScope child = feature.childScope();

child.async().run(this::loadCache);
feature.sync().later(TickDelay.of(100), this::expireCache);

child.close(); // closes only this child
feature.close(); // closes remaining tasks and child scopes
```

Schedulers and scopes reject new work after closure. Registration and closure are synchronized so a task cannot escape ownership if they race. Terminal tasks are removed from their direct owner's active set. `activeTaskCount()` does not include child scopes. Caller-owned executors are not created or shut down by this module; the adapter backend owns its native task resources.

## Async result and server-thread handoff

```java
AsyncTaskResult<PlayerData> load = scheduler.async()
    .submit(() -> repository.load(uuid))
    .thenSync(data -> updateServerInventory(data))
    .onFailure(error -> logger.log(Level.SEVERE, "Player data load failed", error));

// Optional cancellation. It cannot guarantee interruption of work already running.
load.cancel();
```

The background callable must not access Bukkit APIs unless the specific API is documented as safe off-thread. `thenSync` schedules a nonblocking handoff through the same scope; it never calls `Future.get()`/`join()` and runs no callback on the completion thread. One synchronous continuation is allowed per result. Closing the scope cancels queued continuations and settles pending result stages; late computation completion cannot enqueue into a closed scope. If a computation already succeeded before cancellation, its value remains available, while cancellation suppresses a later handoff.

`onFailure` observers run using `CompletionStage` semantics: on the completing thread, or on the registration thread if the result was already complete. Keep observers short and thread-safe. Exceptions in observers are sent to that thread's uncaught exception handler.

## Failure handling

`TaskSchedulers.create(backend, failure -> ...)` installs centralized reporting. A throwing failure handler is itself sent to the callback thread's uncaught exception handler. The default factory also reports failures to the uncaught handler. Bukkit's adapter instead logs task id, target, invocation count, and the original cause through its owning plugin logger.

Repeating tasks use `CANCEL_ON_FAILURE` by default. `CONTINUE_ON_FAILURE` can be selected explicitly; each exception is still reported and the most recent one remains available from `TaskHandle.failure()`. One-shot tasks always become `FAILED` on exception.

## Deterministic tests

`com.trickshotmlg.devkit.scheduler.testing.ManualTaskBackend` is a test/local simulation backend. Advance virtual ticks explicitly, then call `runDue(target)` to execute one snapshot of due work. It runs callbacks on the calling thread and does not simulate Bukkit thread affinity or a live server.

## Extension boundary

The shared `SYNC` context describes the execution context promised by a selected adapter, not a universal main thread across all future Minecraft platforms. The Bukkit adapter currently means the traditional global primary server thread. Adapters for regionized systems such as Folia will need explicit entity/location ownership scheduling; they must not map every world/entity action to a global context. No Folia adapter is included.
