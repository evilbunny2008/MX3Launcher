--
-- Adds the `default_credentials` table to an existing deployment (schema.sql
-- already has it for a fresh install - this is for upgrading one that
-- predates it). Safe to run more than once.
--

CREATE TABLE IF NOT EXISTS `default_credentials` (
  `user_id` int(11) NOT NULL,
  `app_name` varchar(64) NOT NULL,
  `saved_credential_id` int(11) NOT NULL,
  PRIMARY KEY (`user_id`, `app_name`)
) ENGINE=InnoDB DEFAULT;
