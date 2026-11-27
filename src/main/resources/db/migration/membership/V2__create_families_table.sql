-- Create families table
CREATE TABLE membership.families (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    church_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    line1 VARCHAR(200),
    line2 VARCHAR(200),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(60),
    postal_code VARCHAR(20),
    wedding_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Listing index scoped by church
CREATE INDEX idx_families_church_id
    ON membership.families(church_id);

-- Business rule: family names are unique per church, case-insensitively
CREATE UNIQUE INDEX idx_families_unique_name_per_church
    ON membership.families(church_id, lower(name));
