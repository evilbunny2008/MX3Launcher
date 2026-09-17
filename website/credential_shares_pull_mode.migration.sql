-- credential_shares_pull_mode.migration.sql
-- ------------------------------------------
-- Run this against the same database credential_shares.schema.sql was
-- already applied to, to add "pull" mode support: a requesting device
-- (e.g. an MX3 Launcher TV) can start a share with no fields of its own,
-- naming which app it wants credentials for; the logged-in approver then
-- picks one of their own saved_credentials presets (see
-- saved_credentials.schema.sql) to attach via credential_view.php.
--   mysql -u root -p your_database_name < credential_shares_pull_mode.migration.sql

ALTER TABLE credential_shares
    MODIFY payload_json TEXT NULL,
    ADD COLUMN requested_app VARCHAR(64) NULL AFTER code;
