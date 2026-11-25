# devkit-bukkit-events

Small Java 21 Bukkit/Spigot event subscription utility, compiled against Spigot API 1.21.11 and usable on Paper. It delegates dispatch to Bukkit's `PluginManager`; it is not an event bus.

```java
BukkitEvents events = context.manage(BukkitEvents.create(plugin));
events.subscribe(PlayerJoinEvent.class, event -> welcome(event.getPlayer()));
EventSubscription monitor = events.subscribe(
    BlockBreakEvent.class, EventPriority.MONITOR, true, event -> observe(event.getBlock()));
```

Subscriptions default to `NORMAL` and `ignoreCancelled=false`. Call `close()` on a returned handle to remove only that registration. Closing the registrar or disabling its plugin closes all subscriptions. Register the registrar with the existing `LifecycleContext.manage` API to roll back subscriptions when service initialization fails.

Callbacks run on the thread Bukkit used to dispatch that event. Asynchronous Bukkit events remain asynchronous; callbacks must follow the event's own thread-safety rules. `MONITOR` is for observation and must not change the event outcome. The utility does not automatically reschedule callbacks or alter cancellation.
