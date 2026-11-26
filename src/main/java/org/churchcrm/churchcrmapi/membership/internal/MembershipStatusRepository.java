package org.churchcrm.churchcrmapi.membership.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for MembershipStatus.
 * Internal to the module.
 */
@Repository
interface MembershipStatusRepository extends JpaRepository<MembershipStatus, UUID> {

    List<MembershipStatus> findByChurchIdOrderBySortOrderAsc(UUID churchId);

    Optional<MembershipStatus> findByChurchIdAndId(UUID churchId, UUID id);

    boolean existsByChurchIdAndCodeIgnoreCase(UUID churchId, String code);

    boolean existsByChurchIdAndIsDefaultTrue(UUID churchId);

    /**
     * Inserts the six default statuses for a church in a single statement.
     * ON CONFLICT DO NOTHING makes duplicate event deliveries and retries safe,
     * including a conflict on the partial unique default index.
     *
     * @return the number of rows inserted
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO membership.membership_statuses
                (id, church_id, code, label, description, sort_order, is_default, active,
                 created_at, updated_at, created_by, updated_by)
            VALUES
                (gen_random_uuid(), :churchId, 'member', 'Member', NULL, 10, false, true,
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
                (gen_random_uuid(), :churchId, 'regular_attender', 'Regular Attender', NULL, 20, false, true,
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
                (gen_random_uuid(), :churchId, 'guest', 'Guest', NULL, 30, true, true,
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
                (gen_random_uuid(), :churchId, 'non_attender', 'Non-Attender', NULL, 40, false, true,
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
                (gen_random_uuid(), :churchId, 'excluded', 'Excluded', NULL, 50, false, true,
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
                (gen_random_uuid(), :churchId, 'disciplined', 'Disciplined', NULL, 60, false, true,
                 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system')
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int seedDefaults(@Param("churchId") UUID churchId);
}
