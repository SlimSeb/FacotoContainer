package dev.slimf.facoto;

/**
 * The root dependency injection container.
 * Built by ContainerBuilder; itself a lifetime scope.
 * Mirrors Autofac's Container.
 */
public interface Container extends LifetimeScope {
    // Inherits all resolution and scoping capabilities from LifetimeScope.
    // The root container acts as its own lifetime scope (singleton cache lives here).
}
