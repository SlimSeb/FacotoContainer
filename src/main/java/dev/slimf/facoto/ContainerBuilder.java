package dev.slimf.facoto;

import dev.slimf.facoto.internal.Registration;
import dev.slimf.facoto.internal.LifetimeScopeImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Assembles component registrations and builds an Container.
 * Mirrors Autofac's ContainerBuilder.
 *
 * <pre>{@code
 * var builder = new ContainerBuilder();
 *
 * builder.registerType(SqlUserRepository.class).as(IUserRepository.class).singleInstance();
 * builder.registerType(UserService.class).as(IUserService.class).instancePerLifetimeScope();
 * builder.registerInstance(config).asSelf();
 * builder.register(c -> new EmailSender(c.resolve(SmtpConfig.class))).as(IEmailSender.class);
 *
 * Container container = builder.build();
 * }</pre>
 */
public final class ContainerBuilder {

    private final List<RegistrationBuilder<?>> pending = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Registration methods
    // -------------------------------------------------------------------------

    /**
     * Registers a concrete type for constructor injection.
     * By default, the component is exposed as itself (AsSelf).
     * Use .as(Interface.class) to change the exposed service type.
     */
    public <T> RegistrationBuilder<T> registerType(Class<T> implementationType) {
        var rb = new RegistrationBuilder<T>(Registration.Builder.forType(implementationType));
        pending.add(rb);
        return rb;
    }

    /**
     * Registers a pre-existing instance.
     * The instance is always treated as a singleton (SingleInstance lifetime).
     * By default exposed as its own concrete type; use .as() to override.
     */
    @SuppressWarnings("unchecked")
    public <T> RegistrationBuilder<T> registerInstance(T instance) {
        var rb = new RegistrationBuilder<T>(Registration.Builder.forInstance(instance));
        pending.add(rb);
        return rb;
    }

    /**
     * Registers a component using a factory lambda.
     * The lambda receives an ComponentContext to resolve dependencies.
     * You MUST call .as() or .asSelf() — factory registrations have no default service type.
     *
     * <pre>{@code
     * builder.register(c -> new Foo(c.resolve(Bar.class))).as(IFoo.class);
     * }</pre>
     */
    public <T> RegistrationBuilder<T> register(Function<ComponentContext, T> factory) {
        var rb = new RegistrationBuilder<T>(Registration.Builder.forFactory(factory));
        pending.add(rb);
        return rb;
    }

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    /**
     * Builds the container from all accumulated registrations.
     * The ContainerBuilder should not be used after this call.
     */
    public Container build() {
        List<Registration> registrations = new ArrayList<>(pending.size());
        for (RegistrationBuilder<?> rb : pending) {
            registrations.add(rb.build());
        }
        return LifetimeScopeImpl.createRoot(registrations);
    }

    /**
     * Builds a list of registrations for use in child scopes.
     * Called by LifetimeScope.beginLifetimeScope(Consumer<ContainerBuilder>).
     */
    public List<Registration> buildRegistrations() {
        List<Registration> result = new ArrayList<>(pending.size());
        for (RegistrationBuilder<?> rb : pending) {
            result.add(rb.build());
        }
        return result;
    }
}
