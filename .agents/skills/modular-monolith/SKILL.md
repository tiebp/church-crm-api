---
name: modular-monolith
description:
  Develop and evolve features in a Spring Modulith / modular monolith codebase.
  Use this skill whenever the user asks to add, change, refactor, review, or
  test functionality in a Spring Boot application that uses Spring Modulith or
  is organized as a modular monolith; when they mention module boundaries,
  bounded contexts, cross-module dependencies, module APIs or named interfaces,
  domain events between modules, ApplicationModules.verify(),
  @ApplicationModuleTest, module documentation, or preventing a big ball of mud;
  and when deciding where a new feature belongs or how modules should
  communicate. Trigger even if the user only says "modules" or "modular
  architecture" in a Spring context, because architectural drift is cheap to
  prevent and expensive to fix.
---

# Developing in a Modular Monolith with Spring Modulith

Spring Modulith is an opinionated toolkit for building domain-driven, modular
Spring Boot applications. It turns a package structure into an enforceable
module model: each module has a public API, hidden internals, and explicit
dependencies. The goal of this skill is to keep that model intact while you add
or change features.

The most important rule: **a feature belongs to a business capability, and
modules talk through APIs or events, not by reaching into each other's
internals.** Everything below serves that rule.

Be proportionate: reuse the codebase's existing conventions and add only the
code and infrastructure the feature needs. Do not reproduce reference material
in your answer or build speculative abstractions for one use case.

## First, understand the codebase

Before writing code, establish the current module arrangement. Search for:

1. The Spring Boot application class (`@SpringBootApplication`, often also
   `@Modulithic`).
2. Spring Modulith dependencies (`spring-modulith-*`) in `pom.xml` /
   `build.gradle(.kts)` and ensure they are compatible with your Spring Boot
   version (check the compatibility matrix in the reference documentation).
3. The direct sub-packages of the application's main package — these are the
   default application modules.
4. `package-info.java` files annotated with `@ApplicationModule` or
   `@NamedInterface` — these declare open modules, explicit dependencies, nested
   modules, and additional public packages.
5. An existing architecture test calling `ApplicationModules.of(...).verify()`.
6. Existing module integration tests using `@ApplicationModuleTest`.

