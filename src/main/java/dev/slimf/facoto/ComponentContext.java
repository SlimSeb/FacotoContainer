package dev.slimf.facoto;

import java.util.Collection;
import java.util.Optional;

/**
 * Provides access to component instances from the container.
 * Mirrors Autofac's ComponentContext.
 */
public interface ComponentContext {

    /**
     * Resolves a component by its service type.
     * If multiple registrations exist for the type, the last one wins (as in Autofac).
     *
     * @throws ComponentNotRegisteredException if no registration exists
     */
    <T> T resolve(Class<T> serviceType);

    /**
     * Resolves a named component.
     *
     * @throws ComponentNotRegisteredException if no registration exists
     */
    <T> T resolveNamed(String serviceName, Class<T> serviceType);

    /**
     * Attempts to resolve a component; returns empty if not registered.
     */
    <T> Optional<T> resolveOptional(Class<T> serviceType);

    /**
     * Attempts to resolve a named component; returns empty if not registered.
     */
    <T> Optional<T> resolveOptionalNamed(String serviceName, Class<T> serviceType);

    /**
     * Resolves all registered components for the given service type.
     */
    <T> Collection<T> resolveAll(Class<T> serviceType);

    /**
     * Returns true if a component for the given service type is registered.
     */
    <T> boolean isRegistered(Class<T> serviceType);

    /**
     * Returns true if a named component for the given service type is registered.
     */
    <T> boolean isRegisteredWithName(String serviceName, Class<T> serviceType);
}
