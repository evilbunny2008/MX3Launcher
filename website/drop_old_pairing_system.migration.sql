-- drop_old_pairing_system.migration.sql
-- -----------------------------------------
-- Removes the old device_pairings/pairing_urls tables that
-- pair_start.php/pair_approve.php/pair_poll.php/dashboard.php/
-- edit_pairing_url.php used, now replaced by credential_shares (pull
-- mode) and saved_credentials.
--
-- DO NOT RUN THIS until every MX3 Launcher install that will ever talk
-- to this server has been updated to the new pairing flow (this
-- website's PHP files for the old endpoints are already gone as of
-- this commit - deploying that alone breaks any not-yet-updated app
-- install immediately; dropping these tables just removes what's left
-- lying around afterwards).
--
--   mysql -u root -p your_database_name < drop_old_pairing_system.migration.sql

DROP TABLE IF EXISTS device_pairings;
DROP TABLE IF EXISTS pairing_urls;
