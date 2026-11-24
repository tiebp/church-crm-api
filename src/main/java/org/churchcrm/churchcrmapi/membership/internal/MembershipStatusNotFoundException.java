package org.churchcrm.churchcrmapi.membership.internal;

import org.churchcrm.churchcrmapi.crosscutting.web.NotFoundException;

import java.util.UUID;

/**
 * Exception thrown when a membership status is not found or when a user
 * attempts to access a status that belongs to another church.
 *
 * <p>Both cases return HTTP 404 to prevent leaking resource existence
 * (ADR-0005).</p>
 */
public class MembershipStatusNotFoundException extends NotFoundException {

    public MembershipStatusNotFoundException(UUID id) {
        super("MembershipStatus", id);
    }
}
