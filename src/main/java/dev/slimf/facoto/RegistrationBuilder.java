package dev.slimf.facoto;

import dev.slimf.facoto.internal.Registration;
import dev.slimf.facoto.internal.ServiceKey;

/**
 * Fluent builder for configuring how a single component is registered.
 * Mirrors Autofac's IRegistrationBuilder / RegistrationBuilder.
 *
 * <pre>{@code
 * builder.registerType(OrderService.class)
 *        .as(IOrderService.class)
 *        .singleInstance();
 *
 * builder.registerType(Logger.class)
 *        .asSelf()
 *        .instancePerLifetimeScope();
 *
 * builder.register(c -> new PaymentGateway(c.resolve(IHttpClient.class)))
 *        .as(IPaymentGateway.class)
 *        .singleInstance();
 * }</pre>
 *
 * @param <T> the implementation type (used for compile-time chaining; erased at runtime)
 */
public final class RegistrationBuilder<T> {

    private final Registration.Builder inner;

    RegistrationBuilder(Registration.Builder inner) {
        this.inner = inner;
    }

    // -------------------------------------------------------------------------
    // Service exposure
    // -------------------------------------------------------------------------

    /**
     * Exposes this component as the given service type.
     * Multiple calls add multiple exposures.
     * Calling this removes the implicit AsSelf default.
     */
    public RegistrationBuilder<T> as(Class<?> serviceType) {
        inner.services.add(new ServiceKey(serviceType));
        return this;
    }

    /**
     * Exposes this component as itself (its concrete type).
     * Useful when combined with as() to expose as both an interface and the concrete class.
     */
    public RegistrationBuilder<T> asSelf() {
        Class<?> self = switch (inner.sourceKind) {
            case TYPE -> inner.implementationType;
            case INSTANCE -> inner.instance.getClass();
            case FACTORY -> throw new IllegalStateException(
                    "asSelf() is not supported for factory registrations; use as(ConcreteType.class) instead.");
        };
        inner.services.add(new ServiceKey(self));
        return this;
    }

    /**
     * Exposes this component under a string name for the given service type.
     * Resolved via resolveNamed(name, serviceType).
     */
    public RegistrationBuilder<T> named(String name, Class<?> serviceType) {
        inner.services.add(new ServiceKey(serviceType, name));
        return this;
    }

    // -------------------------------------------------------------------------
    // Lifetime
    // -------------------------------------------------------------------------

    /**
     * A new instance is created every time the service is resolved.
     * This is the default.
     */
    public RegistrationBuilder<T> instancePerDependency() {
        inner.lifetime = Lifetime.INSTANCE_PER_DEPENDENCY;
        return this;
    }

    /**
     * A single instance is shared for the lifetime of the root container.
     */
    public RegistrationBuilder<T> singleInstance() {
        inner.lifetime = Lifetime.SINGLE_INSTANCE;
        return this;
    }

    /**
     * One instance per lifetime scope; child scopes each get their own instance.
     */
    public RegistrationBuilder<T> instancePerLifetimeScope() {
        inner.lifetime = Lifetime.INSTANCE_PER_LIFETIME_SCOPE;
        return this;
    }

    // -------------------------------------------------------------------------
    // Parameter overrides
    // -------------------------------------------------------------------------

    /**
     * Supplies a named constructor parameter value, bypassing resolution for that parameter.
     * Requires the project to be compiled with {@code -parameters} (configured in pom.xml).
     */
    public RegistrationBuilder<T> withParameter(String parameterName, Object value) {
        inner.namedParameters.put(parameterName, value);
        return this;
    }

    /**
     * Supplies a typed constructor parameter value.
     * When the constructor has a parameter of the given type, this value is used instead of resolving it.
     */
    public RegistrationBuilder<T> withParameter(Class<?> parameterType, Object value) {
        inner.typedParameters.put(parameterType, value);
        return this;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /** Seals this builder into an immutable Registration. Called by ContainerBuilder.build(). */
    Registration build() {
        return inner.build();
    }
}
