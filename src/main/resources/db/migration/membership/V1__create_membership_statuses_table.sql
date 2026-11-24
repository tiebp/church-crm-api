-- Create membership schema
CREATE SCHEMA IF NOT EXISTS membership;

-- Create membership statuses table
CREATE TABLE membership.membership_statuses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    church_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    label VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_default BOOLEAN NOT NULL DEFAULT false,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Listing index scoped by church
CREATE INDEX idx_membership_statuses_church_sort_order
    ON membership.membership_statuses(church_id, sort_order);

-- Business rule: codes are unique per church, case-insensitively
CREATE UNIQUE INDEX idx_membership_statuses_unique_code_per_church
    ON membership.membership_statuses(church_id, lower(code));

-- Business rule: at most one default status per church
CREATE UNIQUE INDEX idx_membership_statuses_one_default_per_church
    ON membership.membership_statuses(church_id)
    WHERE is_default = true;
