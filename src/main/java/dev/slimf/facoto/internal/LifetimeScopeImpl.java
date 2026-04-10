package dev.slimf.facoto.internal;

import dev.slimf.facoto.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Core implementation of Container and LifetimeScope.
 *
 * <p>Lifetime semantics:
 * <ul>
 *   <li>SINGLE_INSTANCE — cached in the root scope's singletonCache</li>
 *   <li>INSTANCE_PER_LIFETIME_SCOPE — cached in this scope's scopedCache</li>
 *   <li>INSTANCE_PER_DEPENDENCY — never cached; new instance on every resolve</li>
 * </ul>
 *
 * <p>Thread safety: caches use ConcurrentHashMap with computeIfAbsent, which is safe
 * for idempotent factories. Circular dependency detection uses a per-thread stack.
 */
public final class LifetimeScopeImpl implements Container {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /** All registrations visible in this scope (parent + own). */
    private final List<Registration> registrations;

    /**
     * Service-key → registrations lookup (last one registered wins for single-resolve,
     * all collected for resolveAll).
     */
    private final Map<ServiceKey, List<Registration>> byService;

    /** Singleton cache — only stored/read on the root scope. */
    private final Map<Registration, Object> singletonCache;

    /** Scoped instance cache — one entry per registration per scope. */
    private final Map<Registration, Object> scopedCache = new ConcurrentHashMap<>();

    /** AutoCloseable instances owned by this scope (to dispose on close). */
    private final List<AutoCloseable> ownedDisposables = Collections.synchronizedList(new ArrayList<>());

    /** Root scope reference (this == root when isRoot). */
    private final LifetimeScopeImpl root;

    private volatile boolean closed = false;

    /** Per-thread resolution stack for circular dependency detection. */
    private static final ThreadLocal<Deque<Registration>> RESOLVING_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    // -----------------------------------------------------------------------
    // Factory methods
    // -----------------------------------------------------------------------

    /** Creates the root container (Container). */
    public static LifetimeScopeImpl createRoot(List<Registration> registrations) {
        return new LifetimeScopeImpl(null, registrations, new ConcurrentHashMap<>());
    }

    /** Creates a child scope with its own registrations merged on top of the parent's. */
    private static LifetimeScopeImpl createChild(LifetimeScopeImpl parent,
                                                  List<Registration> childRegistrations) {
        // Child inherits all parent registrations; its own are appended (override parent for same key)
        List<Registration> merged = new ArrayList<>(parent.registrations);
        merged.addAll(childRegistrations);
        return new LifetimeScopeImpl(parent.root, merged, parent.root.singletonCache);
    }

    private LifetimeScopeImpl(LifetimeScopeImpl root,
                               List<Registration> registrations,
                               Map<Registration, Object> singletonCache) {
        this.root = (root == null) ? this : root;
        this.registrations = List.copyOf(registrations);
        this.singletonCache = singletonCache;
        this.byService = buildIndex(this.registrations);
    }

    // -----------------------------------------------------------------------
    // LifetimeScope — scoping
    // -----------------------------------------------------------------------

    @Override
    public LifetimeScope beginLifetimeScope() {
        checkNotClosed();
        return createChild(this, List.of());
    }

    @Override
    public LifetimeScope beginLifetimeScope(Consumer<ContainerBuilder> configurationAction) {
        checkNotClosed();
        var cb = new ContainerBuilder();
        configurationAction.accept(cb);
        return createChild(this, cb.buildRegistrations());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;

        // Dispose in reverse-creation order
        var copy = new ArrayList<>(ownedDisposables);
        Collections.reverse(copy);
        for (AutoCloseable disposable : copy) {
            try {
                disposable.close();
            } catch (Exception e) {
                // Match Autofac behaviour: swallow disposal exceptions (log if needed)
            }
        }
        ownedDisposables.clear();
    }

    // -----------------------------------------------------------------------
    // ComponentContext — resolution
    // -----------------------------------------------------------------------

    @Override
    public <T> T resolve(Class<T> serviceType) {
        checkNotClosed();
        Registration reg = findLastRegistration(new ServiceKey(serviceType));
        if (reg == null) throw new ComponentNotRegisteredException(serviceType);
        return serviceType.cast(resolveRegistration(reg));
    }

