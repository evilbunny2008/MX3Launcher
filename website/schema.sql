--
-- Database: `mx3launcher`
--
CREATE DATABASE IF NOT EXISTS `mx3launcher`;
USE `mx3launcher`;

-- --------------------------------------------------------

--
-- Table structure for table `credential_shares`
--

DROP TABLE IF EXISTS `credential_shares`;
CREATE TABLE IF NOT EXISTS `credential_shares` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `token` char(64) NOT NULL,
  `code` char(6) NOT NULL,
  `requested_app` varchar(64) DEFAULT NULL,
  `payload_json` text DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `viewed_at` datetime DEFAULT NULL,
  `viewed_by_user_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_token` (`token`),
  KEY `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT;

-- --------------------------------------------------------

--
-- Table structure for table `saved_credentials`
--

DROP TABLE IF EXISTS `saved_credentials`;
CREATE TABLE IF NOT EXISTS `saved_credentials` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `app_name` varchar(64) NOT NULL,
  `label` varchar(128) NOT NULL,
  `fields_json` text NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT;

-- --------------------------------------------------------

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
CREATE TABLE IF NOT EXISTS `users` (
  `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `pending_email` varchar(255) DEFAULT NULL,
  `password_hash` varchar(255) NOT NULL,
  `email_verified` tinyint(1) NOT NULL DEFAULT 0,
  `verification_token` varchar(64) DEFAULT NULL,
  `verification_token_expires_at` datetime DEFAULT NULL,
  `email_change_token` varchar(64) DEFAULT NULL,
  `email_change_token_expires_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`),
  KEY `idx_email_change_token` (`email_change_token`)
) ENGINE=InnoDB DEFAULT;

-- --------------------------------------------------------

--
-- Table structure for table `default_credentials`
--
-- One remembered saved_credentials choice per (user, app) - lets
-- credential_view.php's pull-mode approval skip the picker and show a
-- single "Approve" button instead, once a user has picked "remember this"
-- for that app at least once. See credential_view.php for how it's used.
--

DROP TABLE IF EXISTS `default_credentials`;
CREATE TABLE IF NOT EXISTS `default_credentials` (
  `user_id` int(11) NOT NULL,
  `app_name` varchar(64) NOT NULL,
  `saved_credential_id` int(11) NOT NULL,
  PRIMARY KEY (`user_id`, `app_name`)
) ENGINE=InnoDB DEFAULT;

-- --------------------------------------------------------

--
-- Table structure for table `device_tokens`
--
-- A long-lived credential a device earns once via the normal QR/code pull
-- flow (see DEVICE_PAIR_APP_NAME in credential_view.php), then holds onto
-- so it can call device_backup.php/device_backups_list.php/
-- device_backup_get.php directly - no further human approval per
-- backup/restore, unlike everything else on this site. See
-- paired_devices.php to review/revoke these.
--

DROP TABLE IF EXISTS `device_tokens`;
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

COMMIT;
