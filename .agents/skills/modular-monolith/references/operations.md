# Operations Reference

This file covers documentation generation, runtime support, module-aware
migrations, and production-ready features.

## Documentation

`spring-modulith-docs` (included in `spring-modulith-starter-test`) provides
`Documenter`. It turns the `ApplicationModules` model into Asciidoc snippets:

- C4 and UML component diagrams,
- Application Module Canvases (tables of beans, aggregate roots, events,
  properties),
- an aggregating `all-docs.adoc`.

### Component diagrams

```java
class DocumentationTests {

  ApplicationModules modules = ApplicationModules.of(Application.class);

  @Test
  void writeDocumentationSnippets() {
    new Documenter(modules)
        .writeModulesAsPlantUml()
        .writeIndividualModulesAsPlantUml();
  }
}
```

- `writeModulesAsPlantUml()` — C4 component diagram of all modules and
  relationships.
- `writeIndividualModulesAsPlantUml()` — one diagram per module plus its direct
  dependencies.

For traditional UML style:

```java
DiagramOptions.defaults().withStyle(DiagramStyle.UML);
```

### Application Module Canvases

```java
new Documenter(modules).writeModuleCanvases();
```

Output goes to `spring-modulith-docs` under the build folder. A canvas contains:

- the module base package,
- Spring beans grouped by stereotype (only API/named-interface beans),
- exposed aggregate roots (entities with repositories or jMolecules aggregates),
- published events (requires jMolecules `@DomainEvent` or `DomainEvent`),
- listened-to events (from `@EventListener`, `@TransactionalEventListener`,
  jMolecules `@DomainEventHandler`, or `ApplicationListener` beans),
- configuration properties (requires `spring-boot-configuration-processor` for
  metadata).

### Aggregating document

```java
new Documenter(modules).writeDocumentation();
```

Generates `all-docs.adoc` linking all diagrams and canvases.
`writeAggregatingDocument()` generates only the aggregating file; it produces
nothing if no diagrams/canvases exist.

## Runtime support

Add `spring-modulith-runtime` (runtime scope) to enable:

- `ApplicationModulesRuntime` — access to `ApplicationModules` at runtime,
- `SpringBootApplicationRuntime` — detects the main application class,
- `RuntimeApplicationModuleVerifier` — verifies the arrangement on startup and
  aborts if violations are found, when
  `spring.modulith.runtime.verification-enabled=true`,
- an `ApplicationStartedEvent` listener that invokes
  `ApplicationModuleInitializer` beans.

If you customize module detection, move that customization into production
sources so runtime features see it, and add `spring-modulith-core` as a
compile-time dependency.

### Application module initializers

Startup code sometimes must run in module dependency order: if B depends on A,
A's initializer must run first even when the initializers do not reference each
other.

```java
@Component
class MyInitializer implements ApplicationModuleInitializer {

  @Override
  public void initialize() {
    // initialization code
  }
}
```

`ApplicationModuleInitializer` beans are invoked only when
`spring-modulith-runtime` is on the classpath. Spring Modulith topologically
sorts them by module dependency structure.

### Module-aware Flyway migrations

Enable with `spring.modulith.runtime.flyway-enabled=true` (requires
`spring-modulith-runtime`).

With a default `classpath:db/migration` setup and modules `first` and `second`
(`second` depends on `first`):

- root migrations move to `db/migration/__root` using the default tracking
  table,
- module migrations live in `db/migration/$moduleIdentifier` and use
  `flyway_schema_history_$moduleIdentifier`,
- module migrations baseline at version 0 and baseline-on-migrate,
- locations ending in a wildcard are not customized.

Version numbers are scoped to the module; do not use global ordering.
Application module tests run only the default migrations plus those of modules
included in the test run.

Customize execution by registering a
`org.springframework.modulith.runtime.flyway.MigrationFilter` bean.

## Production-ready features

The easiest setup is the Insight starter (runtime scope):