    @Override
    public <T> T resolveNamed(String serviceName, Class<T> serviceType) {
        checkNotClosed();
        Registration reg = findLastRegistration(new ServiceKey(serviceType, serviceName));
        if (reg == null) throw new ComponentNotRegisteredException(serviceName, serviceType);
        return serviceType.cast(resolveRegistration(reg));
    }

    @Override
    public <T> Optional<T> resolveOptional(Class<T> serviceType) {
        checkNotClosed();
        Registration reg = findLastRegistration(new ServiceKey(serviceType));
        if (reg == null) return Optional.empty();
        return Optional.of(serviceType.cast(resolveRegistration(reg)));
    }

    @Override
    public <T> Optional<T> resolveOptionalNamed(String serviceName, Class<T> serviceType) {
        checkNotClosed();
        Registration reg = findLastRegistration(new ServiceKey(serviceType, serviceName));
        if (reg == null) return Optional.empty();
        return Optional.of(serviceType.cast(resolveRegistration(reg)));
    }

    @Override
    public <T> Collection<T> resolveAll(Class<T> serviceType) {
        checkNotClosed();
        List<Registration> regs = byService.getOrDefault(new ServiceKey(serviceType), List.of());
        List<T> result = new ArrayList<>(regs.size());
        for (Registration reg : regs) {
            result.add(serviceType.cast(resolveRegistration(reg)));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public <T> boolean isRegistered(Class<T> serviceType) {
        return findLastRegistration(new ServiceKey(serviceType)) != null;
    }

    @Override
    public <T> boolean isRegisteredWithName(String serviceName, Class<T> serviceType) {
        return findLastRegistration(new ServiceKey(serviceType, serviceName)) != null;
    }

    // -----------------------------------------------------------------------
    // Core resolution logic
    // -----------------------------------------------------------------------

    private Object resolveRegistration(Registration reg) {
        return switch (reg.lifetime) {
            case SINGLE_INSTANCE -> singletonCache.computeIfAbsent(reg, r -> createTracked(r));
            case INSTANCE_PER_LIFETIME_SCOPE -> scopedCache.computeIfAbsent(reg, r -> createTracked(r));
            case INSTANCE_PER_DEPENDENCY -> createTracked(reg);
        };
    }

    private Object createTracked(Registration reg) {
        Deque<Registration> stack = RESOLVING_STACK.get();

        if (stack.contains(reg)) {
            String chain = stack.stream()
                    .map(r -> r.implementationType != null ? r.implementationType.getSimpleName() : "<factory>")
                    .collect(Collectors.joining(" -> "));
            throw new DependencyResolutionException(
                    "Circular dependency detected: " + chain + " -> (itself)");
        }

        stack.push(reg);
        try {
            Object instance = reg.create(this);
            if (instance instanceof AutoCloseable ac && reg.lifetime != Lifetime.SINGLE_INSTANCE) {
                // Track disposables owned by this scope.
                // Singletons are owned/disposed by the root container instead.
                ownedDisposables.add(ac);
            } else if (instance instanceof AutoCloseable ac && reg.lifetime == Lifetime.SINGLE_INSTANCE) {
                root.ownedDisposables.add(ac);
            }
            return instance;
        } finally {
            stack.pop();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Registration findLastRegistration(ServiceKey key) {
        List<Registration> regs = byService.get(key);
        if (regs == null || regs.isEmpty()) return null;
        return regs.get(regs.size() - 1); // last-registered wins, as in Autofac
    }

    private static Map<ServiceKey, List<Registration>> buildIndex(List<Registration> registrations) {
        Map<ServiceKey, List<Registration>> index = new LinkedHashMap<>();
        for (Registration reg : registrations) {
            for (ServiceKey key : reg.services) {
                index.computeIfAbsent(key, k -> new ArrayList<>()).add(reg);
            }
        }
        return Collections.unmodifiableMap(index);
    }

    private void checkNotClosed() {
        if (closed) throw new IllegalStateException("This lifetime scope has been closed.");
    }
}
