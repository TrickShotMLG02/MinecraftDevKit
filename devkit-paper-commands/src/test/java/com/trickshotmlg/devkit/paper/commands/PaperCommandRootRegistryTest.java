package com.trickshotmlg.devkit.paper.commands;

import com.trickshotmlg.devkit.commands.CommandDefinition;
import com.trickshotmlg.devkit.commands.CommandRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PaperCommandRootRegistryTest {
    @Test void rejectsRepeatedPrimaryRootsAndAliasesBeforeRegistration() {
        PaperCommandRootRegistry registry = new PaperCommandRootRegistry();
        AtomicInteger calls = new AtomicInteger();
        CommandDefinition first = definition("devkit", "dk");
        registry.register(first, calls::incrementAndGet);

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(definition("other", "DK"), calls::incrementAndGet));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(first, calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    @Test void failedCloudSubmissionDoesNotReserveRoots() {
        PaperCommandRootRegistry registry = new PaperCommandRootRegistry();
        CommandDefinition definition = definition("devkit", "dk");
        assertThrows(IllegalStateException.class, () -> registry.register(definition,
                () -> { throw new IllegalStateException("injected submission failure"); }));
        AtomicInteger calls = new AtomicInteger();
        assertDoesNotThrow(() -> registry.register(definition, calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    @Test void acceptsIndependentRootsAndCaseInsensitiveIdentity() {
        PaperCommandRootRegistry registry = new PaperCommandRootRegistry();
        registry.register(definition("alpha", "a"), () -> { });
        assertDoesNotThrow(() -> registry.register(definition("beta", "b"), () -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(definition("A", "other"), () -> fail("must not submit duplicate")));
    }

    private static CommandDefinition definition(String root, String alias) {
        return CommandRegistry.builder().root(root, command -> command.aliases(alias).execute(context -> { })).build();
    }
}
