# FacotoContainer

A Java dependency injection library with an API modelled after [Autofac](https://autofac.org/) for .NET.

## Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>dev.slimf.facoto</groupId>
    <artifactId>facoto-container</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick start

```java
var builder = new ContainerBuilder();

builder.registerType(SqlUserRepository.class).as(UserRepository.class).singleInstance();
builder.registerType(UserService.class).as(UserService.class);

Container container = builder.build();

UserService svc = container.resolve(UserService.class);
```

## Registering components

### By type — constructor injection

```java
builder.registerType(OrderService.class).as(OrderService.class);
```

FacotoContainer inspects the constructors of `OrderService` at resolve time and picks the one
with the most parameters it can satisfy (greedy strategy). All parameters are resolved
recursively from the container.

### By instance — pre-built object

```java
AppConfig config = loadConfig();
builder.registerInstance(config).asSelf();
```

Instances are always singletons regardless of any lifetime setting.

### By factory — lambda

```java
builder.register(c -> new HttpClient(c.resolve(HttpConfig.class)))
       .as(HttpClient.class)
       .singleInstance();
```

The lambda receives a `ComponentContext` you can use to resolve other services.

## Exposing a component as a service

### As an interface

```java
builder.registerType(SqlOrderRepository.class).as(OrderRepository.class);
```

### As itself

```java
builder.registerType(MetricsCollector.class).asSelf();
```

### As both

```java
builder.registerType(EmailSender.class)
       .as(MessageSender.class)
       .asSelf();
```

### As a named service

```java
builder.registerType(S3Storage.class).named("s3", Storage.class);
builder.registerType(LocalStorage.class).named("local", Storage.class);
```

```java
Storage s3    = container.resolveNamed("s3",    Storage.class);
Storage local = container.resolveNamed("local", Storage.class);
```

When multiple components are registered for the same type, the last one wins on a plain
`resolve()` call. All of them are returned by `resolveAll()`.

## Lifetimes

### Instance per dependency *(default)*

A new instance is created on every `resolve()` call.

```java
builder.registerType(RequestContext.class).as(RequestContext.class).instancePerDependency();
```

### Single instance

One instance for the lifetime of the root container.

```java
builder.registerType(DatabasePool.class).as(DatabasePool.class).singleInstance();
```

### Instance per lifetime scope

One instance per scope; child scopes each get their own.

```java
builder.registerType(UnitOfWork.class).as(UnitOfWork.class).instancePerLifetimeScope();
```

## Lifetime scopes

Scopes let you tie component lifetimes to a unit of work (e.g. an HTTP request).

```java
Container container = builder.build();

try (LifetimeScope scope = container.beginLifetimeScope()) {
    UnitOfWork uow = scope.resolve(UnitOfWork.class);
    // uow is scoped — the same instance is returned within this scope
} // scope closes here; any AutoCloseable components it owns are disposed
```

Child scopes can introduce additional registrations that shadow the parent:

```java
try (LifetimeScope scope = container.beginLifetimeScope(cb ->
        cb.registerType(TestMailer.class).as(Mailer.class))) {
    // TestMailer overrides whatever Mailer was registered in the parent
}
```

## Parameter overrides

Override a specific constructor parameter without creating a subclass or a factory lambda.

### By parameter type

```java
builder.registerType(ReportGenerator.class).asSelf()
       .withParameter(Locale.class, Locale.FRENCH);
```

### By parameter name

Requires compiling with `-parameters` (enabled by default in FacotoContainer's `pom.xml`).

```java
builder.registerType(Greeter.class).asSelf()
       .withParameter("greeting", "Bonjour");
```

## Resolving

```java
// Throws ComponentNotRegisteredException if not registered
OrderService svc = container.resolve(OrderService.class);

// Returns Optional.empty() instead of throwing
Optional<Plugin> plugin = container.resolveOptional(Plugin.class);

// Returns all registrations for a type (useful for plugin patterns)
Collection<Validator> validators = container.resolveAll(Validator.class);

// Check before resolving
if (container.isRegistered(FeatureFlag.class)) { ... }
if (container.isRegisteredWithName("legacy", Storage.class)) { ... }
```

## Disposal

Components that implement `AutoCloseable` are tracked and closed automatically:

- **Scoped / transient** components are disposed when the `LifetimeScope` that created them is closed.
- **Singleton** components are disposed when the root `Container` is closed.

Use try-with-resources to ensure timely disposal:

```java
try (Container container = builder.build()) {
    // use the container
} // all singletons disposed here
```

```java
try (LifetimeScope scope = container.beginLifetimeScope()) {
    // use the scope
} // scoped components disposed here
```
