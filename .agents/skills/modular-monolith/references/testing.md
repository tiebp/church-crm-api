# Testing Reference

Spring Modulith's testing support lets you bootstrap a single application module
(or a set of modules) instead of the whole application. This keeps tests fast
and, more importantly, proves that the module is actually decoupled.

## Setup

Add the test starter:

```xml
<dependency>
  <groupId>org.springframework.modulith</groupId>
  <artifactId>spring-modulith-starter-test</artifactId>
  <scope>test</scope>
</dependency>
```

## `@ApplicationModuleTest`

Place the test in the module package (or any sub-package) and annotate it:

```java
package example.order;

@ApplicationModuleTest
class OrderIntegrationTests {
  // test cases
}
```

It behaves like `@SpringBootTest` but limits auto-configuration, component
scanning, and entity scanning to the module's packages. Enable DEBUG logging for
`org.springframework.modulith` to see the bootstrap decisions.

### Bootstrap modes

| Mode                   | Includes                                           |
| ---------------------- | -------------------------------------------------- |
| `STANDALONE` (default) | Only the current module                            |
| `DIRECT_DEPENDENCIES`  | Current module plus modules it directly depends on |
| `ALL_DEPENDENCIES`     | Current module plus the whole dependency tree      |

```java
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
class OrderIntegrationTests { ... }
```

### Efferent dependencies

If a module's beans reference beans from modules not included in the test,
bootstrap fails. The usual fix is to mock the external beans:

```java
@ApplicationModuleTest
class InventoryIntegrationTests {

  @MockitoBean SomeOtherComponent someOtherComponent;
}
```

Prefer mocking over widening the bootstrap scope. If a module needs many mocks,
that is a coupling signal: consider replacing those dependencies with events.

The import is
`org.springframework.test.context.bean.override.mockito.MockitoBean` on Spring
Boot 3.4+/Spring Framework 6.2+. Older code uses
`org.springframework.boot.test.mock.mockito.MockitoBean`.

### Test data

Seed state through the module's public API (or a module-local fixture), not by
reaching into another module's repositories. Do not assume a shared database
already contains the rows the test needs; each test should set up its own state
so it is deterministic and independent of execution order.

## Boot slice tests

`@ApplicationModuleTest` is vertical slicing. To combine it with Boot's
horizontal slice annotations, use `@ModuleSlicing`:

```java
@ModuleSlicing
@DataJpaTest
class SomeModuleRepositoryIntegrationTests {

  @Autowired SomeRepository repository;

  @Test
  void someTest() { ... }
}
```

`@ModuleSlicing` is the foundation of `@ApplicationModuleTest`.

## Asserting published events

`@ApplicationModuleTest` can inject `PublishedEvents`:

```java
@ApplicationModuleTest
class OrderIntegrationTests {

  @Test
  void someTestMethod(PublishedEvents events) {
    // exercise the module

    var matching = events.ofType(OrderCompleted.class)
        .matching(OrderCompleted::getOrderId, reference.getId());

    assertThat(matching).hasSize(1);
  }
}
```

Use `AssertablePublishedEvents` for fluent AssertJ assertions:

```java
@Test
void someTestMethod(AssertablePublishedEvents events) {
  // exercise the module

  assertThat(events)
      .contains(OrderCompleted.class)
      .matching(OrderCompleted::getOrderId, reference.getId());
}
```

You can inject `Scenario` and `AssertablePublishedEvents` in the same test
method. Wait for the asynchronous outcome first (`andWaitForEventOfType(...)` or
`andWaitForStateChange(...)`), then inspect the published events; otherwise the
listener may not have run yet.

## The `Scenario` API

`Scenario` handles the hard parts of integration testing asynchronous,
transactional event flows: transactions, event delivery, Awaitility-style
waiting, and assertions.

Declare it as a test method parameter in an `@ApplicationModuleTest`:

```java
@ApplicationModuleTest
class SomeApplicationModuleTest {

  @Test
  void someModuleIntegrationTest(Scenario scenario) {
    // define the scenario
  }
}
```

The skeleton is:

1. define a stimulus — publish an event or invoke a bean,
2. optionally customize execution (timeout, etc.),
3. define the expected outcome — an event or a state change,
4. optionally verify the result.

### Stimulus

```java
// start with an event publication
scenario.publish(new MyApplicationEvent(...))....

// start with a bean invocation
scenario.stimulate(() -> someBean.someMethod(...))....
```

