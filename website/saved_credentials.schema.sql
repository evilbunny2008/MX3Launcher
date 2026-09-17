-- saved_credentials.schema.sql
-- ------------------------------
-- Replaces pairing_urls: a logged-in user's own persistent, named
-- credential presets (e.g. several TVs' pairing URL/secret, or any
-- other app's saved fields), tagged by which app they're for. Picked
-- from at credential_view.php approval time for a "pull"-mode request
-- (see credential_shares.schema.sql) - the account can hold as many as
-- needed, for as many different apps/devices as needed. Managed via
-- manage_credentials.php. Run this against the same database as
-- schema.sql:
--   mysql -u root -p your_database_name < saved_credentials.schema.sql

CREATE TABLE saved_credentials (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    app_name VARCHAR(64) NOT NULL,
    label VARCHAR(128) NOT NULL,
    -- {"URL": "...", "Secret": "..."} for app_name = "MX3Launcher" (the
    -- TV app looks up exactly those two keys - see that project's
    -- SoundbarPairing.kt); free-form key/value pairs for any other app,
    -- since a human picks and reads these off credential_view.php's
    -- push-mode reveal page rather than code consuming them directly.
    fields_json TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
