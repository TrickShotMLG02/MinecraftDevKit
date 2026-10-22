# devkit-bukkit-gui

`devkit-bukkit-gui` is a Java 21 Bukkit/Paper adapter for chest inventory menus. It owns inventory listeners, isolates mutable state per viewer, renders immutable reusable menu definitions, blocks item movement through managed inventories, and provides navigation, controls, pagination, reactive refresh, localization, permissions, and player skull helpers. It does not depend on Cloud, so plugins can use it without the command framework.

The module is compiled against Paper API `1.21.11-R0.1-SNAPSHOT`. It uses public Bukkit/Paper APIs and does not include server classes in its artifact. Other server versions have not been verified. Folia is not supported because scheduling uses Bukkit's main-thread scheduler.

## Dependencies

```kotlin
repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    implementation("com.trickshotmlg.devkit:devkit-bukkit-gui:<version>")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}
```

`devkit-text`, `devkit-permissions`, and Adventure API are API dependencies. MiniMessage is used for explicitly formatted item text. Keep Paper/Bukkit APIs `compileOnly`; do not shade them into a plugin. If packaging dependencies into a plugin, relocate Cloud or other shared libraries as appropriate and retain the required third-party notices.

## Basic menu

Create one manager during plugin startup and close it during shutdown:

```java
public final class MenuPlugin extends JavaPlugin {
    private GuiManager guis;

    @Override public void onEnable() {
        guis = GuiManager.create(this);
        GuiMenu menu = GuiMenu.chest(3)
            .title("Main menu")
            .fillEmpty(GuiItems.grayPane())
            .button(11, GuiButton.of(
                ItemBuilder.of(Material.DIAMOND).name("Profile").build(),
                click -> click.viewer().sendMessage(Component.text("Hello!"))))
            .build();
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler public void onJoin(PlayerJoinEvent event) {
                // Example only; applications normally open the menu from a command.
            }
        }, this);
    }

    @Override public void onDisable() {
        if (guis != null) guis.close();
    }
}
```

Open a menu with `guis.open(player, menu)`. A menu definition is immutable after `build()` and can be shared by many players. Components and title/item templates are evaluated for each viewer. `GuiManager` identifies inventories through a private `InventoryHolder`, never by title.

## ItemBuilder

Plain strings stay plain text; only explicitly named MiniMessage methods parse markup:

```java
ItemStack item = ItemBuilder.of(Material.DIAMOND_SWORD)
    .name("Legendary sword")
    .lore("A powerful weapon", "Click to equip")
    .glow(true)
    .unbreakable(true)
    .hideAttributes()
    .customModelData(1234)
    .itemModel(NamespacedKey.fromString("example:legendary_sword"))
    .build();

ItemStack formatted = ItemBuilder.of(Material.PAPER)
    .nameMiniMessage("<aqua>Trusted formatted title")
    .build();
```

`ItemBuilder.from(existing)` clones its input. Every `build()` returns another independent stack. Supported metadata includes Adventure/plain/MiniMessage name and lore, enchantments, glint override, unbreakable, item flags, damage, legacy custom model data, modern Paper item model, persistent data, leather color, potion type, and player profiles. Type-incompatible metadata calls fail with an actionable exception. Item metadata operations require a running Bukkit/Paper server; the pure JUnit test runtime does not initialize Paper registries.

## Player and custom skulls

```java
ItemStack online = GuiItems.playerHead(player);
ItemStack byHash = GuiItems.skullTexture("0123456789abcdef0123456789abcdef");
ItemStack byBase64 = GuiItems.skullBase64(encodedTextureProperty);

SkullTextureService skulls = new SkullTextureService(plugin, 512);
GuiItemTemplate offline = GuiItems.offlinePlayerHead(
    playerUuid, skulls, guis, GuiItems.of(Material.SKELETON_SKULL, "Loading…"));
```

Online profiles are used without a network lookup. UUID-based resolution uses Paper's asynchronous `PlayerProfile.update()` and deduplicates in-flight work in a bounded LRU cache. The template initially returns the placeholder, then requests a slot refresh on the server thread after resolution. Refresh checks the active session, menu, slot, and component identity to reject stale results. Call `skulls.close()` during plugin shutdown. It owns no executor or worker threads.

Custom URL/hash inputs are restricted to HTTPS `textures.minecraft.net/texture/<32-hex-hash>`. Base64 input is size-limited and must contain exactly one allowed skin URL. Arbitrary URLs and custom texture hosts are rejected. Texture availability itself depends on Minecraft's profile/texture services.

