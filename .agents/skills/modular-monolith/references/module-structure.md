# Module Structure Reference

This file explains how Spring Modulith derives an application module model from
a Spring Boot codebase, how to shape that model, and how to verify it.

## Contents

- [What an application module is](#what-an-application-module-is)
- [Inspecting the model](#inspecting-the-model)
- [Module arrangements](#module-arrangements)
  - [Simple application modules](#simple-application-modules)
  - [Advanced application modules](#advanced-application-modules)
  - [Nested application modules](#nested-application-modules)
  - [Open application modules](#open-application-modules)
  - [Explicit application module dependencies](#explicit-application-module-dependencies)
  - [Named interfaces](#named-interfaces)
  - [Kotlin package metadata](#kotlin-package-metadata)
- [Customizing the arrangement](#customizing-the-arrangement)
  - [`@Modulithic`](#modulithic)
  - [Detection strategies](#detection-strategies)
  - [Contributing modules from other packages](#contributing-modules-from-other-packages)
  - [Custom named-interface detection](#custom-named-interface-detection)
- [Verification](#verification)
  - [Handling known violations temporarily](#handling-known-violations-temporarily)
- [Breaking cycles](#breaking-cycles)
- [Shared kernel guidance](#shared-kernel-guidance)
- [Design heuristics](#design-heuristics)
- [Package layout examples](#package-layout-examples)
- [Source](#source)

## What an application module is

An application module is a unit of functionality with:

- a **provided interface** — Spring beans and application events the module
  exposes,
- **internal implementation** — types other modules must not access,
- a **required interface** — Spring bean dependencies, events listened to, and
  configuration properties the module needs.

Spring Modulith primarily derives modules from the Java package structure. There
are several arrangements, from simple to advanced; start simple and move to the
more explicit forms only when needed.

## Inspecting the model

`spring-modulith-core` contains `ApplicationModules`:

```java
var modules = ApplicationModules.of(Application.class);
modules.forEach(System.out::println);
```

The model is derived from the package the application class resides in. The
console output shows each module, its base package, Spring beans, and their
visibility.

To exclude classes or packages from inspection:

```java
ApplicationModules.of(Application.class,
    JavaClass.Predicates.resideInAPackage("com.example.db")).verify();
```

ArchUnit `PackageMatcher` patterns apply:

- `com.example.db` — exactly that package,
- `com.example.db..` — that package and all sub-packages,
- `..example..` — any package containing `example` as a segment.

## Module arrangements

### Simple application modules

The **main package** is the package containing the `@SpringBootApplication`
class. Each direct sub-package of the main package is an application module
package by default.

If a module package has no sub-packages, it is a **simple** module. The module
API is all public types in that package. Package-private types are hidden from
other packages, so Java visibility does the enforcement.

```text
example
├── Application.java
└── inventory
    ├── InventoryManagement.java          # public = API
    └── SomethingInventoryInternal.java   # package-private = hidden
```

### Advanced application modules

If a module package has sub-packages, the base package is the API package. Every
sub-package (such as `internal`) is internal: code from other modules must not
reference it.

```text
example
├── Application.java
├── inventory
│   ├── InventoryManagement.java          # API
│   └── SomethingInventoryInternal.java
├── order
│   ├── OrderManagement.java              # API
│   └── internal
│       └── SomethingOrderInternal.java   # internal
```

Types in internal packages may need to be `public` so other types in the same
module can use them. The compiler then cannot stop other modules from
referencing them; verification does.

### Nested application modules

Since 1.3, a module can contain nested modules. Annotate the nested package with
`@ApplicationModule` (typically on `package-info.java`) to make it a nested
module.

```text
example
├── Application.java
├── inventory
│   ├── InventoryManagement.java
│   ├── SomethingInventoryInternal.java
│   ├── internal
│   │   └── SomethingInventoryInternal.java
│   └── nested
│       ├── package-info.java            # @ApplicationModule
│       ├── NestedApi.java
│       └── internal
│           └── NestedInternal.java
└── order
    ├── OrderManagement.java
    └── SomethingOrderInternal.java
```

Access rules for nested modules:

- Code in a nested module is only available from its parent module or sibling
  nested modules.
- Code in a nested module can access parent module code, including internals.
- Nested modules can access exposed types of top-level modules.

### Open application modules

A closed module exposes only its base package plus explicitly declared named
interfaces. For legacy code where hiding every sub-package is impractical, make
the module **open**:

```java
@org.springframework.modulith.ApplicationModule(type = Type.OPEN)
package example.inventory;
```

Effects:

- other modules may access internal types,
- all types, including sub-package types, are added to the unnamed named
  interface unless explicitly assigned to a named interface.

Use open modules only while migrating an existing codebase. In a fully
modularized application they usually indicate sub-optimal packaging.

### Explicit application module dependencies

A module can declare which modules it may depend on. `allowedDependencies` is a
`String[]`:

```java
@org.springframework.modulith.ApplicationModule(allowedDependencies = "order")
package example.inventory;
```

Multiple dependencies and named interfaces:

```java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = { "order", "billing :: spi" })
package example.inventory;
```

Semantics:

- When `allowedDependencies` is present, dependencies on other application
  modules that are not listed are rejected.
- Dependencies on code not assigned to any module are still allowed.
- Declaring an empty array (`allowedDependencies = {}`) allows no dependencies
  on other modules.
- Leaving the attribute at its default does not restrict dependencies.
- Shared modules registered via `@Modulithic(sharedModules = ...)` are always
  allowed.
- Names are local module names unless
  `@Modulithic(useFullyQualifiedModuleNames = true)` is set.
- Named interfaces are referenced with `::`, e.g. `module::API` (spaces around
  `::` are accepted).

### Named interfaces

The base package is the default API. To expose additional packages, annotate
their `package-info.java` with `@NamedInterface`:

```java
@org.springframework.modulith.NamedInterface("spi")
package example.order.spi;
```

Other modules can then depend on the `spi` named interface:

```java
@org.springframework.modulith.ApplicationModule(allowedDependencies = "order :: spi")
package example.inventory;
```

Use `*` to allow all declared named interfaces of a module:

```java
@org.springframework.modulith.ApplicationModule(allowedDependencies = "order :: *")
package example.inventory;
```

Interface selection is precise:

- `allowedDependencies = "order"` allows the `order` base package (the unnamed
  interface) only.
- `allowedDependencies = "order :: spi"` allows the `spi` named interface only,
  not `OrderManagement` in the base package.
- `allowedDependencies = "order :: *"` allows all explicitly declared named
  interfaces; combine it with `"order"` if the base package is also needed.
- If a module declares no `allowedDependencies`, both the base package and its
  named interfaces are accessible.
- A named interface does not make the rest of the module internal package tree
  public. It only exposes that specific package (and its types).

### Kotlin package metadata

Kotlin has no `package-info.java`. Put the annotation on a type in the package
and combine it with `@PackageInfo`:

```kotlin
package example.order.spi

import org.springframework.modulith.PackageInfo
import org.springframework.modulith.NamedInterface

@PackageInfo
@NamedInterface("spi")
class ModuleMetadata {}
```

## Customizing the arrangement

### `@Modulithic`

Put `@Modulithic` on the main application class to configure system-wide
behavior:

```java
@Modulithic
@SpringBootApplication
class MyApplication {
  public static void main(String... args) {
    SpringApplication.run(MyApplication.class, args);
  }
}
```

Attributes:

| Attribute            | Purpose                                                         |
| -------------------- | --------------------------------------------------------------- |
| `systemName`         | Human-readable name used in generated documentation             |
| `sharedModules`      | Modules always included in application module integration tests |
| `additionalPackages` | Additional root packages to trigger module detection            |

### Detection strategies

Default detection uses direct sub-packages of the main package. To only consider
explicitly annotated packages:

```properties
spring.modulith.detection-strategy=explicitly-annotated
```

Annotate modules with Spring Modulith's `@ApplicationModule` or jMolecules'
`@Module`.

For a fully custom strategy, implement `ApplicationModuleDetectionStrategy`:

```java
class CustomApplicationModuleDetectionStrategy implements ApplicationModuleDetectionStrategy {

  @Override
  public Stream<JavaPackage> getModuleBasePackages(JavaPackage basePackage) {
    // select module base packages
  }
}
```

Register it with:

```properties
spring.modulith.detection-strategy=example.CustomApplicationModuleDetectionStrategy
```

If you use runtime features (initializers, actuator, observability), move custom
detection into production sources and declare `spring-modulith-core` as a
compile-time dependency.

### Contributing modules from other packages

`@Modulithic(additionalPackages = ...)` works when the packages are known up
front. For external contributions, implement `ApplicationModuleSourceFactory`
and register it in `META-INF/spring.factories`:

```text
org.springframework.modulith.core.ApplicationModuleSourceFactory=example.CustomApplicationModuleSourceFactory
```

The factory can return root packages for detection and/or explicit module base
packages.

### Custom named-interface detection

Implement `detectNamedInterfaces(...)` on a custom
`ApplicationModuleDetectionStrategy`:

```java
@Override
public NamedInterfaces detectNamedInterfaces(JavaPackage basePackage,
    ApplicationModuleInformation information) {

  return NamedInterfaces.builder()
      .recursive()
      .matching("api")
      .build();
}
```

The builder always includes the unnamed named interface for public types in the
base package, because modules require it.

## Verification

`ApplicationModules.verify()` enforces:

1. **No cycles** between application modules — dependencies must form a directed
   acyclic graph.
2. **Efferent access via API packages only** — references to internal packages
   are rejected. Access into open module internals is allowed.
3. **Explicitly allowed dependencies only** — if `allowedDependencies` is
   configured, other module dependencies are rejected.

```java
ApplicationModules.of(Application.class).verify();
```

For more control, use `detectViolations()`:

```java
ApplicationModules.of(Application.class)
    .detectViolations()
    .filter(violation -> ...)
    .throwIfPresent();
```

`verify(...)` and `detectViolations(...)` accept `VerificationOptions` to
disable defaults or add verifications such as jMolecules architecture rules:

```java
var hexagonal = JMoleculesArchitectureRules.ensureHexagonal(VerificationDepth.STRICT);
var options = VerificationOptions.defaults().withAdditionalVerifications(hexagonal);

ApplicationModules.of(Application.class).verify(options);
```

### Handling known violations temporarily

`detectViolations()` returns a `Violations` instance. Filter narrowly, document
why, and keep the filter short-lived:

```java
ApplicationModules.of(Application.class)
    .detectViolations()
    .filter(violation -> isTrackedLegacyViolation(violation))
    .throwIfPresent();
```

`verify()` runs once per `ApplicationModules` instance; `detectViolations()`
runs every time, which is useful when you need to inspect the violations. Do not
replace verification with a global disable. Track the suppressed violation with
an issue and remove the filter when the refactoring lands.

## Breaking cycles

A cycle means the dependency graph is not acyclic. Events remove call coupling
but not necessarily type coupling, so choose the pattern deliberately.

### 1. Invert the notification

If B calls A only to tell it that something happened, move the fact into B's API
and let A listen:

```text
Before: A -> B (call) and B -> A (call)
After:  A -> B (A listens to B's event)
```

B publishes `SomethingHappened` from its base package; A has an
`@ApplicationModuleListener`. B no longer knows A.

### 2. Extract a shared contract

If both modules need the same event types, value objects, or interfaces, put
them in a neutral shared module:

```text
A -> shared <- B
```

The shared module must not depend on A or B. Keep it small and stable; otherwise
it becomes a `common` dumping ground.

### 3. Move orchestration to a coordinator

If A and B must both be invoked, add a coordinator that depends on both:

```text
coordinator -> A
coordinator -> B
```

A and B do not depend on each other. The coordinator owns the use case; A and B
own their capabilities.

### 4. Use a port for a synchronous callback

If B needs a synchronous result from A, define a small interface in B's API and
have A implement it. B calls its own interface, so B no longer depends on A; A
depends on B, which is the direction that already exists. This keeps the call
synchronous while making the graph one-way.

```java
// billing (the caller) owns the port
package example.billing;

public interface OrderLookup {
    boolean isPaid(UUID orderId);
}

// order (the provider) implements it
package example.order.internal;

class OrderAdapter implements OrderLookup { ... }
```

The port belongs to the caller, not the provider. That is what removes the
back-edge.

### 5. Merge or re-split

If the modules cannot be described independently, they may be one capability.
Merge them, or split along a different axis (for example, by aggregate rather
than by noun).

### 6. Watch for mutual event consumption

This still creates a cycle:

```text
A listens to B's event  => A -> B
B listens to A's event  => B -> A
```

Fix it with shared event contracts (pattern 2), one-way interaction (pattern 1),
or a coordinator (pattern 3).

## Shared kernel guidance

A shared module is appropriate for stable, domain-neutral concepts that multiple
modules genuinely need: `Money`, `CustomerId`, shared event contracts, or a
small set of interfaces. It is not appropriate for feature logic, repositories,
or "misc" utilities.

Rules:

- The shared module depends on no feature module.
- Feature modules depend on it, never the reverse.
- Keep the API small and stable; shared code is the hardest to change.
- Prefer IDs and primitives over types owned by feature modules.
- If the shared module starts accumulating unrelated code, split it by concept.

## Design heuristics

- A module should be describable as a business capability without "and".
- A module should own its data. If two modules need the same table, the boundary
  is wrong.
- Public API should be small: a handful of services, records, events, and
  exceptions.
- Internal packages can be large and change freely; API changes have consumers.
- Prefer one-way dependencies. If you need a cycle, use an event to invert one
  direction.
- Avoid technical layers at the top level. `controller`, `service`,
  `repository`, and `entity` belong inside a module.
- Shared code should be a deliberately small module with a stable API, not a
  `util` package.
- Use `@NamedInterface` for SPI-style extension points, not for exposing
  internal implementation.
- Declare `allowedDependencies` for modules whose dependency set should stay
  intentionally narrow.
- Add the verification test early. It is the executable definition of your
  architecture.

## Package layout examples

Feature-oriented layout:

```text
com.acme.shop
├── ShopApplication.java
├── catalog
│   ├── CatalogManagement.java
│   ├── Product.java
│   ├── ProductAdded.java
│   └── internal
│       ├── CatalogRepository.java
│       └── CatalogConfiguration.java
├── ordering
│   ├── OrderManagement.java
│   ├── OrderCompleted.java
│   └── internal
│       ├── OrderRepository.java
│       └── OrderCompletedListener.java
├── inventory
│   ├── InventoryManagement.java
│   ├── StockShort.java
│   └── internal
│       ├── InventoryRepository.java
│       └── OrderCompletedListener.java
└── shared
    ├── Money.java
    └── internal
        └── MoneyConverter.java
```

Named interface layout:

```text
com.acme.shop.ordering
├── OrderManagement.java              # unnamed API
├── OrderCompleted.java
├── spi
│   ├── package-info.java             # @NamedInterface("spi")
│   └── OrderLookup.java
└── internal
    └── OrderRepository.java
```

## Source

- https://docs.spring.io/spring-modulith/reference/fundamentals.html
- https://docs.spring.io/spring-modulith/reference/verification.html
