package dev.slimf.facoto.internal;

import dev.slimf.facoto.DependencyResolutionException;
import dev.slimf.facoto.ComponentContext;
import dev.slimf.facoto.Lifetime;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Function;

/**
 * Holds all data for a single component registration.
 * Instantiated by RegistrationBuilder and sealed when the container is built.
 */
public final class Registration {

    public enum SourceKind { TYPE, INSTANCE, FACTORY }

    // --- Source ---
    public final SourceKind sourceKind;
    public final Class<?> implementationType;           // TYPE
    public final Object instance;                       // INSTANCE
    public final Function<ComponentContext, ?> factory; // FACTORY

    // --- Services this registration is exposed as ---
    public final List<ServiceKey> services;

    // --- Lifetime ---
    public final Lifetime lifetime;

    // --- Parameter overrides ---
    // Named parameters: parameter name -> value
    public final Map<String, Object> namedParameters;
    // Typed parameters: parameter type -> value (first match wins)
    public final Map<Class<?>, Object> typedParameters;

    private Registration(Builder b) {
        this.sourceKind = b.sourceKind;
        this.implementationType = b.implementationType;
        this.instance = b.instance;
        this.factory = b.factory;
        this.lifetime = b.lifetime;
        this.namedParameters = Collections.unmodifiableMap(new LinkedHashMap<>(b.namedParameters));
        this.typedParameters = Collections.unmodifiableMap(new LinkedHashMap<>(b.typedParameters));

        List<ServiceKey> resolved;
        if (b.services.isEmpty()) {
            // Default: expose as the implementation type itself (AsSelf behaviour)
            resolved = List.of(new ServiceKey(implementationClass()));
        } else {
            resolved = Collections.unmodifiableList(new ArrayList<>(b.services));
        }
        this.services = resolved;
    }

    private Class<?> implementationClass() {
        return switch (sourceKind) {
            case TYPE -> implementationType;
            case INSTANCE -> instance.getClass();
            case FACTORY -> throw new IllegalStateException(
                    "Factory registrations must explicitly declare services via as() or asSelf().");
        };
    }

    /**
     * Creates an instance of this component using the given resolution context.
     * Does NOT apply lifetime caching — that is the scope's responsibility.
     */
    public Object create(ComponentContext ctx) {
        return switch (sourceKind) {
            case INSTANCE -> instance;
            case FACTORY -> factory.apply(ctx);
            case TYPE -> constructViaReflection(ctx);
        };
    }

    private Object constructViaReflection(ComponentContext ctx) {
        Constructor<?> ctor = findBestConstructor(ctx);
        ctor.setAccessible(true);
        Object[] args = resolveArguments(ctor, ctx);
        try {
            return ctor.newInstance(args);
        } catch (Exception e) {
            throw new DependencyResolutionException(
                    "Failed to instantiate '" + implementationType.getName() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Picks the constructor with the most parameters whose dependencies can all be satisfied.
     * Uses getDeclaredConstructors() to support non-public constructors (e.g. package-private).
     * Mirrors Autofac's "greedy" constructor selection strategy.
     */
    private Constructor<?> findBestConstructor(ComponentContext ctx) {
        Constructor<?>[] ctors = implementationType.getDeclaredConstructors();
        if (ctors.length == 0) {
            throw new DependencyResolutionException(
                    "No constructors found on '" + implementationType.getName() + "'.");
        }
        return Arrays.stream(ctors)
                .sorted(Comparator.comparingInt((Constructor<?> c) -> c.getParameterCount()).reversed())
                .filter(c -> canSatisfy(c, ctx))
                .findFirst()
                .orElseThrow(() -> new DependencyResolutionException(
                        "No constructor on '" + implementationType.getName() + "' could be satisfied. " +
                        "Check that all dependencies are registered."));
    }

    private boolean canSatisfy(Constructor<?> ctor, ComponentContext ctx) {
        for (Parameter p : ctor.getParameters()) {
            if (namedParameters.containsKey(p.getName())) continue;
            if (typedParameters.containsKey(p.getType())) continue;
            if (!ctx.isRegistered(p.getType())) return false;
        }
        return true;
    }

    private Object[] resolveArguments(Constructor<?> ctor, ComponentContext ctx) {
        Parameter[] params = ctor.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            if (namedParameters.containsKey(p.getName())) {
                args[i] = namedParameters.get(p.getName());
            } else if (typedParameters.containsKey(p.getType())) {
                args[i] = typedParameters.get(p.getType());
            } else {
                args[i] = ctx.resolve(p.getType());
            }
        }
        return args;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {

        public SourceKind sourceKind;
        public Class<?> implementationType;
        public Object instance;
        public Function<ComponentContext, ?> factory;
        public Lifetime lifetime = Lifetime.INSTANCE_PER_DEPENDENCY;
        public final List<ServiceKey> services = new ArrayList<>();
        public final Map<String, Object> namedParameters = new LinkedHashMap<>();
        public final Map<Class<?>, Object> typedParameters = new LinkedHashMap<>();

        public static Builder forType(Class<?> type) {
            Builder b = new Builder();
            b.sourceKind = SourceKind.TYPE;
            b.implementationType = type;
            return b;
        }

        public static Builder forInstance(Object instance) {
            Builder b = new Builder();
            b.sourceKind = SourceKind.INSTANCE;
            b.instance = instance;
            b.lifetime = Lifetime.SINGLE_INSTANCE; // instances are always singletons
            return b;
        }

        public static Builder forFactory(Function<ComponentContext, ?> factory) {
            Builder b = new Builder();
            b.sourceKind = SourceKind.FACTORY;
            b.factory = factory;
            return b;
        }

        public Registration build() {
            return new Registration(this);
        }
    }
}
