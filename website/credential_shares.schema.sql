-- credential_shares.schema.sql
-- -----------------------------
-- Backs credential_start.php / credential_status.php / credential_view.php:
-- a short-lived, generic app-to-app credential relay (e.g. an MQTT
-- broker's hostname/username/password, or an MX3 Launcher TV's pairing
-- URL/secret), the same short-code/QR/expiry pattern device_pairings
-- used to, but for arbitrary key/value payloads instead of a fixed
-- {url, secret} pair. Run this against the same database as schema.sql:
--   mysql -u root -p your_database_name < credential_shares.schema.sql
--
-- Two modes, distinguished by whether payload_json is set at creation:
-- - "push" (e.g. Z2M Dash sharing its own broker credentials): the
--   sending app already has the data, so credential_start.php stores
--   payload_json immediately; credential_view.php does a one-time
--   reveal of it.
-- - "pull" (e.g. an MX3 Launcher TV asking to be paired): the
--   requesting device has no data of its own - payload_json starts
--   NULL and requested_app says which app it wants credentials for;
--   credential_view.php lets the logged-in approver pick one of their
--   own saved_credentials presets (see saved_credentials.schema.sql)
--   to attach.
-- Either way, credential_status.php reports "pending" until viewed_at
-- is set, then hands the resolved fields to whoever holds the token
-- and deletes the row - a one-time collection, same as the old
-- device_pairings/pair_poll.php.

CREATE TABLE credential_shares (
    id INT AUTO_INCREMENT PRIMARY KEY,
    token CHAR(64) NOT NULL,
    code CHAR(6) NOT NULL,
    -- Which app a "pull" request wants credentials for - NULL for "push"
    -- requests, which already know their own payload.
    requested_app VARCHAR(64) NULL,
    -- {"app": "...", "label": "...", "fields": {"Hostname": "...", ...}},
    -- see credential_start.php for the exact shape. NULL until a "pull"
    -- request's approver attaches a chosen saved_credentials preset.
    payload_json TEXT NULL,
    created_at DATETIME NOT NULL,
    viewed_at DATETIME NULL,
    viewed_by_user_id INT NULL,
    UNIQUE KEY idx_token (token),
    KEY idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
