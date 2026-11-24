package org.churchcrm.churchcrmapi.membership.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.churchcrm.churchcrmapi.organization.ChurchCreated;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Seeds the default membership statuses when a church is created.
 *
 * <p>The listener is asynchronous and runs in its own transaction. Failures are
 * rethrown so the Modulith event registry can retry (ADR-0012).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
class MembershipStatusSeeder {

    private final MembershipStatusRepository repository;

    @ApplicationModuleListener
    public void on(ChurchCreated event) {
        try {
            seed(event.getChurchId());
        } catch (Exception ex) {
            log.error("Failed to seed default membership statuses for church {}", event.getChurchId(), ex);
            throw ex;
        }
    }

    /**
     * Performs the idempotent seeding. Exposed within the module so tests can
     * exercise duplicate delivery synchronously.
     */
    @org.springframework.transaction.annotation.Transactional
    public int seed(UUID churchId) {
        int inserted = repository.seedDefaults(churchId);
        log.info("Seeded {} default membership statuses for church {}", inserted, churchId);
        return inserted;
    }
}
