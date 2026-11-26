package com.trickshotmlg.devkit.paper.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.trickshotmlg.devkit.bukkit.commands.BukkitCommandSenderAdapter;
import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.commands.CommandNode;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

/**
 * Optional Paper-only guard for static literals in outgoing completion responses.
 * Dynamic suggestion providers are never rerun. Use only for command roots known to be
 * effectively registered by this plugin; Paper's completion event does not expose command ownership.
 * Listener registration and cleanup require the Bukkit primary thread. Asynchronous completion events
 * are deliberately left unchanged because player permission reads have no safe asynchronous guarantee.
 */
public final class PaperPermissionSuggestionFilter implements AutoCloseable {
    private final JavaPlugin plugin;
    private final BukkitCommandSenderAdapter senderAdapter;
    private final Listener listener = new SuggestionListener();
    private final Map<String, CommandNode> roots = new HashMap<>();
    private final Set<String> registeredRootLabels = new HashSet<>();
    private volatile boolean closed;
    private boolean asyncWarningLogged;

    /** Registers the opt-in filter listener. Add each command definition only after registration succeeds.
     * @param plugin owning JavaPlugin
     * @param defaultLocale fallback locale used by shared sender requirements
     */
    public PaperPermissionSuggestionFilter(JavaPlugin plugin, Locale defaultLocale) {
        requirePrimaryThread("register the completion listener");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.senderAdapter = new BukkitCommandSenderAdapter(Objects.requireNonNull(defaultLocale, "defaultLocale"));
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    /** Adds roots and aliases from a successfully registered definition to this filter's ownership set.
     * Duplicate labels are rejected rather than allowing two definitions to filter each other.
     * @param definition immutable command tree registered by this plugin
     */
    public synchronized void register(CommandDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (closed) throw new IllegalStateException("Permission suggestion filter is closed");
        Map<String, CommandNode> additions = new HashMap<>();
        for (CommandNode root : definition.roots()) {
            addLabel(additions, root.name(), root);
            root.aliases().forEach(alias -> addLabel(additions, alias, root));
        }
        Set<String> collisions = new HashSet<>(additions.keySet());
        collisions.retainAll(registeredRootLabels);
        if (!collisions.isEmpty()) throw new IllegalArgumentException("Completion roots already owned by this filter: " + collisions);
        roots.putAll(additions);
        registeredRootLabels.addAll(additions.keySet());
    }

    private static void addLabel(Map<String, CommandNode> additions, String label, CommandNode root) {
        String key = normalize(label);
        if (additions.putIfAbsent(key, root) != null) throw new IllegalArgumentException("Duplicate command label: " + label);
    }

    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT); }

    @Override public synchronized void close() {
        if (closed) return;
        requirePrimaryThread("close the completion listener");
        closed = true;
        roots.clear();
        registeredRootLabels.clear();
        org.bukkit.event.HandlerList.unregisterAll(listener);
    }

    private static void requirePrimaryThread(String operation) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Must run on the Bukkit primary server thread to " + operation);
        }
    }

    private final class SuggestionListener implements Listener {
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onSuggestions(AsyncPlayerSendSuggestionsEvent event) {
            if (closed) return;
            if (event.isAsynchronous()) {
                synchronized (PaperPermissionSuggestionFilter.this) {
                    if (!asyncWarningLogged) {
                        asyncWarningLogged = true;
                        plugin.getLogger().warning("Permission-aware completion filtering skipped an asynchronous "
                                + "suggestion event; Bukkit player permission reads are not guaranteed thread-safe.");
                    }
                }
                return;
            }
            try {
                Map<String, CommandNode> snapshot;
                synchronized (PaperPermissionSuggestionFilter.this) { snapshot = Map.copyOf(roots); }
                if (closed) return;
                if (snapshot.isEmpty()) return;
                var sender = senderAdapter.adapt(event.getPlayer());
                Suggestions filtered = filterSuggestions(event.getBuffer(), event.getSuggestions(), snapshot,
                        plugin.getName(), sender);
                if (filtered != event.getSuggestions()) event.setSuggestions(filtered);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not filter a Paper command completion response", failure);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPluginDisable(PluginDisableEvent event) {
            if (event.getPlugin() == plugin) close();
        }
    }

    static Suggestions filterSuggestions(String buffer, Suggestions original, Map<String, CommandNode> roots,
                                         String pluginName, com.trickshotmlg.devkit.commands.CommandSender sender) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(roots, "roots");
        Objects.requireNonNull(pluginName, "pluginName");
        Objects.requireNonNull(sender, "sender");
        if (original.isEmpty()) return original;

        int start = Math.max(0, Math.min(buffer.length(), original.getRange().getStart()));
        List<String> consumed = tokens(buffer.substring(0, start));
        List<Suggestion> kept = new ArrayList<>(original.getList().size());

        if (consumed.isEmpty()) {
            for (Suggestion suggestion : original.getList()) {
                CommandNode root = ownedRoot(suggestion.getText(), roots, pluginName);
                if (root == null || reachable(root, sender)) kept.add(suggestion);
            }
        } else {
            CommandNode node = ownedRoot(consumed.get(0), roots, pluginName);
            if (node == null) return original;
            boolean ambiguous = false;
            for (int i = 1; i < consumed.size(); i++) {
                if (!node.arguments().isEmpty()) { ambiguous = true; break; }
                CommandNode child = child(node, consumed.get(i));
                if (child == null) return original;
                node = child;
            }
            if (ambiguous || !node.arguments().isEmpty()) return original;
            for (Suggestion suggestion : original.getList()) {
                CommandNode child = child(node, suggestion.getText());
                if (child == null || reachable(child, sender)) kept.add(suggestion);
            }
        }

        if (kept.size() == original.getList().size()) return original;
        return new Suggestions(original.getRange(), kept);
    }

    private static CommandNode ownedRoot(String token, Map<String, CommandNode> roots, String pluginName) {
        int colon = token.indexOf(':');
        if (colon >= 0) {
            if (!token.substring(0, colon).equalsIgnoreCase(pluginName)) return null;
            token = token.substring(colon + 1);
        }
        return roots.get(normalize(token));
    }

    private static CommandNode child(CommandNode node, String token) {
        for (CommandNode child : node.children()) {
            if (child.name().equalsIgnoreCase(token)
                    || child.aliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(token))) return child;
        }
        return null;
    }

    private static boolean reachable(CommandNode node, com.trickshotmlg.devkit.commands.CommandSender sender) {
        if (!node.isVisibleTo(sender)) return false;
        if (node.executable()) return true;
        return node.children().stream().anyMatch(child -> reachable(child, sender));
    }

    private static List<String> tokens(String input) {
        String command = input.stripLeading();
        if (command.startsWith("/")) command = command.substring(1);
        StringReader reader = new StringReader(command);
        List<String> result = new ArrayList<>();
        while (reader.canRead()) {
            while (reader.canRead() && Character.isWhitespace(reader.peek())) reader.skip();
            if (!reader.canRead()) break;
            int before = reader.getCursor();
            try {
                String token = reader.readString();
                if (result.isEmpty() && reader.canRead() && reader.peek() == ':') {
                    StringBuilder qualifiedRoot = new StringBuilder(token);
                    while (reader.canRead() && !Character.isWhitespace(reader.peek())) qualifiedRoot.append(reader.read());
                    token = qualifiedRoot.toString();
                }
                result.add(token);
            }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException malformed) { return List.of(); }
            if (reader.getCursor() == before) return List.of();
        }
        return result;
    }
}
