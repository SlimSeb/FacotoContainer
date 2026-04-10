package dev.slimf.facoto;

/**
 * Controls how instances are shared within a container or scope.
 * Mirrors Autofac's lifetime semantics.
 */
public enum Lifetime {

    /**
     * A new instance is created every time the service is resolved.
     * Equivalent to Autofac's InstancePerDependency (the default).
     */
    INSTANCE_PER_DEPENDENCY,

    /**
     * A single instance is shared for the lifetime of the root container.
     * Equivalent to Autofac's SingleInstance.
     */
    SINGLE_INSTANCE,

    /**
     * A single instance is shared within a lifetime scope.
     * Child scopes get their own instance.
     * Equivalent to Autofac's InstancePerLifetimeScope.
     */
    INSTANCE_PER_LIFETIME_SCOPE
}
