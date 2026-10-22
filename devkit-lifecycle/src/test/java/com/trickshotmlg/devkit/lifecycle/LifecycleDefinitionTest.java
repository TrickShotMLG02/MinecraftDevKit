package com.trickshotmlg.devkit.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleDefinitionTest {
    record Value(String value) { }

    @Test void topologicalOrderUsesRegistrationOrderForNewlyReadyNodes() {
        ServiceKey<Value> database = ServiceKey.of("database", Value.class);
        ServiceKey<Value> config = ServiceKey.of("config", Value.class);
        ComponentKey listener = ComponentKey.of("listener");
        ServiceKey<Value> gui = ServiceKey.of("gui", Value.class);
        LifecycleDefinition definition = LifecycleDefinition.builder()
                .service(gui, context -> new Value("gui"), listener)
                .service(database, context -> new Value("db"), config)
                .service(config, context -> new Value("config"))
                .component(listener, context -> { }, context -> { }, database)
                .build();

        assertEquals(java.util.List.of("config", "database", "listener", "gui"), definition.startupOrder());
        assertEquals(java.util.List.of("gui", "database", "config", "listener"), definition.registeredNames());
    }

    @Test void rejectsMissingSelfAndIncompatibleDependenciesAtBuildTime() {
        ServiceKey<Value> service = ServiceKey.of("service", Value.class);
        ServiceKey<String> wrongType = ServiceKey.of("service", String.class);

        assertTrue(assertThrows(IllegalArgumentException.class, () -> LifecycleDefinition.builder()
                .service(service, context -> new Value("x"), ServiceKey.of("missing", Value.class)).build())
                .getMessage().contains("missing dependency 'missing'"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LifecycleDefinition.builder()
                .service(service, context -> new Value("x"), service).build())
                .getMessage().contains("cannot depend on itself"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LifecycleDefinition.builder()
                .service(service, context -> new Value("x"))
                .service(ServiceKey.of("consumer", Value.class), context -> new Value("y"), wrongType)
                .build()).getMessage().contains("incompatible key type"));
    }

    @Test void reportsDirectAndIndirectCyclesWithAReadablePath() {
        ServiceKey<Value> a = ServiceKey.of("A", Value.class);
        ServiceKey<Value> b = ServiceKey.of("B", Value.class);
        ServiceKey<Value> c = ServiceKey.of("C", Value.class);

        IllegalArgumentException direct = assertThrows(IllegalArgumentException.class, () -> LifecycleDefinition.builder()
                .service(a, context -> new Value("a"), b)
                .service(b, context -> new Value("b"), a).build());
        assertTrue(direct.getMessage().contains("A -> B -> A"));

        IllegalArgumentException indirect = assertThrows(IllegalArgumentException.class, () -> LifecycleDefinition.builder()
                .service(a, context -> new Value("a"), c)
                .service(b, context -> new Value("b"), a)
                .service(c, context -> new Value("c"), b)
                .build());
        assertTrue(indirect.getMessage().contains("A -> C -> B -> A"));
    }
}