## Navigation and state

Use the click context so inventory transitions are deferred safely:

```java
GuiMenu child = GuiMenu.chest(3).title("Details")
    .button(22, GuiButtons.back()).build();
GuiMenu parent = GuiMenu.chest(3).title("Main")
    .button(13, GuiButton.of(ItemBuilder.of(Material.PAPER).name("Details").build(),
        click -> click.open(child)))
    .build();
// Also available: click.back(), click.replace(menu), click.close(), click.refresh().
```

`open` pushes the current menu; `back` restores the previous menu and its session-local values; `replace` leaves history unchanged; `close` terminates the session. Conflicting same-tick navigation requests use last-request-wins semantics. Manual close and disconnect discard the navigation session. Typed `GuiStateKey<T>` values belong to one `GuiSession`; never put player-specific state in a shared component definition.

Direct manager operations and session state mutation must run on the server thread. Click handlers run synchronously on the server thread. Navigation requested through `GuiClickContext` is scheduled for the next tick. Do not perform blocking I/O from handlers.

## Toggles, radio groups, and confirmation

```java
ToggleButton soundToggle = ToggleButton.builder()
    .key("sound-effects").initialValue(true)
    .onItem(GuiItems.greenPane("Sounds: enabled"))
    .offItem(GuiItems.redPane("Sounds: disabled"))
    .onChange((click, enabled) -> click.viewer().sendMessage(Component.text("Sounds: " + enabled)))
    .build();

RadioGroup<String> difficulty = RadioGroup.<String>builder()
    .key("difficulty").selected("normal")
    .option(20, "easy", ctx -> ItemBuilder.of(Material.LIME_DYE).name("Easy (selected)").build(),
        ctx -> ItemBuilder.of(Material.GRAY_DYE).name("Easy").build(), null, ctx -> true, null)
    .option(22, "normal", ctx -> ItemBuilder.of(Material.GOLD_INGOT).name("Normal (selected)").build(),
        ctx -> ItemBuilder.of(Material.GRAY_DYE).name("Normal").build(), null, ctx -> true, null)
    .option(24, "hard", ctx -> ItemBuilder.of(Material.REDSTONE).name("Hard (selected)").build(),
        ctx -> ItemBuilder.of(Material.GRAY_DYE).name("Hard").build(), null, ctx -> true, null)
    .onChange((click, value) -> click.viewer().sendMessage(Component.text("Difficulty: " + value)))
    .build();

GuiMenu settings = GuiMenu.chest(5).component(10, soundToggle)
    .radioGroup(difficulty).build();
```

Toggles and radio groups store state per session by default, or can bind to an application getter/setter. For external bindings the setter runs before the callback; if it throws, the control does not refresh. A required radio group needs a valid selected option; optional groups explicitly use `allowNone(true)`. An option is mutually exclusive with every other option in its group.

```java
GuiMenu confirm = ConfirmGui.builder()
    .title("Delete entry?")
    .onConfirm(click -> click.viewer().sendMessage(Component.text("Confirmed")))
    .onCancel(GuiClickContext::back)
    .build();
```

The confirmation action is claimed before its callback runs, so repeated or reentrant clicks cannot invoke it twice. If the callback throws after partially completing its work, the action stays consumed and the dialog remains open with its confirm button disabled; the user can close it, but cannot retry. Make externally visible confirmation callbacks atomic or idempotent. Closing with Escape never confirms.

## Pagination

Slots are zero-based; `Slots.rectangle` endpoints are inclusive. Rows and columns also use zero-based coordinates.

```java
GuiMenu onlinePlayers = PagedGui.<Player>chest(6)
    .title("Online players")
    .contentSlots(Slots.rectangle(1, 1, 4, 7))
    .items(context -> List.copyOf(Bukkit.getOnlinePlayers()))
    .render((context, player) -> GuiButton.of(
        ctx -> ItemBuilder.from(GuiItems.playerHead(player)).name(player.getName()).build(),
        click -> click.viewer().sendMessage(Component.text("Selected " + player.getName()))))
    .previousPage(45)
    .pageIndicator(49, page -> ItemBuilder.of(Material.PAPER)
        .name("Page " + page.currentPage() + "/" + page.totalPages())
        .lore("Entries: " + page.totalItems(), "On page: " + page.visibleItemCount()).build())
    .nextPage(53)
    .emptyState(44, GuiItems.of(Material.BARRIER, "No entries"))
    .refreshOn(PlayerJoinEvent.class, event -> true, RefreshScope.CONTENT)
    .refreshOn(PlayerQuitEvent.class, event -> true, RefreshScope.CONTENT)
    .build();
```

