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

import java.time.LocalDate;
import java.util.UUID;

/**
 * Church-scoped family (household) record.
 *
 * ArchUnit Rules:
 * - Rule #1: Has @Embedded Audit field
 * - Rule #2: Uses UUID for @Id
 * - Rule #4: No inheritance (extends nothing)
 * - Rule #5: Audit field named "audit"
 * - Rule #6: Has @EntityListeners(AuditingEntityListener.class)
 */
@Entity
@Table(schema = "membership", name = "families")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Family {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "church_id", nullable = false)
    private UUID churchId;

    @Column(nullable = false, length = 200)
    private String name;

    @Embedded
    private FamilyAddress address;

    @Column(name = "wedding_date")
    private LocalDate weddingDate;

    @Embedded
    private Audit audit = new Audit();
}
