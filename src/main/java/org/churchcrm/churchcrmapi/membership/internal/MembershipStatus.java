package org.churchcrm.churchcrmapi.membership.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.churchcrm.churchcrmapi.crosscutting.auditing.Audit;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

/**
 * Church-scoped membership status configuration.
 *
 * ArchUnit Rules:
 * - Rule #1: Has @Embedded Audit field
 * - Rule #2: Uses UUID for @Id
 * - Rule #4: No inheritance (extends nothing)
 * - Rule #5: Audit field named "audit"
 * - Rule #6: Has @EntityListeners(AuditingEntityListener.class)
 */
@Entity
@Table(schema = "membership", name = "membership_statuses")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MembershipStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "church_id", nullable = false)
    private UUID churchId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(nullable = false)
    private Boolean active = true;

    @Embedded
    private Audit audit = new Audit();
}
