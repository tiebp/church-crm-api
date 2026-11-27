package org.churchcrm.churchcrmapi.membership.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for Family.
 * Internal to the module.
 */
@Repository
interface FamilyRepository extends JpaRepository<Family, UUID> {

    boolean existsByChurchIdAndNameIgnoreCase(UUID churchId, String name);
}
