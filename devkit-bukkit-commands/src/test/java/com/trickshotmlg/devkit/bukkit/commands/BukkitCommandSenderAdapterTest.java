package com.trickshotmlg.devkit.bukkit.commands;

import com.trickshotmlg.devkit.commands.CommandSender;
import com.trickshotmlg.devkit.commands.SenderKind;
import com.trickshotmlg.devkit.permissions.Permission;
import com.trickshotmlg.devkit.permissions.PermissionNamespace;
import net.kyori.adventure.text.Component;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BukkitCommandSenderAdapterTest {
    @Test void playerLocalePermissionAndAdventureMessagesUseNativeSender() {
        AtomicReference<Component> delivered = new AtomicReference<>();
        Player player = proxy(Player.class, (method, args) -> switch (method) {
            case "getLocale" -> "de_DE";
            case "hasPermission" -> "friends.add".equals(args[0]);
            case "sendMessage" -> { delivered.set((Component) args[0]); yield null; }
            default -> defaultValue(method);
        });
        CommandSender sender = new BukkitCommandSenderAdapter(Locale.US).adapt(player);
        Permission permission = PermissionNamespace.create("friends").permission("add");
        Component message = Component.text("Hallo");
        sender.sendMessage(message);
        assertEquals(SenderKind.PLAYER, sender.kind());
        assertSame(player, ((BukkitCommandSender) sender).bukkitSender());
        assertEquals(Locale.GERMANY, sender.locale());
        assertTrue(sender.hasPermission(permission));
        assertSame(message, delivered.get());
    }

    @Test void consoleUsesConfiguredFallbackLocaleAndOtherSendersRemainOther() {
        ConsoleCommandSender console = proxy(ConsoleCommandSender.class, (method, args) -> defaultValue(method));
        CommandSender consoleView = new BukkitCommandSenderAdapter(Locale.CANADA_FRENCH).adapt(console);
        assertEquals(SenderKind.CONSOLE, consoleView.kind());
        assertEquals(Locale.CANADA_FRENCH, consoleView.locale());

        org.bukkit.command.CommandSender other = proxy(org.bukkit.command.CommandSender.class,
                (method, args) -> defaultValue(method));
        assertEquals(SenderKind.OTHER, new BukkitCommandSenderAdapter(Locale.US).adapt(other).kind());
    }

    @Test void playerArgumentsAreStronglyTypedAndUseBukkitPlayerParser() {
        var playerArgument = BukkitArguments.player("target");
        assertEquals("target", playerArgument.name());
        assertEquals(Player.class, playerArgument.valueType());
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        Object instance = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "toString" -> "test-proxy"; case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0]; default -> null;
            };
            return invocation.call(method.getName(), args == null ? new Object[0] : args);
        });
        return type.cast(instance);
    }
    private static Object defaultValue(String method) {
        return switch (method) {
            case "isOp", "isPermissionSet", "hasPermission", "isOnline", "isValid", "isSneaking", "isSprinting" -> false;
            case "getName", "getLocale", "getDisplayName" -> "test";
            case "getUniqueId" -> java.util.UUID.randomUUID();
            case "spigot" -> null;
            default -> null;
        };
    }
    @FunctionalInterface private interface Invocation { Object call(String method, Object[] args); }
}