Useful searches (adapt to the repo's tools):

```bash
rg -l "@SpringBootApplication|@Modulithic" --glob '*.java' --glob '*.kt'
rg -l "spring-modulith" pom.xml build.gradle build.gradle.kts
rg -l "@ApplicationModule|@NamedInterface" --glob 'package-info.java' --glob '*.java' --glob '*.kt'
rg -l "ApplicationModules\.of|@ApplicationModuleTest" --glob '*.java' --glob '*.kt'
```

If there is no architecture test yet, add one as part of the feature work (see
"Verify the structure"). Without it, module boundaries are only conventions and
will drift.

## The feature development workflow

### 1. Decide which module owns the feature

Place the feature in the module that owns the business capability, its
aggregate, and its data. A feature is not a module; a module is a cohesive
capability that can be described without "and" (e.g. `inventory`, `order`,
`billing`, `shipping`).

Create a new module only when the capability is genuinely separate: it owns its
own data, has its own lifecycle, and is likely to change independently. Do not
create a module per use case, per controller, or per technical layer.

If a feature spans modules, put the orchestration in the module that owns the
initiating action and let the other modules react to events. If two modules keep
needing each other's data, the boundary is probably wrong — reconsider the split
before adding dependencies.

Avoid a `common`, `shared`, or `util` dumping ground. If code is truly shared,
create a small, explicitly named shared module (and register it via
`@Modulithic(sharedModules = ...)` if it should be part of module tests) or
expose a named interface. Keep it small and stable.

### 2. Keep the module's API intentional

Default Spring Modulith layout:

```text
example
├── Application.java                 # main package
├── inventory                        # module base package = API
│   ├── InventoryManagement.java     # public API
│   ├── InventoryItem.java           # public API type if needed
│   └── internal                     # not accessible from other modules
│       ├── InventoryRepository.java
│       └── InventoryItemListener.java
└── order
    ├── OrderManagement.java
    ├── OrderCompleted.java          # event in the API
    └── internal
        └── OrderRepository.java
```

Rules:

- Types in the module's base package are public API. Keep the API small and
  stable.
- Put implementation types in `internal` (or another sub-package) and keep them
  package-private where possible.
- Never expose JPA entities or persistence types across module boundaries.
  Return records/DTOs and IDs.
- Expose additional packages only through `@NamedInterface` on
  `package-info.java`:

```java
@org.springframework.modulith.NamedInterface("spi")
package example.order.spi;
```

- Declare explicit allowed dependencies when a module is new or when you want to
  prevent accidental coupling. `allowedDependencies` is a `String[]`, so list
  multiple dependencies as an array:

```java
@org.springframework.modulith.ApplicationModule(allowedDependencies = "order")
package example.inventory;
```

```java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = { "order", "billing :: spi" })
package example.inventory;
```

Named interfaces use `::`: `"order :: spi"` or `"order :: *"`. An empty array
(`allowedDependencies = {}`) allows no module dependencies; leaving the
attribute at its default does not restrict dependencies.

- Use `@ApplicationModule(type = Type.OPEN)` only for legacy code you are
  gradually modularizing; in a healthy codebase, closed modules are the default.
- Controllers are adapters of the owning module. Put them in the module (base
  package or an internal `web` package) and have them call the module's
  application service. Do not let a controller call another module's internals.

### 3. Choose the interaction style

| Need                                       | Use                                                    |
| ------------------------------------------ | ------------------------------------------------------ |
| Work inside the same module                | Direct method calls                                    |
| Notify other modules, eventual consistency | Domain event + `@ApplicationModuleListener`            |
| Immediate result from another module       | Call its public API bean (explicit allowed dependency) |
| Notify an external system                  | `@Externalized` event + event externalization artifact |
| React to time passing                      | Moments (`HourHasPassed`, `DayHasPassed`, ...)         |
| Shared, stable concepts                    | Small shared module or named interface                 |

Prefer events for cross-module side effects. They keep the publisher unaware of
consumers, make module integration tests possible, and avoid widening the
original transaction. Use a direct API call only when the caller genuinely needs
the result synchronously.

Events remove the _call_ dependency, but not necessarily the _type_ dependency:
if module A listens to an event type declared in module B, A depends on B. Two
modules listening to each other's events still form a cycle. If you need
bidirectional notification, put the shared event contracts in a neutral shared
module, or keep the interaction one-way.

### 4. Implement cross-module events correctly

Publish a domain event from the module that owns the state change:

```java
@Service
@RequiredArgsConstructor
public class OrderManagement {

  private final ApplicationEventPublisher events;

  @Transactional
  public void complete(Order order) {
    // state transition
    events.publishEvent(new OrderCompleted(order.getId()));
  }
}
```

Define the event as an immutable record in the publishing module's API package
so consumers can depend on it. Include IDs and the facts consumers need; do not
include entities.

Consume it in another module with `@ApplicationModuleListener`:

```java
import org.springframework.modulith.events.ApplicationModuleListener;

@Component
class InventoryManagement {

  @ApplicationModuleListener
  void on(OrderCompleted event) {
    // runs asynchronously, after commit, in a new transaction
  }
}
```

`@ApplicationModuleListener` is the default integration style: asynchronous,
transactional, and triggered after the publisher's transaction commits. The
import is `org.springframework.modulith.events.ApplicationModuleListener`, not
`org.springframework.modulith.ApplicationModuleListener` (a common mistake). Do
not add `@Async` or `@Transactional` alongside it; the stereotype already covers
both. Make listeners idempotent, because events can be redelivered.

When backed by the event publication registry, delivery is at-least-once; in all
cases listeners must be idempotent. If a listener fails after the publisher
commits, the publisher's transaction cannot be rolled back. Decide explicitly
how the system recovers: retry via the publication registry, compensate with a
new event, or mark the business process failed. Do not make a critical invariant
depend on a best-effort listener without a safety net.

Note: Specific DLQ or retry strategies for failed events vary significantly by
transport type (e.g., Kafka vs. JDBC). This skill prioritizes idempotency as the
primary defense against delivery anomalies; complex infrastructure-level retry
configurations are currently outside this skill's scope as they are highly
dependent on the chosen transport.

For reliable delivery across restarts, add an event publication registry starter
(`spring-modulith-starter-jpa`, `-jdbc`, `-mongodb`, or `-neo4j`). It records
publications in the same transaction as the business change and marks them
complete when the listener succeeds. Configure completion mode (`update`,
`delete`, `archive`) and purge completed publications as needed.

For external systems, annotate the event with `@Externalized("target::key")` and
add the broker artifact (`spring-modulith-events-kafka`, `-amqp`, `-jms`,
`-messaging`). See `references/events.md`.

### 5. Keep persistence inside the module

- Each module owns its tables/schema. Do not join across module tables.
- Repositories are internal implementation details.
- If another module needs data, expose a query method on the module's API or
  publish an event and let the consumer maintain a local projection.
- For module-specific Flyway migrations, use `db/migration/$module` and set
  `spring.modulith.runtime.flyway-enabled=true` (requires
  `spring-modulith-runtime`). Migrations run in module dependency order.

### 6. Verify the structure

Add or update an architecture test in the test sources:

```java
class ModularityTests {

  static final ApplicationModules MODULES = ApplicationModules.of(Application.class);

  @Test
  void verifiesModularStructure() {
    MODULES.verify();
  }
}
```

Verification checks:

- no cycles between modules,
- only API packages are accessed from other modules,
- explicit `allowedDependencies` are respected.

If verification fails, fix the cause instead of suppressing it:

- **Internal access** → move the type to the API, add a named interface, or
  replace the dependency with an event.
- **Disallowed dependency** → add it to `allowedDependencies` only if it is
  genuinely intended; otherwise redesign.
- **Cycle** → use one of these patterns:
  1. **Invert the notification.** If B only calls A to notify it, let B publish
     an event in B's API and A listen. Both dependencies then point A → B.
  2. **Extract a shared contract.** If both modules need the same event types or
     value objects, move them to a small shared module that neither feature
     module owns. Both depend on the shared module; the shared module depends on
     neither.
  3. **Move orchestration.** Create or use a coordinator module (e.g.
     `checkout`) that depends on both A and B. A and B no longer depend on each
     other.
  4. **Use a port for a synchronous callback.** If B needs a synchronous result
     from A, define a small interface in B's API and have A implement it. B
     calls its own interface, so B no longer depends on A; A depends on B, which
     is the direction that already exists. This keeps the call synchronous while
     making the graph one-way.
  5. **Merge or re-split.** If the modules cannot be described independently,
     they may be one capability. Merge them, or split along a different axis.
  6. **Check mutual event consumption.** A listening to B's event and B
     listening to A's event is still a cycle. Shared event contracts or one-way
     interaction are required.

For temporary exceptions, use `detectViolations()` and filter narrowly, with a
comment and a follow-up issue. Keep the filter scoped to the known violation and
remove it when the refactoring lands; do not disable verification globally.

```java
MODULES.detectViolations()
    .filter(violation -> isTrackedLegacyViolation(violation)) // narrow; link the issue
    .throwIfPresent();
```

### 7. Test at the right level

- Unit-test internal logic directly.
- Put `@ApplicationModuleTest` in the module's package (or a sub-package). It
  bootstraps only that module by default:

```java
@ApplicationModuleTest
class OrderIntegrationTests { ... }
```

- Bootstrap modes: `STANDALONE` (default), `DIRECT_DEPENDENCIES`,
  `ALL_DEPENDENCIES`.
- Mock beans from other modules with `@MockitoBean` instead of bootstrapping
  more modules. Many mocks signal high coupling; consider events instead.
- For asynchronous event flows, use the `Scenario` API:

```java
@ApplicationModuleTest
class OrderIntegrationTests {

  @Test
  void completesOrder(Scenario scenario) {
    scenario.publish(new OrderCompleted(orderId))
      .andWaitForEventOfType(InventoryUpdated.class)
      .matching(event -> event.orderId().equals(orderId))
      .toArriveAndVerify(event -> assertThat(event.quantity()).isEqualTo(1));
  }
}
```

- Assert published events with `PublishedEvents` or `AssertablePublishedEvents`.
- Combine with Boot slice tests via `@ModuleSlicing`.

### 8. Document and operate

- Use `Documenter` to generate C4/UML diagrams and Application Module Canvases;
  `writeDocumentation()` also writes an aggregating Asciidoc file.
- Enable runtime support with `spring-modulith-runtime` if you need
  `ApplicationModuleInitializer` or module-aware Flyway.
- Enable `spring.modulith.runtime.verification-enabled=true` to fail startup on
  architecture violations.
- Add `spring-modulith-starter-insight` for the `modulith` actuator endpoint and
  module observability (spans/metrics).

## Worked example: order cancellation

Requirement: cancelling an order restocks inventory and notifies the customer.

- `order` owns the cancellation state transition. Add
  `OrderManagement.cancel(orderId)` and
  `OrderCancelled(orderId, customerId, lines)` in the `order` base package.
- `inventory` owns stock. Add an `internal` listener
  `@ApplicationModuleListener void on(OrderCancelled event)` that restocks
  idempotently.
- `notification` owns customer messaging. Add an `internal` listener
  `@ApplicationModuleListener void on(OrderCancelled event)` that sends the
  notification idempotently.
- No module calls another's beans. `order` does not know about `inventory` or
  `notification`.
- Add the event publication registry if restocking or notification must survive
  crashes. Decide the compensation policy if a listener keeps failing.
- Test `order` with `@ApplicationModuleTest` and `Scenario`/`PublishedEvents`;
  test each consumer with `@ApplicationModuleTest`, mocking unrelated beans with
  `@MockitoBean`.
- Run `ApplicationModules.verify()`.

## Rules of the road

1. **Modules are vertical slices, not layers.** Do not create top-level
   `controller`, `service`, `repository` packages. Put those inside the module.
2. **The base package is the API.** Everything else is internal unless
   explicitly exposed via `@NamedInterface`.
3. **Dependencies are one-way.** Cycles are architecture bugs; fix them with
   events or a shared concept.
4. **Events are facts.** Name them in the past tense (`OrderCompleted`), keep
   them immutable, and include IDs rather than entities.
5. **Listeners are idempotent.** Assume at-least-once delivery.
6. **Do not leak persistence.** No entities, repositories, or module-internal
   types across boundaries.
7. **Keep the shared kernel tiny.** A large `shared` module is a big ball of mud
   in disguise.
8. **Verify in CI.** An architecture test is part of the feature, not an
   optional extra.

## Anti-patterns and fixes

| Smell                                                  | Fix                                                                                                      |
| ------------------------------------------------------ | -------------------------------------------------------------------------------------------------------- |
| `@Autowired` another module's internal bean            | Expose an API method or publish an event                                                                 |
| Module A ↔ Module B cycle                              | Make one direction one-way, extract a shared contract, add a coordinator, or invert the call with a port |
| Cross-module database joins                            | Query the other module's API or maintain a local projection via events                                   |
| Top-level `controller`/`service`/`repository` packages | Reorganize by capability                                                                                 |
| `common`/`util` module growing                         | Split into named, cohesive shared modules; keep stable                                                   |
| Everything is an open module                           | Close modules; use open only for legacy migration                                                        |
| Synchronous event listener for secondary work          | Use `@ApplicationModuleListener`                                                                         |
| Completed event publications growing forever           | Use `delete`/`archive` completion mode or purge on a schedule                                            |
| Entity returned from a module API                      | Return a record/DTO                                                                                      |
| Feature implemented in a new module for one use case   | Put it in the owning capability module                                                                   |

## Reference map

Read the reference that matches the task; do not load all of them unless needed.

- `references/module-structure.md` — module types (simple/advanced/nested/open),
  named interfaces, explicit dependencies, detection strategies, verification
  details, package layout.
- `references/events.md` — event publication registry, lifecycle, listeners,
  resubmission, externalization, Moments, event design.
- `references/testing.md` — `@ApplicationModuleTest`, bootstrap modes,
  `Scenario`, `PublishedEvents`, `@ModuleSlicing`, change-aware tests.
- `references/operations.md` — `Documenter`, runtime support,
  `ApplicationModuleInitializer`, module-aware Flyway, actuator, observability.
- `references/reference.md` — starters/artifacts, configuration properties,
  schemas, compatibility, source links.

## Source documentation

Based on Spring Modulith 2.1.x reference documentation:

- https://docs.spring.io/spring-modulith/reference/index.html
- https://docs.spring.io/spring-modulith/reference/fundamentals.html
- https://docs.spring.io/spring-modulith/reference/verification.html
- https://docs.spring.io/spring-modulith/reference/events.html
- https://docs.spring.io/spring-modulith/reference/testing.html
- https://docs.spring.io/spring-modulith/reference/moments.html
- https://docs.spring.io/spring-modulith/reference/documentation.html
- https://docs.spring.io/spring-modulith/reference/runtime.html
- https://docs.spring.io/spring-modulith/reference/production-ready.html
- https://docs.spring.io/spring-modulith/reference/appendix.html
