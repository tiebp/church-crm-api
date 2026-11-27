package org.churchcrm.churchcrmapi.membership.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Embedded address value object for a family.
 * All fields are optional; validation happens on the request DTO.
 */
@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class FamilyAddress {

    @Column(length = 200)
    private String line1;

    @Column(length = 200)
    private String line2;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 60)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;
}
