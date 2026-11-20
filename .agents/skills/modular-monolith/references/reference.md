# Artifacts, Configuration, and Schemas Reference

## Starters

| Starter                             | Typical scope | Includes                                                                                                                                                                 |
| ----------------------------------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `spring-modulith-starter-core`      | compile       | `spring-modulith-api`, `spring-modulith-apt`, `spring-modulith-moments`, `spring-modulith-core` (runtime), `spring-modulith-runtime` (runtime)                           |
| `spring-modulith-starter-insight`   | runtime       | `spring-modulith-actuator` (runtime), `spring-modulith-observability` (runtime), `spring-boot-starter-actuator` (runtime)                                                |
| `spring-modulith-starter-jdbc`      | compile       | starter-core, `spring-modulith-events-api`, `spring-modulith-events-core` (runtime), `spring-modulith-events-jdbc` (runtime), `spring-modulith-events-jackson` (runtime) |
| `spring-modulith-starter-jpa`       | compile       | starter-core, events-api, events-core (runtime), events-jpa (runtime), events-jackson (runtime)                                                                          |
| `spring-modulith-starter-mongodb`   | compile       | starter-core, events-api, events-core (runtime), events-mongodb (runtime), events-jackson (runtime)                                                                      |
| `spring-modulith-starter-neo4j`     | compile       | starter-core, events-api, events-core (runtime), events-neo4j (runtime), events-jackson (runtime)                                                                        |
| `spring-modulith-starter-test`      | test          | `spring-modulith-docs`, `spring-modulith-test`                                                                                                                           |
| `spring-modulith-starter-namastack` | compile       | Namastack outbox integration (2.1+)                                                                                                                                      |
| `spring-modulith-starter-jobrunr`   | compile       | JobRunr outbox integration (2.1+)                                                                                                                                        |

Use the BOM to align versions:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.modulith</groupId>
      <artifactId>spring-modulith-bom</artifactId>
      <version>2.1.1</version>
      <scope>import</scope>
      <type>pom</type>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Gradle:

```groovy
dependencyManagement {
  imports {
    mavenBom 'org.springframework.modulith:spring-modulith-bom:2.1.1'
  }
}
```

## Individual JARs

| Module                             | Typical scope | Description                                                 |
| ---------------------------------- | ------------- | ----------------------------------------------------------- |
| `spring-modulith-actuator`         | runtime       | Actuator endpoint exposing the module structure             |
| `spring-modulith-api`              | compile       | Abstractions to customize default behavior                  |
| `spring-modulith-apt`              | compile       | Annotation processor extracting Javadoc for module canvases |
| `spring-modulith-core`             | runtime       | Core module model and API                                   |
| `spring-modulith-docs`             | test          | `Documenter` API for Asciidoc/PlantUML docs                 |
| `spring-modulith-events-amqp`      | runtime       | Event externalization for AMQP                              |
| `spring-modulith-events-api`       | runtime       | API to customize event features                             |
| `spring-modulith-events-core`      | runtime       | Event publication registry core and abstractions            |
| `spring-modulith-events-jackson`   | runtime       | Jackson-based `EventPublicationSerializer`                  |
| `spring-modulith-events-jdbc`      | runtime       | JDBC `EventPublicationRegistry`                             |
| `spring-modulith-events-jms`       | runtime       | Event externalization for JMS                               |
| `spring-modulith-events-jpa`       | runtime       | JPA `EventPublicationRegistry`                              |
| `spring-modulith-events-kafka`     | runtime       | Event externalization for Kafka                             |
| `spring-modulith-events-messaging` | runtime       | Externalization into Spring Messaging `MessageChannel`s     |
| `spring-modulith-events-mongodb`   | runtime       | MongoDB `EventPublicationRegistry`                          |
| `spring-modulith-events-neo4j`     | runtime       | Neo4j `EventPublicationRegistry`                            |
| `spring-modulith-junit`            | test          | Change-aware test execution optimization                    |
| `spring-modulith-moments`          | compile       | Passage of Time events                                      |
| `spring-modulith-observability`    | runtime       | Observability infrastructure                                |
| `spring-modulith-runtime`          | runtime       | Runtime bootstrap of `ApplicationModules`                   |
| `spring-modulith-test`             | test          | Integration testing support                                 |

Note: the production-ready section refers to
`spring-modulith-observability-core` as the runtime artifact to add directly;
the Insight starter brings the aggregate `spring-modulith-observability`
artifact.

## Configuration properties

