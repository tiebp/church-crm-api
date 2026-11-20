#rApplication Events Reference

Events are the primary way modules should interact. They keep the publisher
unaware of consumers, avoid widening the original transaction, and make isolated
module integration tests possible.

## Contents

- [Why events instead of direct calls](#why-events-instead-of-direct-calls)
- [Synchronous vs asynchronous listeners](#synchronous-vs-asynchronous-listeners)
- [Failure semantics and compensation](#failure-semantics-and-compensation)
- [Events and module cycles](#events-and-module-cycles)
- [The Event Publication Registry](#the-event-publication-registry)
- [Externalizing events](#externalizing-events)
- [Moments — Passage of Time events](#moments--passage-of-time-events)
- [Event design checklist](#event-design-checklist)
- [Source](#source)

## Why events instead of direct calls

Direct calls create "functional gravity": a service that calls other modules'
beans attracts more and more related functionality, becomes hard to test, and
forces changes whenever a new consumer appears.

```java
@Service
@RequiredArgsConstructor
public class OrderManagement {

  private final InventoryManagement inventory;   // couples order to inventory

  @Transactional
  public void complete(Order order) {
    inventory.updateStockFor(order);
  }
}
```

Publishing an event inverts the dependency: the order module states a fact, and
interested modules react.

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

Spring events are synchronous by default. That preserves transactional semantics
(both changes commit or neither does), but it also widens the transaction and
can fail the whole operation because of secondary work.

## Synchronous vs asynchronous listeners

A plain listener runs in the publisher's thread and transaction:

```java
@Component
class InventoryManagement {
  @EventListener
  void on(OrderCompleted event) { ... }
}
```

A transactional listener runs after commit, but still synchronously unless made
async:

```java
@Async
@TransactionalEventListener
void on(OrderCompleted event) { ... }
```

Making it async decouples the original transaction, but if the listener fails,
the event is lost unless a safety net exists.

To run the listener asynchronously, after commit, in its own transaction:

```java
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
@TransactionalEventListener
void on(OrderCompleted event) { ... }
```

Spring Modulith provides `@ApplicationModuleListener` as the shortcut for this
default integration style:

```java
import org.springframework.modulith.events.ApplicationModuleListener;

@Component
class InventoryManagement {

  @ApplicationModuleListener
  void on(OrderCompleted event) {
    // async, after commit, new transaction
  }
}
```

The import is `org.springframework.modulith.events.ApplicationModuleListener`,
not `org.springframework.modulith.ApplicationModuleListener`. Do not add
`@Async` or `@Transactional` alongside it; the stereotype already covers both.

Listeners should be idempotent because events can be redelivered, especially
when resubmission is enabled.

## Failure semantics and compensation

Because `@ApplicationModuleListener` runs after the publisher's transaction
commits, the publisher cannot roll back if the listener fails. Design for this
explicitly:

- **Idempotency** — the same event may be delivered more than once. Use a
  natural key, a processed-event table, or an upsert.
- **Retry** — add an event publication registry starter so failed publications
  can be resubmitted. Configure staleness and monitor `FAILED` and incomplete
  publications.
- **Compensation** — if retry is not enough, publish a compensating event or
  move the business process into an explicit state machine/saga with states such
  as `PENDING`, `CONFIRMED`, `CANCELLED`.
- **Critical invariants** — do not make a critical invariant depend on a
  best-effort listener without a safety net. If the invariant must be
  synchronous, keep it in the publisher's transaction or use a direct API call.
- **External side effects** — use the registry or an outbox (Namastack/JobRunr)
  so a broker outage does not silently drop the event. Make the external
  operation idempotent.

## Events and module cycles

An event removes the _call_ dependency but not the _type_ dependency. If A
listens to an event declared in B's API, A depends on B. If B also listens to an
event declared in A's API, the modules form a cycle even though neither calls
the other.

To keep event-driven interaction acyclic:

- let the depended-on module publish the event and the dependent module listen,
- put shared event contracts in a neutral shared module,
- or introduce a coordinator that owns the orchestration and both modules depend
  on it.

## The Event Publication Registry

The registry hooks into Spring's event publication. When an event is published,
it finds the transactional listeners that will receive it and writes an entry
per listener into an event publication log as part of the original business
transaction.

- By default, listeners meta-annotated with `@TransactionalEventListener` are
  registered.
- Customize the trigger annotation with
  `spring.modulith.events.registry-trigger-annotation`.
- An aspect around each listener marks the entry completed on success; on
  failure the entry stays incomplete so it can be retried.

This protects against lost events when the application crashes between commit
and listener execution.

### Starters

| Persistence | Artifact                                                                                              |
| ----------- | ----------------------------------------------------------------------------------------------------- |
| JPA         | `spring-modulith-starter-jpa`                                                                         |
| JDBC        | `spring-modulith-starter-jdbc` (also works with JPA, bypasses the JPA provider for event persistence) |
| MongoDB     | `spring-modulith-starter-mongodb` (enables MongoDB transactions; requires a replica set)              |
| Neo4j       | `spring-modulith-starter-neo4j`                                                                       |

The starters default to the Jackson-based `EventSerializer`. Disable MongoDB
transaction management with
`spring.modulith.events.mongodb.transaction-management.enabled=false`.

### Publication lifecycle (2.0+)

Each publication has an `EventPublication.Status`:

| Status        | Meaning                                                                                |
| ------------- | -------------------------------------------------------------------------------------- |
| `PUBLISHED`   | Stored and waiting to be processed                                                     |
| `PROCESSING`  | A listener claimed it and is executing                                                 |
| `COMPLETED`   | Listener finished successfully; completion date set unless completion mode is `DELETE` |
| `FAILED`      | Listener threw, or staleness marked it failed                                          |
| `RESUBMITTED` | Previously failed publication resubmitted and pending again                            |

Each publication also tracks:

- last resubmission date (`getLastResubmissionDate()`),
- completion attempts (`getCompletionAttempts()`), incremented when moving to
  `PROCESSING`.

### Staleness

Publications can get stuck in `PUBLISHED`, `PROCESSING`, or `RESUBMITTED` if the
application crashes or a listener hangs. Configure durations after which each
state is considered stale:

```properties
spring.modulith.events.staleness.check-interval=1m
spring.modulith.events.staleness.published=10m
spring.modulith.events.staleness.processing=30m
spring.modulith.events.staleness.resubmitted=10m
```

When any duration is non-zero, a scheduled Staleness Monitor marks older
publications as `FAILED`. If all are zero (the default), the monitor does not
run.

### Failed publications and resubmission

- `FailedEventPublications.resubmit(ResubmissionOptions)` — resubmit only failed
  publications.
- `ResubmissionOptions` — batch size, maximum in-flight, minimum age, optional
  filter (event type, completion attempts). Start with
  `ResubmissionOptions.defaults()` and customize with `withBatchSize(...)`,
  `withMinAge(...)`, `withFilter(...)`.
- `IncompleteEventPublications` — resubmit incomplete publications with a
  predicate, a duration, or
  `resubmitIncompletePublications(ResubmissionOptions)`.

Resubmission moves `FAILED` to `RESUBMITTED` and updates the resubmission date.

### Managing publications

Add `spring-modulith-events-api` to application code to access:

- `CompletedEventPublications` — purge all completed publications or those older
  than a duration.
- `IncompleteEventPublications` — resubmit incomplete publications.
- `FailedEventPublications` — resubmit failed publications.

### Completion modes

`spring.modulith.events.completion-mode`:

| Mode               | Behavior                                                                                   |
| ------------------ | ------------------------------------------------------------------------------------------ |
| `update` (default) | Sets the completion date; publications remain and must be purged periodically              |
| `delete`           | Deletes on completion; `CompletedEventPublications` returns nothing                        |
| `archive`          | Copies to an archive table/collection/node with a completion date and removes the original |

With `update`, the registry grows unbounded unless you purge. Plan for
purge/archive from the start.

### Repositories and schema

`EventPublicationRepository` is an SPI with JPA, JDBC, MongoDB, and Neo4j
implementations. The JDBC implementation creates a dedicated table unless
`spring.modulith.events.jdbc.schema-initialization.enabled=false`. Schema
creation backs off if tables already exist (for example when using Flyway or
Liquibase).

See the appendix for DDL per database.

### Serialization

`EventSerializer` (in `spring-modulith-events-core`) controls how event
instances are serialized. `spring-modulith-events-jackson` provides
`JacksonEventSerializer` using the Boot-configured `ObjectMapper`.

### Publication date

The registry uses `Clock.systemUTC()` by default. Register a `Clock` bean to
customize it.

## Externalizing events

Some events are interesting to external systems. Steps:

1. Add the broker-specific artifact.
2. Annotate selected event types with Spring Modulith's or jMolecules'
   `@Externalized`.
3. Specify the routing target in the annotation value.

| Broker           | Artifact                           | Notes                                                                                  |
| ---------------- | ---------------------------------- | -------------------------------------------------------------------------------------- |
| Kafka            | `spring-modulith-events-kafka`     | Routing key becomes topic and message key                                              |
| AMQP             | `spring-modulith-events-amqp`      | Routing key becomes AMQP routing key; needs Spring Rabbit dependency                   |
| JMS              | `spring-modulith-events-jms`       | No routing keys                                                                        |
| Spring Messaging | `spring-modulith-events-messaging` | Resolves `MessageChannel` by bean name; forwards `springModulith_routingTarget` header |

Externalization is a transactional event listener, so the Event Publication
Registry guards it against broker failures. The three steps per event:

1. **Selection** — by default, event types in the Boot auto-configuration
   package annotated with `@Externalized`.
2. **Mapping (optional)** — replace/customize the payload; add headers for
   Kafka/AMQP.
3. **Routing target** — from `@Externalized`, or the application-local type name
   (for `com.acme.app.sample.SampleEvent` → `sample.SampleEvent`).

### Annotation-based routing

Use `$target::$key`, where both target and key can be SpEL expressions with the
event as root:

```java
@Externalized("customer-created::#{#this.getLastname()}")
class CustomerCreated {
  String getLastname() { ... }
}
```

For complex keys, delegate to a bean:

```java
@Externalized("...::#{@beanName.someMethod(#this)}")
```

### Programmatic configuration

`EventExternalizationConfiguration` customizes selection, mapping, headers, and
routing:

```java
@Bean
EventExternalizationConfiguration eventExternalizationConfiguration() {
  return EventExternalizationConfiguration.externalizing()
      .select(EventExternalizationConfiguration.annotatedAsExternalized())
      .mapping(SomeEvent.class, event -> ...)
      .headers(event -> ...)
      .routeKey(WithKeyProperty.class, WithKeyProperty::getKey)
      .build();
}
```

Selecting explicitly disables the default application-base-package filter.
Convenience selectors exist for type, package, package+annotation, and
selection+routing shortcuts.

### Serializing externalization

Set `spring.modulith.events.externalization.serialize-externalization=true` to
send only one event to the broker at a time. This avoids later events overtaking
earlier ones, especially during resubmission.

### Outbox integrations (2.1+)

For full outbox semantics, Spring Modulith can delegate externalization to:

- **Namastack Outbox** — `spring-modulith-starter-namastack`, relational
  databases only.
- **JobRunr** — `spring-modulith-starter-jobrunr`.

Enable with `spring.modulith.events.externalization.mode=outbox`.

## Moments — Passage of Time events

`spring-modulith-moments` provides an event-based API for time-based triggers,
inspired by Matthias Verraes' "Passage of Time" pattern.

Add the dependency. Application code can then listen for:

- `HourHasPassed`
- `DayHasPassed`
- `WeekHasPassed`
- `MonthHasPassed`
- `QuarterHasPassed`
- `YearHasPassed`

A `Moments` bean triggers these events. Set
`spring.modulith.moments.enable-time-machine=true` to get a `TimeMachine` that
can shift time forward and fire intermediate events, useful in tests.

Properties:

| Property                                      | Default               | Description                                      |
| --------------------------------------------- | --------------------- | ------------------------------------------------ |
| `spring.modulith.moments.enable-time-machine` | `false`               | Expose `TimeMachine` for tests                   |
| `spring.modulith.moments.granularity`         | `hours`               | Minimum granularity; `days` avoids hourly events |
| `spring.modulith.moments.locale`              | `Locale.getDefault()` | Locale for week boundaries                       |
| `spring.modulith.moments.quarter-start-month` | `JANUARY`             | Month at which quarters start                    |
| `spring.modulith.moments.zone-id`             | `UTC`                 | Zone for event timestamps                        |

A `Clock` bean customizes the time source.

## Event design checklist

- Name events as past-tense facts: `OrderCompleted`, `StockShort`,
  `PaymentFailed`.
- Make events immutable records with only the data consumers need.
- Include IDs, not entities. Consumers can query the owning module's API if they
  need more.
- Put events in the publishing module's API package so consumers can reference
  them.
- Do not make events a hidden RPC mechanism. If the caller needs a result, use
  the API.
- Version events when externalized; external consumers are harder to change than
  internal ones.
- Make consumers idempotent and tolerant of out-of-order delivery.
- Decide the delivery guarantee explicitly: synchronous, async fire-and-forget,
  registry-backed, or outbox.
- Decide the retention policy for completed publications.
- Test the event flow with the `Scenario` API, not with sleeps.

## Source

- https://docs.spring.io/spring-modulith/reference/events.html
- https://docs.spring.io/spring-modulith/reference/moments.html
