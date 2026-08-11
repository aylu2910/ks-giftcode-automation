-- H2: Convert ENUM column to VARCHAR to allow new enum values
-- JPA @Enumerated(EnumType.STRING) handles validation
ALTER TABLE redemption_log ALTER COLUMN status VARCHAR(255) NOT NULL;