Pages shown to players are one-based. Empty data reports page `1/1`; previous and next controls are disabled automatically. Current pages are clamped when data shrinks. `PageInfo` contains current/total pages, total item count, page size, first zero-based item index, visible count, and navigation flags. Content and its navigation/indicator controls are reserved together and validated against duplicates.

For viewer-localized indicators, use the two-argument renderer overload; it receives the same per-viewer `GuiRenderContext` as other dynamic components:

```java
.pageIndicator(49, (context, page) -> ItemBuilder.of(Material.PAPER)
    .name(context.text(TextRef.translated("gui.page", "Page {current}/{total}"),
        Map.of("current", page.currentPage(), "total", page.totalPages())))
    .build())
```

## Backgrounds and layouts

```java
GuiMenu patterned = GuiMenu.chest(3)
    .background(Pattern.checkerboard(GuiItems.grayPane(), GuiItems.blackPane()))
    .button(13, someButton)
    .build();

List<Integer> borderSlots = Slots.border(3);
List<Integer> middle = Slots.row(1);
```

Background patterns render first and only fill slots where no component produced an item. Static/decorative and interactive components take precedence. Overlapping components are rejected at definition time. Pattern-provided `ItemStack`s are cloned. Layout helpers include rows, columns, borders, inclusive rectangles, and explicit slot lists.

## Refresh and localization

`guis.refresh(session)` refreshes one session, `guis.refreshOpen(menu)` refreshes active instances of a definition, and click contexts expose `refresh()` and `refreshSlot(slot)`. Refreshing any reserved paginated content/control slot re-renders that whole region so displayed entries and their click actions share one data snapshot. `refreshOn(eventType, filter, scope)` installs one shared Bukkit subscription per menu definition. `FULL` re-evaluates every component; `CONTENT` re-evaluates the paged content and its controls. Multiple requests in one tick are coalesced, and stack equality is checked before writing a slot. The manager does not call `Player.updateInventory()` for ordinary updates.

For localized titles pass an existing `TextProvider` to `GuiManager.create(plugin, provider)` and use `TextRef`:

```java
GuiMenu menu = GuiMenu.chest(3)
    .title(TextRef.translated("gui.title", "Menu"))
    .button(13, GuiButton.of(
        GuiItems.localized(Material.PAPER, TextRef.translated("gui.refresh", "Refresh")),
        GuiClickContext::refresh))
    .build();
```

Item templates are resolved for each viewer, so a localization service reload is visible after refreshing the menu. Inventory titles are selected when the inventory opens; Bukkit/Paper does not update them in place, so reopen the menu to change a displayed title. Placeholder values resolved through `GuiRenderContext.text(...)` use the existing `TextProvider` safety rules and are inserted as data.

## Interaction protection and lifecycle

Managed top inventory clicks and drags are always cancelled, including stale inventory instances. In the default `PROTECT_GUI` policy, ordinary bottom-inventory actions remain available while shift transfers into the GUI and double-click collection are cancelled. Use `.interactionPolicy(GuiInteractionPolicy.LOCK_ALL)` to cancel all clicks/drags for the view. Each manager handles only inventories it created; close events also verify the session viewer and inventory identity. Component callbacks run only for the active viewer/session and the exact currently registered slot component. Typed permissions are checked at render and again at click time using Bukkit's native `hasPermission` check. Click predicates receive the actual top-inventory slot in their render context.

Construct and open GUIs on the server thread. All inventory mutation and refresh callbacks run on the server thread; `executeOnMainThread` always schedules to the next tick. Close `GuiManager` and `SkullTextureService` in `JavaPlugin.onDisable()`. Close/disconnect clears manager-held player sessions, their navigation history, and session state. Paper is the only verified runtime target; Folia's region/entity scheduler is not supported.

## Tests and live verification

The module contains pure JUnit coverage for inventory sizes, slot validation, state keys, pagination metadata, radio invariants, and texture input validation. Paper `ItemStack` construction requires a live Paper registry; those operations are covered by compilation and the standalone example, not a mocked server. Run the independent example's manual Paper test plan in [`examples/bukkit-gui-example/README.md`](../examples/bukkit-gui-example/README.md). No live Paper server test is claimed.