Both run in a transaction callback so the event (and any events published by the
invocation) reach transactional listeners. This requires a **new** transaction;
database state changes are **not** rolled back automatically. Use
`....andCleanup(...)` to clean up.

### Expected event

```java
....andWaitForEventOfType(SomeOtherEvent.class)
 .matching(event -> ...)
 ....
```

Execution blocks until a matching event arrives or the timeout is reached.

### Expected state change

```java
scenario.publish(new MyApplicationEvent(...))
  .andWaitForStateChange(() -> someBean.someMethod(...))
  .andVerify(result -> ...);
```

By default, non-`null` values and non-empty `Optional`s count as a conclusive
state change. Use the predicate overload to customize.

### Terminal operations

```java
// execute the scenario
....toArrive(...)

// execute and assert on the received event
....toArriveAndVerify(event -> ...)
```

### Complete example

```java
scenario.publish(new MyApplicationEvent(...))
  .andWaitForEventOfType(SomeOtherEvent.class)
  .matching(event -> ...)
  .toArriveAndVerify(event -> ...);
```

### Customizing execution

Per scenario:

```java
scenario.publish(new MyApplicationEvent(...))
  .customize(conditionFactory -> conditionFactory.atMost(Duration.ofSeconds(2)))
  .andWaitForEventOfType(SomeOtherEvent.class)
  .matching(event -> ...)
  .toArriveAndVerify(event -> ...);
```

Globally per test class, implement `ScenarioCustomizer` and register it with
`@ExtendWith`:

```java
@ExtendWith(MyCustomizer.class)
class MyTests {

  @Test
  void myTestCase(Scenario scenario) { ... }

  static class MyCustomizer implements ScenarioCustomizer {

    @Override
    public Function<ConditionFactory, ConditionFactory> getDefaultCustomizer(
        Method method, ApplicationContext context) {
      return conditionFactory -> ...;
    }
  }
}
```

## Change-aware test execution

`spring-modulith-junit` (test scope) skips tests unaffected by changes:

```xml
<dependency>
  <groupId>org.springframework.modulith</groupId>
  <artifactId>spring-modulith-junit</artifactId>
  <scope>test</scope>
</dependency>
```

Tests run if they are in a root module, a changed module, or a module that
transitively depends on a changed one. Optimization backs off when:

- the run originates from an IDE,
- a build-system resource at the module root changed (`pom.xml`,
  `build.gradle(.kts)`, `settings.gradle(.kts)`, `gradle.properties`,
  `gradle/libs.versions.toml`, wrapper properties),
- a classpath resource changed,
- the project has no changes at all.

By default, all tests run when no changes are detected. Set
`spring.modulith.test.on-no-changes=skip-all` to skip.

For CI, set `spring.modulith.test.reference-commit` to the last successful
build's commit and check out history up to that commit. Override change
detection with a `FileModificationDetector` via
`spring.modulith.test.file-modification-detector`.

Relevant properties:

| Property                                             | Default       | Purpose                                                               |
| ---------------------------------------------------- | ------------- | --------------------------------------------------------------------- |
| `spring.modulith.test.reference-commit`              | none          | Commit to diff against in CI                                          |
| `spring.modulith.test.file-modification-detector`    | none          | `uncommitted-changes`, `reference-commit`, `default`, or a class name |
| `spring.modulith.test.on-no-changes`                 | `execute-all` | `execute-all` or `skip-all`                                           |
| `spring.modulith.test.thread-bound-published-events` | `false`       | Restrict `Scenario`/`PublishedEvents` to the test thread and children |

## Test strategy checklist

- Unit tests for pure internal logic; no Spring context needed.
- One `@ApplicationModuleTest` per module boundary, in the module package.
- Mock external module beans with `@MockitoBean`; do not bootstrap the world.
- Use `Scenario` for asynchronous event flows. Avoid `Thread.sleep`.
- Use `PublishedEvents`/`AssertablePublishedEvents` to verify emitted facts.
- Use `@ModuleSlicing` plus Boot slice annotations for repository/controller
  slices.
- Keep an architecture test (`ApplicationModules.verify()`) in the same test
  suite.
- In CI, configure change-aware execution if the project is large.

## Source

- https://docs.spring.io/spring-modulith/reference/testing.html
- https://docs.spring.io/spring-modulith/reference/events.html
