package com.trickshotmlg.devkit.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceKeyTest {
    record Store(String value) { }

    @Test void serviceKeysAreTypedAndHaveStableLogicalNames() {
        ServiceKey<Store> first = ServiceKey.of("database.store", Store.class);
        ServiceKey<Store> equivalent = ServiceKey.of("database.store", Store.class);
        ServiceKey<String> incompatibleType = ServiceKey.of("database.store", String.class);

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertNotEquals(first, incompatibleType);
        assertEquals("database.store", first.name());
        assertEquals(Store.class, first.serviceType());
        assertThrows(IllegalArgumentException.class, () -> ServiceKey.of("", Store.class));
        assertThrows(NullPointerException.class, () -> ServiceKey.of("store", null));
    }

    @Test void builderRejectsDuplicateLogicalNamesEvenWhenTypesDiffer() {
        LifecycleBuilder builder = LifecycleDefinition.builder()
                .service(ServiceKey.of("store", Store.class), context -> new Store("one"));

        assertThrows(IllegalArgumentException.class,
                () -> builder.service(ServiceKey.of("store", String.class), context -> "two"));
    }

    @Test void definitionsAreImmutableAndBuilderCannotBeReused() {
        LifecycleBuilder builder = LifecycleDefinition.builder()
                .service(ServiceKey.of("store", Store.class), context -> new Store("one"));
        LifecycleDefinition definition = builder.build();

        assertEquals(java.util.List.of("store"), definition.registeredNames());
        assertThrows(IllegalStateException.class, builder::build);
        assertThrows(IllegalStateException.class,
                () -> builder.service(ServiceKey.of("other", String.class), context -> "two"));
    }
}
