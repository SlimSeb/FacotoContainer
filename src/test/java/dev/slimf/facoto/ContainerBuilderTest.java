package dev.slimf.facoto;

import org.junit.jupiter.api.*;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ContainerBuilderTest {

    // -----------------------------------------------------------------------
    // Sample interfaces and classes
    // -----------------------------------------------------------------------

    interface IAnimal { String speak(); }
    interface IVehicle { String drive(); }

    static class Dog implements IAnimal {
        @Override public String speak() { return "Woof"; }
    }

    static class Cat implements IAnimal {
        @Override public String speak() { return "Meow"; }
    }

    static class Car implements IVehicle {
        private final IAnimal passenger;
        public Car(IAnimal passenger) { this.passenger = passenger; }
        @Override public String drive() { return "Vroom, " + passenger.speak(); }
    }

    static class PetOwner {
        final IAnimal animal;
        public PetOwner(IAnimal animal) { this.animal = animal; }
    }

    static class MultiDepService {
        final IAnimal animal;
        final IVehicle vehicle;
        public MultiDepService(IAnimal animal, IVehicle vehicle) {
            this.animal = animal;
            this.vehicle = vehicle;
        }
    }

    // Tracks how many times it was instantiated
    static class Counter {
        private static final AtomicInteger count = new AtomicInteger(0);
        final int id = count.incrementAndGet();
        public static void reset() { count.set(0); }
    }

    // Self-referential (circular)
    interface IA {}
    interface IB {}
    static class A implements IA { public A(IB b) {} }
    static class B implements IB { public B(IA a) {} }

    // Disposable
    static class DisposableService implements AutoCloseable {
        boolean closed = false;
        @Override public void close() { closed = true; }
    }

    // Named parameter test
    static class Greeter {
        final String greeting;
        public Greeter(String greeting) { this.greeting = greeting; }
        public String greet(String name) { return greeting + ", " + name + "!"; }
    }

    // -----------------------------------------------------------------------
    // Basic resolution
    // -----------------------------------------------------------------------

    @Test
    void resolveTypeRegisteredAsSelf() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).asSelf();
        try (var container = builder.build()) {
            var dog = container.resolve(Dog.class);
            assertEquals("Woof", dog.speak());
        }
    }

    @Test
    void resolveTypeRegisteredAsInterface() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).as(IAnimal.class);
        try (var container = builder.build()) {
            var animal = container.resolve(IAnimal.class);
            assertEquals("Woof", animal.speak());
        }
    }

    @Test
    void resolveTypeRegisteredAsBothSelfAndInterface() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).as(IAnimal.class).asSelf();
        try (var container = builder.build()) {
            assertInstanceOf(Dog.class, container.resolve(IAnimal.class));
            assertInstanceOf(Dog.class, container.resolve(Dog.class));
        }
    }

    @Test
    void throwsWhenNotRegistered() {
        var container = new ContainerBuilder().build();
        assertThrows(ComponentNotRegisteredException.class, () -> container.resolve(Dog.class));
    }

    @Test
    void resolveOptionalReturnsEmptyWhenNotRegistered() {
        var container = new ContainerBuilder().build();
        assertTrue(container.resolveOptional(Dog.class).isEmpty());
    }

    @Test
    void resolveOptionalReturnsPresentWhenRegistered() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).asSelf();
        try (var container = builder.build()) {
            assertTrue(container.resolveOptional(Dog.class).isPresent());
        }
    }

    @Test
    void isRegisteredReturnsFalseWhenNotRegistered() {
        var container = new ContainerBuilder().build();
        assertFalse(container.isRegistered(Dog.class));
    }

    @Test
    void isRegisteredReturnsTrueWhenRegistered() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).asSelf();
        try (var container = builder.build()) {
            assertTrue(container.isRegistered(Dog.class));
        }
    }

    // -----------------------------------------------------------------------
    // Instance registration
    // -----------------------------------------------------------------------

    @Test
    void resolveRegisteredInstance() {
        var dog = new Dog();
        var builder = new ContainerBuilder();
        builder.registerInstance(dog).asSelf();
        try (var container = builder.build()) {
            assertSame(dog, container.resolve(Dog.class));
        }
    }

    @Test
    void registeredInstanceIsAlwaysSameReference() {
        var dog = new Dog();
        var builder = new ContainerBuilder();
        builder.registerInstance(dog).as(IAnimal.class);
        try (var container = builder.build()) {
            assertSame(container.resolve(IAnimal.class), container.resolve(IAnimal.class));
        }
    }

    // -----------------------------------------------------------------------
    // Factory registration
    // -----------------------------------------------------------------------

    @Test
    void resolveViaFactory() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).as(IAnimal.class);
        builder.register(c -> new Car(c.resolve(IAnimal.class))).as(IVehicle.class);
        try (var container = builder.build()) {
            var vehicle = container.resolve(IVehicle.class);
            assertEquals("Vroom, Woof", vehicle.drive());
        }
    }

    // -----------------------------------------------------------------------
    // Constructor injection
    // -----------------------------------------------------------------------

    @Test
    void constructorInjectionResolvesParameters() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).as(IAnimal.class);
        builder.registerType(PetOwner.class).asSelf();
        try (var container = builder.build()) {
            var owner = container.resolve(PetOwner.class);
            assertInstanceOf(Dog.class, owner.animal);
        }
    }

    @Test
    void constructorInjectionWithMultipleDependencies() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).as(IAnimal.class);
        builder.registerType(Car.class).as(IVehicle.class);
        builder.registerType(MultiDepService.class).asSelf();
        try (var container = builder.build()) {
            var svc = container.resolve(MultiDepService.class);
            assertInstanceOf(Dog.class, svc.animal);
            assertInstanceOf(Car.class, svc.vehicle);
        }
    }

    @Test
    void withTypedParameterOverridesResolution() {
        var dog = new Dog();
        var builder = new ContainerBuilder();
        builder.registerType(PetOwner.class).asSelf()
               .withParameter(IAnimal.class, dog);
        try (var container = builder.build()) {
            var owner = container.resolve(PetOwner.class);
            assertSame(dog, owner.animal);
        }
    }

    @Test
    void withNamedParameterOverridesResolution() {
        var builder = new ContainerBuilder();
        builder.registerType(Greeter.class).asSelf()
               .withParameter("greeting", "Hello");
        try (var container = builder.build()) {
            var greeter = container.resolve(Greeter.class);
            assertEquals("Hello, World!", greeter.greet("World"));
        }
    }

    // -----------------------------------------------------------------------
    // Lifetimes
    // -----------------------------------------------------------------------

    @Test
    void instancePerDependencyCreatesNewInstanceEachTime() {
        Counter.reset();
        var builder = new ContainerBuilder();
        builder.registerType(Counter.class).asSelf().instancePerDependency();
        try (var container = builder.build()) {
            var a = container.resolve(Counter.class);
            var b = container.resolve(Counter.class);
            assertNotSame(a, b);
        }
    }

    @Test
    void singleInstanceReturnsSameReference() {
        Counter.reset();
        var builder = new ContainerBuilder();
        builder.registerType(Counter.class).asSelf().singleInstance();
        try (var container = builder.build()) {
            var a = container.resolve(Counter.class);
            var b = container.resolve(Counter.class);
            assertSame(a, b);
        }
    }

    @Test
    void singleInstanceSharedAcrossChildScopes() {
        Counter.reset();
        var builder = new ContainerBuilder();
        builder.registerType(Counter.class).asSelf().singleInstance();
        try (var container = builder.build()) {
            Counter fromRoot = container.resolve(Counter.class);
            try (var scope = container.beginLifetimeScope()) {
                Counter fromScope = scope.resolve(Counter.class);
                assertSame(fromRoot, fromScope);
            }
        }
    }

    @Test
    void instancePerLifetimeScopeReturnsSameWithinScope() {
        Counter.reset();
        var builder = new ContainerBuilder();
        builder.registerType(Counter.class).asSelf().instancePerLifetimeScope();
        try (var container = builder.build()) {
            var a = container.resolve(Counter.class);
            var b = container.resolve(Counter.class);
            assertSame(a, b);
        }
    }

    @Test
    void instancePerLifetimeScopeDiffersAcrossScopes() {
        Counter.reset();
        var builder = new ContainerBuilder();
        builder.registerType(Counter.class).asSelf().instancePerLifetimeScope();
        try (var container = builder.build()) {
            Counter fromRoot = container.resolve(Counter.class);
            try (var scope = container.beginLifetimeScope()) {
                Counter fromScope = scope.resolve(Counter.class);
                assertNotSame(fromRoot, fromScope);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Named registrations
    // -----------------------------------------------------------------------

    @Test
    void namedRegistrationResolvesCorrectly() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).named("dog", IAnimal.class);
        builder.registerType(Cat.class).named("cat", IAnimal.class);
        try (var container = builder.build()) {
            assertInstanceOf(Dog.class, container.resolveNamed("dog", IAnimal.class));
            assertInstanceOf(Cat.class, container.resolveNamed("cat", IAnimal.class));
        }
    }

    @Test
    void isRegisteredWithNameReturnsTrueWhenRegistered() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).named("rex", IAnimal.class);
        try (var container = builder.build()) {
            assertTrue(container.isRegisteredWithName("rex", IAnimal.class));
            assertFalse(container.isRegisteredWithName("fido", IAnimal.class));
        }
    }

    @Test
    void throwsWhenNamedNotRegistered() {
        var container = new ContainerBuilder().build();
        assertThrows(ComponentNotRegisteredException.class,
                () -> container.resolveNamed("rex", IAnimal.class));
    }

    // -----------------------------------------------------------------------
    // ResolveAll
    // -----------------------------------------------------------------------

    @Test
    void resolveAllReturnsAllRegistrationsForType() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).as(IAnimal.class);
        builder.registerType(Cat.class).as(IAnimal.class);
        try (var container = builder.build()) {
            Collection<IAnimal> animals = container.resolveAll(IAnimal.class);
            assertEquals(2, animals.size());
        }
    }

    @Test
    void resolveAllReturnsEmptyCollectionWhenNotRegistered() {
        var container = new ContainerBuilder().build();
        assertTrue(container.resolveAll(IAnimal.class).isEmpty());
    }

    // -----------------------------------------------------------------------
    // Last-registration-wins
    // -----------------------------------------------------------------------

    @Test
    void lastRegistrationWinsForSingleResolve() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).as(IAnimal.class);
        builder.registerType(Cat.class).as(IAnimal.class);
        try (var container = builder.build()) {
            assertInstanceOf(Cat.class, container.resolve(IAnimal.class));
        }
    }

    // -----------------------------------------------------------------------
    // Child scopes with extra registrations
    // -----------------------------------------------------------------------

    @Test
    void childScopeInheritsParentRegistrations() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).as(IAnimal.class);
        try (var container = builder.build()) {
            try (var scope = container.beginLifetimeScope()) {
                assertInstanceOf(Dog.class, scope.resolve(IAnimal.class));
            }
        }
    }

    @Test
    void childScopeCanOverrideParentRegistration() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).as(IAnimal.class);
        try (var container = builder.build()) {
            try (var scope = container.beginLifetimeScope(cb ->
                    cb.registerType(Cat.class).as(IAnimal.class))) {
                // child's Cat overrides parent's Dog
                assertInstanceOf(Cat.class, scope.resolve(IAnimal.class));
            }
            // parent is unchanged
            assertInstanceOf(Dog.class, container.resolve(IAnimal.class));
        }
    }

    // -----------------------------------------------------------------------
    // Disposal
    // -----------------------------------------------------------------------

    @Test
    void scopedDisposablesAreClosedWithScope() {
        var builder = new ContainerBuilder();
        builder.registerType(DisposableService.class).asSelf().instancePerLifetimeScope();
        DisposableService instance;
        try (var container = builder.build()) {
            try (var scope = container.beginLifetimeScope()) {
                instance = scope.resolve(DisposableService.class);
                assertFalse(instance.closed);
            }
            assertTrue(instance.closed);
        }
    }

    @Test
    void singletonDisposableIsClosedWhenContainerCloses() {
        var builder = new ContainerBuilder();
        builder.registerType(DisposableService.class).asSelf().singleInstance();
        DisposableService instance;
        var container = builder.build();
        instance = container.resolve(DisposableService.class);
        assertFalse(instance.closed);
        container.close();
        assertTrue(instance.closed);
    }

    @Test
    void closedScopeThrowsOnResolve() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class).asSelf();
        var container = builder.build();
        container.close();
        assertThrows(IllegalStateException.class, () -> container.resolve(Dog.class));
    }

    // -----------------------------------------------------------------------
    // Circular dependency detection
    // -----------------------------------------------------------------------

    @Test
    void circularDependencyThrowsDependencyResolutionException() {
        var builder = new ContainerBuilder();
        builder.registerType(A.class).as(IA.class);
        builder.registerType(B.class).as(IB.class);
        try (var container = builder.build()) {
            assertThrows(DependencyResolutionException.class, () -> container.resolve(IA.class));
        }
    }

    // -----------------------------------------------------------------------
    // Default lifetime is InstancePerDependency
    // -----------------------------------------------------------------------

    @Test
    void defaultLifetimeIsInstancePerDependency() {
        Counter.reset();
        var builder = new ContainerBuilder();
        builder.registerType(Counter.class).asSelf();
        try (var container = builder.build()) {
            var a = container.resolve(Counter.class);
            var b = container.resolve(Counter.class);
            assertNotSame(a, b);
        }
    }

    // -----------------------------------------------------------------------
    // Default service type is AsSelf when no As() is called
    // -----------------------------------------------------------------------

    @Test
    void noExplicitAsDefaultsToAsSelf() {
        var builder = new ContainerBuilder();
        builder.registerType(Dog.class); // no .as() or .asSelf()
        try (var container = builder.build()) {
            assertInstanceOf(Dog.class, container.resolve(Dog.class));
        }
    }
}
