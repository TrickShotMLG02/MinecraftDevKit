package com.trickshotmlg.devkit.paper.lifecycle;

import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.registrar.RegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PaperBootstrapLifecycleTest {
    @Test void registersDatapackCallbackOnceAndDoesNotRetainBootstrapContext() {
        AtomicInteger registrations = new AtomicInteger();
        AtomicReference<Object> capturedHandler = new AtomicReference<>();
        LifecycleEventManager<BootstrapContext> manager = proxy(LifecycleEventManager.class, (method, args) -> {
            if (method.getName().equals("registerEventHandler") && args != null && args.length == 2) {
                registrations.incrementAndGet();
                capturedHandler.set(args[1]);
            }
            return null;
        });
        BootstrapContext context = proxy(BootstrapContext.class,
                (method, args) -> method.getName().equals("getLifecycleManager") ? manager : null);
        LifecycleEventType<BootstrapContext, RegistrarEvent<DatapackRegistrar>, ?> eventType =
                proxy(LifecycleEventType.class, (method, args) -> null);

        PaperBootstrapLifecycle helper = new PaperBootstrapLifecycle();
        AtomicInteger callbacks = new AtomicInteger();
        assertTrue(helper.register(context, eventType, ignored -> callbacks.incrementAndGet()));
        assertFalse(helper.register(context, eventType, ignored -> fail("duplicate handler must not replace the first")));
        assertTrue(helper.isDatapackDiscoveryRegistered());
        assertEquals(1, registrations.get());

        DatapackRegistrar registrar = proxy(DatapackRegistrar.class, (method, args) -> null);
        RegistrarEvent<DatapackRegistrar> event = proxy(RegistrarEvent.class,
                (method, args) -> method.getName().equals("registrar") ? registrar : null);
        invokeHandler(capturedHandler.get(), event);
        assertEquals(1, callbacks.get());
    }

    @Test void propagatesPaperRegistrationFailureAndAllowsExplicitRetry() {
        AtomicInteger calls = new AtomicInteger();
        LifecycleEventManager<BootstrapContext> manager = proxy(LifecycleEventManager.class, (method, args) -> {
            if (method.getName().equals("registerEventHandler") && calls.getAndIncrement() == 0) {
                throw new IllegalStateException("Paper rejected lifecycle handler");
            }
            return null;
        });
        BootstrapContext context = proxy(BootstrapContext.class,
                (method, args) -> method.getName().equals("getLifecycleManager") ? manager : null);
        LifecycleEventType<BootstrapContext, RegistrarEvent<DatapackRegistrar>, ?> eventType =
                proxy(LifecycleEventType.class, (method, args) -> null);
        PaperBootstrapLifecycle helper = new PaperBootstrapLifecycle();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> helper.register(context, eventType, ignored -> { }));
        assertEquals("Paper rejected lifecycle handler", failure.getMessage());
        assertFalse(helper.isDatapackDiscoveryRegistered());
        assertTrue(helper.register(context, eventType, ignored -> { }));
        assertEquals(2, calls.get());
    }

    private static void invokeHandler(Object handler, RegistrarEvent<DatapackRegistrar> event) {
        assertNotNull(handler);
        try {
            var method = java.util.Arrays.stream(handler.getClass().getMethods())
                    .filter(candidate -> candidate.getName().equals("run") && candidate.getParameterCount() == 1)
                    .findFirst().orElseThrow();
            method.invoke(handler, event);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not invoke captured Paper lifecycle handler", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "TestProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            return invocation.invoke(method, args);
        });
    }

    @FunctionalInterface private interface Invocation { Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable; }
}
