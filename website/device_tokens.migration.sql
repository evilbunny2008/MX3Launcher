--
-- Adds the `device_tokens` table to an existing deployment (schema.sql
-- already has it for a fresh install - this is for upgrading one that
-- predates it). Safe to run more than once.
--

CREATE TABLE IF NOT EXISTS `device_tokens` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `token` char(64) NOT NULL,
  `label` varchar(128) NOT NULL,
  `created_at` datetime NOT NULL,
  `last_used_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_token` (`token`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT;