| Property                                                           | Default           | Description                                                                                                          |
| ------------------------------------------------------------------ | ----------------- | -------------------------------------------------------------------------------------------------------------------- |
| `spring.modulith.default-async-termination`                        | `true`            | Configure async processing termination defaults (wait up to 2s)                                                      |
| `spring.modulith.detection-strategy`                               | none              | `direct-subpackages` (fallback), `explicitly-annotated`, or a custom `ApplicationModuleDetectionStrategy` class name |
| `spring.modulith.events.completion-mode`                           | `update`          | `update`, `delete`, or `archive`                                                                                     |
| `spring.modulith.events.externalization.enabled`                   | `true`            | Enable event externalization                                                                                         |
| `spring.modulith.events.externalization.mode`                      | `module-listener` | `module-listener` or `outbox`                                                                                        |
| `spring.modulith.events.externalization.serialize-externalization` | `false`           | Serialize broker interactions                                                                                        |
| `spring.modulith.events.jdbc.schema-initialization.enabled`        | `true`            | Initialize the JDBC event publication schema                                                                         |
| `spring.modulith.events.jdbc.schema`                               | none              | Schema name for the event publication table                                                                          |
| `spring.modulith.events.jdbc.use-legacy-structure`                 | `false`           | Use the legacy publication table structure                                                                           |
| `spring.modulith.events.kafka.enable-json`                         | `true`            | Enable JSON support for `KafkaTemplate`                                                                              |
| `spring.modulith.events.mongodb.transaction-management.enabled`    | `true`            | Enable MongoDB transactions (requires replica set)                                                                   |
| `spring.modulith.events.neo4j.event-index.enabled`                 | `false`           | Create indexes on the Neo4j event hash property                                                                      |
| `spring.modulith.events.rabbitmq.enable-json`                      | `true`            | Enable JSON support for `RabbitTemplate`                                                                             |
| `spring.modulith.events.registry-trigger-annotation`               | none              | Annotation that triggers registry entries; default is `@TransactionalEventListener`                                  |
| `spring.modulith.events.republish-outstanding-events-on-restart`   | `false`           | Republish outstanding publications on restart (not recommended for multi-instance deployments)                       |
| `spring.modulith.events.staleness.check-interval`                  | `1m`              | Interval for the Staleness Monitor                                                                                   |
| `spring.modulith.events.staleness.processing`                      | `0`               | Duration after which `PROCESSING` is stale                                                                           |
| `spring.modulith.events.staleness.published`                       | `0`               | Duration after which `PUBLISHED` is stale                                                                            |
| `spring.modulith.events.staleness.resubmitted`                     | `0`               | Duration after which `RESUBMITTED` is stale                                                                          |
| `spring.modulith.moments.enable-time-machine`                      | `false`           | Expose `TimeMachine` for tests                                                                                       |
| `spring.modulith.moments.granularity`                              | `HOURS`           | `HOURS` or `DAYS`                                                                                                    |
| `spring.modulith.moments.locale`                                   | JVM default       | Locale for week boundaries                                                                                           |
| `spring.modulith.moments.zone-id`                                  | `UTC`             | Zone for event timestamps                                                                                            |
| `spring.modulith.runtime.flyway-enabled`                           | `false`           | Enable module-specific Flyway customization                                                                          |
| `spring.modulith.runtime.verification-enabled`                     | `false`           | Verify module arrangement on startup                                                                                 |
| `spring.modulith.test.file-modification-detector`                  | none              | `uncommitted-changes`, `reference-commit`, `default`, or a class name                                                |
| `spring.modulith.test.on-no-changes`                               | `execute-all`     | `execute-all` or `skip-all`                                                                                          |
| `spring.modulith.test.reference-commit`                            | none              | Commit hash for CI change detection                                                                                  |
| `spring.modulith.test.thread-bound-published-events`               | `false`           | Restrict `Scenario`/`PublishedEvents` to the test thread and children                                                |

Notes:

- `spring.modulith.republish-outstanding-events-on-restart` is deprecated since
  1.3; use `spring.modulith.events.republish-outstanding-events-on-restart`.
- Spring Boot relaxed binding accepts both kebab-case and camelCase for these
  properties.
- The appendix also lists `spring.modulith.moments.enableTimeMachine` and
  `zoneId`; the kebab-case forms above are the canonical ones.

## Event publication schema

The JDBC registry expects an `EVENT_PUBLICATION` table (and optionally
`EVENT_PUBLICATION_ARCHIVE` for `archive` mode). Current columns:

| Column                   | Type (PostgreSQL)          | Notes                                                           |
| ------------------------ | -------------------------- | --------------------------------------------------------------- |
| `id`                     | `UUID`                     | Primary key                                                     |
| `listener_id`            | `TEXT`                     | Listener identity                                               |
| `event_type`             | `TEXT`                     | Event class name                                                |
| `serialized_event`       | `TEXT`                     | Serialized event payload                                        |
| `publication_date`       | `TIMESTAMP WITH TIME ZONE` | Publication time                                                |
| `completion_date`        | `TIMESTAMP WITH TIME ZONE` | Set on completion (`update`/`archive`)                          |
| `status`                 | `TEXT`                     | `PUBLISHED`, `PROCESSING`, `COMPLETED`, `FAILED`, `RESUBMITTED` |
| `completion_attempts`    | `INT`                      | Number of listener invocations                                  |
| `last_resubmission_date` | `TIMESTAMP WITH TIME ZONE` | Last resubmission                                               |

PostgreSQL current schema:

```sql
CREATE TABLE IF NOT EXISTS event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
  ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
  ON event_publication (completion_date);
```

The appendix contains equivalent DDL for H2, HSQLDB, MariaDB, Microsoft SQL
Server, MySQL, and PostgreSQL, including archive-enabled and legacy variants.

If you use Flyway/Liquibase, set
`spring.modulith.events.jdbc.schema-initialization.enabled=false` and create the
table yourself.

## Compatibility

Spring Modulith 2.x aligns with Spring Boot 4.x; the 1.4.x line aligns with
Spring Boot 3.5.x, 1.3.x with 3.4.x, 1.2.x with 3.3.x, and 1.1.x with 3.2.x.
Always check the appendix matrix for the exact compiled/tested versions and
jMolecules compatibility:

- https://docs.spring.io/spring-modulith/reference/appendix.html#compatibility-matrix

## Examples and API docs

- Official examples:
  https://github.com/spring-projects/spring-modulith/tree/2.1.1/spring-modulith-examples
- Javadoc: https://docs.spring.io/spring-modulith/docs/2.1.1/api
- GitHub: https://github.com/spring-projects/spring-modulith
- Milestone repository: https://repo.spring.io/milestone
- Snapshot repository: https://repo.spring.io/snapshot

## Source documentation

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