```xml
<dependency>
  <groupId>org.springframework.modulith</groupId>
  <artifactId>spring-modulith-starter-insight</artifactId>
  <version>2.1.1</version>
  <scope>runtime</scope>
</dependency>
```

It brings actuator and observability support plus Boot's actuator starter. You
still need to connect to your monitoring backend (Zipkin, Wavefront,
OpenTelemetry, Brave, etc.).

### Actuator endpoint

Alternatively add `spring-modulith-actuator` plus `spring-boot-starter-actuator`
(runtime scope). The app exposes a `modulith` actuator resource:

```json
GET http://localhost:8080/actuator

{
  "_links": {
    "modulith": {
      "href": "http://localhost:8080/actuator/modulith",
      "templated": false
    }
  }
}
```

JSON structure:

| Path                                    | Description                                            |
| --------------------------------------- | ------------------------------------------------------ |
| `$.{moduleName}`                        | Technical module name; target of dependency references |
| `$.{moduleName}.displayName`            | Human-readable name                                    |
| `$.{moduleName}.basePackage`            | Base package                                           |
| `$.{moduleName}.parent`                 | Parent module name (nested modules)                    |
| `$.{moduleName}.nested`                 | Nested module names                                    |
| `$.{moduleName}.dependencies[]`         | Outgoing dependencies                                  |
| `$.{moduleName}.dependencies[].target`  | Target module name                                     |
| `$.{moduleName}.dependencies[].types[]` | `DEFAULT`, `USES_COMPONENT`, or `EVENT_LISTENER`       |

Example:

```json
{
  "a": {
    "basePackage": "example.a",
    "displayName": "A",
    "dependencies": []
  },
  "b": {
    "basePackage": "example.b",
    "displayName": "B",
    "dependencies": [
      {
        "target": "a",
        "types": ["EVENT_LISTENER", "USES_COMPONENT"]
      }
    ]
  }
}
```

### Observability

Add `spring-modulith-observability-core` (runtime) or use the Insight starter.
Add `spring-modulith-observability-api` in compile scope to customize event
publication metrics.

Spring components that are part of a module's API are decorated with an aspect
that creates Micrometer spans. A trace shows module entry and downstream module
interactions.

#### Module entry span tags

| Name                     | Description                |
| ------------------------ | -------------------------- |
| `module.identifier`      | Module identifier          |
| `module.invocation-type` | `event listener` or `bean` |
| `module.method`          | Executed method            |
| `module.name`            | Module name                |

#### Metrics

- `module.events.published` — counter for all event publications.
- `module.events.published.$moduleIdentifier.$simpleEventTypeName` — counter per
  event.

All-events metric tags: `module.event.type`, `module.identifier`, `module.name`.

Individual-event metric tags: `module.identifier`, `module.name`.

Add domain-specific tags:

```java
@Bean
ModulithEventMetricsCustomizer modulithEventMetricsCustomizer() {
  return metrics -> {
    metrics.customize(MyCustomDomainEvent.class, (event, it) -> {
      it.tags("someTag", event.getValue());
    });
  };
}
```

Customize conventions with a `ModulithObservationConvention` bean. The default
delegates to `ModulithContext`.

## Operational checklist

- Keep the architecture verification test in CI.
- Enable runtime verification in staging/production if startup should fail fast
  on violations.
- Use `ApplicationModuleInitializer` instead of `@Order` for module-dependent
  startup logic.
- Put module migrations in `db/migration/$module` and enable module-aware
  Flyway.
- Add the Insight starter for the `modulith` actuator and module traces/metrics.
- Monitor event publication registry size; purge or archive completed
  publications.
- Monitor incomplete/failed publications and configure staleness + resubmission.
- If externalizing events, decide whether the built-in registry or an outbox
  (Namastack/JobRunr) is appropriate.

## Source

- https://docs.spring.io/spring-modulith/reference/documentation.html
- https://docs.spring.io/spring-modulith/reference/runtime.html
- https://docs.spring.io/spring-modulith/reference/production-ready.html
