package dev.slimf.facoto;

import java.util.function.Consumer;

/**
 * A nested container with its own instance cache for scoped components.
 * Implements AutoCloseable to dispose tracked instances when the scope ends.
 * Mirrors Autofac's LifetimeScope.
 */
public interface LifetimeScope extends ComponentContext, AutoCloseable {

    /**
     * Creates a new child lifetime scope that inherits all registrations.
     */
    LifetimeScope beginLifetimeScope();

    /**
     * Creates a new child lifetime scope and applies additional registrations
     * scoped to that child via the provided configuration action.
     */
    LifetimeScope beginLifetimeScope(Consumer<ContainerBuilder> configurationAction);

    /**
     * Closes this scope and disposes all AutoCloseable instances it owns.
     * Does not throw a checked exception (overrides AutoCloseable).
     */
    @Override
    void close();
}
